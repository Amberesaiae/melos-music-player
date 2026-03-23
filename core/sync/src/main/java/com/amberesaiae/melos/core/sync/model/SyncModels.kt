@file:Suppress("kotlin:S6290")

package com.amberesaiae.melos.core.sync.model

/**
 * Sync operation status.
 */
enum class SyncStatus {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Conflict resolution strategy.
 */
enum class ConflictResolution {
    SERVER_WINS,
    LOCAL_WINS,
    MANUAL;

    companion object {
        fun fromString(value: String): ConflictResolution {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                SERVER_WINS
            }
        }
    }
}

/**
 * Bidirectional sync strategy for playlists.
 */
enum class BidirectionalSyncStrategy {
    MERGE,
    SERVER_WINS,
    LOCAL_WINS;

    companion object {
        fun fromString(value: String): BidirectionalSyncStrategy {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                MERGE
            }
        }
    }
}

/**
 * Result of a sync operation.
 */
data class SyncResult(
    val isSuccessful: Boolean,
    val hasPartialFailures: Boolean,
    val itemsSynced: Int,
    val totalItems: Int,
    val timestamp: Long,
    val conflicts: List<SyncConflict>,
    val errorMessage: String? = null
)

/**
 * Represents a conflict between local and server data.
 */
data class SyncConflict(
    val itemId: String,
    val itemType: String,
    val serverVersion: Any,
    val localVersion: Any,
    val conflictType: String,
    val serverModifiedDate: Long? = null,
    val localModifiedDate: Long? = null
)
