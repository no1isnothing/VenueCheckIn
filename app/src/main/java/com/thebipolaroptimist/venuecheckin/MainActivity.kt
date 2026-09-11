package com.thebipolaroptimist.venuecheckin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.thebipolaroptimist.venuecheckin.ui.VenueScreen
import com.thebipolaroptimist.venuecheckin.ui.theme.VenueCheckInTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VenueCheckInTheme {
                VenueScreen()
            }
        }
    }
}
