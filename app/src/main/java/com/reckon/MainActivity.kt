package com.reckon

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reckon.ui.ReckonScreen
import com.reckon.ui.ReckonViewModel
import com.reckon.ui.theme.ReckonTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val vm: ReckonViewModel = viewModel()
            ReckonTheme(dark = vm.darkMode) {
                ReckonScreen(vm)
            }
        }
    }
}
