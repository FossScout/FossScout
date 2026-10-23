package com.fossscout.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fossscout.app.ui.FossScoutApp
import com.fossscout.core.inventory.PackageIdentityParser
import com.fossscout.core.ui.theme.FossScoutTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }
    private var sharedInventoryQuery by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedInventoryQuery = extractInventoryQuery(intent)
        setContent {
            FossScoutTheme {
                FossScoutApp(
                    container = container,
                    initialInventoryQuery = sharedInventoryQuery,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedInventoryQuery = extractInventoryQuery(intent)
    }

    private fun extractInventoryQuery(intent: Intent?): String? {
        if (intent == null) return null
        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        return PackageIdentityParser.extract(sharedText.orEmpty())
            ?: PackageIdentityParser.extract(subject.orEmpty())
            ?: PackageIdentityParser.extract(intent.dataString.orEmpty())
    }
}
