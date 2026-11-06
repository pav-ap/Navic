/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import paige.navic.R

/**
 * Ordering options for the songs *inside* a playlist.
 *
 * Kept separate from [DomainSongListType], which selects what a library-wide
 * song list shows rather than how an existing playlist is ordered.
 */
@Serializable
@Immutable
enum class DomainPlaylistSongSort(val displayName: Int) {
	Position(R.string.option_sort_playlist_order),
	Bpm(R.string.option_sort_by_bpm)
}
