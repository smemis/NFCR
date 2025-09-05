// MainActivity.kt - DEĞİŞEN KOD
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

class MainActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null

    private lateinit var textViewInfo: TextView
    private var isNfcSupported = false  // YENİ: NFC desteği flag'i

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

        // YENİ: NFC desteğini kontrol et
        checkNfcSupport()
    }

    // YENİ FONKSİYON: NFC desteği kontrolü
    private fun checkNfcSupport() {
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

    // YENİ FONKSİYON: NFC desteklenmiyor mesajı
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

    // YENİ FONKSİYON: NFC kapalı mesajı
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

    // YENİ FONKSİYON: NFC hazır mesajı
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
            • T.C. Kimlik kartları (çipli - sınırlı bilgi)
            
            🔄 Bekleniyor... Kartı yaklaştırın
        """.trimIndent()
        
        textViewInfo.text = message
    }

    // ESKİ setupNfc fonksiyonu aynı kalıyor
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

    // DEĞİŞEN: onResume - NFC durumu kontrolü eklendi
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

    // DEĞİŞEN: onPause - NFC desteği kontrolü eklendi
    override fun onPause() {
        super.onPause()
        if (isNfcSupported && nfcAdapter?.isEnabled == true) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
    }

    // DEĞİŞEN: onNewIntent - NFC desteği kontrolü eklendi
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (isNfcSupported) {
            handleNfcIntent(intent)
        }
    }

    // ESKİ handleNfcIntent aynı kalıyor
    private fun handleNfcIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        tag?.let {
            readTagInfo(it)
        }
    }

    // GENİŞLETİLDİ: readTagInfo - T.C. Kimlik kartı desteği eklendi
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

        // Ham veri
        sb.append("💾 Ham Veri (UID):\n")
        sb.append("Hex: ${bytesToHex(uid)}\n")
        sb.append("Decimal: ${uid.joinToString(", ") { (it.toInt() and 0xFF).toString() }}\n")
        sb.append("Binary: ${uid.joinToString(" ") { 
            String.format("%8s", Integer.toBinaryString(it.toInt() and 0xFF)).replace(' ', '0')
        }}\n\n")

        // ISO-DEP bilgileri (T.C. Kimlik kartı için önemli)
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            try {
                isoDep.connect()
                sb.append("💳 ISO-DEP Bilgileri:\n")
                sb.append("Bağlantı durumu: Başarılı\n")
                sb.append("Geçmiş baytları: ${bytesToHex(isoDep.historicalBytes ?: byteArrayOf())}\n")
                sb.append("Hi-layer yanıtı: ${bytesToHex(isoDep.hiLayerResponse ?: byteArrayOf())}\n")
                sb.append("Maksimum geçici uzunluk: ${isoDep.maxTransceiveLength}\n")
                sb.append("Timeout: ${isoDep.timeout} ms\n")
                
                // T.C. Kimlik kartı için özel bilgi
                if (cardType.contains("T.C. Kimlik")) {
                    sb.append("\n🇹🇷 T.C. Kimlik Kartı Tespit Edildi:\n")
                    sb.append("• Bu kart ISO/IEC 14443-4 Type B standardında\n")
                    sb.append("• Kişisel veriler şifrelidir ve okunamaz\n")
                    sb.append("• Sadece UID ve temel chip bilgileri görüntülenir\n")
                    sb.append("• Resmi uygulamalar dışında veri okunamaz\n")
                }
                
                isoDep.close()
                sb.append("\n")
            } catch (e: Exception) {
                sb.append("❌ ISO-DEP bağlantı hatası: ${e.message}\n\n")
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
                        sb.append("  Payload: ${String(record.payload)}\n")
                        sb.append("  ID: ${if (record.id.isNotEmpty()) String(record.id) else "Yok"}\n")
                    }
                    sb.append("\n")
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

    // YENİ FONKSİYON: Kart tipini tespit et
    private fun detectCardType(tag: Tag): String {
        val uid = tag.id
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

    // ESKİ yardımcı fonksiyonlar aynı kalıyor...
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
