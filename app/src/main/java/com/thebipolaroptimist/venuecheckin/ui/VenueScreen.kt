package com.thebipolaroptimist.venuecheckin.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun VenueScreen(viewModel: VenueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Text(
            text = state.toString(),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
