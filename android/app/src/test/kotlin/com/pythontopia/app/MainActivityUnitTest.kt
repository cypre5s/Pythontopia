package com.pythontopia.app

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets

class MainActivityUnitTest {

    @Test
    fun packageName_isCorrect() {
        assertEquals(
            "com.pythontopia.app",
            PythontopiaApplication::class.java.`package`.name
        )
    }

    @Test
    fun androidMainSources_doNotUseWebViewShells() {
        val repoRoot = Path.of("").toAbsolutePath()
        val forbidden = listOf(
            "WebView",
            "android.webkit",
            "CustomTabs",
            "TrustedWebActivity",
            "androidx.browser",
        )
        val offenders = Files.walk(repoRoot)
            .use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter { it.toString().contains("/src/main/") }
                    .filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") || it.toString().endsWith(".xml") }
                    .filter { path ->
                        val text = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
                        forbidden.any { token -> text.contains(token) }
                    }
                    .map { repoRoot.relativize(it).toString() }
                    .toList()
            }

        assertTrue(offenders.isEmpty(), "Android must stay native; WebView shell references: $offenders")
    }
}
