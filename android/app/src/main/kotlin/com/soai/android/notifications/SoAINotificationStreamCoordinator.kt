// SPDX-License-Identifier: MIT

package com.soai.android.notifications

class SoAINotificationStreamCoordinator(private val seenStore: SoAINotificationSeenStore) {

    private val lock = Any()
    private val bufferedCreated = mutableListOf<SoAINotificationRecord>()
    private val suppressedIds = mutableSetOf<String>()
    private var initialSyncComplete = false
    private var clearBarrierMs = 0L

    fun onCreated(record: SoAINotificationRecord): List<SoAINotificationRecord> {
        return synchronized(lock) {
            if (!initialSyncComplete) {
                bufferedCreated.add(record)
                emptyList()
            } else if (shouldPostLocked(record)) {
                listOf(record)
            } else {
                emptyList()
            }
        }
    }

    fun suppress(ids: Collection<String>): List<String> {
        val normalized = ids.map { id -> id.trim() }.filter { id -> id.isNotBlank() }
        if (normalized.isEmpty()) return emptyList()
        synchronized(lock) {
            suppressedIds.addAll(normalized)
            bufferedCreated.removeAll { record -> record.id in normalized }
            seenStore.remember(normalized)
        }
        return normalized
    }

    fun clearAll(clearAtMs: Long): List<String> {
        return synchronized(lock) {
            clearBarrierMs = clearBarrierMs.coerceAtLeast(clearAtMs)
            val knownIds = (seenStore.snapshot() + bufferedCreated.map { record -> record.id }).distinct()
            suppressedIds.addAll(knownIds)
            seenStore.remember(knownIds)
            bufferedCreated.clear()
            knownIds
        }
    }

    fun onUnreadFetched(records: List<SoAINotificationRecord>, baseline: Boolean): List<SoAINotificationRecord> {
        return synchronized(lock) {
            if (baseline) {
                val bufferedIds = bufferedCreated.map { record -> record.id }.toSet()
                val historicalIds = records.map { record -> record.id }.filter { id -> id !in bufferedIds }
                seenStore.remember(historicalIds)
                emptyList()
            } else {
                records
                    .sortedBy { record -> record.createdAtMs }
                    .filter { record -> shouldPostLocked(record) }
            }
        }
    }

    fun completeInitialSync(): List<SoAINotificationRecord> {
        return synchronized(lock) {
            initialSyncComplete = true
            val drained = bufferedCreated.toList()
            bufferedCreated.clear()
            drained.filter { record -> shouldPostLocked(record) }
        }
    }

    private fun shouldPostLocked(record: SoAINotificationRecord): Boolean {
        val blocked = record.createdAtMs <= clearBarrierMs ||
            record.id in suppressedIds ||
            seenStore.isSeen(record.id)
        seenStore.remember(record.id)
        return !blocked
    }
}
