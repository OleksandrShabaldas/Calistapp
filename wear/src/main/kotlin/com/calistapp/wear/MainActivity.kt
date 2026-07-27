package com.calistapp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calistapp.wear.session.WearSessionViewModel
import com.calistapp.wear.ui.WearApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: WearSessionViewModel = viewModel()
            WearApp(viewModel)
        }
    }
}
