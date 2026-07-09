package dev.matejgroombridge.argot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.matejgroombridge.argot.ui.AppViewModel
import dev.matejgroombridge.argot.ui.Screen
import dev.matejgroombridge.argot.ui.components.ConfettiOverlay
import dev.matejgroombridge.argot.ui.components.LevelUpToast
import dev.matejgroombridge.argot.ui.components.MethodDialog
import dev.matejgroombridge.argot.ui.components.SettingsDialog
import dev.matejgroombridge.argot.ui.screens.HomeScreen
import dev.matejgroombridge.argot.ui.screens.SessionScreen
import dev.matejgroombridge.argot.ui.screens.SummaryScreen
import dev.matejgroombridge.argot.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppContent(vm)
                }
            }
        }
    }
}

@Composable
private fun AppContent(vm: AppViewModel) {
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 40 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "screen",
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        ) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(vm)
                Screen.Session -> SessionScreen(vm)
                Screen.Summary -> SummaryScreen(vm)
            }
        }

        // one-shot overlays, drawn above everything (web: #confetti canvas + .levelup toast)
        ConfettiOverlay(vm.confettiTick, Modifier.fillMaxSize())
        vm.levelUpLevel?.let { lvl ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .safeDrawingPadding()
                    .padding(top = 18.dp),
            ) {
                LevelUpToast(lvl)
            }
        }

        if (vm.showMethod) MethodDialog { vm.showMethod = false }
        if (vm.showSettings) SettingsDialog(vm) { vm.showSettings = false }
    }
}
