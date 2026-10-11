package com.phantom.launcher

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var web: WebView
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
        tts = TextToSpeech(this, this)
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
    }

    override fun onInit(status: Int) { tts.language = Locale("pt", "PT") }

    @Deprecated("launcher não deve fechar")
    override fun onBackPressed() {}

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    inner class Bridge {
        @JavascriptInterface
        fun openApp(name: String): Boolean {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(main, 0)
            val q = name.lowercase().trim()
            val m = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == q }
                ?: list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(q) }
            val intent = m?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            return true
        }

        @JavascriptInterface
        fun speak(t: String) { tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "j") }

        @JavascriptInterface
        fun listen() = runOnUiThread {
            sr?.destroy()
            sr = SpeechRecognizer.createSpeechRecognizer(this@MainActivity)
            sr?.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    js("onVoice(${JSONObject.quote(t)})")
                }
                override fun onError(e: Int) { js("onVoice('')") }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            sr?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
            })
        }
    }
}
