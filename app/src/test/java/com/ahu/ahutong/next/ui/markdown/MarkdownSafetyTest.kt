package com.ahu.ahutong.next.ui.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MarkdownSafetyTest {
    @Test
    fun removesImages() {
        val input = "看图 ![截图](https://example.com/a.png) 结束"
        assertEquals("看图 [图片：截图] 结束", MarkdownSafety.sanitize(input))
    }

    @Test
    fun keepsHttpLinksAndUnwrapsUnsafeSchemes() {
        val input = "好链接 [官网](https://ahu.edu.cn) 坏链接 [点我](javascript:alert(1))"
        val output = MarkdownSafety.sanitize(input)
        assertTrue(output.contains("[官网](https://ahu.edu.cn)"))
        assertFalse(output.contains("javascript:"))
        assertTrue(output.contains("坏链接 点我"))
    }

    @Test
    fun handlesParenthesesInsideUrls() {
        assertEquals("坏链接", MarkdownSafety.sanitize("[坏链接](javascript:alert(1))"))
        assertEquals(
            "[维基](https://example.com/a_(b))",
            MarkdownSafety.sanitize("[维基](https://example.com/a_(b))"),
        )
    }

    @Test
    fun removesRawImagesAndUnsafeAutolinks() {
        assertEquals("前 [图片已省略] 后", MarkdownSafety.sanitize("前 <img src=x onerror=alert(1)> 后"))
        val output = MarkdownSafety.sanitize("看 <javascript:alert(1)> 和 <https://ahu.edu.cn>")
        assertFalse(output.contains("<javascript:"))
        assertTrue(output.contains("<https://ahu.edu.cn>"))
    }

    @Test
    fun leavesFencedCodeBlocksAlone() {
        val input = """
            示例：
            ```
            [链接](javascript:alert(1))
            ```
            结束
        """.trimIndent()
        val output = MarkdownSafety.sanitize(input)
        assertTrue(output.contains("[链接](javascript:alert(1))"))
    }

    @Test
    fun allowsOnlyHttpSchemes() {
        assertTrue(MarkdownSafety.isSafeExternalUri("https://example.com"))
        assertTrue(MarkdownSafety.isSafeExternalUri("http://example.com"))
        assertFalse(MarkdownSafety.isSafeExternalUri("javascript:alert(1)"))
        assertFalse(MarkdownSafety.isSafeExternalUri("file:///data/data/x"))
        assertFalse(MarkdownSafety.isSafeExternalUri("intent://scan"))
    }
}
