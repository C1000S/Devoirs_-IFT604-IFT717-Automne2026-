package ca.usherbrooke.reparecampus.util

import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException

// Traduit une exception d'appel réseau en message lisible pour la personne.
// Les erreurs de l'API RépareCampus renvoient un corps JSON { "error": { "message": ... } }
// ou { "message": ... } ; on tente d'en extraire le message, sinon un repli par type.
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> parseHttpMessage(this) ?: when (code()) {
        401 -> "Identifiants invalides ou session expirée."
        403 -> "Action non autorisée pour votre rôle."
        404 -> "Ressource introuvable."
        409 -> "Conflit : la ressource existe déjà."
        422, 400 -> "Données invalides."
        in 500..599 -> "Erreur du serveur. Réessayez plus tard."
        else -> "Erreur réseau (${code()})."
    }
    is IOException -> "Impossible de joindre le serveur. Vérifiez la connexion."
    else -> message ?: "Une erreur inattendue est survenue."
}

private fun parseHttpMessage(e: HttpException): String? = try {
    val raw = e.response()?.errorBody()?.string().orEmpty()
    if (raw.isBlank()) null else {
        val json = JSONObject(raw)
        when {
            json.has("message") -> json.getString("message")
            json.has("error") && json.get("error") is JSONObject ->
                json.getJSONObject("error").optString("message").ifBlank { null }
            json.has("error") -> json.getString("error")
            else -> null
        }
    }
} catch (_: Exception) {
    null
}
