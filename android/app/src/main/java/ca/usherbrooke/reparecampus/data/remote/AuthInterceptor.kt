package ca.usherbrooke.reparecampus.data.remote

import ca.usherbrooke.reparecampus.data.session.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

// Ajoute l'en-tête Authorization: Bearer <token> aux requêtes sortantes
// lorsque la personne est connectée.
class AuthInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.cachedToken
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
