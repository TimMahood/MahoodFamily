package com.mahood.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mahood.family.core.Family

/** Bottom navigation: Home, each enabled module, and Family (members, modules, sync). */
@Composable
fun MainScreen(onLeft: () -> Unit) {
    val nav = rememberNavController()
    // Bumped when modules are switched on/off so the bottom bar updates.
    var modulesVersion by remember { mutableIntStateOf(0) }
    val enabled = remember(modulesVersion) { Family.enabledModules() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun go(target: String) = nav.navigate(target) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "home", onClick = { go("home") },
                    icon = { Icon(Icons.Filled.Home, null) }, label = { Text("Home") },
                )
                enabled.forEach { m ->
                    NavigationBarItem(
                        selected = route == "m/${m.id}", onClick = { go("m/${m.id}") },
                        icon = { Icon(m.icon, null) }, label = { Text(m.title) },
                    )
                }
                NavigationBarItem(
                    selected = route == "family", onClick = { go("family") },
                    icon = { Icon(Icons.Filled.Apps, null) }, label = { Text("Family") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(enabled, open = { go("m/$it") }) }
                // One route per module so each tab keeps its own saved state.
                Family.modules.forEach { module ->
                    composable("m/${module.id}") {
                        Column(Modifier.fillMaxSize()) { module.Screen(Family.context) }
                    }
                }
                composable("family") {
                    FamilyScreen(onModulesChanged = { modulesVersion++ }, onLeft = onLeft)
                }
            }
        }
    }
}
