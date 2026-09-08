package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.LiveVolumeBottomBar
import com.example.ui.components.NavigationTab
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.GuidesScreen
import com.example.ui.screens.HelpSupportScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen

sealed class Screen(val route: String) {
  data object Welcome : Screen("welcome")
  data object Auth : Screen("auth")
  data object Main : Screen("main")
  data object Call : Screen("call/{callerName}") {
    fun createRoute(callerName: String) = "call/${java.net.URLEncoder.encode(callerName, "UTF-8")}"
  }
  data object Guides : Screen("guides")
  data object Help : Screen("help")
  data object About : Screen("about")
  data object Privacy : Screen("privacy")
}

@Composable
fun LiveVolumeApp() {
  val navController = rememberNavController()

  NavHost(
    navController = navController,
    startDestination = Screen.Welcome.route
  ) {
    composable(Screen.Welcome.route) {
      WelcomeScreen(
        onGetStarted = {
          navController.navigate(Screen.Main.route)
        },
        onSignIn = {
          navController.navigate(Screen.Auth.route)
        },
        onTermsClick = {
          navController.navigate(Screen.About.route)
        },
        onPrivacyClick = {
          navController.navigate(Screen.Privacy.route)
        }
      )
    }

    composable(Screen.Auth.route) {
      AuthScreen(
        onBack = {
          navController.popBackStack()
        },
        onAuthSuccess = {
          navController.navigate(Screen.Main.route) {
            popUpTo(Screen.Welcome.route) { inclusive = true }
          }
        },
        onTermsClick = {
          navController.navigate(Screen.Privacy.route)
        }
      )
    }

    composable(Screen.Main.route) {
      MainShellScreen(
        onStartCall = { callerName ->
          navController.navigate(Screen.Call.createRoute(callerName))
        },
        onNavigateToGuides = {
          navController.navigate(Screen.Guides.route)
        },
        onNavigateToHelp = {
          navController.navigate(Screen.Help.route)
        },
        onNavigateToAbout = {
          navController.navigate(Screen.About.route)
        },
        onNavigateToPrivacy = {
          navController.navigate(Screen.Privacy.route)
        },
        onLogOut = {
          navController.navigate(Screen.Welcome.route) {
            popUpTo(0) { inclusive = true }
          }
        }
      )
    }

    composable(
      route = Screen.Call.route,
      arguments = listOf(
        navArgument("callerName") {
          type = NavType.StringType
          defaultValue = "Sarah Chen"
        }
      )
    ) { backStackEntry ->
      val rawName = backStackEntry.arguments?.getString("callerName") ?: "Sarah Chen"
      val callerName = try {
        java.net.URLDecoder.decode(rawName, "UTF-8")
      } catch (e: Exception) {
        rawName
      }

      CallScreen(
        callerName = callerName,
        onEndCall = {
          navController.popBackStack()
        }
      )
    }

    composable(Screen.Guides.route) {
      GuidesScreen(
        onBack = {
          navController.popBackStack()
        },
        onNavigateToHelp = {
          navController.navigate(Screen.Help.route)
        }
      )
    }

    composable(Screen.Help.route) {
      HelpSupportScreen(
        onBack = {
          navController.popBackStack()
        },
        onNavigateToLightingGuide = {
          navController.navigate(Screen.Guides.route)
        }
      )
    }

    composable(Screen.About.route) {
      AboutScreen(
        onBack = {
          navController.popBackStack()
        },
        onNavigateToPrivacy = {
          navController.navigate(Screen.Privacy.route)
        }
      )
    }

    composable(Screen.Privacy.route) {
      PrivacyScreen(
        onBack = {
          navController.popBackStack()
        }
      )
    }
  }
}

@Composable
fun MainShellScreen(
  onStartCall: (callerName: String) -> Unit,
  onNavigateToGuides: () -> Unit,
  onNavigateToHelp: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onLogOut: () -> Unit
) {
  var currentTab by remember { mutableStateOf(NavigationTab.RECENTS) }

  Scaffold(
    bottomBar = {
      LiveVolumeBottomBar(
        currentTab = currentTab,
        onTabSelected = { currentTab = it }
      )
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (currentTab) {
        NavigationTab.RECENTS -> RecentsScreen(
          onStartCall = onStartCall
        )
        NavigationTab.CONTACTS -> ContactsScreen(
          onStartCall = onStartCall
        )
        NavigationTab.HISTORY -> HistoryScreen(
          onStartCall = onStartCall
        )
        NavigationTab.SETTINGS -> SettingsScreen(
          onNavigateToGuides = onNavigateToGuides,
          onNavigateToHelp = onNavigateToHelp,
          onNavigateToAbout = onNavigateToAbout,
          onNavigateToPrivacy = onNavigateToPrivacy,
          onLogOut = onLogOut
        )
      }
    }
  }
}
