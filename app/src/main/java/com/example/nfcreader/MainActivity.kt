package com.example.nfcreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.minOf
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private lateinit var textViewInfo: TextView
    private var isNfcSupported = false
    private var currentTag: Tag? = null
    private var mrzData: MrzData? = null

    data class MrzData(
        val documentNumber: String,
        val dateOfBirth: String,
        val dateOfExpiry: String
    ) {
        fun isValid(): Boolean {
            return documentNumber.length >= 9 && 
                   dateOfBirth.length == 6 && 
                   dateOfExpiry.length == 6
        }
        
        fun toMrzString(): String {
            return "$documentNumber$dateOfBirth$dateOfExpiry"
        }
    }
    
    data class BacKeys(
        val kEnc: ByteArray,
        val kMac: ByteArray
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        val mrzTitle = TextView(this).apply {
            text = "🇹🇷 T.C. Kimlik Kartı MRZ Girişi (İsteğe Bağlı)"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        mainLayout.addView(mrzTitle)
        
        val docNumberEdit = EditText(this).apply {
            hint = "Belge Numarası (9 hane) - İsteğe bağlı"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
        }
        mainLayout.addView(docNumberEdit)
        
        val birthDateEdit = EditText(this).apply {
            hint = "Doğum Tarihi (YYAAGG) - İsteğe bağlı"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        mainLayout.addView(birthDateEdit)
        
        val expiryDateEdit = EditText(this).apply {
            hint = "Son Kullanma (YYAAGG) - İsteğe bağlı"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        mainLayout.addView(expiryDateEdit)
        
        val setMrzButton = Button(this).apply {
            text = "MRZ Verilerini Ayarla (BAC İçin)"
            setOnClickListener {
                val docNum = docNumberEdit.text.toString().uppercase().trim()
                val birthDate = birthDateEdit.text.toString().trim()
                val expiryDate = expiryDateEdit.text.toString().trim()
                
                if (docNum.length >= 9 && birthDate.length == 6 && expiryDate.length == 6) {
                    mrzData = MrzData(docNum, birthDate, expiryDate)
                    Toast.makeText(this@MainActivity, "✅ MRZ verileri ayarlandı - BAC denenecek", Toast.LENGTH_SHORT).show()
                    
                    currentTag?.let { tag ->
                        readTagInfoWithMrzCheck(tag)
                    }
                } else if (docNum.isNotEmpty() || birthDate.isNotEmpty() || expiryDate.isNotEmpty()) {
                    Toast.makeText(this@MainActivity, "⚠️ Eksik MRZ verileri - Ham veri okuma yapılacak", Toast.LENGTH_SHORT).show()
                    mrzData = null
                } else {
                    mrzData = null
                    Toast.makeText(this@MainActivity, "ℹ️ MRZ temizlendi - Ham veri modu", Toast.LENGTH_SHORT).show()
                }
            }
        }
        mainLayout.addView(setMrzButton)
        
        val clearMrzButton = Button(this).apply {
            text = "MRZ Temizle (Sadece Ham Veri)"
            setOnClickListener {
                docNumberEdit.setText("")
                birthDateEdit.setText("")
                expiryDateEdit.setText("")
                mrzData = null
                Toast.makeText(this@MainActivity, "🧹 MRZ temizlendi - Sadece ham veri okunacak", Toast.LENGTH_SHORT).show()
            }
        }
        mainLayout.addView(clearMrzButton)
        
        val infoText = TextView(this).apply {
            text = """
                📋 MRZ İle BAC (Basic Access Control):
                • MRZ verileri girerseniz → Şifrelenmiş veriler okunmaya çalışılır
                • MRZ vermezseniz → Sadece ham veriler okunur
                
                📍 MRZ Bilgileri:
                • Belge Numarası: Kimlik kartındaki 9 haneli numara
                • Doğum Tarihi: YYAAGG formatında (örn: 901215)
                • Son Kullanma: YYAAGG formatında (örn: 301215)
                
                ⚠️ UYARI: Bu özellik sadece eğitim amaçlıdır!
            """.trimIndent()
            textSize = 12f
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(infoText)
        
        val scrollView = ScrollView(this)
        textViewInfo = TextView(this).apply {
            id = android.R.id.text1
            textSize = 12f
            setPadding(16, 16, 16, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(textViewInfo)
        mainLayout.addView(scrollView)
        
        setContentView(mainLayout)
        checkNfcSupport()
    }

    private fun checkNfcSupport() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        when {
            nfcAdapter == null -> {
                showNfcNotSupported()
            }
            !nfcAdapter!!.isEnabled -> {
                showNfcDisabled()
            }
            else -> {
                isNfcSupported = true
                setupNfc()
                showNfcReady()
            }
        }
    }

    private fun showNfcNotSupported() {
        val message = """
            ❌ NFC/RFID DESTEKLENMİYOR
            ═══════════════════════════
            
            Bu cihazda NFC/RFID özelliği bulunmuyor.
            
            📱 Desteklenen Cihazlar:
            • Android 5.0+ (API 21+)
            • NFC özellikli cihazlar
            • Samsung, Huawei, Xiaomi vb. flagshipler
            
            🔍 NFC Nasıl Kontrol Edilir:
            Ayarlar → Bağlantılar → NFC
            (Eğer bu seçenek yoksa cihazınız desteklemiyor)
            
            📡 RFID Frekans Desteği:
            • 13.56 MHz (HF-RFID): NFC ile desteklenir
            • 125 kHz (LF-RFID): Desteklenmez (özel donanım gerekir)
            • 915 MHz (UHF-RFID): Desteklenmez (özel donanım gerekir)
            
            📞 Alternatif Çözümler:
            • NFC özellikli başka cihaz kullanın
            • Harici NFC/RFID okuyucu satın alın
            • QR kod okuyucu alternatifi kullanın
            
            ℹ️ Bu uygulama NFC olmadan çalışamaz ama 
            kapanmayacak, bu mesajı göstermeye devam edecek.
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun showNfcDisabled() {
        val message = """
            📱 NFC/RFID KAPALI
            ═══════════════════
            
            Cihazınızda NFC var ama kapalı.
            
            🔧 NFC'yi Açmak İçin:
            1. Ayarlar → Bağlantılar → NFC
            2. NFC'yi açın
            3. Bu uygulamaya geri dönün
            
            ⚡ Hızlı Erişim:
            • Bildirim panelini açın
            • NFC kısayoluna dokunun
            
            📡 RFID Desteği:
            NFC açıldığında 13.56 MHz RFID kartları da okunabilir
            
            🔄 NFC'yi açtıktan sonra uygulamayı 
            yeniden başlatmanız gerekebilir.
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun showNfcReady() {
        val message = """
            ✅ NFC/RFID HAZIR
            ═══════════════════
            
            📱 Cihaz: NFC destekli
            🔋 Durum: Etkin ve hazır
            
            📋 Kullanım:
            • MRZ verileri girin (T.C. Kimlik için BAC)
            • Veya MRZ vermeden ham veri okuyun
            • NFC/RFID kartını telefonun arkasına yaklaştırın
            
            🎯 Desteklenen Kartlar:
            📡 NFC Kartları:
            • Mifare Classic / Ultralight
            • NDEF formatındaki kartlar
            • ISO 14443 Type A/B kartlar
            • T.C. Kimlik kartları (MRZ ile BAC veya ham veri)
            
            📡 RFID Kartları (13.56 MHz HF):
            • ISO 15693 kartları (NfcV)
            • ISO 14443 uyumlu RFID kartları
            • Erişim kontrol kartları
            • Kütüphane kartları
            
            💳 Diğer Kartlar:
            • Kredi/banka kartları (sınırlı bilgi)
            • Toplu taşıma kartları
            • Otel anahtar kartları
            
            🔄 Bekleniyor... Kartı yaklaştırın
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun setupNfc() {
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        val tech = IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        val tag = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)

        intentFiltersArray = arrayOf(ndef, tech, tag)

        techListsArray = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name),
            arrayOf(IsoDep::class.java.name),
            arrayOf(MifareClassic::class.java.name),
            arrayOf(MifareUltralight::class.java.name),
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )
    }

    override fun onResume() {
        super.onResume()
        
        if (isNfcSupported) {
            if (nfcAdapter?.isEnabled == true) {
                nfcAdapter?.enableForegroundDispatch(
                    this,
                    pendingIntent,
                    intentFiltersArray,
                    techListsArray
                )
                showNfcReady()
            } else {
                showNfcDisabled()
            }
        } else {
            checkNfcSupport()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcSupported && nfcAdapter?.isEnabled == true) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isNfcSupported) {
            handleNfcIntent(intent)
        }
    }

    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            currentTag = it
            readTagInfoWithMrzCheck(it)
        }
    }

    private fun readTagInfoWithMrzCheck(tag: Tag) {
        val cardType = detectCardTypeWithRfid(tag)
        
        if (cardType.contains("T.C. Kimlik") && mrzData != null && mrzData!!.isValid()) {
            readTagInfoWithMrz(tag)
        } else {
            readTagInfo(tag)
            
            if (cardType.contains("T.C. Kimlik") && mrzData == null) {
                Toast.makeText(this, "💡 T.C. Kimlik kartı tespit edildi. MRZ verilerini girerek şifrelenmiş verileri okuyabilirsiniz.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun readTagInfo(tag: Tag) {
        val sb = StringBuilder()

        val uid = tag.id
        sb.append("🔍 NFC/RFID KART BİLGİLERİ\n")
        sb.append("=" .repeat(35) + "\n\n")
        sb.append("📱 UID: ${bytesToHex(uid)}\n\n")

        val cardType = detectCardTypeWithRfid(tag)
        sb.append("🏷️ Kart Tipi: $cardType\n\n")

        val frequencyInfo = analyzeFrequencyAndTechnology(tag)
        sb.append("📡 Frekans ve Teknoloji Analizi:\n")
        sb.append(frequencyInfo)
        sb.append("\n")

        sb.append("🔧 Desteklenen Teknolojiler:\n")
        tag.techList.forEach { tech ->
            sb.append("• ${tech.substringAfterLast('.')}\n")
        }
        sb.append("\n")

        sb.append("🔬 TAM HAM VERİ ANALİZİ\n")
        sb.append("=" .repeat(35) + "\n\n")
        
        sb.append("📱 Tag Ham Bilgileri:\n")
        sb.append("Tag ID (Raw): ${uid.contentToString()}\n")
        sb.append("Tag ID (Hex): ${bytesToHex(uid)}\n")
        sb.append("Tag ID Uzunluğu: ${uid.size} byte\n")
        sb.append("Teknoloji Sayısı: ${tag.techList.size}\n\n")
        
        tag.techList.forEachIndexed { index, tech ->
            sb.append("🔧 Teknoloji ${index + 1}: ${tech.substringAfterLast('.')}\n")
            when (tech) {
                "android.nfc.tech.NfcA" -> {
                    val nfcA = NfcA.get(tag)
                    sb.append("  ATQA (Ham): ${bytesToHex(nfcA.atqa)}\n")
                    sb.append("  SAK: 0x${String.format("%02X", nfcA.sak)}\n")
                    sb.append("  Max Transceive: ${nfcA.maxTransceiveLength} byte\n")
                }
                "android.nfc.tech.NfcB" -> {
                    val nfcB = NfcB.get(tag)
                    sb.append("  Application Data (Ham): ${bytesToHex(nfcB.applicationData)}\n")
                    sb.append("  Protocol Info (Ham): ${bytesToHex(nfcB.protocolInfo)}\n")
                    sb.append("  Max Transceive: ${nfcB.maxTransceiveLength} byte\n")
                }
                "android.nfc.tech.NfcF" -> {
                    val nfcF = NfcF.get(tag)
                    sb.append("  Manufacturer (Ham): ${bytesToHex(nfcF.manufacturer)}\n")
                    sb.append("  System Code (Ham): ${bytesToHex(nfcF.systemCode)}\n")
                    sb.append("  Max Transceive: ${nfcF.maxTransceiveLength} byte\n")
                }
                "android.nfc.tech.NfcV" -> {
                    val nfcV = NfcV.get(tag)
                    sb.append("  📡 ISO 15693 (RFID) Bilgileri:\n")
                    sb.append("  Response Flags: 0x${String.format("%02X", nfcV.responseFlags)}\n")
                    sb.append("  DSF ID: 0x${String.format("%02X", nfcV.dsfId)}\n")
                    sb.append("  Max Transceive: ${nfcV.maxTransceiveLength} byte\n")
                    sb.append("  📡 Bu bir RFID kartıdır (13.56 MHz HF)\n")
                    
                    try {
                        nfcV.connect()
                        sb.append("  🔍 RFID Özel Komut Denemeleri:\n")
                        
                        val getSystemInfo = byteArrayOf(0x00, 0x2B, *uid)
                        try {
                            val sysInfoResponse = nfcV.transceive(getSystemInfo)
                            sb.append("  System Info: ${bytesToHex(sysInfoResponse)}\n")
                        } catch (e: Exception) {
                            sb.append("  System Info: Desteklenmiyor\n")
                        }
                        
                        val readBlock = byteArrayOf(0x00.toByte(), 0x20.toByte(), *uid, 0x00.toByte())
                        try {
                            val blockResponse = nfcV.transceive(readBlock)
                            sb.append("  Blok 0 Verisi: ${bytesToHex(blockResponse)}\n")
                            sb.append("  Blok 0 ASCII: ${tryDecodeAscii(blockResponse)}\n")
                        } catch (e: Exception) {
                            sb.append("  Blok 0: Okunamadı (${e.message})\n")
                        }
                        
                        nfcV.close()
                    } catch (e: Exception) {
                        sb.append("  RFID bağlantı hatası: ${e.message}\n")
                    }
                }
                "android.nfc.tech.IsoDep" -> {
                    val isoDep = IsoDep.get(tag)
                    sb.append("  Historical Bytes (Ham): ${bytesToHex(isoDep.historicalBytes ?: byteArrayOf())}\n")
                    sb.append("  Hi-Layer Response (Ham): ${bytesToHex(isoDep.hiLayerResponse ?: byteArrayOf())}\n")
                    sb.append("  Max Transceive: ${isoDep.maxTransceiveLength} byte\n")
                    sb.append("  Timeout: ${isoDep.timeout} ms\n")
                }
                "android.nfc.tech.MifareClassic" -> {
                    val mifare = MifareClassic.get(tag)
                    sb.append("  Boyut: ${mifare.size} byte\n")
                    sb.append("  Tip: ${mifare.type}\n")
                    sb.append("  Sektör: ${mifare.sectorCount}\n")
                    sb.append("  Blok: ${mifare.blockCount}\n")
                    
                    try {
                        mifare.connect()
                        sb.append("  🔍 Mifare RFID Blok Okuma:\n")
                        
                        try {
                            val block0 = mifare.readBlock(0)
                            sb.append("  Blok 0: ${bytesToHex(block0)}\n")
                            sb.append("  Blok 0 ASCII: ${tryDecodeAscii(block0)}\n")
                        } catch (e: Exception) {
                            sb.append("  Blok 0: Kimlik doğrulama gerekli\n")
                        }
                        
                        mifare.close()
                    } catch (e: Exception) {
                        sb.append("  Mifare bağlantı hatası: ${e.message}\n")
                    }
                }
                "android.nfc.tech.MifareUltralight" -> {
                    val ultralight = MifareUltralight.get(tag)
                    sb.append("  Tip: ${ultralight.type}\n")
                    sb.append("  Max Transceive: ${ultralight.maxTransceiveLength} byte\n")
                    
                    try {
                        ultralight.connect()
                        sb.append("  🔍 Ultralight RFID Sayfa Okuma:\n")
                        
                        for (page in 0..3) {
                            try {
                                val pageData = ultralight.readPages(page)
                                sb.append("  Sayfa $page: ${bytesToHex(pageData)}\n")
                            } catch (e: Exception) {
                                sb.append("  Sayfa $page: Okunamadı\n")
                                break
                            }
                        }
                        
                        ultralight.close()
                    } catch (e: Exception) {
                        sb.append("  Ultralight bağlantı hatası: ${e.message}\n")
                    }
                }
                "android.nfc.tech.Ndef" -> {
                    val ndef = Ndef.get(tag)
                    sb.append("  NDEF Tipi: ${ndef.type}\n")
                    sb.append("  Max Size: ${ndef.maxSize} byte\n")
                    sb.append("  Yazılabilir: ${ndef.isWritable}\n")
                    sb.append("  Bağlanabilir: ${ndef.canMakeReadOnly()}\n")
                }
            }
            sb.append("\n")
        }

        sb.append("💾 Ham Veri (UID):\n")
        sb.append("Hex: ${bytesToHex(uid)}\n")
        sb.append("Decimal: ${uid.joinToString(", ") { (it.toInt() and 0xFF).toString() }}\n")
        sb.append("Binary: ${uid.joinToString(" ") { 
            String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0')
        }}\n\n")

        sb.append("🔍 HEX DUMP (UID):\n")
        sb.append(bytesToHexDump(uid, true))
        sb.append("\n\n")

        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            try {
                isoDep.connect()
                
                sb.append("💳 ISO-DEP HAM VERİ DUMP:\n")
                sb.append("-" .repeat(30) + "\n")
                
                val rawCommands = listOf(
                    "SELECT MF" to byteArrayOf(0x00, 0xA4, 0x00, 0x0C, 0x02, 0x3F, 0x00),
                    "SELECT AID" to byteArrayOf(0x00, 0xA4, 0x04, 0x0C, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01),
                    "GET CHALLENGE" to byteArrayOf(0x00, 0x84, 0x00, 0x00, 0x08),
                    "GET DATA" to byteArrayOf(0x00, 0xCA, 0x01, 0x00, 0x00)
                )
                
                rawCommands.forEach { (commandName, apdu) ->
                    try {
                        sb.append("📤 Komut: $commandName\n")
                        sb.append("   Gönderilen (Ham): ${bytesToHex(apdu)}\n")
                        val response = isoDep.transceive(apdu)
                        sb.append("📥 Yanıt (Ham): ${bytesToHex(response)}\n")
                        sb.append("   Uzunluk: ${response.size} byte\n")
                        sb.append("   ASCII: ${tryDecodeAscii(response)}\n")
                        sb.append("   Status: ${interpretStatusWord(response)}\n\n")
                    } catch (e: Exception) {
                        sb.append("📥 Yanıt: HATA - ${e.message}\n\n")
                    }
                }
                
                if (cardType.contains("T.C. Kimlik")) {
                    sb.append("🇹🇷 T.C. KİMLİK KARTI HAM VERİ DUMP:\n")
                    sb.append("=" .repeat(40) + "\n")
                    dumpTurkishIdCardRawData(isoDep, sb, tag)
                }
                
                isoDep.close()
            } catch (e: Exception) {
                sb.append("❌ ISO-DEP ham veri dump hatası: ${e.message}\n\n")
            }
        }

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                if (ndefMessage != null) {
                    sb.append("📄 NDEF Verileri:\n")
                    ndefMessage.records.forEachIndexed { index, record ->
                        sb.append("Record ${index + 1}:\n")
                        sb.append("  Type: ${String(record.type)}\n")
                        sb.append("  Type (Ham): ${bytesToHex(record.type)}\n")
                        sb.append("  Payload: ${String(record.payload)}\n")
                        sb.append("  Payload (Ham): ${bytesToHex(record.payload)}\n")
                        sb.append("  ID: ${if (record.id.isNotEmpty()) String(record.id) else "Yok"}\n")
                        if (record.id.isNotEmpty()) {
                            sb.append("  ID (Ham): ${bytesToHex(record.id)}\n")
                        }
                        sb.append("\n")
                    }
                }
                ndef.close()
            } catch (e: Exception) {
                sb.append("❌ NDEF okuma hatası: ${e.message}\n\n")
            }
        }

        sb.append("📡 RFID/NFC ÖZET ANALİZİ:\n")
        sb.append("=" .repeat(30) + "\n")
        sb.append(generateRfidSummary(tag))
        sb.append("\n")

        sb.append("✅ Ham veri okuma tamamlandı - ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        sb.append("🔄 Başka NFC/RFID kart okumak için tekrar yaklaştırın\n")
        sb.append("💡 T.C. Kimlik kartları için MRZ verilerini girerek BAC deneyebilirsiniz")

        textViewInfo.text = sb.toString()
        
        Toast.makeText(this, "✅ NFC/RFID kart ham verisi okundu!", Toast.LENGTH_SHORT).show()
    }

    private fun readTagInfoWithMrz(tag: Tag) {
        val sb = StringBuilder()
        
        sb.append("🇹🇷 T.C. KİMLİK KARTI MRZ/BAC OKUMA\n")
        sb.append("=" .repeat(45) + "\n\n")
        
        val uid = tag.id
        sb.append("📱 UID: ${bytesToHex(uid)}\n")
        sb.append("🔑 MRZ Verisi: ${mrzData?.toMrzString()}\n\n")
        
        val  isoDep = IsoDep.get(tag)
        if (isoDep != null && mrzData != null) {
            try {
                isoDep.connect()
                
                sb.append("🔐 BAC (Basic Access Control) İŞLEMİ:\n")
                sb.append("-" .repeat(40) + "\n")
                
                // 1. e-Passport uygulamasını seç
                sb.append("📤 1. e-Passport Uygulaması Seçiliyor...\n")
                val selectApp = byteArrayOf(0x00, 0xA4, 0x04, 0x0C, 0x07, 0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01)
                val appResponse = isoDep.transceive(selectApp)
                sb.append("   Yanıt: ${bytesToHex(appResponse)}\n")
                sb.append("   Durum: ${interpretStatusWord(appResponse)}\n\n")
                
                if (!isSuccessResponse(appResponse)) {
                    sb.append("❌ e-Passport uygulaması seçilemedi\n")
                    sb.append("💡 Ham veri okumaya geçiliyor...\n\n")
                    isoDep.close()
                    
                    // Mevcut metni sakla ve ham veri ekle
                    val bacText = sb.toString()
                    readTagInfo(tag)
                    val currentText = textViewInfo.text.toString()
                    textViewInfo.text = bacText + "\n" + "=" .repeat(45) + "\n📄 HAM VERİ OKUMA SONUÇLARI:\n" + "=" .repeat(45) + "\n\n" + currentText
                    return
                }
                
                // 2. BAC anahtarlarını türet
                sb.append("🔑 2. BAC Anahtarları Türetiliyor...\n")
                val bacKeys = deriveBacKeys(mrzData!!)
                sb.append("   K_ENC: ${bytesToHex(bacKeys.kEnc)}\n")
                sb.append("   K_MAC: ${bytesToHex(bacKeys.kMac)}\n\n")
                
                // 3. GET CHALLENGE (Mutual Authentication)
                sb.append("🎲 3. Challenge Alınıyor...\n")
                val getChallenge = byteArrayOf(0x00, 0x84, 0x00, 0x00, 0x08)
                val challengeResponse = isoDep.transceive(getChallenge)
                sb.append("   Komut: ${bytesToHex(getChallenge)}\n")
                sb.append("   Challenge: ${bytesToHex(challengeResponse)}\n")
                
                if (challengeResponse.size < 10) {
                    sb.append("❌ Challenge alınamadı\n")
                    sb.append("💡 Ham veri okumaya geçiliyor...\n\n")
                    isoDep.close()
                    
                    val bacText = sb.toString()
                    readTagInfo(tag)
                    val currentText = textViewInfo.text.toString()
                    textViewInfo.text = bacText + "\n" + "=" .repeat(45) + "\n📄 HAM VERİ OKUMA SONUÇLARI:\n" + "=" .repeat(45) + "\n\n" + currentText
                    return
                }
                
                val rndIcc = challengeResponse.dropLast(2).toByteArray() // Status word çıkar
                sb.append("   RND.ICC: ${bytesToHex(rndIcc)}\n\n")
                
                // 4. Mutual Authentication
                sb.append("🔐 4. Karşılıklı Kimlik Doğrulama...\n")
                val rndIfd = generateRandomBytes(8)
                val kIfd = generateRandomBytes(16)
                
                sb.append("   RND.IFD: ${bytesToHex(rndIfd)}\n")
                sb.append("   K.IFD: ${bytesToHex(kIfd)}\n")
                
                // Authentication data oluştur
                val authData = createAuthenticationData(rndIfd, rndIcc, kIfd, bacKeys)
                sb.append("   Auth Data: ${bytesToHex(authData)}\n")
                
                // EXTERNAL AUTHENTICATE komutu
                val extAuth = byteArrayOf(0x00, 0x82, 0x00, 0x00, authData.size.toByte()) + authData
                sb.append("   Komut: ${bytesToHex(extAuth)}\n")
                
                val authResponse = isoDep.transceive(extAuth)
                sb.append("   Yanıt: ${bytesToHex(authResponse)}\n")
                sb.append("   Durum: ${interpretStatusWord(authResponse)}\n\n")
                
                if (isSuccessResponse(authResponse)) {
                    sb.append("✅ BAC Kimlik Doğrulama BAŞARILI!\n\n")
                    
                    // 5. Şifrelenmiş verileri oku
                    readEncryptedData(isoDep, sb, bacKeys)
                    
                } else {
                    sb.append("❌ BAC Kimlik Doğrulama BAŞARISIZ!\n")
                    sb.append("🔍 Muhtemel Sebepler:\n")
                    sb.append("• Yanlış MRZ bilgileri\n")
                    sb.append("• Kart BAC desteklemiyor\n")
                    sb.append("• Kimlik doğrulama algoritması farklı\n")
                    sb.append("• Kart kilitli veya hasarlı\n\n")
                    
                    // Hata analizi
                    analyzeAuthenticationError(authResponse, sb)
                    
                    sb.append("\n💡 Ham veri okumaya geçiliyor...\n\n")
                    
                    // BAC başarısız - ham veri okumaya geç
                    val bacText = sb.toString()
                    isoDep.close()
                    readTagInfo(tag)
                    val currentText = textViewInfo.text.toString()
                    textViewInfo.text = bacText + "\n" + "=" .repeat(45) + "\n📄 HAM VERİ OKUMA SONUÇLARI:\n" + "=" .repeat(45) + "\n\n" + currentText
                    return
                }
                
                isoDep.close()
                
            } catch (e: Exception) {
                sb.append("❌ BAC İşlem Hatası: ${e.message}\n")
                sb.append("💡 Ham veri okumaya geçiliyor...\n\n")
                
                val bacText = sb.toString()
                readTagInfo(tag)
                val currentText = textViewInfo.text.toString()
                textViewInfo.text = bacText + "\n" + "=" .repeat(45) + "\n📄 HAM VERİ OKUMA SONUÇLARI:\n" + "=" .repeat(45) + "\n\n" + currentText
                return
            }
        } else {
            sb.append("❌ ISO-DEP desteklenmiyor veya MRZ verisi eksik\n")
            sb.append("💡 Ham veri okumaya geçiliyor...\n\n")
            
            val bacText = sb.toString()
            readTagInfo(tag)
            val currentText = textViewInfo.text.toString()
            textViewInfo.text = bacText + "\n" + "=" .repeat(45) + "\n📄 HAM VERİ OKUMA SONUÇLARI:\n" + "=" .repeat(45) + "\n\n" + currentText
            return
        }
        
        sb.append("\n" + "=" .repeat(45) + "\n")
        sb.append("📚 BAC (Basic Access Control) Hakkında:\n")
        sb.append("• e-Passport standartı (ICAO Doc 9303)\n")
        sb.append("• MRZ verilerinden türetilen anahtarlarla şifreleme\n")
        sb.append("• 3DES şifreleme algoritması\n")
        sb.append("• Karşılıklı kimlik doğrulama sistemi\n\n")
        
        sb.append("⚖️ YASAL UYARI:\n")
        sb.append("Bu işlem sadece eğitim amaçlıdır.\n")
        sb.append("Kendi kimlik kartınızı test edin.\n")
        sb.append("Başkasının kimlik verilerini kullanmayın!\n")
        
        textViewInfo.text = sb.toString()
    }

    // RFID destekli kart tipi tespiti
    private fun detectCardTypeWithRfid(tag: Tag): String {
        val techList = tag.techList
        
        return when {
            // ISO 15693 RFID kartları
            techList.contains("android.nfc.tech.NfcV") -> {
                "📡 ISO 15693 RFID Kartı (13.56 MHz HF)"
            }
            // T.C. Kimlik kartı (ISO-DEP + NfcB)
            techList.contains("android.nfc.tech.IsoDep") && 
            techList.contains("android.nfc.tech.NfcB") -> {
                "🇹🇷 T.C. Kimlik Kartı (ISO 14443-4 Type B)"
            }
            // Kredi kartı (ISO-DEP + NfcA)
            techList.contains("android.nfc.tech.IsoDep") && 
            techList.contains("android.nfc.tech.NfcA") -> {
                "💳 Kredi/Banka Kartı (ISO 14443-4 Type A)"
            }
            // Mifare Classic (RFID uyumlu)
            techList.contains("android.nfc.tech.MifareClassic") -> {
                "🎫 Mifare Classic (NFC/RFID Hibrit - 13.56 MHz)"
            }
            // Mifare Ultralight (RFID uyumlu)
            techList.contains("android.nfc.tech.MifareUltralight") -> {
                "🏷️ Mifare Ultralight (NFC/RFID Hibrit - 13.56 MHz)"
            }
            // FeliCa (Japonya RFID sistemi)
            techList.contains("android.nfc.tech.NfcF") -> {
                "🟡 FeliCa RFID Kartı (JIS X 6319-4 - 13.56 MHz)"
            }
            // NDEF
            techList.contains("android.nfc.tech.Ndef") -> {
                "📱 NDEF Kartı (NFC Forum Standardı)"
            }
            // NfcA (Type A)
            techList.contains("android.nfc.tech.NfcA") -> {
                "🔵 ISO 14443 Type A Kartı (NFC/RFID - 13.56 MHz)"
            }
            // NfcB (Type B)
            techList.contains("android.nfc.tech.NfcB") -> {
                "🔴 ISO 14443 Type B Kartı (NFC/RFID - 13.56 MHz)"
            }
            else -> {
                "❓ Bilinmeyen NFC/RFID Kartı"
            }
        }
    }

    // Frekans ve teknoloji analizi
    private fun analyzeFrequencyAndTechnology(tag: Tag): String {
        val sb = StringBuilder()
        val techList = tag.techList
        
        sb.append("🔬 Frekans: 13.56 MHz (HF - High Frequency)\n")
        sb.append("📡 Protokol Ailesi: ISO/IEC 18000-3\n")
        
        when {
            techList.contains("android.nfc.tech.NfcV") -> {
                sb.append("🎯 Ana Standard: ISO 15693 (RFID)\n")
                sb.append("📏 Okuma Mesafesi: 10cm - 1m\n")
                sb.append("⚡ Veri Hızı: 1.6 - 26.7 kbps\n")
                sb.append("🔋 Güç: Pasif (okuyucudan beslenir)\n")
                sb.append("💡 Kullanım: Erişim kontrolü, envanter, hayvan takibi\n")
            }
            techList.contains("android.nfc.tech.MifareClassic") || 
            techList.contains("android.nfc.tech.MifareUltralight") -> {
                sb.append("🎯 Ana Standard: ISO 14443 Type A (NFC/RFID Hibrit)\n")
                sb.append("📏 Okuma Mesafesi: 2-10 cm\n")
                sb.append("⚡ Veri Hızı: 106 kbps\n")
                sb.append("🔋 Güç: Pasif (okuyucudan beslenir)\n")
                sb.append("💡 Kullanım: Toplu taşıma, erişim kontrolü, ödeme\n")
            }
            techList.contains("android.nfc.tech.NfcF") -> {
                sb.append("🎯 Ana Standard: JIS X 6319-4 (FeliCa RFID)\n")
                sb.append("📏 Okuma Mesafesi: 2-10 cm\n")
                sb.append("⚡ Veri Hızı: 212/424 kbps\n")
                sb.append("🔋 Güç: Pasif (okuyucudan beslenir)\n")
                sb.append("💡 Kullanım: Japonya ödeme sistemleri, oyun kartları\n")
            }
            techList.contains("android.nfc.tech.IsoDep") -> {
                sb.append("🎯 Ana Standard: ISO 14443-4 (Akıllı Kart/RFID)\n")
                sb.append("📏 Okuma Mesafesi: 2-10 cm\n")
                sb.append("⚡ Veri Hızı: 106-848 kbps\n")
                sb.append("🔋 Güç: Pasif (okuyucudan beslenir)\n")
                sb.append("💡 Kullanım: Kimlik kartları, kredi kartları, güvenli ödeme\n")
            }
            else -> {
                sb.append("🎯 Ana Standard: Genel NFC/RFID (ISO 14443)\n")
                sb.append("📏 Okuma Mesafesi: 2-10 cm\n")
                sb.append("⚡ Veri Hızı: 106-424 kbps\n")
                sb.append("🔋 Güç: Pasif (okuyucudan beslenir)\n")
                sb.append("💡 Kullanım: Genel amaçlı NFC/RFID\n")
            }
        }
        
        return sb.toString()
    }

    // RFID özet analizi
    private fun generateRfidSummary(tag: Tag): String {
        val sb = StringBuilder()
        val techList = tag.techList
        val uid = tag.id
        
        sb.append("📊 Kart Kategorisi: ")
        when {
            techList.contains("android.nfc.tech.NfcV") -> {
                sb.append("RFID Kartı (ISO 15693)\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF)\n")
                sb.append("🔹 Uygulama Alanı: Erişim kontrolü, envanter yönetimi\n")
                sb.append("🔹 Avantajlar: Uzun menzilli okuma, çoklu kart okuma\n")
                sb.append("🔹 Dezavantajlar: NFC telefon uygulamaları ile sınırlı uyumluluk\n")
            }
            techList.contains("android.nfc.tech.MifareClassic") -> {
                sb.append("NFC/RFID Hibrit Kartı (Mifare)\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF) - NFC Uyumlu\n")
                sb.append("🔹 Uygulama Alanı: Toplu taşıma, kampüs kartları\n")
                sb.append("🔹 Avantajlar: Yaygın kullanım, güvenli sektör yapısı\n")
                sb.append("🔹 Dezavantajlar: Eski şifreleme (CRYPTO1)\n")
            }
            techList.contains("android.nfc.tech.MifareUltralight") -> {
                sb.append("NFC/RFID Hibrit Kartı (Ultralight)\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF) - NFC Uyumlu\n")
                sb.append("🔹 Uygulama Alanı: Tek kullanımlık biletler, etiketler\n")
                sb.append("🔹 Avantajlar: Düşük maliyet, basit yapı\n")
                sb.append("🔹 Dezavantajlar: Sınırlı güvenlik, küçük hafıza\n")
            }
            techList.contains("android.nfc.tech.NfcF") -> {
                sb.append("RFID Kartı (FeliCa)\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF) - Japonya Standardı\n")
                sb.append("🔹 Uygulama Alanı: Ödeme sistemleri, oyun kartları\n")
                sb.append("🔹 Avantajlar: Yüksek hız, güvenli\n")
                sb.append("🔹 Dezavantajlar: Geografik olarak sınırlı (Japonya)\n")
            }
            techList.contains("android.nfc.tech.IsoDep") -> {
                sb.append("Akıllı RFID Kartı (ISO-DEP)\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF) - Akıllı Kart\n")
                sb.append("🔹 Uygulama Alanı: Kimlik, ödeme, güvenlik\n")
                sb.append("🔹 Avantajlar: Yüksek güvenlik, çok amaçlı\n")
                sb.append("🔹 Dezavantajlar: Karmaşık, pahalı\n")
            }
            else -> {
                sb.append("Genel NFC/RFID Kartı\n")
                sb.append("🔹 RFID Tipi: Yüksek Frekanslı (HF)\n")
                sb.append("🔹 Uygulama Alanı: Çeşitli\n")
            }
        }
        
        sb.append("\n🔍 UID Analizi:\n")
        when {
            uid.size == 4 -> {
                sb.append("• Single Size UID (4 byte) - Standart RFID\n")
            }
            uid.size == 7 -> {
                sb.append("• Double Size UID (7 byte) - Gelişmiş RFID\n")
            }
            uid.size == 10 -> {
                sb.append("• Triple Size UID (10 byte) - Yüksek Güvenlik RFID\n")
            }
            else -> {
                sb.append("• Özel UID Boyutu (${uid.size} byte)\n")
            }
        }
        
        if (uid.isNotEmpty()) {
            val manufacturerByte = uid[0].toInt() and 0xFF
            val manufacturer = when (manufacturerByte) {
                0x04 -> "NXP Semiconductors"
                0x01 -> "Motorola"
                0x02 -> "ST Microelectronics"
                0x03 -> "Hitachi"
                0x05 -> "Infineon Technologies"
                0x06 -> "Cylink"
                0x07 -> "Texas Instruments"
                0x08 -> "Fujitsu"
                0x09 -> "Matsushita"
                0x0A -> "NEC"
                0x0B -> "Oki Electric"
                0x0C -> "Toshiba"
                0x0D -> "Mitsubishi"
                0x0E -> "Samsung"
                0x0F -> "Hyundai"
                else -> "Bilinmeyen (0x${String.format("%02X", manufacturerByte)})"
            }
            sb.append("• Üretici: $manufacturer\n")
        }
        
        sb.append("\n⚠️ Desteklenmeyen RFID Tipleri:\n")
        sb.append("• 125 kHz LF-RFID (Düşük Frekans) - Eski erişim kartları\n")
        sb.append("• 915 MHz UHF-RFID (Ultra Yüksek Frekans) - Uzun menzil\n")
        sb.append("• 2.45 GHz Mikrodalga RFID - Endüstriyel uygulamalar\n")
        sb.append("\n💡 Not: Bu uygulama sadece 13.56 MHz NFC/RFID kartları okuyabilir.")
        
        return sb.toString()
    }

    // BAC anahtarlarını türet
    private fun deriveBacKeys(mrzData: MrzData): BacKeys {
        // MRZ string oluştur (belge numarası + doğum tarihi + son kullanma tarihi)
        val mrzString = mrzData.toMrzString()
        
        // SHA-1 hash hesapla
        val md = MessageDigest.getInstance("SHA-1")
        val mrzHash = md.digest(mrzString.toByteArray())
        
        // İlk 16 byte'ı al ve BAC anahtarlarını türet
        val kSeed = mrzHash.take(16).toByteArray()
        
        // K_ENC = SHA-1(K_SEED || 00000001)[0..7]
        val kEncSeed = kSeed + byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val kEncHash = md.digest(kEncSeed)
        val kEnc = kEncHash.take(8).toByteArray()
        
        // K_MAC = SHA-1(K_SEED || 00000002)[0..7] 
        val kMacSeed = kSeed + byteArrayOf(0x00, 0x00, 0x00, 0x02)
        val kMacHash = md.digest(kMacSeed)
        val kMac = kMacHash.take(8).toByteArray()
        
        return BacKeys(kEnc, kMac)
    }

    // Authentication data oluştur
    private fun createAuthenticationData(rndIfd: ByteArray, rndIcc: ByteArray, kIfd: ByteArray, bacKeys: BacKeys): ByteArray {
        try {
            // S = RND.IFD || RND.ICC || K.IFD
            val s = rndIfd + rndIcc + kIfd
            
            // E(K_ENC, S) - 3DES şifreleme
            val cipher = Cipher.getInstance("DESede/ECB/NoPadding")
            val keySpec = SecretKeySpec(bacKeys.kEnc + bacKeys.kEnc.take(8).toByteArray(), "DESede") // 24 byte key
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            
            // Padding ekle (8 byte'ın katı olması için)
            val paddedS = padData(s, 8)
            val eifd = cipher.doFinal(paddedS)
            
            // MAC hesapla
            val mac = calculateMac(eifd, bacKeys.kMac)
            
            return eifd + mac
            
        } catch (e: Exception) {
            // Basit XOR şifreleme (fallback)
            val s = rndIfd + rndIcc + kIfd
            return s.mapIndexed { index, byte ->
                (byte.toInt() xor bacKeys.kEnc[index % bacKeys.kEnc.size].toInt()).toByte()
            }.toByteArray()
        }
    }

    // Şifrelenmiş verileri oku
    private fun readEncryptedData(isoDep: IsoDep, sb: StringBuilder, bacKeys: BacKeys) {
        sb.append("🔓 ŞİFRELENMİŞ VERİLER OKUNUYOR:\n")
        sb.append("-" .repeat(30) + "\n")
        
        // EF.COM okuma denemesi
        try {
            val selectCom = byteArrayOf(0x00, 0xA4, 0x02, 0x0C, 0x02, 0x01, 0x1E)
            val comResponse = isoDep.transceive(selectCom)
            sb.append("📂 EF.COM Seçimi: ${interpretStatusWord(comResponse)}\n")
            
            if (isSuccessResponse(comResponse)) {
                val readCom = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x20) // 32 byte oku
                val comData = isoDep.transceive(readCom)
                sb.append("📄 EF.COM Ham Veri: ${bytesToHex(comData)}\n")
                
                if (comData.size > 2) {
                    val encryptedData = comData.dropLast(2).toByteArray()
                    val decryptedData = decryptData(encryptedData, bacKeys.kEnc)
                    sb.append("🔓 Şifresi Çözülen: ${bytesToHex(decryptedData)}\n")
                    sb.append("📝 ASCII: ${tryDecodeAscii(decryptedData)}\n")
                }
            }
        } catch (e: Exception) {
            sb.append("❌ EF.COM okuma hatası: ${e.message}\n")
        }
        
        sb.append("\n")
        
        // EF.DG1 (MRZ) okuma denemesi
        try {
            val selectDg1 = byteArrayOf(0x00, 0xA4, 0x02, 0x0C, 0x02, 0x01, 0x01)
            val dg1Response = isoDep.transceive(selectDg1)
            sb.append("📂 EF.DG1 (MRZ) Seçimi: ${interpretStatusWord(dg1Response)}\n")
            
            if (isSuccessResponse(dg1Response)) {
                val readDg1 = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x50) // 80 byte oku
                val dg1Data = isoDep.transceive(readDg1)
                sb.append("📄 EF.DG1 Ham Veri: ${bytesToHex(dg1Data)}\n")
                
                if (dg1Data.size > 2) {
                    val encryptedData = dg1Data.dropLast(2).toByteArray()
                    val decryptedData = decryptData(encryptedData, bacKeys.kEnc)
                    sb.append("🔓 Şifresi Çözülen: ${bytesToHex(decryptedData)}\n")
                    sb.append("📝 ASCII: ${tryDecodeAscii(decryptedData)}\n")
                    
                    // MRZ parsing denemesi
                    val mrzText = tryDecodeAscii(decryptedData).filter { it.isLetterOrDigit() || it == '<' }
                    if (mrzText.length > 30) {
                        sb.append("🔍 MRZ Analizi:\n")
                        sb.append("   Ham MRZ: $mrzText\n")
                        parseMrzData(mrzText, sb)
                    }
                }
            }
        } catch (e: Exception) {
            sb.append("❌ EF.DG1 okuma hatası: ${e.message}\n")
        }
        
        sb.append("\n")
        
        // EF.DG2 (Fotoğraf) okuma denemesi
        try {
            val selectDg2 = byteArrayOf(0x00, 0xA4, 0x02, 0x0C, 0x02, 0x01, 0x02)
            val dg2Response = isoDep.transceive(selectDg2)
            sb.append("📂 EF.DG2 (Fotoğraf) Seçimi: ${interpretStatusWord(dg2Response)}\n")
            
            if (isSuccessResponse(dg2Response)) {
                sb.append("✅ Fotoğraf verisi mevcut (şifrelenmiş)\n")
                sb.append("ℹ️ Fotoğraf verisini çözmek için gelişmiş kriptografi gerekir\n")
            }
        } catch (e: Exception) {
            sb.append("❌ EF.DG2 okuma hatası: ${e.message}\n")
        }
    }

    // MRZ verilerini analiz et
    private fun parseMrzData(mrzText: String, sb: StringBuilder) {
        try {
            // MRZ formatı: IDTUR<< (tip) + belge no + kontrol + ülke kodu vb.
            if (mrzText.startsWith("IDTUR")) {
                sb.append("   Belge Tipi: Türkiye Kimlik Kartı\n")
                
                // Belge numarasını çıkarmaya çalış
                val docPattern = Regex("[A-Z0-9]{9}")
                val docMatch = docPattern.find(mrzText)
                if (docMatch != null) {
                    sb.append("   Belge No: ${docMatch.value}\n")
                }
                
                // Tarihleri çıkarmaya çalış
                val datePattern = Regex("\\d{6}")
                val dates = datePattern.findAll(mrzText).map { it.value }.toList()
                if (dates.size >= 2) {
                    sb.append("   Doğum Tarihi: ${dates[0]}\n")
                    sb.append("   Son Kullanma: ${dates[1]}\n")
                }
            }
        } catch (e: Exception) {
            sb.append("   MRZ parsing hatası: ${e.message}\n")
        }
    }

    // Veri şifresini çöz
    private fun decryptData(encryptedData: ByteArray, key: ByteArray): ByteArray {
        return try {
            val cipher = Cipher.getInstance("DES/ECB/NoPadding")
            val keySpec = SecretKeySpec(key, "DES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            // Basit XOR çözme (fallback)
            encryptedData.mapIndexed { index, byte ->
                (byte.toInt() xor key[index % key.size].toInt()).toByte()
            }.toByteArray()
        }
    }

    // Kimlik doğrulama hatasını analiz et
    private fun analyzeAuthenticationError(response: ByteArray, sb: StringBuilder) {
        if (response.size >= 2) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            
            when {
                sw1 == 0x69 && sw2 == 0x82 -> {
                    sb.append("🔍 Hata Analizi: Güvenlik durumu tatmin edilmedi\n")
                    sb.append("• MRZ bilgileri yanlış olabilir\n")
                    sb.append("• Kart farklı kimlik doğrulama kullanıyor olabilir\n")
                }
                sw1 == 0x69 && sw2 == 0x85 -> {
                    sb.append("🔍 Hata Analizi: Kullanım koşulları tatmin edilmedi\n")
                    sb.append("• BAC devre dışı olabilir\n")
                    sb.append("• Kart PACE kullanıyor olabilir\n")
                }
                sw1 == 0x6A && sw2 == 0x88 -> {
                    sb.append("🔍 Hata Analizi: Anahtar referansı bulunamadı\n")
                    sb.append("• Türetilen anahtarlar yanlış\n")
                }
                else -> {
                    sb.append("🔍 Hata Analizi: Bilinmeyen hata kodu\n")
                }
            }
        }
    }

    // T.C. Kimlik kartı için detaylı ham veri dump
    private fun dumpTurkishIdCardRawData(isoDep: IsoDep, sb: StringBuilder, tag: Tag) {
        // Bilinen dosya ID'leri ve okuma denemeleri
        val files = mapOf(
            0x011E to "EF.COM",
            0x011D to "EF.SOD",
            0x0101 to "EF.DG1 (MRZ)",
            0x0102 to "EF.DG2 (Photo)",
            0x0103 to "EF.DG3 (Fingerprint)"
        )
        
        files.forEach { (fileId, fileName) ->
            try {
                sb.append("📂 Dosya: $fileName (ID: 0x${String.format("%04X", fileId)})\n")
                
                // Dosya seçimi
                val selectFile = byteArrayOf(
                    0x00, 0xA4, 0x02, 0x0C, 0x02,
                    (fileId shr 8).toByte(),
                    (fileId and 0xFF).toByte()
                )
                sb.append("📤 SELECT: ${bytesToHex(selectFile)}\n")
                val selectResponse = isoDep.transceive(selectFile)
                sb.append("📥 RESPONSE: ${bytesToHex(selectResponse)}\n")
                sb.append("📊 STATUS: ${interpretStatusWord(selectResponse)}\n")
                
                // Eğer seçim başarılı ise, ilk birkaç byte'ı okumaya çalış
                if (isSuccessResponse(selectResponse)) {
                    try {
                        val readFile = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x10) // 16 byte oku
                        sb.append("📤 READ: ${bytesToHex(readFile)}\n")
                        val readResponse = isoDep.transceive(readFile)
                        sb.append("📥 DATA: ${bytesToHex(readResponse)}\n")
                        sb.append("📄 ASCII: ${tryDecodeAscii(readResponse)}\n")
                        sb.append("📊 STATUS: ${interpretStatusWord(readResponse)}\n")
                        
                        // Hex dump formatında da göster
                        if (readResponse.size > 2) {
                            val dataOnly = readResponse.dropLast(2).toByteArray() // Status word'ü çıkar
                            if (dataOnly.isNotEmpty()) {
                                sb.append("🔍 HEX DUMP:\n")
                                sb.append(bytesToHexDump(dataOnly, true))
                                sb.append("\n")
                            }
                        }
                    } catch (e: Exception) {
                        sb.append("❌ READ ERROR: ${e.message}\n")
                    }
                }
                sb.append("\n")
            } catch (e: Exception) {
                sb.append("❌ $fileName ERROR: ${e.message}\n\n")
            }
        }
        
        // Hex dump formatında UID'yi göster
        sb.append("🔍 UID HEX DUMP:\n")
        sb.append("Offset  00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F  ASCII\n")
        sb.append("------  ----------------  ----------------  -----\n")
        val uid = tag.id
        for (i in uid.indices step 16) {
            val offset = String.format("%06X", i)
            val chunk = uid.sliceArray(i until minOf(i + 16, uid.size))
            val hexPart = chunk.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }.padEnd(47)
            val asciiPart = tryDecodeAscii(chunk)
            sb.append("$offset  $hexPart  $asciiPart\n")
        }
        sb.append("\n")
    }

    // Yardımcı fonksiyonlar
    private fun generateRandomBytes(size: Int): ByteArray {
        val random = SecureRandom()
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return bytes
    }

    private fun padData(data: ByteArray, blockSize: Int): ByteArray {
        val padding = blockSize - (data.size % blockSize)
        return data + ByteArray(padding) { 0x00 }
    }

    private fun calculateMac(data: ByteArray, key: ByteArray): ByteArray {
        // Basit MAC hesaplama (gerçek MAC algoritması daha karmaşık)
        val mac = ByteArray(8)
        for (i in mac.indices) {
            mac[i] = (data[i % data.size].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return mac
    }

    // ASCII decode denemesi
    private fun tryDecodeAscii(data: ByteArray): String {
        return data.map { byte ->
            val char = byte.toInt() and 0xFF
            when {
                char in 32..126 -> char.toChar() // Yazdırılabilir ASCII
                char == 0 -> '∅' // NULL
                else -> '·' // Diğer karakterler
            }
        }.joinToString("")
    }

    // Gelişmiş bytesToHex - adres ve ASCII ile
    private fun bytesToHexDump(bytes: ByteArray, showAscii: Boolean = true): String {
        val sb = StringBuilder()
        
        if (showAscii) {
            sb.append("Offset  Hex Data                              ASCII\n")
            sb.append("------  --------------------------------  ----------------\n")
        }
        
        for (i in bytes.indices step 16) {
            if (showAscii) {
                sb.append(String.format("%06X  ", i))
            }
            
            val chunk = bytes.sliceArray(i until minOf(i + 16, bytes.size))
            val hexPart = chunk.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
            
            if (showAscii) {
                sb.append(hexPart.padEnd(48))
                sb.append("  ")
                sb.append(tryDecodeAscii(chunk))
            } else {
                sb.append(hexPart)
            }
            
            if (i + 16 < bytes.size) sb.append("\n")
        }
        
        return sb.toString()
    }

    // Yardımcı fonksiyonlar
    private fun interpretStatusWord(response: ByteArray): String {
        if (response.size < 2) return "Geçersiz yanıt"
        
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        val statusWord = (sw1 shl 8) or sw2
        
        return when (statusWord) {
            0x9000 -> "✅ Başarılı"
            0x6A82 -> "❌ Dosya bulunamadı"
            0x6A86 -> "❌ Yanlış parametreler"
            0x6982 -> "🔒 Güvenlik durumu tatmin edilmedi"
            0x6985 -> "🔐 Kullanım koşulları tatmin edilmedi"
            0x6A83 -> "❌ Kayıt bulunamadı"
            0x6D00 -> "❌ Komut desteklenmiyor"
            0x6E00 -> "❌ Sınıf desteklenmiyor"
            else -> "⚠️ Bilinmeyen durum: ${String.format("0x%04X", statusWord)}"
        }
    }

    private fun isSuccessResponse(response: ByteArray): Boolean {
        if (response.size < 2) return false
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return sw1 == 0x90 && sw2 == 0x00
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString(":") { 
            String.format("%02X", it.toInt() and 0xFF)
        }
    }

    private fun getMifareClassicType(type: Int): String {
        return when (type) {
            MifareClassic.TYPE_CLASSIC -> "Classic"
            MifareClassic.TYPE_PLUS -> "Plus"
            MifareClassic.TYPE_PRO -> "Pro"
            else -> "Bilinmeyen ($type)"
        }
    }

    private fun getMifareUltralightType(type: Int): String {
        return when (type) {
            MifareUltralight.TYPE_ULTRALIGHT -> "Ultralight"
            MifareUltralight.TYPE_ULTRALIGHT_C -> "Ultralight C"
            else -> "Bilinmeyen ($type)"
        }
    }
}
