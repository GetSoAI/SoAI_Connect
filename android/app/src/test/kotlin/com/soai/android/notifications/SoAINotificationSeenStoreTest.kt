// SPDX-License-Identifier: MIT

package com.soai.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeSeenStorage : SoAINotificationSeenStorage {
    override var notificationBaselineServerUrl: String? = null
    var seenIdsWriteCount = 0
    private var storedSeenIds: List<String> = emptyList()

    override var notificationSeenIds: List<String>
        get() = storedSeenIds
        set(value) {
            storedSeenIds = value
            seenIdsWriteCount += 1
        }
}

class SoAINotificationSeenStoreTest {

    @Test
    fun remember_persistsInInsertionOrder() {
        val storage = FakeSeenStorage()
        val store = SoAINotificationSeenStore(storage)

        store.remember("first")
        store.remember(listOf("second", "third"))

        assertEquals(listOf("first", "second", "third"), storage.notificationSeenIds)
        assertEquals(listOf("first", "second", "third"), store.snapshot())
    }

    @Test
    fun remember_evictsOldestBeyondCap() {
        val storage = FakeSeenStorage()
        val store = SoAINotificationSeenStore(storage)

        store.remember((1..501).map { index -> "id-$index" })

        val stored = storage.notificationSeenIds
        assertEquals(500, stored.size)
        assertEquals("id-2", stored.first())
        assertEquals("id-501", stored.last())
        assertFalse(store.isSeen("id-1"))
        assertTrue(store.isSeen("id-501"))
    }

    @Test
    fun remember_doesNotRewriteStorageWhenNothingChanged() {
        val storage = FakeSeenStorage()
        val store = SoAINotificationSeenStore(storage)

        store.remember("known")
        assertEquals(1, storage.seenIdsWriteCount)

        store.remember("known")
        store.remember(listOf("known", " known ", ""))
        assertEquals(1, storage.seenIdsWriteCount)
    }

    @Test
    fun remember_ignoresBlankIdsAndTrimsWhitespace() {
        val storage = FakeSeenStorage()
        val store = SoAINotificationSeenStore(storage)

        store.remember(listOf("  padded  ", "", "   "))

        assertEquals(listOf("padded"), storage.notificationSeenIds)
        assertTrue(store.isSeen("padded"))
    }

    @Test
    fun seenIds_restoredFromStorageOnConstruction() {
        val storage = FakeSeenStorage()
        storage.notificationSeenIds = listOf("earlier", "later")

        val store = SoAINotificationSeenStore(storage)

        assertTrue(store.isSeen("earlier"))
        assertTrue(store.isSeen("later"))
        assertFalse(store.isSeen("never"))
    }

    @Test
    fun baseline_tracksServerUrlChanges() {
        val storage = FakeSeenStorage()
        val store = SoAINotificationSeenStore(storage)

        assertTrue(store.shouldBaseline("https://soai.local:5090"))
        store.markBaseline("https://soai.local:5090")
        assertFalse(store.shouldBaseline("https://soai.local:5090"))
        assertTrue(store.shouldBaseline("https://other.local:5090"))
    }
}
