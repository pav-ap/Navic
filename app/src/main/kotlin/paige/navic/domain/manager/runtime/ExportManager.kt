/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager.runtime

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import paige.navic.database.dao.DownloadDao
import paige.navic.database.model.ExportSongRow
import paige.navic.util.Logger
import java.io.File

/**
 * Copies downloaded songs out of the app-private download directory into shared
 * storage, indexing them with MediaStore so other music apps and file managers
 * can see them.
 *
 * Downloads normally live in either filesDir/downloads or the app-specific
 * external directory, both of which are unreadable to other apps on Android 11+.
 * Re-exporting through MediaStore is the supported way to get the audio back out.
 */
class ExportManager(
	private val context: Context,
	private val downloadDao: DownloadDao
) {
	data class ExportProgress(
		val total: Int = 0,
		val done: Int = 0,
		val exported: Int = 0,
		val skipped: Int = 0,
		val failed: Int = 0
	)

	/** MediaStore RELATIVE_PATH is only available from Android 10 onwards. */
	val isSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

	/**
	 * Exports every downloaded song. Idempotent: a song whose destination file
	 * already exists in the collection is counted as skipped rather than
	 * duplicated, so re-running only copies what is missing.
	 */
	fun exportDownloads(): Flow<ExportProgress> = flow {
		val songs = downloadDao.getDownloadedSongsForExport()
		var progress = ExportProgress(total = songs.size)
		emit(progress)

		for (song in songs) {
			val outcome = runCatching { exportSong(song) }
				.getOrElse { error ->
					Logger.w("Export", "Failed to export ${song.songId}: ${error.message}")
					Outcome.FAILED
				}
			progress = when (outcome) {
				Outcome.EXPORTED -> progress.copy(exported = progress.exported + 1)
				Outcome.SKIPPED -> progress.copy(skipped = progress.skipped + 1)
				Outcome.FAILED -> progress.copy(failed = progress.failed + 1)
			}.let { it.copy(done = it.done + 1) }
			emit(progress)
		}
	}.flowOn(Dispatchers.IO)

	private enum class Outcome { EXPORTED, SKIPPED, FAILED }

	private fun exportSong(song: ExportSongRow): Outcome {
		val source = File(song.filePath)
		if (!source.isFile || source.length() == 0L) return Outcome.FAILED

		val extension = (song.fileExtension ?: source.extension)
			.takeIf { it.isNotBlank() }
			?.lowercase()
			?: return Outcome.FAILED

		val artist = sanitize(song.artistName, UNKNOWN_ARTIST)
		val album = sanitize(song.albumTitle, UNKNOWN_ALBUM)
		val title = sanitize(song.title, "Track")
		val fileName = if (song.trackNumber != null && song.trackNumber > 0) {
			"%02d - %s.%s".format(song.trackNumber, title, extension)
		} else {
			"$title.$extension"
		}
		val relativePath = "${Environment.DIRECTORY_MUSIC}/$EXPORT_FOLDER/$artist/$album/"

		val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
		if (alreadyExported(collection, relativePath, fileName)) return Outcome.SKIPPED

		val values = ContentValues().apply {
			put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
			put(MediaStore.Audio.Media.TITLE, song.title)
			put(MediaStore.Audio.Media.ARTIST, song.artistName ?: UNKNOWN_ARTIST)
			put(MediaStore.Audio.Media.ALBUM, song.albumTitle ?: UNKNOWN_ALBUM)
			song.trackNumber?.let { put(MediaStore.Audio.Media.TRACK, it) }
			song.discNumber?.let { put(MediaStore.Audio.Media.DISC_NUMBER, it) }
			put(MediaStore.Audio.Media.MIME_TYPE, song.downloadMimeType ?: song.mimeType ?: guessMimeType(extension))
			put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
			put(MediaStore.Audio.Media.IS_PENDING, 1)
		}

		val uri = context.contentResolver.insert(collection, values) ?: return Outcome.FAILED

		try {
			context.contentResolver.openOutputStream(uri)?.use { output ->
				source.inputStream().use { input -> input.copyTo(output) }
			} ?: run {
				context.contentResolver.delete(uri, null, null)
				return Outcome.FAILED
			}

			ContentValues().apply {
				put(MediaStore.Audio.Media.IS_PENDING, 0)
			}.let { context.contentResolver.update(uri, it, null, null) }

			return Outcome.EXPORTED
		} catch (error: Exception) {
			Logger.w("Export", "Write failed for ${song.songId}: ${error.message}")
			runCatching { context.contentResolver.delete(uri, null, null) }
			return Outcome.FAILED
		}
	}

	private fun alreadyExported(collection: Uri, relativePath: String, fileName: String): Boolean {
		val projection = arrayOf(MediaStore.Audio.Media._ID)
		val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND " +
			"${MediaStore.Audio.Media.DISPLAY_NAME} = ?"
		return runCatching {
			context.contentResolver.query(
				collection,
				projection,
				selection,
				arrayOf(relativePath, fileName),
				null
			)?.use { it.count > 0 } ?: false
		}.getOrDefault(false)
	}

	private fun sanitize(raw: String?, fallback: String): String {
		val cleaned = (raw ?: "")
			.replace(UNSAFE_PATH_CHARS, "_")
			.trim()
			.trim('.')
			.take(MAX_COMPONENT_LENGTH)
		return cleaned.ifBlank { fallback }
	}

	private fun guessMimeType(extension: String): String = when (extension) {
		"mp3" -> "audio/mpeg"
		"m4a", "mp4" -> "audio/mp4"
		"aac" -> "audio/aac"
		"flac" -> "audio/flac"
		"ogg" -> "audio/ogg"
		"opus" -> "audio/opus"
		"wav" -> "audio/x-wav"
		"wma" -> "audio/x-ms-wma"
		else -> "audio/*"
	}

	private companion object {
		const val EXPORT_FOLDER = "Navic"
		const val UNKNOWN_ARTIST = "Unknown Artist"
		const val UNKNOWN_ALBUM = "Unknown Album"
		const val MAX_COMPONENT_LENGTH = 60
		val UNSAFE_PATH_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
	}
}
