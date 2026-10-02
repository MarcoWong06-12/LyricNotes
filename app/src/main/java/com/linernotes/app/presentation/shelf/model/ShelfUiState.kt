package com.linernotes.app.presentation.shelf.model

import com.linernotes.app.data.local.entity.AlbumEntity

data class ShelfUiState(
    val albums: List<AlbumEntity> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isAddSheetOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isLoading: Boolean = true
)
