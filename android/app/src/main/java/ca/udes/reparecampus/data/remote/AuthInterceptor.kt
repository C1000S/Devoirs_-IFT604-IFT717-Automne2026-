package ca.udes.reparecampus.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajoute l'en-tête `Authorization: Bearer <token>` et signale un 401
 * (jeton expiré ou invalide) pour forcer la déconnexion.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider()
        val request = chain.request().newBuilder().apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        val response = chain.proceed(request)
        if (response.code == 401 && token != null) onUnauthorized()
        return response
    }
}
