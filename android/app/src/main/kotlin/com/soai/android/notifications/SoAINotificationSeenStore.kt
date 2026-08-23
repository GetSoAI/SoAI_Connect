// SPDX-License-Identifier: MIT

package com.soai.android.notifications

interface SoAINotificationSeenStorage {
    var notificationBaselineServerUrl: String?
    var notificationSeenIds: List<String>
}

class SoAINotificationSeenStore(private val storage: SoAINotificationSeenStorage) {

    private val lock = Any()
    private val seenIds: LinkedHashSet<String> = LinkedHashSet(storage.notificationSeenIds)

    fun shouldBaseline(serverUrl: String): Boolean {
        return storage.notificationBaselineServerUrl != serverUrl
    }

    fun markBaseline(serverUrl: String) {
        storage.notificationBaselineServerUrl = serverUrl
    }

    fun isSeen(id: String): Boolean {
        return synchronized(lock) { seenIds.contains(id) }
    }

    fun remember(id: String) {
        remember(listOf(id))
    }

    fun remember(ids: Collection<String>) {
        val normalized = ids.map { id -> id.trim() }.filter { id -> id.isNotBlank() }
        if (normalized.isEmpty()) return
        synchronized(lock) {
            var changed = false
            normalized.forEach { id ->
                if (!seenIds.contains(id)) {
                    seenIds.add(id)
                    changed = true
                }
            }
            while (seenIds.size > MAX_SEEN_IDS) {
                seenIds.remove(seenIds.first())
                changed = true
            }
            if (changed) {
                storage.notificationSeenIds = seenIds.toList()
            }
        }
    }

    fun snapshot(): List<String> {
        return synchronized(lock) { seenIds.toList() }
    }

    companion object {
        private const val MAX_SEEN_IDS = 500
    }
}
