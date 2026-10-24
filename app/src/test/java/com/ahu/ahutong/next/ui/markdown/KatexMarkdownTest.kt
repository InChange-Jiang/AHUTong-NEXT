package com.ahu.ahutong.next.ui.markdown

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KatexMarkdownTest {
    @Test
    fun detectsInlineAndBlockMath() {
        assertTrue("公式 \$x = 1\$".containsMath())
        assertTrue("\$\$x = 1\$\$".containsMath())
        assertTrue("\\[x = 1\\]".containsMath())
        assertTrue("\\begin{aligned}x=1\\end{aligned}".containsMath())
        assertFalse("价格 5 到 10 元".containsMath())
    }
}
