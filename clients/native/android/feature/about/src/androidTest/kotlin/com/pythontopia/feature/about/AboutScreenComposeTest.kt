package com.pythontopia.feature.about

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI instrumented 测试（androidTest）：在真机 / 模拟器或 CI 上经
 * `connectedDebugAndroidTest` 运行；本沙箱无设备，仅做 `compileDebugAndroidTestKotlin` 编译校验。
 * 选 about 模块破冰是因为三屏均为纯静态 @Composable（无 Hilt），最小依赖即可覆盖。
 */
@RunWith(AndroidJUnit4::class)
class AboutScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun graduationScreen_showsCeremonyTitle() {
        composeRule.setContent { GraduationScreen() }
        composeRule.onNodeWithText("毕业典礼").assertExists()
    }

    @Test
    fun creditsScreen_showsAttributionHeaderAndAnEntry() {
        composeRule.setContent { CreditsScreen() }
        composeRule.onNodeWithText("致谢 · 素材署名").assertExists()
        composeRule.onNodeWithText("Tiny Town").assertExists()
    }

    @Test
    fun guideScreen_showsGuideHeader() {
        composeRule.setContent { GuideScreen() }
        composeRule.onNodeWithText("操作指南").assertExists()
    }
}
