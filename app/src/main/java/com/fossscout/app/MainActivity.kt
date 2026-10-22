package com.fossscout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fossscout.app.ui.FossScoutApp
import com.fossscout.core.ui.theme.FossScoutTheme

class MainActivity : ComponentActivity() {
    private val container by lazy { AppContainer(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FossScoutTheme {
                FossScoutApp(container = container)
            }
        }
    }
}
