package ca.udes.reparecampus.data.remote

import ca.udes.reparecampus.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun createClient(authInterceptor: AuthInterceptor, reachabilityInterceptor: Interceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(reachabilityInterceptor)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** Le flux SSE reste ouvert indéfiniment (battement toutes les 25 s) : pas de délai de lecture. */
    fun createSseClient(base: OkHttpClient): OkHttpClient = base.newBuilder().readTimeout(0, TimeUnit.SECONDS).build()

    fun createApi(client: OkHttpClient): RepareCampusApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RepareCampusApi::class.java)
}
