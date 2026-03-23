package com.amberesaiae.melos.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.amberesaiae.melos.core.model.MusicLibrary
import com.amberesaiae.melos.core.database.local.MusicLibraryDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * HomeViewModel - Manages UI state for the home screen.
 *
 * Handles:
 * - Initial app loading state
 * - Library data fetching
 * - Recent tracks and recommendations
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicLibraryDataSource: MusicLibraryDataSource
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _library = MutableStateFlow<MusicLibrary?>(null)
    val library: StateFlow<MusicLibrary?> = _library.asStateFlow()

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            // TODO: Load library data from data source
            // musicLibraryDataSource.getLibrary().collect { library ->
            //     _library.value = library
            //     _isLoading.value = false
            // }
            _isLoading.value = false
        }
    }
}
