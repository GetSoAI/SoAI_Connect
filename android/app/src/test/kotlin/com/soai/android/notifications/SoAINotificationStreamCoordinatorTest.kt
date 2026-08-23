// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemorySeenStorage : SoAINotificationSeenStorage {
    override var notificationBaselineServerUrl: String? = null
    override var notificationSeenIds: List<String> = emptyList()
}

private fun record(id: String, createdAtMs: Long): SoAINotificationRecord {
    return SoAINotificationRecord(
        id = id,
        createdAtMs = createdAtMs,
        type = SoAINotificationType.INFO,
        title = SoAINotificationText(text = "title-$id", template = null, params = emptyMap()),
        message = SoAINotificationText(text = "message-$id", template = null, params = emptyMap()),
        link = null
    )
}

private fun newCoordinator(): Pair<SoAINotificationStreamCoordinator, SoAINotificationSeenStore> {
    val seenStore = SoAINotificationSeenStore(InMemorySeenStorage())
    return SoAINotificationStreamCoordinator(seenStore) to seenStore
}

class SoAINotificationStreamCoordinatorTest {

    @Test
    fun onCreated_buffersUntilInitialSyncCompletes() {
        val (coordinator, _) = newCoordinator()

        assertEquals(emptyList<SoAINotificationRecord>(), coordinator.onCreated(record("a", 100)))
        assertEquals(emptyList<SoAINotificationRecord>(), coordinator.onCreated(record("b", 200)))

        val drained = coordinator.completeInitialSync()
        assertEquals(listOf("a", "b"), drained.map { posted -> posted.id })
    }

    @Test
    fun onCreated_postsImmediatelyAfterInitialSync() {
        val (coordinator, _) = newCoordinator()
        coordinator.completeInitialSync()

        val posted = coordinator.onCreated(record("live", 100))
        assertEquals(listOf("live"), posted.map { record -> record.id })
    }

    @Test
    fun onCreated_neverPostsSameIdTwice() {
        val (coordinator, _) = newCoordinator()
        coordinator.completeInitialSync()

        assertEquals(1, coordinator.onCreated(record("dup", 100)).size)
        assertEquals(0, coordinator.onCreated(record("dup", 100)).size)
    }

    @Test
    fun suppress_dropsBufferedRecordsAndBlocksLaterPosting() {
        val (coordinator, seenStore) = newCoordinator()
        coordinator.onCreated(record("quiet", 100))
        coordinator.onCreated(record("loud", 200))

        val suppressed = coordinator.suppress(listOf(" quiet ", ""))
        assertEquals(listOf("quiet"), suppressed)
        assertTrue(seenStore.isSeen("quiet"))

        val drained = coordinator.completeInitialSync()
        assertEquals(listOf("loud"), drained.map { posted -> posted.id })
    }

    @Test
    fun clearAll_blocksRecordsAtOrBeforeBarrier() {
        val (coordinator, _) = newCoordinator()
        coordinator.completeInitialSync()

        coordinator.clearAll(clearAtMs = 1_000)

        assertEquals(0, coordinator.onCreated(record("old", 900)).size)
        assertEquals(0, coordinator.onCreated(record("exact", 1_000)).size)
        assertEquals(1, coordinator.onCreated(record("new", 1_001)).size)
    }

    @Test
    fun clearAll_suppressesAllKnownIdsIncludingBuffered() {
        val (coordinator, seenStore) = newCoordinator()
        coordinator.onCreated(record("buffered", 2_000))

        val cleared = coordinator.clearAll(clearAtMs = 1_000)

        assertTrue(cleared.contains("buffered"))
        assertTrue(seenStore.isSeen("buffered"))
        assertEquals(emptyList<SoAINotificationRecord>(), coordinator.completeInitialSync())
    }

    @Test
    fun onUnreadFetched_baselineRemembersHistoricalButKeepsBuffered() {
        val (coordinator, seenStore) = newCoordinator()
        coordinator.onCreated(record("fresh", 300))

        val posted = coordinator.onUnreadFetched(
            records = listOf(record("historical", 100), record("fresh", 300)),
            baseline = true
        )

        assertEquals(emptyList<SoAINotificationRecord>(), posted)
        assertTrue(seenStore.isSeen("historical"))

        val drained = coordinator.completeInitialSync()
        assertEquals(listOf("fresh"), drained.map { record -> record.id })
    }

    @Test
    fun onUnreadFetched_nonBaselinePostsUnseenInChronologicalOrder() {
        val (coordinator, _) = newCoordinator()
        coordinator.completeInitialSync()
        coordinator.onCreated(record("already-posted", 50))

        val posted = coordinator.onUnreadFetched(
            records = listOf(
                record("later", 300),
                record("earlier", 100),
                record("already-posted", 50)
            ),
            baseline = false
        )

        assertEquals(listOf("earlier", "later"), posted.map { record -> record.id })
    }
}
