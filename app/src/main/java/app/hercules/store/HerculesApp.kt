package app.hercules.store

import android.app.Application
import app.hercules.store.data.ModuleRepository
import app.hercules.store.data.remote.ModuleApi
import app.hercules.store.root.RootExecutor
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(application: Application) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val rootExecutor = RootExecutor()

    val repository = ModuleRepository(
        api = ModuleApi(client),
        catalogUrl = BuildConfig.CATALOG_URL,
        downloadDir = File(application.cacheDir, "downloads")
    )
}

class HerculesApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
