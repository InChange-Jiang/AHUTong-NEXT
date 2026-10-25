package com.ahu.ahutong.next.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoulRepositoryTest {
    private val markdown = """
        # 安筱桐 Soul

        ## 标准
        标准段落。

        ## 活泼
        活泼段落，可以有少量 🙂。

        ## 简洁
        简洁段落。

        ## 陪伴
        陪伴段落。
    """.trimIndent()

    @Test
    fun readsOnlyTheSelectedSection() {
        assertEquals("活泼段落，可以有少量 🙂。", SoulRepository.section(markdown, "活泼"))
        assertEquals("陪伴段落。", SoulRepository.section(markdown, "陪伴"))
    }

    @Test
    fun missingSectionReturnsNull() {
        assertNull(SoulRepository.section(markdown, "不存在"))
    }

    @Test
    fun personalityFallsBackToStandard() {
        assertEquals(AssistantPersonality.STANDARD, AssistantPersonality.fromId(null))
        assertEquals(AssistantPersonality.STANDARD, AssistantPersonality.fromId("unknown"))
        assertEquals(AssistantPersonality.LIVELY, AssistantPersonality.fromId("lively"))
    }
}
