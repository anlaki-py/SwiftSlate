package com.musheer360.swiftslate.feature.google.data

import com.musheer360.swiftslate.BuildConfig

/** The supplied installed-app OAuth client uses this fixed loopback redirect. */
internal data class GoogleOAuthSettings(
    val clientId: String = BuildConfig.GOOGLE_OAUTH_CLIENT_ID,
    val clientSecret: String = BuildConfig.GOOGLE_OAUTH_CLIENT_SECRET
) {
    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    override fun toString(): String = "GoogleOAuthSettings(redacted)"

    companion object {
        const val CALLBACK_PORT = 51121
        const val CALLBACK_PATH = "/oauth-callback"
        const val REDIRECT_URI = "http://localhost:51121/oauth-callback"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val ENDPOINT = "https://daily-cloudcode-pa.googleapis.com"
        const val USER_AGENT = "antigravity/hub/2.24.0 (aidev_client; os_type=darwin; arch=arm64; cl=1000335682)"
        val SCOPES = listOf("cloud-platform", "userinfo.email", "userinfo.profile", "cclog", "experimentsandconfigs")
            .joinToString(" ") { "https://www.googleapis.com/auth/$it" }
    }
}
