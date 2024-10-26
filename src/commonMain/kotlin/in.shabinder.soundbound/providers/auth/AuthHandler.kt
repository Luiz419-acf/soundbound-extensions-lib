package `in`.shabinder.soundbound.providers.auth

import androidx.compose.runtime.Immutable
import `in`.shabinder.soundbound.providers.Dependencies
import `in`.shabinder.soundbound.providers.auth.AuthHandler.AuthMethod.*
import `in`.shabinder.soundbound.providers.auth.AuthHandler.AuthMethod.AuthData.*
import `in`.shabinder.soundbound.utils.GlobalJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Immutable
interface AuthHandler : Dependencies {

    val authMethodType: AuthMethod
    val isAuthAvailable: Boolean
        get() = authMethodType !is AuthNotNeeded

    val needsReAuthentication: Boolean
        get() = authMethodType !is AuthNotNeeded && authStatus !is AuthStatus.Authenticated

    val authStatus: AuthStatus
        get() {
            return when (val authType = authMethodType) {
                is CookieAuthAvailable -> {
                    val savedCookies = (mAuthData as? CookieData)?.cookies

                    authType.requiredCookieNames.forEach { cookieNames ->
                        if (cookieNames.none { savedCookies?.containsKey(it.key) == true }) {
                            return AuthStatus.NotAuthenticated
                        }
                    }

                    return AuthStatus.Authenticated
                }

                is AuthNotNeeded -> {
                    // Auth not needed
                    AuthStatus.Authenticated
                }
            }
        }


    val authDataConfigKey: String
    /*protected*/ var mAuthData: AuthData?
        set(value) {
            if (value == null) {
                devicePreferences.remove(authDataConfigKey)
            } else {
                devicePreferences.putString(authDataConfigKey, GlobalJson.encodeToString(value))
            }
        }
        get() {
            if (!isAuthAvailable) {
                return NoAuthData
            }

            val serialisedData = devicePreferences.getStringOrNull(authDataConfigKey)
                ?: return null

            return runCatching {
                GlobalJson.decodeFromString<AuthData>(serialisedData)
            }.getOrNull()
        }

    // Subclass can override below for sanitation // cleanup if needed
    val authData: AuthData? get() = mAuthData
    fun setAuthData(authData: AuthData?) {
        mAuthData = authData
    }


    @Immutable
    @Serializable
    sealed class AuthMethod {

      // Signifies that auth is a pre-requisite for operations
      abstract val isRequired: Boolean

        @Immutable
        @Serializable
        sealed class AuthData {
            @Immutable
            @Serializable
            data class CookieData(val cookies: Map<String, String>) : AuthData()

            @Immutable
            @Serializable
            data object NoAuthData : AuthData()
        }

        @Immutable
        @Serializable
        data class CookieAuthAvailable(
            val originURL: String,
            val requiredCookieNames: List<List<CookieKey>>, // within list, any cookie name suffices, acts like OR
            val headers: Map<String, String> = emptyMap(),
            val userAgentString: String? = null,
            val saveAll: Boolean = true, // we check against required cookies, but save all.
            override val isRequired: Boolean = false
        ) : AuthMethod() {
            @Immutable
            @Serializable
            data class CookieKey(val key: String, val forURL: String)
        }

        @Immutable
        @Serializable
        data object AuthNotNeeded : AuthMethod() {
            override val isRequired: Boolean = false
        }
    }


    @Serializable
    @Immutable
    sealed class AuthStatus {

        @Serializable
        @Immutable
        data object Authenticated: AuthStatus()

        @Serializable
        @Immutable
        data object NotAuthenticated: AuthStatus()

        @Serializable
        @Immutable
        data class Error(val message: String): AuthStatus()
    }
}
