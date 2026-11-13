package com.hossa.distorsion

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    // Cambia cada vez que el launcher vuelve a primer plano (para refrescar apps)
    private val resumeTick = mutableIntStateOf(0)

    // Cambia cuando el usuario presiona "Inicio" estando ya en el launcher
    private val homePressed = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            DistorsionTheme {
                LauncherScreen(
                    resumeTick = resumeTick.intValue,
                    homePressed = homePressed.intValue
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue = resumeTick.intValue + 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePressed.intValue = homePressed.intValue + 1
    }
}
