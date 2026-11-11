/*
 * Navic, a Subsonic music streaming app for Android
 * Copyright (c) 2026 paige
 * SPDX-License-Identifier: GPL-3.0-only
 */

package paige.navic.domain.manager.playback

import dev.zt64.subsonic.client.SubsonicAuth
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import paige.navic.domain.manager.runtime.PreferenceManager
import paige.navic.domain.manager.session.SessionManager
import paige.navic.util.network.NavicDnsResolver
import paige.navic.util.network.createHttpClientWithPreferences

/**
 * Small direct client for the Subsonic jukebox API.
 *
 * The vendored subsonic-kotlin client's jukebox binding is not usable for
 * Navidrome: it types song IDs as Long and discards the returned status. This
 * class speaks the same protocol directly so server output does not depend on
 * that binding.
 */
class JukeboxClient(
	private val sessionManager: SessionManager,
	preferenceManager: PreferenceManager,
	dnsResolver: NavicDnsResolver
) {
	private val json = Json {
		ignoreUnknownKeys = true
	}
	private val client: HttpClient = createHttpClientWithPreferences(
		preferenceManager,
		dnsResolver
	)

	suspend fun status(): JukeboxStatus = request("status")

	suspend fun start(): JukeboxStatus = request("start")

	suspend fun stop(): JukeboxStatus = request("stop")

	suspend fun clear(): JukeboxStatus = request("clear")

	suspend fun shuffle(): JukeboxStatus = request("shuffle")

	suspend fun set(ids: List<String>): JukeboxStatus = request("set") {
		ids.forEach { id -> parameter("id", id) }
	}

	suspend fun add(ids: List<String>): JukeboxStatus = request("add") {
		ids.forEach { id -> parameter("id", id) }
	}

	suspend fun remove(index: Int): JukeboxStatus = request("remove") {
		parameter("index", index)
	}

	suspend fun skip(index: Int, offsetSeconds: Int = 0): JukeboxStatus = request("skip") {
		parameter("index", index)
		if (offsetSeconds != 0) parameter("offset", offsetSeconds)
	}

	suspend fun setGain(gain: Float): JukeboxStatus = request("setGain") {
		parameter("gain", gain)
	}

	private suspend fun request(
		action: String,
		configure: HttpRequestBuilder.() -> Unit = {}
	): JukeboxStatus {
		val instanceUrl = sessionManager.instanceUrl.trimEnd('/')
		if (instanceUrl.isBlank()) throw JukeboxException("Not signed in")

		val auth = SubsonicAuth.Token(sessionManager.username, sessionManager.password)
		val body = client.get("$instanceUrl/rest/jukeboxControl") {
			parameter("u", auth.username)
			parameter("t", auth.token)
			parameter("s", auth.salt)
			parameter("v", "1.16.1")
			parameter("c", "Navic")
			parameter("f", "json")
			parameter("action", action)
			configure()
		}.bodyAsText()

		val envelope = json.decodeFromString<JukeboxEnvelope>(body)
		if (envelope.response.status != "ok") {
			throw JukeboxException(
				envelope.response.error?.message ?: "Jukebox request failed"
			)
		}
		return envelope.response.jukeboxStatus
			?: throw JukeboxException("Jukebox response did not contain a status")
	}
}

class JukeboxException(message: String) : Exception(message)

@Serializable
data class JukeboxStatus(
	val currentIndex: Int = 0,
	val playing: Boolean = false,
	val gain: Float = 1f,
	val position: Int = 0
)

@Serializable
private data class JukeboxEnvelope(
	@SerialName("subsonic-response")
	val response: JukeboxResponse
)

@Serializable
private data class JukeboxResponse(
	val status: String,
	val error: JukeboxError? = null,
	val jukeboxStatus: JukeboxStatus? = null
)

@Serializable
private data class JukeboxError(
	val code: Int? = null,
	val message: String = ""
)
