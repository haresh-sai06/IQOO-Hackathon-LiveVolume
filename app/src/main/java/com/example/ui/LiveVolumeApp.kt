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

import com.example.ui.components.DepthDebugPreviewScreen
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
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
import com.example.ui.components.InAppNotificationBanner
import com.example.ui.theme.LiveError
import com.example.ui.theme.LivePrimaryContainer
import com.example.util.InAppNotificationManager

sealed class Screen(val route: String) {
  data object Welcome : Screen("welcome")
  data object Auth : Screen("auth")
  data object Main : Screen("main")
  data object Profile : Screen("profile")
  data object Call : Screen("call/{callerName}?channel={channelName}") {
    fun createRoute(callerName: String, channelName: String? = null): String {
      val encName = java.net.URLEncoder.encode(callerName, "UTF-8")
      val encChan = channelName?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: ""
      return "call/$encName?channel=$encChan"
    }
  }
  data object Guides : Screen("guides")
  data object Help : Screen("help")
  data object About : Screen("about")
  data object Privacy : Screen("privacy")
  data object Notifications : Screen("notifications")
  data object DepthDebug : Screen("depth_debug")
}

@Composable
fun LiveVolumeApp() {
  val navController = rememberNavController()
  val context = LocalContext.current
  val signalingRepo = remember { CallSignalingRepository.getInstance(context) }
  val incomingCall by signalingRepo.incomingCall.collectAsStateWithLifecycle()
  val authRepository = remember { AuthRepository.getInstance(context) }
  val currentUser by authRepository.currentUser.collectAsStateWithLifecycle()
  val currentInAppNotification by InAppNotificationManager.currentNotification.collectAsStateWithLifecycle()
  val startDestination = if (currentUser != null) Screen.Main.route else Screen.Auth.route

  // Automatically start listening for incoming call invites
  androidx.compose.runtime.LaunchedEffect(currentUser) {
    val myName = currentUser?.name ?: "Me"
    signalingRepo.startListeningForIncomingCalls(myName)
  }

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
          val myName = currentUser?.name ?: "Me"
          val session = signalingRepo.startCall(
            callerName = myName,
            receiverName = callerName
          )
          navController.navigate(Screen.Call.createRoute(callerName, session.channelName))
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
        onNavigateToDepthDebug = {
          navController.navigate(Screen.DepthDebug.route)
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
          defaultValue = "Live Contact"
        },
        navArgument("channelName") {
          type = NavType.StringType
          defaultValue = ""
        }
      )
    ) { backStackEntry ->
      val rawName = backStackEntry.arguments?.getString("callerName") ?: "Live Contact"
      val rawChannel = backStackEntry.arguments?.getString("channelName") ?: ""
      val callerName = try {
        java.net.URLDecoder.decode(rawName, "UTF-8")
      } catch (e: Exception) {
        rawName
      }
      val channelName = try {
        java.net.URLDecoder.decode(rawChannel, "UTF-8").ifBlank { null }
      } catch (e: Exception) {
        null
      }

      CallScreen(
        callerName = callerName,
        channelName = channelName,
        onEndCall = {
          signalingRepo.endCall()
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

    composable(Screen.DepthDebug.route) {
      DepthDebugPreviewScreen(
        onBack = {
          navController.popBackStack()
        }
      )
    }
  }

  // Floating In-App Push Notification Banner (FCM / Realtime alerts)
  InAppNotificationBanner(
    notification = currentInAppNotification,
    onDismiss = { InAppNotificationManager.dismiss() },
    onNotificationClick = { notification ->
      // If notification is about a call, could navigate directly
      if (notification.type == com.example.util.InAppNotificationType.CALL_MISSED ||
          notification.type == com.example.util.InAppNotificationType.CALL_INCOMING) {
        navController.navigate(Screen.Main.route)
      }
    }
  )

  // Incoming Real-time Video Call Notification Dialog
  incomingCall?.let { call ->
    AlertDialog(
      onDismissRequest = { signalingRepo.dismissIncomingCall() },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Videocam, contentDescription = null, tint = LivePrimaryContainer)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Incoming Video Call", fontWeight = FontWeight.Bold)
        }
      },
      text = {
        Text("${call.callerName} is calling you via LiveVolume.")
      },
      confirmButton = {
        Button(
          onClick = {
            signalingRepo.acceptCall(call)
            navController.navigate(Screen.Call.createRoute(call.callerName, call.channelName))
          },
          colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
        ) {
          Text("Answer", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { signalingRepo.declineCall(call) }) {
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
  onNavigateToDepthDebug: () -> Unit,
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
          onNavigateToDepthDebug = onNavigateToDepthDebug,
          onLogOut = onLogOut
        )
      }
    }
  }
}
