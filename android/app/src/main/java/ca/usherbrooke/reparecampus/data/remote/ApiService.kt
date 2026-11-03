package ca.usherbrooke.reparecampus.data.remote

import ca.usherbrooke.reparecampus.data.remote.dto.AssignBody
import ca.usherbrooke.reparecampus.data.remote.dto.AuthResponse
import ca.usherbrooke.reparecampus.data.remote.dto.CategoryDto
import ca.usherbrooke.reparecampus.data.remote.dto.CreateRequestBody
import ca.usherbrooke.reparecampus.data.remote.dto.DiagnosisBody
import ca.usherbrooke.reparecampus.data.remote.dto.EstimateBody
import ca.usherbrooke.reparecampus.data.remote.dto.EstimateResponseBody
import ca.usherbrooke.reparecampus.data.remote.dto.InterventionBody
import ca.usherbrooke.reparecampus.data.remote.dto.LoginBody
import ca.usherbrooke.reparecampus.data.remote.dto.NotificationPage
import ca.usherbrooke.reparecampus.data.remote.dto.PartBody
import ca.usherbrooke.reparecampus.data.remote.dto.RegisterBody
import ca.usherbrooke.reparecampus.data.remote.dto.RequestDto
import ca.usherbrooke.reparecampus.data.remote.dto.RequestPage
import ca.usherbrooke.reparecampus.data.remote.dto.SlotDto
import ca.usherbrooke.reparecampus.data.remote.dto.StatusBody
import ca.usherbrooke.reparecampus.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginBody): AuthResponse

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): AuthResponse

    @GET("api/auth/me")
    suspend fun me(): UserDto

    @GET("api/categories")
    suspend fun categories(): List<CategoryDto>

    // Annuaire du personnel (coordination/administration) : sert à l'affectation.
    // Filtres : role=repairer, categoryId=<id> (réparateurs compétents).
    @GET("api/users")
    suspend fun users(@QueryMap filters: Map<String, String>): List<UserDto>

    // Créneaux de la clinique (personnel) : proposés lors de l'affectation.
    @GET("api/slots")
    suspend fun slots(): List<SlotDto>

    @GET("api/repair-requests")
    suspend fun listRequests(@QueryMap filters: Map<String, String>): RequestPage

    @GET("api/repair-requests/{id}")
    suspend fun getRequest(@Path("id") id: String): RequestDto

    @POST("api/repair-requests")
    suspend fun createRequest(@Body body: CreateRequestBody): RequestDto

    @POST("api/repair-requests/{id}/assign")
    suspend fun assign(@Path("id") id: String, @Body body: AssignBody): RequestDto

    @POST("api/repair-requests/{id}/claim")
    suspend fun claim(@Path("id") id: String): RequestDto

    @POST("api/repair-requests/{id}/status")
    suspend fun changeStatus(@Path("id") id: String, @Body body: StatusBody): RequestDto

    @POST("api/repair-requests/{id}/diagnosis")
    suspend fun addDiagnosis(@Path("id") id: String, @Body body: DiagnosisBody): RequestDto

    @POST("api/repair-requests/{id}/estimate")
    suspend fun addEstimate(@Path("id") id: String, @Body body: EstimateBody): RequestDto

    @POST("api/repair-requests/{id}/estimate/response")
    suspend fun respondEstimate(@Path("id") id: String, @Body body: EstimateResponseBody): RequestDto

    @POST("api/repair-requests/{id}/interventions")
    suspend fun addIntervention(@Path("id") id: String, @Body body: InterventionBody): RequestDto

    @POST("api/repair-requests/{id}/parts")
    suspend fun addPart(@Path("id") id: String, @Body body: PartBody): RequestDto

    @GET("api/notifications")
    suspend fun notifications(@Query("unread") unread: Boolean? = null): NotificationPage

    @PATCH("api/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): NotificationDtoResponse

    @POST("api/notifications/read-all")
    suspend fun markAllRead(): Map<String, Int>
}

// La route PATCH /read renvoie la notification seule.
typealias NotificationDtoResponse = ca.usherbrooke.reparecampus.data.remote.dto.NotificationDto
