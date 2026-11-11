package paige.navic.ui.core

/**
 * Where audio is currently being rendered.
 *
 * PHONE  - local ExoPlayer playback on this device
 * SERVER - Navidrome Jukebox playback on the server's audio hardware
 */
enum class PlaybackOutput {
	PHONE,
	SERVER
}
