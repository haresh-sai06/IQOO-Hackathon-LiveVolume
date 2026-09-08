package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PrivacyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.CallSignalingRepository
import com.example.data.repository.AuthRepository
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer

sealed class Screen(val route: String) {
  data object Welcome : Screen("welcome")
  data object Auth : Screen("auth")
  data object Main : Screen("main")
  data object Profile : Screen("profile")
  data object Call : Screen("call/{callerName}") {
    fun createRoute(callerName: String) = "call/${java.net.URLEncoder.encode(callerName, "UTF-8")}"
  }
  data object Guides : Screen("guides")
  data object Help : Screen("help")
  data object About : Screen("about")
  data object Privacy : Screen("privacy")
  data object Notifications : Screen("notifications")
}

@Composable
fun LiveVolumeApp() {
  val navController = rememberNavController()
  val context = LocalContext.current
  val signalingRepo = remember { CallSignalingRepository.getInstance(context) }
  val incomingCall by signalingRepo.incomingCall.collectAsStateWithLifecycle()
  val authRepository = remember { AuthRepository.getInstance(context) }
  val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
  val startDestination = if (currentUser != null) Screen.Main.route else Screen.Auth.route

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .statusBarsPadding()
  ) {
    NavHost(
      navController = navController,
      startDestination = startDestination
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
            popUpTo(Screen.Auth.route) { inclusive = true }
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
        onOpenProfile = {
          navController.navigate(Screen.Profile.route)
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
        onNavigateToNotifications = {
          navController.navigate(Screen.Notifications.route)
        },
        onLogOut = {
          authRepository.signOut()
          navController.navigate(Screen.Auth.route) {
            popUpTo(0) { inclusive = true }
          }
        }
      )
    }

    composable(Screen.Profile.route) {
      ProfileScreen(
        onBack = { navController.popBackStack() },
        onLogOut = {
          authRepository.signOut()
          navController.navigate(Screen.Auth.route) {
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

    composable(Screen.Notifications.route) {
      NotificationsScreen(
        onBack = {
          navController.popBackStack()
        }
      )
    }
  }

  // Incoming Real-time 3D Call Notification Dialog
  incomingCall?.let { call ->
    AlertDialog(
      onDismissRequest = { signalingRepo.dismissIncomingCall() },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.ViewInAr, contentDescription = null, tint = LivePrimaryContainer)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Incoming 3D Spatial Call", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Text("${call.callerName} is calling you with live volumetric depth and spatial audio.")
      },
      confirmButton = {
        Button(
          onClick = {
            signalingRepo.dismissIncomingCall()
            navController.navigate(Screen.Call.createRoute(call.callerName))
          },
          colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
        ) {
          Text("Answer (3D)", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { signalingRepo.dismissIncomingCall() }) {
          Text("Decline", color = LiveError)
        }
      }
    )
  }
}
}

@Composable
fun MainShellScreen(
  onStartCall: (callerName: String) -> Unit,
  onOpenProfile: () -> Unit,
  onNavigateToGuides: () -> Unit,
  onNavigateToHelp: () -> Unit,
  onNavigateToAbout: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onNavigateToNotifications: () -> Unit,
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
          onStartCall = onStartCall,
          onOpenProfile = onOpenProfile
        )
        NavigationTab.HISTORY -> HistoryScreen(
          onStartCall = onStartCall
        )
        NavigationTab.SETTINGS -> SettingsScreen(
          onNavigateToProfile = onOpenProfile,
          onNavigateToGuides = onNavigateToGuides,
          onNavigateToHelp = onNavigateToHelp,
          onNavigateToAbout = onNavigateToAbout,
          onNavigateToPrivacy = onNavigateToPrivacy,
          onNavigateToNotifications = onNavigateToNotifications,
          onLogOut = onLogOut
        )
      }
    }
  }
}
