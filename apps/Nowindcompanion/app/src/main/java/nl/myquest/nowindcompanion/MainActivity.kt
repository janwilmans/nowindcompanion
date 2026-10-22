package nl.myquest.nowindcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nl.myquest.nowindcompanion.ui.NowindNavHost
import nl.myquest.nowindcompanion.ui.theme.NowindCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NowindCompanionTheme {
                NowindNavHost(nowindApp)
            }
        }
    }
}
