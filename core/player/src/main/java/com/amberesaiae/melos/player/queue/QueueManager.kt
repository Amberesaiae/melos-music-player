/*
 * Copyright (c) 2024 Amberesaiae. All rights reserved.
 * SPDX-License-Identifier: MIT
 */
package com.amberesaiae.melos.player.queue

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.amberesaiae.melos.player.MelosPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue item representing a track in the playback queue.
 *
 * @property id Unique identifier for the track
 * @property mediaItem The MediaItem for playback
 * @property title Track title
 * @property artist Artist name
 * @property album Album name
 * @property duration Track duration in milliseconds
 * @property artUri URI for album art
 * @property isPlayable Whether the track can be played
 * @property metadata Additional metadata map
 */
data class QueueItem(
    val id: String,
    val mediaItem: MediaItem,
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long = 0L,
    val artUri: Uri? = null,
    val isPlayable: Boolean = true,
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Create QueueItem from MediaItem.
     */
    constructor(mediaItem: MediaItem) : this(
        id = mediaItem.mediaId,
        mediaItem = mediaItem,
        title = mediaItem.mediaMetadata.title?.toString() ?: "Unknown",
        artist = mediaItem.mediaMetadata.artist?.toString() ?: "Unknown",
        album = mediaItem.mediaMetadata.albumTitle?.toString(),
        duration = mediaItem.mediaMetadata.extras?.getLong("duration") ?: 0L,
        artUri = mediaItem.localConfiguration?.uri,
        isPlayable = true
    )

    /**
     * Create QueueItem from track data.
     */
    constructor(
        id: String,
        uri: Uri,
        title: String,
        artist: String,
        album: String? = null,
        duration: Long = 0L,
        artUri: Uri? = null
    ) : this(
        id = id,
        mediaItem = MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setTag(this)
            .build(),
        title = title,
        artist = artist,
        album = album,
        duration = duration,
        artUri = artUri
    )
}

/**
 * Shuffle mode for queue playback.
 */
enum class ShuffleMode {
    /** Shuffle disabled - sequential playback */
    OFF,

    /** Shuffle all tracks in the queue */
    ALL,

    /** Shuffle within current album/artist grouping */
    GROUP
}

/**
 * Repeat mode for queue playback.
 */
enum class RepeatMode {
    /** No repeat - playback stops at end of queue */
    OFF,

    /** Repeat current track */
    ONE,

    /** Repeat entire queue */
    ALL
}

/**
 * Queue state for UI observation.
 *
 * @property items All items in the queue
 * @property currentIndex Current position in the queue
 * @property currentItem Currently playing item
 * @property shuffleMode Current shuffle mode
 * @property repeatMode Current repeat mode
 * @property history Queue of previously played indices (for previous button)
 * @property isEmpty Whether the queue is empty
 * @property size Number of items in the queue
 * @property lastModified Timestamp of last modification
 */
data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val currentIndex: Int = -1,
    val currentItem: QueueItem? = null,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val history: List<Int> = emptyList(),
    val isEmpty: Boolean = true,
    val size: Int = 0,
    val lastModified: Long = 0L
) {
    companion object {
        val EMPTY = QueueState()
    }
}

/**
 * Listener interface for queue changes.
 */
interface QueueListener {
    fun onQueueChanged(items: List<QueueItem>)
    fun onCurrentItemChanged(item: QueueItem?, index: Int)
    fun onShuffleModeChanged(mode: ShuffleMode)
    fun onRepeatModeChanged(mode: RepeatMode)
    fun onItemAdded(item: QueueItem, index: Int)
    fun onItemRemoved(item: QueueItem, index: Int)
    fun onQueueCleared()
}

/**
 * Queue manager for handling playback queue operations.
 *
 * This class manages the playback queue, providing functionality for:
 * - Adding/removing/reordering tracks
 * - Shuffle and repeat modes
 * - Navigation (next, previous, skip to position)
 * - Queue history tracking
 * - State observation via StateFlow
 *
 * Features:
 * - Full queue manipulation (add, remove, move, clear)
 * - Shuffle modes (OFF, ALL, GROUP)
 * - Repeat modes (OFF, ONE, ALL)
 * - History tracking for intelligent previous button
 * - StateFlow for real-time UI updates
 * - Integration with MelosPlayer
 *
 * @property player MelosPlayer instance for playback control
 */
