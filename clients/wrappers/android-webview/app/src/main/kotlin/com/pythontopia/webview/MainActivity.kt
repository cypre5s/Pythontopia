package com.pythontopia.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar

/**
 * Pythontopia 安卓外壳：单 Activity + 全屏 WebView，加载云端像素学习小镇。
 *
 * 前端使用相对 API base（/api），加载云端站点即「后端指向云服务器」，
 * 外壳本身不含业务代码、不打包前端资源，始终与线上保持一致。
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        webView = WebView(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = PROGRESS_MAX
            visibility = View.GONE
        }
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(THREE_DP)),
        )
        setContentView(root)

        configureWebView()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(APP_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = "$userAgentString PythontopiaWebView/1.0"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val scheme = url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") {
                    // 同主机在 WebView 内导航；跨主机交给系统浏览器。
                    return if (url.host == APP_HOST) {
                        false
                    } else {
                        openExternally(url)
                        true
                    }
                }
                // tel: / mailto: / intent: 等非网页协议交给系统处理。
                openExternally(url)
                return true
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) showErrorPage()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1 until PROGRESS_MAX) View.VISIBLE else View.GONE
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val wantsAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                if (!wantsAudio) {
                    request.deny()
                    return
                }
                if (hasAudioPermission()) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                } else {
                    pendingPermissionRequest = request
                    requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER)
                    true
                } catch (_: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }
    }

    private fun openExternally(url: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url))
        } catch (_: Exception) {
            // 没有可处理的应用则静默忽略，不崩溃。
        }
    }

    private fun hasAudioPermission(): Boolean =
        checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showErrorPage() {
        val html = """
            <!doctype html><html lang="zh-CN"><head><meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <style>
              body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;
                font-family:sans-serif;background:#fef7e3;color:#2f2418;padding:24px;}
              .c{max-width:440px;text-align:center;border:2px solid #5c4a32;border-radius:12px;
                background:#fffdf8;padding:28px;box-shadow:4px 4px 0 rgba(92,74,50,.18);}
              h1{font-size:20px;margin:0 0 8px;} p{color:#6b5a45;line-height:1.7;margin:0 0 18px;word-break:break-all;}
              a{display:inline-block;padding:12px 28px;font-size:16px;font-weight:700;text-decoration:none;
                color:#2f2418;background:#f1c453;border:2px solid #5c4a32;border-radius:8px;}
            </style></head>
            <body><div class="c"><h1>无法连接到 Pythontopia</h1>
            <p>请检查网络后重试：<br/>$APP_URL</p>
            <a href="$APP_URL">重试</a></div></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    @Deprecated("Framework Activity back handling; sufficient for a single-WebView shell.")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE_CHOOSER) {
            val callback = filePathCallback ?: return
            filePathCallback = null
            callback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            val request = pendingPermissionRequest ?: return
            pendingPermissionRequest = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request.deny()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        // 云端入口；前端相对 API base 使后端自动指向此主机。
        private const val APP_URL = "http://47.120.29.62/"
        private const val APP_HOST = "47.120.29.62"
        private const val REQ_FILE_CHOOSER = 1001
        private const val REQ_AUDIO = 2001
        private const val PROGRESS_MAX = 100
        private const val THREE_DP = 3
    }
}
