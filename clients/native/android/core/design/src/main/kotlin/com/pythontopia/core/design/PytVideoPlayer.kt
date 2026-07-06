package com.pythontopia.core.design

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 课件视频播放器（依赖无关，WebView 内嵌 HTML5 `<video>`）。
 *
 * 与 Web 端一致：`/api/course/videos/{id}` 字节流端点放行匿名且支持 HTTP Range，故 `<video src>`
 * 直连即可（无需 Authorization）。避免引入 Media3 等重依赖，降低构建风险；播放/拖拽交由系统 WebView。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PytVideoPlayer(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { web ->
            val safeUrl = url.replace("\"", "%22")
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
                <style>html,body{margin:0;background:#000;height:100%}video{width:100%;height:100%;object-fit:contain;background:#000}</style>
                </head><body>
                <video controls preload="metadata" playsinline src="$safeUrl"></video>
                </body></html>
            """.trimIndent()
            web.loadDataWithBaseURL(safeUrl, html, "text/html", "utf-8", null)
        },
    )
}
