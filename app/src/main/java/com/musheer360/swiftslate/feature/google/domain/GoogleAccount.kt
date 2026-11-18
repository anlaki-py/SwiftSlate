package com.musheer360.swiftslate.feature.google.domain

/** Public account state deliberately excludes OAuth access and refresh tokens. */
data class GoogleAccount(
    val id: String,
    val email: String,
    val name: String,
    val isEnabled: Boolean = true,
    val needsLogin: Boolean = false,
    val cooldownUntil: Long = 0,
    val remainingFraction: Double? = null,
    val verificationUrl: String? = null
)

enum class GoogleFailure {
    NETWORK, NO_ACCOUNTS, ALL_DISABLED, LOGIN_REQUIRED, RATE_LIMITED, ACCESS_DENIED,
    VERIFY_ACCOUNT, CLIENT_OUTDATED, CONFIGURATION, LOGIN_CANCELLED, LOGIN_TIMEOUT,
    LOGIN_PORT_BUSY, INVALID_CALLBACK, STORAGE, INVALID_RESPONSE, MODEL_UNAVAILABLE,
    BLOCKED, TRUNCATED, SERVER
}

/** Typed failures contain no response bodies or credentials. */
class GoogleException(
    val failure: GoogleFailure,
    val status: Int = 0,
    val retryAfterMillis: Long = 60_000,
    val verificationUrl: String? = null
) : Exception(failure.name)

/** Selects the next usable owned account, respecting disablement, revocation and cooldowns. */
object SelectGoogleAccount {
    operator fun invoke(accounts: List<GoogleAccount>, start: Int, attempted: Set<String>, now: Long): GoogleAccount? {
        if (accounts.isEmpty()) return null
        return accounts.indices.asSequence().map { accounts[(start + it).mod(accounts.size)] }
            .firstOrNull { it.isEnabled && !it.needsLogin && it.cooldownUntil <= now &&
                it.verificationUrl == null && it.id !in attempted }
    }
}