@Singleton
class QueueManager @Inject constructor(
    private val player: MelosPlayer
) {

    companion object {
        /** Maximum history size for previous button navigation */
        const val MAX_HISTORY_SIZE = 50

        /** Default queue capacity */
        const val DEFAULT_CAPACITY = 1000
    }

    /** MutableStateFlow for exposing queue state to UI */
    private val _queueState = MutableStateFlow(QueueState.EMPTY)

    /** StateFlow for observing queue state */
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    /** List of registered listeners */
    private val listeners = mutableListOf<QueueListener>()

    /** Internal queue storage */
    private val queueItems = mutableListOf<QueueItem>()

    /** History stack for previous button (stores indices) */
    private val historyStack = mutableListOf<Int>()

    /** Whether queue has been initialized with player listener */
    private var isInitialized = false

    /**
     * Initialize the queue manager with player integration.
     * Should be called once during app initialization.
     */
    fun init() {
        if (isInitialized) return

        // Observe player changes
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateCurrentItem()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                // Queue structure changed
                updateQueueState()
            }
        })

        isInitialized = true
    }

    /**
     * Set the entire queue, replacing any existing items.
     * @param items New queue items
     * @param startIndex Index to start playback from (default: 0)
     */
    fun setQueue(items: List<QueueItem>, startIndex: Int = 0) {
        queueItems.clear()
        queueItems.addAll(items)
        historyStack.clear()

        // Update player with new media items
        val mediaItems = items.map { it.mediaItem }
        player.setMediaItems(mediaItems, startIndex, Player.DEFAULT_POSITION_UNSET)

        updateQueueState()
        notifyListenersQueueChanged(items)

        // Start playback if items exist
        if (items.isNotEmpty() && player.getPlayerState().isReady) {
            player.prepare()
            player.play()
        }
    }

    /**
     * Add a single item to the end of the queue.
     * @param item Item to add
     * @param playNow Whether to start playing immediately
     */
    fun addItem(item: QueueItem, playNow: Boolean = false) {
        val index = queueItems.size
        queueItems.add(item)

        // Add to player queue
        player.addMediaItem(item.mediaItem)

        updateQueueState()
        notifyListenersItemAdded(item, index)

        if (playNow && queueItems.size == 1) {
            player.prepare()
            player.play()
        }
    }

    /**
     * Add multiple items to the end of the queue.
     * @param items Items to add
     * @param startIndex Index in the added items to start playing from
     * @param playNow Whether to start playing immediately
     */
    fun addItems(items: List<QueueItem>, startIndex: Int = 0, playNow: Boolean = false) {
        val startIndexInQueue = queueItems.size
        queueItems.addAll(items)

        // Add to player queue
        items.forEach { player.addMediaItem(it.mediaItem) }

        updateQueueState()
        notifyListenersQueueChanged(queueItems)

        if (playNow && queueItems.size == items.size) {
            player.prepare()
            player.play()
        }
    }

    /**
     * Add an item to play next (after current item).
     * @param item Item to add
     */
    fun playNext(item: QueueItem) {
        val currentIndex = player.getCurrentWindowIndex()
        val insertIndex = (currentIndex + 1).coerceAtMost(queueItems.size)

        queueItems.add(insertIndex, item)
        player.addMediaItem(insertIndex, item.mediaItem)

        updateQueueState()
        notifyListenersItemAdded(item, insertIndex)
    }

    /**
     * Remove an item from the queue.
     * @param index Index of item to remove
     * @return Removed item, or null if index was invalid
     */
    fun removeItem(index: Int): QueueItem? {
        if (index < 0 || index >= queueItems.size) return null

        val removedItem = queueItems.removeAt(index)
        player.removeMediaItem(index)

        // Adjust history if needed
        adjustHistoryForRemoval(index)

        updateQueueState()
        notifyListenersItemRemoved(removedItem, index)

        return removedItem
    }

    /**
     * Remove an item from the queue by ID.
     * @param itemId ID of item to remove
     * @return Removed item, or null if not found
     */
    fun removeItemById(itemId: String): QueueItem? {
        val index = queueItems.indexOfFirst { it.id == itemId }
        return if (index >= 0) removeItem(index) else null
    }

    /**
     * Remove multiple items from the queue.
     * @param indices Indices of items to remove (sorted descending recommended)
     */
    fun removeItems(indices: List<Int>) {
        // Sort descending to remove from end first (preserves indices)
        indices.sortedDescending().forEach { index ->
            removeItem(index)
        }
    }

    /**
     * Move an item to a new position.
     * @param fromIndex Current position
     * @param toIndex New position
     */
    fun moveItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= queueItems.size ||
            toIndex < 0 || toIndex >= queueItems.size) return

        val item = queueItems.removeAt(fromIndex)
        queueItems.add(toIndex, item)

        // Move in player queue
        player.moveMediaItem(fromIndex, toIndex)

        updateQueueState()
        notifyListenersQueueChanged(queueItems)
    }

    /**
     * Clear the entire queue.
     */
    fun clearQueue() {
        queueItems.clear()
        historyStack.clear()
        player.clearMediaItems()

        updateQueueState()
        notifyListenersQueueCleared()
    }

    /**
     * Skip to the next item in the queue.
     */
    fun next() {
        if (queueItems.isEmpty()) return

        // Save current index to history
        val currentIndex = player.getCurrentWindowIndex()
        if (currentIndex >= 0) {
            addToHistory(currentIndex)
        }

        player.seekToNext()
        updateCurrentItem()
    }

    /**
     * Skip to the previous item.
     * If less than 3 seconds into current track, go to previous in history/queue.
     * Otherwise, restart current track.
     */
    fun previous() {
        if (queueItems.isEmpty()) return

        val currentPosition = player.getCurrentPosition()

        // If more than 3 seconds in, restart current track
        if (currentPosition > 3000) {
            player.seekTo(0)
            return
        }

        // Try to use history first
        if (historyStack.isNotEmpty()) {
            val previousIndex = historyStack.removeAt(historyStack.size - 1)
            if (previousIndex >= 0 && previousIndex < queueItems.size) {
                player.seekTo(previousIndex, 0)
                updateCurrentItem()
                return
            }
        }

        // Fall back to standard previous
        player.seekToPrevious()
        updateCurrentItem()
    }

    /**
     * Skip to a specific position in the queue.
     * @param index Index to skip to
     */
    fun skipTo(index: Int) {
        if (index < 0 || index >= queueItems.size) return

        // Save current index to history
        val currentIndex = player.getCurrentWindowIndex()
        if (currentIndex >= 0 && currentIndex != index) {
            addToHistory(currentIndex)
        }

        player.seekTo(index, 0)
        updateCurrentItem()
    }

    /**
     * Set shuffle mode.
     * @param mode Shuffle mode to set
     */
    fun setShuffleMode(mode: ShuffleMode) {
        val playerMode = when (mode) {
            ShuffleMode.OFF -> Player.SHUFFLE_MODE_OFF
            ShuffleMode.ALL -> Player.SHUFFLE_MODE_ALL
            ShuffleMode.GROUP -> Player.SHUFFLE_MODE_ALL // Fallback to ALL for now
        }

        player.setShuffleMode(playerMode)
        
        _queueState.update { 
            it.copy(
                shuffleMode = mode,
                lastModified = System.currentTimeMillis()
            )
        }

        notifyListenersShuffleModeChanged(mode)
    }

    /**
     * Set repeat mode.
     * @param mode Repeat mode to set
     */
    fun setRepeatMode(mode: RepeatMode) {
        val playerMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }

        player.setRepeatMode(playerMode)
        
        _queueState.update { 
            it.copy(
                repeatMode = mode,
                lastModified = System.currentTimeMillis()
            )
        }

        notifyListenersRepeatModeChanged(mode)
    }

    /**
     * Get the current queue state.
     */
    fun getQueueState(): QueueState = _queueState.value

    /**
     * Get a queue item by index.
     * @param index Index of item
     * @return QueueItem at index, or null if invalid
     */
    fun getItemAt(index: Int): QueueItem? {
        return queueItems.getOrNull(index)
    }

    /**
     * Get the current playing item.
     */
    fun getCurrentItem(): QueueItem? {
        return _queueState.value.currentItem
    }

    /**
     * Get the current position in the queue.
     */
    fun getCurrentIndex(): Int {
        return player.getCurrentWindowIndex()
    }

    /**
     * Check if a specific item is in the queue.
     * @param itemId ID of item to check
     * @return true if item is in queue
     */
    fun hasItem(itemId: String): Boolean {
        return queueItems.any { it.id == itemId }
    }

    /**
     * Add a listener.
     * @param listener Listener to add
     */
    fun addListener(listener: QueueListener) {
        listeners.add(listener)
        // Immediately notify of current state
        listener.onQueueChanged(queueItems)
    }

    /**
     * Remove a listener.
     * @param listener Listener to remove
     */
    fun removeListener(listener: QueueListener) {
        listeners.remove(listener)
    }

    // ============ Private Methods ============

    /**
     * Update the current item in state.
     */
    private fun updateCurrentItem() {
        val currentIndex = player.getCurrentWindowIndex()
        val currentItem = queueItems.getOrNull(currentIndex)

        _queueState.update {
            it.copy(
                currentIndex = currentIndex,
                currentItem = currentItem,
                lastModified = System.currentTimeMillis()
            )
        }

        notifyListenersCurrentItemChanged(currentItem, currentIndex)
    }

    /**
     * Update the full queue state.
     */
    private fun updateQueueState() {
        val currentIndex = player.getCurrentWindowIndex()
        val currentItem = queueItems.getOrNull(currentIndex)

        _queueState.update {
            it.copy(
                items = queueItems.toList(),
                currentIndex = currentIndex,
                currentItem = currentItem,
                isEmpty = queueItems.isEmpty(),
                size = queueItems.size,
                lastModified = System.currentTimeMillis()
            )
        }
    }

    /**
     * Add index to history stack.
     */
    private fun addToHistory(index: Int) {
        historyStack.add(index)
        // Limit history size
        while (historyStack.size > MAX_HISTORY_SIZE) {
            historyStack.removeAt(0)
        }
    }

    /**
     * Adjust history after an item removal.
     */
    private fun adjustHistoryForRemoval(removedIndex: Int) {
        // Remove any history entries that are no longer valid
        val iterator = historyStack.listIterator()
        while (iterator.hasNext()) {
            val index = iterator.next()
            when {
                index == removedIndex -> iterator.remove() // Removed item
                index > removedIndex -> iterator.set(index - 1) // Shift down
            }
        }
    }

    /**
     * Notify all listeners of queue change.
     */
    private fun notifyListenersQueueChanged(items: List<QueueItem>) {
        listeners.forEach { it.onQueueChanged(items) }
    }

    /**
     * Notify all listeners of current item change.
     */
    private fun notifyListenersCurrentItemChanged(item: QueueItem?, index: Int) {
        listeners.forEach { it.onCurrentItemChanged(item, index) }
    }

    /**
     * Notify all listeners of shuffle mode change.
     */
    private fun notifyListenersShuffleModeChanged(mode: ShuffleMode) {
        listeners.forEach { it.onShuffleModeChanged(mode) }
    }

    /**
     * Notify all listeners of repeat mode change.
     */
    private fun notifyListenersRepeatModeChanged(mode: RepeatMode) {
        listeners.forEach { it.onRepeatModeChanged(mode) }
    }

    /**
     * Notify all listeners of item addition.
     */
    private fun notifyListenersItemAdded(item: QueueItem, index: Int) {
        listeners.forEach { it.onItemAdded(item, index) }
    }

    /**
     * Notify all listeners of item removal.
     */
    private fun notifyListenersItemRemoved(item: QueueItem, index: Int) {
        listeners.forEach { it.onItemRemoved(item, index) }
    }

    /**
     * Notify all listeners of queue clear.
     */
    private fun notifyListenersQueueCleared() {
        listeners.forEach { it.onQueueCleared() }
    }
}
