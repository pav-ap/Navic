/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.ui.screen.nowPlaying.component.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import paige.navic.R
import paige.navic.domain.manager.runtime.PreferenceManager
import paige.navic.playback.MediaPlayer
import paige.navic.ui.core.PlaybackOutput
import paige.navic.ui.icons.Icons
import paige.navic.ui.icons.outlined.Radio
import paige.navic.ui.screen.nowPlaying.component.controls.NowPlayingProgressBar

@Composable
fun NowPlayingControlsRow(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	songRating: Int,
	onSetSongRating: (Int) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayer>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Column {
			NowPlayingInfoRow(
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred,
				songRating = songRating,
				onSetSongRating = onSetSongRating
			)
			NowPlayingProgressBar()
			NowPlayingDurationsRow()
			if (playerState.output == PlaybackOutput.SERVER) {
				Row(
					modifier = Modifier.padding(top = 4.dp),
					horizontalArrangement = Arrangement.spacedBy(4.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(
						imageVector = Icons.Outlined.Radio,
						contentDescription = null,
						modifier = Modifier.size(14.dp),
						tint = MaterialTheme.colorScheme.primary
					)
					Text(
						text = stringResource(R.string.info_playing_on_server),
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.primary
					)
				}
			}
			if (preferenceManager.nowPlayingSongInfo) {
				NowPlayingTechnicalInfoRow()
			}
		}
		Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 30.dp))
		NowPlayingButtonsRow()
	}
}
