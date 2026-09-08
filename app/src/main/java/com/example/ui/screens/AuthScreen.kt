package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.DataRepository
import com.example.ui.theme.LivePrimaryContainer

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  onBack: () -> Unit,
  onAuthSuccess: () -> Unit,
  onTermsClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val authRepository = remember { AuthRepository.getInstance(context) }
  val scope = rememberCoroutineScope()

  var isRegisterMode by remember { mutableStateOf(false) }
  var fullName by remember { mutableStateOf("") }
  var emailOrPhone by remember { mutableStateOf("alex@example.com") }
  var password by remember { mutableStateOf("Password123!") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var rememberMe by remember { mutableStateOf(true) }
  var agreeTerms by remember { mutableStateOf(true) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val handleAuth: () -> Unit = {
    scope.launch {
      isLoading = true
      errorMessage = null
      val result = if (isRegisterMode) {
        authRepository.register(fullName.ifBlank { "Alex Rivera" }, emailOrPhone, password)
      } else {
        authRepository.signIn(emailOrPhone, password)
      }
      isLoading = false
      result.onSuccess {
        onAuthSuccess()
      }.onFailure {
        errorMessage = it.message ?: "Authentication failed"
      }
    }
  }

  val scrollState = rememberScrollState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .verticalScroll(scrollState)
      .padding(horizontal = 20.dp, vertical = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("auth_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface
          )
        }
        AsyncImage(
          model = DataRepository.LOGO_URL,
          contentDescription = "Logo",
          modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "LiveVolume",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = if (isRegisterMode) "Register" else "Sign In",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(LivePrimaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Brand Logo & Greeting Center
    Box(
      modifier = Modifier
        .size(64.dp)
        .clip(RoundedCornerShape(20.dp))
        .background(Color(0xFFF2F3FF))
        .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(20.dp))
        .padding(10.dp),
      contentAlignment = Alignment.Center
    ) {
      AsyncImage(
        model = DataRepository.LOGO_URL,
        contentDescription = "LiveVolume",
        modifier = Modifier.fillMaxSize()
      )
      Box(
        modifier = Modifier
          .size(12.dp)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(LivePrimaryContainer)
          .border(2.dp, Color.White, CircleShape)
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "LiveVolume",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Spacer(modifier = Modifier.width(8.dp))
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(99.dp))
          .background(Color(0xFFEAEDFF))
          .padding(horizontal = 8.dp, vertical = 2.dp)
      ) {
        Text(
          text = "3D LIVE",
          style = MaterialTheme.typography.labelSmall,
          color = LivePrimaryContainer,
          fontWeight = FontWeight.Bold
        )
      }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Text(
      text = if (isRegisterMode) "Create account" else "Welcome back",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onSurface
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
      text = if (isRegisterMode) "Join LiveVolume and stream in spatial 3D" else "Sign in to connect in live spatial 3D",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(24.dp))

    // Segmented Tab Switcher (Log In / Register)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(99.dp))
        .background(Color(0xFFE2E7FF))
        .padding(4.dp)
    ) {
      Row(modifier = Modifier.fillMaxWidth()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(99.dp))
            .background(if (!isRegisterMode) Color.White else Color.Transparent)
            .clickable { isRegisterMode = false }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Log In",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Medium,
            color = if (!isRegisterMode) LivePrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Box(
          modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(99.dp))
            .background(if (isRegisterMode) Color.White else Color.Transparent)
            .clickable { isRegisterMode = true }
            .padding(vertical = 10.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "Register",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Medium,
            color = if (isRegisterMode) LivePrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Input Fields
    Column(
      modifier = Modifier.fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      AnimatedVisibility(visible = isRegisterMode) {
        Column {
          Text(
            text = "Full Name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
          )
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            placeholder = { Text("Alex Rivera") },
            leadingIcon = {
              Icon(
                imageVector = Icons.Default.Badge,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
              )
            },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("full_name_input"),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = Color.White,
              unfocusedContainerColor = Color.White,
              focusedBorderColor = LivePrimaryContainer,
              unfocusedBorderColor = Color(0xFFE2E7FF)
            ),
            singleLine = true
          )
        }
      }

      Column {
        Text(
          text = "Email or Mobile Number",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
          value = emailOrPhone,
          onValueChange = { emailOrPhone = it },
          placeholder = { Text("alex@example.com") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.AlternateEmail,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("email_phone_input"),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = LivePrimaryContainer,
            unfocusedBorderColor = Color(0xFFE2E7FF)
          ),
          singleLine = true
        )
      }

      Column {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = "Password",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (isRegisterMode) {
            Text(
              text = "Min. 8 characters",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.outline
            )
          }
        }
        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          placeholder = { Text(if (isRegisterMode) "Create a secure password" else "Enter your password") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.outline
            )
          },
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle password visibility",
                tint = MaterialTheme.colorScheme.outline
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("password_input"),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = LivePrimaryContainer,
            unfocusedBorderColor = Color(0xFFE2E7FF)
          ),
          singleLine = true
        )
      }

      if (!isRegisterMode) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { rememberMe = !rememberMe }
          ) {
            Checkbox(
              checked = rememberMe,
              onCheckedChange = { rememberMe = it },
              colors = CheckboxDefaults.colors(checkedColor = LivePrimaryContainer)
            )
            Text(
              text = "Remember me",
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Text(
            text = "Forgot password?",
            style = MaterialTheme.typography.labelMedium,
            color = LivePrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { }
          )
        }
      } else {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { agreeTerms = !agreeTerms },
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = agreeTerms,
            onCheckedChange = { agreeTerms = it },
            colors = CheckboxDefaults.colors(checkedColor = LivePrimaryContainer)
          )
          Text(
            text = "I agree to LiveVolume's Terms & Privacy Policy",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
    }

    // Error Banner if auth fails
    AnimatedVisibility(visible = errorMessage != null) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFFFEE2E2))
          .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
          .padding(12.dp)
      ) {
        Text(
          text = errorMessage ?: "",
          style = MaterialTheme.typography.bodySmall,
          color = Color(0xFFB91C1C)
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Primary CTA Button
    Button(
      onClick = handleAuth,
      enabled = !isLoading,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .testTag("auth_submit_button"),
      shape = RoundedCornerShape(99.dp),
      colors = ButtonDefaults.buttonColors(containerColor = LivePrimaryContainer)
    ) {
      if (isLoading) {
        CircularProgressIndicator(
          color = Color.White,
          strokeWidth = 2.dp,
          modifier = Modifier.size(22.dp)
        )
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = if (isRegisterMode) "Create Account" else "Sign In",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Visual Divider
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E7FF))
      Text(
        text = "OR CONTINUE WITH",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(horizontal = 12.dp)
      )
      HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E7FF))
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Social Auth Buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
          .clickable {
            scope.launch {
              authRepository.signIn("google.user@example.com", "Password123!")
              onAuthSuccess()
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "G",
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF4285F4),
            fontSize = 18.sp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Google",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .height(48.dp)
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(1.dp, Color(0xFFE2E7FF), RoundedCornerShape(16.dp))
          .clickable {
            scope.launch {
              authRepository.signIn("apple.user@example.com", "Password123!")
              onAuthSuccess()
            }
          },
        contentAlignment = Alignment.Center
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color.Black
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Apple",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Mode Switcher Footer
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = if (isRegisterMode) "Already have an account?" else "Don't have an account?",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = if (isRegisterMode) "Sign In" else "Create account",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = LivePrimaryContainer,
        modifier = Modifier.clickable { isRegisterMode = !isRegisterMode }
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Trust & Encryption Indicator
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .clip(RoundedCornerShape(99.dp))
        .background(Color(0xFFF2F3FF))
        .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
      Icon(
        imageVector = Icons.Default.VerifiedUser,
        contentDescription = "Encrypted",
        tint = LivePrimaryContainer,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "End-to-end encrypted • On-device 3D processing",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}
