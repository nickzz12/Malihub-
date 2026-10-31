package com.malihub.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val host = "appassets.androidplatform.net"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                    loader.shouldInterceptRequest(request.url)

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                    request.url.host != host
            }
            addJavascriptInterface(Bridge(), "Android")
            loadUrl("https://$host/assets/www/index.html")
        }
        setContentView(web)
    }

    inner class Bridge {
        @JavascriptInterface
        fun pickExe() = runOnUiThread {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, REQ_EXE)
        }

        @JavascriptInterface
        fun deviceInfo(): String {
            val mi = ActivityManager.MemoryInfo()
            (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
            return JSONObject()
                .put("ramMb", mi.totalMem / 1048576)
                .put("cores", Runtime.getRuntime().availableProcessors())
                .put("abi", Build.SUPPORTED_ABIS[0])
                .put("hardware", Build.HARDWARE)
                .put("api", Build.VERSION.SDK_INT)
                .toString()
        }

        @JavascriptInterface
        fun launch(uri: String, profile: String): String = Engine.launch(this@MainActivity, uri, profile)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == REQ_EXE && resultCode == RESULT_OK && uri != null) readExe(uri)
    }

    private fun readExe(uri: Uri) = Thread {
        val out = JSONObject()
        try {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            var name = "arquivo"
            var size = 0L
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            val head = ByteArray(4096)
            var n = 0
            contentResolver.openInputStream(uri)!!.use { s ->
                while (n < head.size) {
                    val r = s.read(head, n, head.size - n)
                    if (r <= 0) break
                    n += r
                }
            }
            val bb = ByteBuffer.wrap(head, 0, n).order(ByteOrder.LITTLE_ENDIAN)
            val mz = n > 64 && head[0] == 'M'.code.toByte() && head[1] == 'Z'.code.toByte()
            var arch = 0
            if (mz) {
                val pe = bb.getInt(60)
                if (pe in 0..(n - 6) && bb.getInt(pe) == 0x00004550) {
                    arch = when (bb.getShort(pe + 4).toInt() and 0xFFFF) { 0x8664 -> 64; 0x14C -> 32; else -> 0 }
                }
            }
            if (mz) out.put("ok", true).put("name", name).put("size", size).put("arch", arch).put("uri", uri.toString())
            else out.put("ok", false).put("msg", "Esse arquivo não parece ser um executável do Windows (.exe).")
        } catch (e: Exception) {
            out.put("ok", false).put("msg", "Não foi possível ler o arquivo.")
        }
        runOnUiThread { web.evaluateJavascript("window.onExePicked($out)", null) }
    }.start()

    @Deprecated("Mantido simples de propósito")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    companion object { const val REQ_EXE = 1 }
}
