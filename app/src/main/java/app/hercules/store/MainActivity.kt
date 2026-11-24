package app.hercules.store

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.content.Context
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.hercules.store.ui.HerculesRoot
import app.hercules.store.ui.theme.HerculesTheme

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = newBase.getSharedPreferences("hercules_settings", Context.MODE_PRIVATE)
            .getString("language", "system")
        val configuration = Configuration(newBase.resources.configuration)
        when (language) {
            "en" -> configuration.setLocale(java.util.Locale.forLanguageTag("en"))
            "ru" -> configuration.setLocale(java.util.Locale.forLanguageTag("ru"))
            else -> configuration.setLocales(LocaleList.getDefault())
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge()
        }
        setContent {
            HerculesTheme {
                HerculesRoot()
            }
        }
    }
}
