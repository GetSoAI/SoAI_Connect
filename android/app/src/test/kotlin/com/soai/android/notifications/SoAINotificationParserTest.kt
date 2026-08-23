// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoAINotificationParserTest {
    @Test
    fun createdEvent_parsesTextAndTemplate() {
        val record = SoAINotificationParser.parseCreatedEvent(JSONObject(
            """{"notification_id":"n1","notification_type":"warning","created_at_ms":1234,"title":{"text_type":"text","text":"Title"},"message":{"text_type":"template","template":"ask_user_required_message","params":{}},"link":{"link_type":"conversation","value":"c1"}}"""
        ))
        assertEquals("n1", record?.id)
        assertEquals("Title", record?.title?.text)
        assertEquals("ask_user_required_message", record?.message?.template)
        assertEquals("c1", record?.link?.value)
    }

    @Test
    fun recordWithoutTimestamp_isRejected() {
        val body = """{"notifications":[{"id":"n1","type":"info","title":{"text_type":"text","text":"T"},"message":{"text_type":"text","text":"M"}}]}"""
        assertEquals(emptyList<SoAINotificationRecord>(), SoAINotificationParser.parseListResponse(body))
    }

    @Test
    fun eventTimestampAndMarkedIds_areNormalized() {
        assertEquals(1_500L, SoAINotificationParser.parseEventTimestampMs(JSONObject("""{"timestamp":1.5}""")))
        assertEquals(listOf("a", "b"), SoAINotificationParser.parseMarkedReadIds(
            JSONObject("""{"notification_ids":[" a ","",null,"b"]}""")
        ))
        assertNull(SoAINotificationParser.parseCreatedEvent(JSONObject()))
    }
}
