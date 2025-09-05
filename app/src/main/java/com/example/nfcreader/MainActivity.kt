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
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.minOf

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private lateinit var textViewInfo: TextView
    private var isNfcSupported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Layout'u programmatik olarak oluştur
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            id = android.R.id.text1
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)
        setContentView(scrollView)
        
        textViewInfo = textView

        // NFC desteğini kontrol et
        checkNfcSupport()
    }

    private fun checkNfcSupport() {
        // NFC adaptörünü kontrol et
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        when {
            nfcAdapter == null -> {
                // Cihazda NFC yok
                showNfcNotSupported()
            }
            !nfcAdapter!!.isEnabled -> {
                // NFC kapalı
                showNfcDisabled()
            }
            else -> {
                // NFC var ve açık
                isNfcSupported = true
                setupNfc()
                showNfcReady()
            }
        }
    }

    private fun showNfcNotSupported() {
        val message = """
            ❌ NFC DESTEKLENMİYOR
            ═══════════════════════
            
            Bu cihazda NFC özelliği bulunmuyor.
            
            📱 Desteklenen Cihazlar:
            • Android 5.0+ (API 21+)
            • NFC özellikli cihazlar
            • Samsung, Huawei, Xiaomi vb. flagshipler
            
            🔍 NFC Nasıl Kontrol Edilir:
            Ayarlar → Bağlantılar → NFC
            (Eğer bu seçenek yoksa cihazınız desteklemiyor)
            
            📞 Alternatif Çözümler:
            • NFC özellikli başka cihaz kullanın
            • Harici NFC okuyucu satın alın
            • QR kod okuyucu alternatifi kullanın
            
            ℹ️ Bu uygulama NFC olmadan çalışamaz ama 
            kapanmayacak, bu mesajı göstermeye devam edecek.
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun showNfcDisabled() {
        val message = """
            📱 NFC KAPALI
            ═══════════════
            
            Cihazınızda NFC var ama kapalı.
            
            🔧 NFC'yi Açmak İçin:
            1. Ayarlar → Bağlantılar → NFC
            2. NFC'yi açın
            3. Bu uygulamaya geri dönün
            
            ⚡ Hızlı Erişim:
            • Bildirim panelini açın
            • NFC kısayoluna dokunun
            
            🔄 NFC'yi açtıktan sonra uygulamayı 
            yeniden başlatmanız gerekebilir.
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun showNfcReady() {
        val message = """
            ✅ NFC HAZIR
            ═══════════════
            
            📱 Cihaz: NFC destekli
            🔋 Durum: Etkin ve hazır
            
            📋 Kullanım:
            • NFC kartını telefonun arkasına yaklaştırın
            • Kart bilgileri otomatik olarak gösterilecek
            
            🎯 Desteklenen Kartlar:
            • Mifare Classic / Ultralight
            • NDEF formatındaki kartlar
            • ISO 14443 Type A/B kartlar
            • Kredi kartları (sınırlı bilgi)
            • Toplu taşıma kartları
            • T.C. Kimlik kartları (ham veri analizi)
            
            🔄 Bekleniyor... Kartı yaklaştırın
        """.trimIndent()
        
        textViewInfo.text = message
    }

    private fun setupNfc() {
        // NFC ayarlarını yapılandır
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
        
        // Her resume'da NFC durumunu tekrar kontrol et
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
            checkNfcSupport() // NFC durumunu tekrar kontrol et
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
            readTagInfo(it)
        }
    }

    private fun readTagInfo(tag: Tag) {
        val sb = StringBuilder()

        // Tag UID'si
        val uid = tag.id
        sb.append("🔍 NFC KART BİLGİLERİ\n")
        sb.append("=" .repeat(30) + "\n\n")
        sb.append("📱 UID: ${bytesToHex(uid)}\n\n")

        // Kart tipini tahmin et
        val cardType = detectCardType(tag)
        sb.append("🏷️ Kart Tipi: $cardType\n\n")

        // Tag teknolojileri
        sb.append("🔧 Desteklenen Teknolojiler:\n")
        tag.techList.forEach { tech ->
            sb.append("• ${tech.substringAfterLast('.')}\n")
        }
        sb.append("\n")

        // TAM HAM VERİ ANALİZİ
        sb.append("🔬 TAM HAM VERİ ANALİZİ\n")
        sb.append("=" .repeat(35) + "\n\n")
        
        // Tag'in kendisinin ham bilgileri
        sb.append("📱 Tag Ham Bilgileri:\n")
        sb.append("Tag ID (Raw): ${uid.contentToString()}\n")
        sb.append("Tag ID (Hex): ${bytesToHex(uid)}\n")
        sb.append("Tag ID Uzunluğu: ${uid.size} byte\n")
        sb.append("Teknoloji Sayısı: ${tag.techList.size}\n\n")
        
        // Her teknoloji için ayrı ham veri
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
                }
                "android.nfc.tech.MifareUltralight" -> {
                    val ultralight = MifareUltralight.get(tag)
                    sb.append("  Tip: ${ultralight.type}\n")
                    sb.append("  Max Transceive: ${ultralight.maxTransceiveLength} byte\n")
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

        // Ham veri görüntüleme
        sb.append("💾 Ham Veri (UID):\n")
        sb.append("Hex: ${bytesToHex(uid)}\n")
        sb.append("Decimal: ${uid.joinToString(", ") { (it.toInt() and 0xFF).toString() }}\n")
        sb.append("Binary: ${uid.joinToString(" ") { 
            String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0')
        }}\n\n")

        // Hex Dump formatı
        sb.append("🔍 HEX DUMP (UID):\n")
        sb.append(bytesToHexDump(uid, true))
        sb.append("\n\n")

        // ISO-DEP için detaylı ham veri okuma
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            try {
                isoDep.connect()
                
                sb.append("💳 ISO-DEP HAM VERİ DUMP:\n")
                sb.append("-" .repeat(30) + "\n")
                
                // APDU komutlarının ham yanıtları
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
                
                // T.C. Kimlik kartı için özel ham veri dump
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

        // NDEF verisi (eğer varsa)
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

        // Mifare Classic bilgileri (eğer varsa)
        val mifareClassic = MifareClassic.get(tag)
        if (mifareClassic != null) {
            sb.append("🏷️ Mifare Classic Bilgileri:\n")
            sb.append("Boyut: ${mifareClassic.size} byte\n")
            sb.append("Sektör sayısı: ${mifareClassic.sectorCount}\n")
            sb.append("Blok sayısı: ${mifareClassic.blockCount}\n")
            sb.append("Tip: ${getMifareClassicType(mifareClassic.type)}\n\n")
        }

        // Mifare Ultralight bilgileri (eğer varsa)
        val mifareUltralight = MifareUltralight.get(tag)
        if (mifareUltralight != null) {
            sb.append("🔷 Mifare Ultralight Bilgileri:\n")
            sb.append("Tip: ${getMifareUltralightType(mifareUltralight.type)}\n\n")
        }

        sb.append("✅ Okuma tamamlandı - ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n\n")
        sb.append("🔄 Başka kart okumak için tekrar yaklaştırın")

        textViewInfo.text = sb.toString()
        
        // Başarılı okuma toast'ı
        Toast.makeText(this, "✅ NFC kart başarıyla okundu!", Toast.LENGTH_SHORT).show()
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

    // Kart tipini tespit et
    private fun detectCardType(tag: Tag): String {
        val techList = tag.techList
        
        return when {
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
            // Mifare Classic
            techList.contains("android.nfc.tech.MifareClassic") -> {
                "🎫 Mifare Classic (Toplu Taşıma/Erişim Kartı)"
            }
            // Mifare Ultralight
            techList.contains("android.nfc.tech.MifareUltralight") -> {
                "🏷️ Mifare Ultralight (NFC Etiketi)"
            }
            // NDEF
            techList.contains("android.nfc.tech.Ndef") -> {
                "📱 NDEF Kartı (NFC Forum)"
            }
            // NfcA (Type A)
            techList.contains("android.nfc.tech.NfcA") -> {
                "🔵 ISO 14443 Type A Kartı"
            }
            // NfcB (Type B)
            techList.contains("android.nfc.tech.NfcB") -> {
                "🔴 ISO 14443 Type B Kartı"
            }
            // NfcF (FeliCa)
            techList.contains("android.nfc.tech.NfcF") -> {
                "🟡 FeliCa Kartı (JIS X 6319-4)"
            }
            // NfcV (ISO 15693)
            techList.contains("android.nfc.tech.NfcV") -> {
                "🟢 ISO 15693 Kartı (Vicinity)"
            }
            else -> {
                "❓ Bilinmeyen Kart Tipi"
            }
        }
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
