package ca.udes.reparecampus.data.remote

import ca.udes.reparecampus.data.remote.dto.ApiErrorBody
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/** Erreur applicative lisible, dérivée d'une exception réseau ou HTTP. */
class ApiException(message: String, val httpCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause) {
    val isNetwork: Boolean get() = httpCode == null
}

private val errorJson = Json { ignoreUnknownKeys = true }

fun Throwable.toApiException(): ApiException = when (this) {
    is ApiException -> this
    is HttpException -> {
        val serverMessage = runCatching {
            response()?.errorBody()?.string()?.let { errorJson.decodeFromString<ApiErrorBody>(it).error }
        }.getOrNull()
        ApiException(serverMessage ?: "Erreur du serveur (${code()}).", code(), this)
    }
    is IOException -> ApiException("Serveur injoignable. Vérifiez la connexion et l'URL de l'API.", null, this)
    else -> ApiException(message ?: "Erreur inattendue.", null, this)
}

/** Exécute un appel API et convertit toute erreur en [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e.toApiException())
    }
