/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.database.model

/**
 * Projection of a downloaded song, joined from [paige.navic.database.entity.DownloadEntity]
 * and [paige.navic.database.entity.SongEntity], used when copying downloads out to
 * shared storage where a human-readable filename and proper tags are needed.
 */
data class ExportSongRow(
	val songId: String,
	val title: String,
	val artistName: String?,
	val albumTitle: String?,
	val trackNumber: Int?,
	val discNumber: Int?,
	val fileExtension: String?,
	val mimeType: String?,
	val filePath: String,
	val downloadMimeType: String?
)
