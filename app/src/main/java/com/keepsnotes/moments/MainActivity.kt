package com.keepsnotes.moments

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class AndroidPdfBridge(private val activity: Activity) {
    @JavascriptInterface
    fun openPdfBase64(base64Data: String, fileName: String) {
        try {
            Log.d("AndroidPdfBridge", "Opening PDF: $fileName")
            val cleanBase64 = if (base64Data.contains("base64,")) {
                base64Data.substringAfter("base64,")
            } else {
                base64Data
            }
            val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val cacheDir = File(activity.cacheDir, "pdf_cache")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val safeName = if (fileName.isBlank()) "document.pdf" else fileName.replace("[^a-zA-Z0-9.-]".toRegex(), "_")
            val file = File(cacheDir, safeName)
            FileOutputStream(file).use { it.write(bytes) }

            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e("AndroidPdfBridge", "No PDF viewer activity found", e)
                activity.runOnUiThread {
                    Toast.makeText(activity, "No PDF viewer is installed on this device.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("AndroidPdfBridge", "Failed to open PDF", e)
            activity.runOnUiThread {
                Toast.makeText(activity, "Could not open PDF file.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(194, 218, 237)
        window.navigationBarColor = android.graphics.Color.rgb(194, 218, 237)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.rgb(194, 218, 237))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.textZoom = 100
            addJavascriptInterface(AndroidPdfBridge(this@MainActivity), "AndroidPdf")
            setDownloadListener { url, _, _, mimetype, _ ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(url), mimetype)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            webViewClient = LocalAssetsClient()
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = callback
                    return try {
                        val pickerIntent = try {
                            params?.createIntent()
                        } catch (_: Exception) {
                            null
                        } ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        if (params?.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            pickerIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        }
                        pickerIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        startActivityForResult(pickerIntent, FILE_PICKER)
                        true
                    } catch (e: Exception) {
                        fileCallback?.onReceiveValue(null)
                        fileCallback = null
                        false
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        if (request.origin.host != APP_HOST) {
                            request.deny()
                            return@runOnUiThread
                        }
                        val grants = mutableListOf<String>()
                        val permsToRequest = mutableListOf<String>()
                        for (res in request.resources) {
                            if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    grants.add(res)
                                } else {
                                    permsToRequest.add(Manifest.permission.RECORD_AUDIO)
                                }
                            } else if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    grants.add(res)
                                } else {
                                    permsToRequest.add(Manifest.permission.CAMERA)
                                }
                            }
                        }
                        if (permsToRequest.isEmpty()) {
                            if (grants.isNotEmpty()) request.grant(grants.toTypedArray()) else request.deny()
                        } else {
                            pendingPermission?.deny()
                            pendingPermission = request
                            requestPermissions(permsToRequest.toTypedArray(), MEDIA_PERMISSIONS_REQ)
                        }
                    }
                }
            }
        }
        setContentView(webView, FrameLayout.LayoutParams(-1, -1))
        webView.loadUrl(START_URL)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER) {
            try {
                if (resultCode == RESULT_OK && data != null) {
                    val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
                    if (results != null && results.isNotEmpty()) {
                        fileCallback?.onReceiveValue(results)
                    } else {
                        val uriList = mutableListOf<Uri>()
                        data.data?.let { uriList.add(it) }
                        data.clipData?.let { clip ->
                            for (i in 0 until clip.itemCount) {
                                uriList.add(clip.getItemAt(i).uri)
                            }
                        }
                        if (uriList.isNotEmpty()) {
                            fileCallback?.onReceiveValue(uriList.toTypedArray())
                        } else {
                            fileCallback?.onReceiveValue(null)
                        }
                    }
                } else {
                    fileCallback?.onReceiveValue(null)
                }
            } catch (_: Exception) {
                fileCallback?.onReceiveValue(null)
            } finally {
                fileCallback = null
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_PERMISSIONS_REQ) {
            val request = pendingPermission
            pendingPermission = null
            if (request != null) {
                val grantedRes = mutableListOf<String>()
                for (res in request.resources) {
                    if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        grantedRes.add(res)
                    } else if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        grantedRes.add(res)
                    }
                }
                if (grantedRes.isNotEmpty()) request.grant(grantedRes.toTypedArray()) else request.deny()
            }
        }
    }

    @Deprecated("Use system back navigation")
    override fun onBackPressed() {
        if (::webView.isInitialized) {
            webView.evaluateJavascript("window.handleAndroidBack && window.handleAndroidBack()") { result ->
                if (result != "true") {
                    if (webView.canGoBack()) {
                        webView.goBack()
                    } else {
                        super.onBackPressed()
                    }
                }
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private inner class LocalAssetsClient : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
            val uri = request?.url ?: return null
            if (uri.scheme != "https" || uri.host != APP_HOST) return null
            val path = uri.path ?: return null
            if (!path.startsWith("/assets/")) return null
            val assetName = path.removePrefix("/assets/")
            if (assetName.isBlank() || assetName.split('/').any { it == ".." }) return notFound()
            return try {
                val mime = when (assetName.substringAfterLast('.', "").lowercase()) {
                    "html" -> "text/html"
                    "js" -> "application/javascript"
                    "css" -> "text/css"
                    "svg" -> "image/svg+xml"
                    "webmanifest", "json" -> "application/manifest+json"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "webp" -> "image/webp"
                    else -> "application/octet-stream"
                }
                WebResourceResponse(mime, "UTF-8", this@MainActivity.assets.open(assetName))
            } catch (_: Exception) {
                notFound()
            }
        }

        private fun notFound() = WebResourceResponse("text/plain", "UTF-8", 404, "Not found", emptyMap(), "Not found".byteInputStream())
    }

    companion object {
        private const val APP_HOST = "appassets.androidplatform.net"
        private const val START_URL = "https://appassets.androidplatform.net/assets/index.html"
        private const val FILE_PICKER = 7301
        private const val MEDIA_PERMISSIONS_REQ = 7302
    }
}
