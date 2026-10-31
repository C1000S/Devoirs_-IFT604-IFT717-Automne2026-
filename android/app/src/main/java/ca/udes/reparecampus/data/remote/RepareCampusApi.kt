package ca.udes.reparecampus.data.remote

import ca.udes.reparecampus.data.model.AppNotification
import ca.udes.reparecampus.data.model.Category
import ca.udes.reparecampus.data.model.RepairRequest
import ca.udes.reparecampus.data.remote.dto.AssignBody
import ca.udes.reparecampus.data.remote.dto.AuthResponse
import ca.udes.reparecampus.data.remote.dto.CreateRequestBody
import ca.udes.reparecampus.data.remote.dto.DiagnosisBody
import ca.udes.reparecampus.data.remote.dto.EstimateBody
import ca.udes.reparecampus.data.remote.dto.EstimateResponseBody
import ca.udes.reparecampus.data.remote.dto.InterventionBody
import ca.udes.reparecampus.data.remote.dto.LoginRequest
import ca.udes.reparecampus.data.remote.dto.RegisterRequest
import ca.udes.reparecampus.data.remote.dto.StatusBody
import ca.udes.reparecampus.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrat Retrofit de l'API RépareCampus (TP1a). Les chemins sont relatifs à BuildConfig.API_BASE_URL. */
interface RepareCampusApi {

    @GET("api/health")
    suspend fun health(): Unit

    // --- Authentification ---
    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): UserDto

    // --- Référentiels ---
    @GET("api/categories")
    suspend fun categories(): List<Category>

    @GET("api/users")
    suspend fun users(@Query("role") role: String? = null): List<UserDto>

    // --- Demandes (la visibilité est filtrée par le serveur selon le rôle) ---
    @GET("api/repair-requests")
    suspend fun requests(): List<RepairRequest>

    @GET("api/repair-requests/{id}")
    suspend fun request(@Path("id") id: String): RepairRequest

    @POST("api/repair-requests")
    suspend fun createRequest(@Body body: CreateRequestBody): RepairRequest

    @PATCH("api/repair-requests/{id}/estimate-response")
    suspend fun respondToEstimate(@Path("id") id: String, @Body body: EstimateResponseBody): RepairRequest

    @PATCH("api/repair-requests/{id}/status")
    suspend fun changeStatus(@Path("id") id: String, @Body body: StatusBody): RepairRequest

    @PATCH("api/repair-requests/{id}/diagnosis")
    suspend fun addDiagnosis(@Path("id") id: String, @Body body: DiagnosisBody): RepairRequest

    @PATCH("api/repair-requests/{id}/estimate")
    suspend fun proposeEstimate(@Path("id") id: String, @Body body: EstimateBody): RepairRequest

    @POST("api/repair-requests/{id}/interventions")
    suspend fun addIntervention(@Path("id") id: String, @Body body: InterventionBody): RepairRequest

    @PATCH("api/repair-requests/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: AssignBody): RepairRequest

    // --- Notifications ---
    @GET("api/notifications")
    suspend fun notifications(): List<AppNotification>

    @PATCH("api/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): AppNotification
}
