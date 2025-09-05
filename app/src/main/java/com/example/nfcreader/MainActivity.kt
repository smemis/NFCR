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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Layout'u programmatik olarak oluştur
        val scrollView = ScrollView(this)
        val textView = TextView(this).apply {
            id = android.R.id.text1
            text = "NFC kartını cihaza yaklaştırın..."
            textSize = 14f
            setPadding(32, 32, 32, 32)
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)
        setContentView(scrollView)
        
        textViewInfo = textView

        // NFC adaptörünü başlat
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "Bu cihaz NFC desteklemiyor", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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
        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFiltersArray,
            techListsArray
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNfcIntent(intent)
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

        // ISO-DEP bilgileri (eğer varsa)
        val isoDep = IsoDep.get(tag)
        if (isoDep != null) {
            sb.append("💳 ISO-DEP Bilgileri:\n")
            sb.append("Geçmiş baytları: ${bytesToHex(isoDep.historicalBytes ?: byteArrayOf())}\n")
            sb.append("Hi-layer yanıtı: ${bytesToHex(isoDep.hiLayerResponse ?: byteArrayOf())}\n\n")
        }

        sb.append("✅ Okuma tamamlandı - ${java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())}")

        textViewInfo.text = sb.toString()
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
