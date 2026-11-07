package com.p2pacademy.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.p2pacademy.ui.home.HomeScreen
import com.p2pacademy.ui.home.HomeViewModel

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Dest("home", "Главная", Icons.Filled.Home)
    data object Market : Dest("market", "Рынок", Icons.Filled.TrendingUp)
    data object Trades : Dest("trades", "Сделки", Icons.Filled.SwapHoriz)
    data object Bank : Dest("bank", "Банк", Icons.Filled.AccountBalance)
    data object Wallet : Dest("wallet", "Кошелёк", Icons.Filled.AccountBalanceWallet)
}

private val bottomItems = listOf(Dest.Home, Dest.Market, Dest.Trades, Dest.Bank, Dest.Wallet)

@Composable
fun AppNavigation(homeViewModel: HomeViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun go(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomItems.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = { go(d.route) },
                        icon = { Icon(d.icon, contentDescription = d.label) },
                        label = { Text(d.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = Dest.Home.route, modifier = Modifier.padding(pad)) {
            composable(Dest.Home.route) {
                HomeScreen(
                    vm = homeViewModel,
                    onOpenMarket = { go(Dest.Market.route) },
                )
            }
            composable(Dest.Market.route) { Placeholder("Рынок — этап 2") }
            composable(Dest.Trades.route) { Placeholder("Сделки — этап 3") }
            composable(Dest.Bank.route) { Placeholder("Банк — этап 4") }
            composable(Dest.Wallet.route) { Placeholder("Кошелёк — этап 3") }
        }
    }
}

@Composable
private fun Placeholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column { Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
