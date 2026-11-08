package com.p2pacademy.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.p2pacademy.ui.*

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Главная", Icons.Filled.Home),
    Tab("market", "Рынок", Icons.AutoMirrored.Filled.TrendingUp),
    Tab("trades", "Сделки", Icons.Filled.SwapHoriz),
    Tab("bank", "Банк", Icons.Filled.AccountBalance),
    Tab("wallet", "Кошелёк", Icons.Filled.AccountBalanceWallet),
)

@Composable
fun AppNavigation(vm: GameViewModel) {
    val nav = rememberNavController()
    val state by vm.state.collectAsState()
    val message by vm.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    fun go(route: String) {
        if (tabs.any { it.route == route }) {
            nav.navigate(route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else nav.navigate(route) { launchSingleTop = true }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = { go(t.route) },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        },
    ) { pad ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(vm, s, ::go) }
            composable("market") { MarketScreen(vm, s, ::go) }
            composable("trades") { TradesScreen(vm, s, ::go) }
            composable("bank") { BankScreen(s, ::go) }
            composable("wallet") { WalletScreen(s) }
            composable("trade/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                TradeDetailScreen(vm, s, it.arguments?.getInt("id") ?: -1, ::go)
            }
            composable("trader/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                TraderScreen(s, it.arguments?.getInt("id") ?: -1)
            }
            composable("bank/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                BankDetailScreen(vm, s, it.arguments?.getInt("id") ?: -1)
            }
            composable("learn") { LearnScreen(s, ::go) }
            composable("lesson/{id}") { LessonScreen(vm, it.arguments?.getString("id") ?: "") { nav.popBackStack() } }
            composable("practice") { PracticeScreen(vm, s) }
            composable("exam") { ExamScreen(vm, s) }
            composable("cases") { CasesScreen(s, ::go) }
            composable("case/{id}") { CaseScreen(vm, it.arguments?.getString("id") ?: "") { nav.popBackStack() } }
            composable("stats") { StatsScreen(s) }
            composable("profile") { ProfileScreen(s) }
            composable("settings") { SettingsScreen(vm, s) }
        }
    }
}
