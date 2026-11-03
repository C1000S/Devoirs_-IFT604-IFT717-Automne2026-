package ca.usherbrooke.reparecampus.data

import android.content.Context
import ca.usherbrooke.reparecampus.data.local.AppDatabase
import ca.usherbrooke.reparecampus.data.remote.ApiService
import ca.usherbrooke.reparecampus.data.remote.AuthInterceptor
import ca.usherbrooke.reparecampus.data.repository.AuthRepository
import ca.usherbrooke.reparecampus.data.repository.CategoryRepository
import ca.usherbrooke.reparecampus.data.repository.DirectoryRepository
import ca.usherbrooke.reparecampus.data.repository.DraftRepository
import ca.usherbrooke.reparecampus.data.repository.NotificationRepository
import ca.usherbrooke.reparecampus.data.repository.RequestRepository
import ca.usherbrooke.reparecampus.data.session.SessionManager
import ca.usherbrooke.reparecampus.util.ConnectivityObserver
import ca.usherbrooke.reparecampus.util.Constants
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Conteneur d'injection manuel (service locator) : instancie et relie les dépendances.
class AppContainer(context: Context) {

    val session = SessionManager(context)
    val db = AppDatabase.get(context)
    val gson = Gson()
    val connectivity = ConnectivityObserver(context)

    @Volatile
    private var baseUrl = Constants.DEFAULT_BASE_URL

    @Volatile
    private var apiService: ApiService = buildApi(baseUrl)

    private fun buildApi(url: String): ApiService {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(session))
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }

    fun api(): ApiService = apiService

    // Reconstruit le client lorsque l'URL du serveur change (champ de connexion).
    fun rebuildApi(url: String) {
        baseUrl = url
        apiService = buildApi(url)
    }

    // Charge le jeton en cache et l'URL du serveur persistés (au démarrage).
    suspend fun warmUp() {
        session.warmUp()
        rebuildApi(session.getBaseUrl())
    }

    val authRepository = AuthRepository({ api() }, session, db)
    val requestRepository = RequestRepository({ api() }, db, gson)
    val categoryRepository = CategoryRepository({ api() }, db)
    val notificationRepository = NotificationRepository({ api() }, db)
    val draftRepository = DraftRepository(db)
    val directoryRepository = DirectoryRepository({ api() })
}
