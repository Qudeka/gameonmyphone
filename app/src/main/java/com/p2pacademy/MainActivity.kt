package com.p2pacademy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.p2pacademy.navigation.AppNavigation
import com.p2pacademy.ui.home.HomeViewModel
import com.p2pacademy.ui.theme.P2PTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as P2PApp).walletRepository
        setContent {
            P2PTheme {
                val vm: HomeViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repo) as T
                })
                AppNavigation(vm)
            }
        }
    }
}
