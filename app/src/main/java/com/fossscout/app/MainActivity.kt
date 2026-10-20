package com.fossscout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fossscout.app.ui.FossScoutApp
import com.fossscout.app.ui.theme.FossScoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FossScoutTheme {
                FossScoutApp()
            }
        }
    }
}
