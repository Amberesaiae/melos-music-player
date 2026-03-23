package com.amberesaiae.melos.core.network.util

import android.util.Base64
import java.security.MessageDigest
import java.util.UUID

/**
 * Utility functions for network authentication and encoding.
 */

/**
 * Generate MD5 hash for Subsonic authentication.
 */
fun md5(input: String): String {
    val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * Generate a random salt for Subsonic token authentication.
 */
fun generateSalt(): String {
    return UUID.randomUUID().toString()
}

/**
 * Generate Subsonic authentication token.
 * Token is MD5 hash of (password + salt).
 */
fun generateSubsonicToken(password: String, salt: String): String {
    return md5(password + salt)
}

/**
 * Generate Jellyfin X-Emby-Authorization header value.
 */
fun generateJellyfinAuthHeader(
    client: String = "Melos",
    device: String = "Android",
    deviceId: String = UUID.randomUUID().toString(),
    version: String = "1.0.0",
    token: String? = null
): String {
    var authHeader = "MediaBrowser " +
            "Client=\"$client\", " +
            "Device=\"$device\", " +
            "DeviceId=\"$deviceId\", " +
            "Version=\"$version\""
    
    if (token != null) {
        authHeader += ", Token=\"$token\""
    }
    
    return authHeader
}

/**
 * URL encode a string for query parameters.
 */
fun urlEncode(value: String): String {
    return java.net.URLEncoder.encode(value, "UTF-8")
}

/**
 * Build Subsonic query parameters map for authentication.
 */
fun buildSubsonicAuthParams(
    username: String,
    password: String,
    version: String = "1.16.1",
    client: String = "Melos",
    format: String = "json",
    useTokenAuth: Boolean = false
): Map<String, String> {
    val params = mutableMapOf(
        "u" to username,
        "v" to version,
        "c" to client,
        "f" to format
    )
    
    if (useTokenAuth) {
        val salt = generateSalt()
        val token = generateSubsonicToken(password, salt)
        params["t"] = token
        params["s"] = salt
    } else {
        params["p"] = password
    }
    
    return params
}
