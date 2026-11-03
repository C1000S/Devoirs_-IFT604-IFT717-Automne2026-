package ca.usherbrooke.reparecampus.data.repository

import ca.usherbrooke.reparecampus.data.remote.ApiService
import ca.usherbrooke.reparecampus.data.remote.dto.SlotDto
import ca.usherbrooke.reparecampus.data.remote.dto.UserDto
import ca.usherbrooke.reparecampus.util.Constants

// Annuaire du personnel et créneaux : réservé à la coordination/administration côté serveur.
// Utilisé par l'écran de détail pour affecter une demande à un réparateur.
class DirectoryRepository(private val api: () -> ApiService) {

    suspend fun repairers(categoryId: String? = null): Result<List<UserDto>> = runCatching {
        val filters = buildMap {
            put("role", Constants.ROLE_REPAIRER)
            if (!categoryId.isNullOrBlank()) put("categoryId", categoryId!!)
        }
        api().users(filters)
    }

    suspend fun slots(): Result<List<SlotDto>> = runCatching { api().slots() }
}
