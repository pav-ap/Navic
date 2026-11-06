/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.playlist.component

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.toImmutableList
import paige.navic.R
import paige.navic.domain.model.DomainPlaylistSongSort
import paige.navic.ui.component.sheet.SortSheet
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.outlined.Sort

@Composable
fun PlaylistSongSortButton(
	sort: DomainPlaylistSongSort,
	onSetSort: (DomainPlaylistSongSort) -> Unit,
	reversed: Boolean,
	onSetReversed: (Boolean) -> Unit
) {
	val entries = remember { DomainPlaylistSongSort.entries.toImmutableList() }
	var expanded by remember { mutableStateOf(false) }

	IconButton(onClick = { expanded = true }) {
		Icon(
			imageVector = Icons.Outlined.Sort,
			contentDescription = stringResource(R.string.title_sort_by)
		)
	}

	if (expanded) {
		SortSheet(
			entries = entries,
			selectedSorting = sort,
			label = { stringResource(it.displayName) },
			onSetSorting = onSetSort,
			selectedReversed = reversed,
			onSetReversed = onSetReversed,
			onDismissRequest = { expanded = false }
		)
	}
}
