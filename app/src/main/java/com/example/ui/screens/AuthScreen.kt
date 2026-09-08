package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.repository.AuthRepository
import com.example.model.DataRepository
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
  val focusManager = LocalFocusManager.current

  var isRegisterMode by remember { mutableStateOf(false) }
  var fullName by remember { mutableStateOf("") }
  var emailOrPhone by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var rememberMe by remember { mutableStateOf(true) }
  var agreeTerms by remember { mutableStateOf(true) }
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val handleAuth: () -> Unit = {
    val emailClean = emailOrPhone.trim()
    val passwordClean = password.trim()
    val nameClean = fullName.trim()

    if (isRegisterMode && nameClean.isBlank()) {
      errorMessage = "Please enter your full name."
    } else if (emailClean.isBlank()) {
      errorMessage = "Please enter your email or phone number."
    } else if (!emailClean.contains("@") && emailClean.length < 7) {
      errorMessage = "Please enter a valid email address or phone number."
    } else if (passwordClean.isBlank()) {
      errorMessage = "Please enter your password."
    } else if (passwordClean.length < 6) {
      errorMessage = "Password must be at least 6 characters."
    } else if (isRegisterMode && !agreeTerms) {
      errorMessage = "Please accept the Terms & Privacy Policy to continue."
    } else {
      scope.launch {
        isLoading = true
        errorMessage = null
        val result = if (isRegisterMode) {
          authRepository.register(nameClean, emailClean, passwordClean)
        } else {
          authRepository.signIn(emailClean, passwordClean)
        }
        isLoading = false
        result.onSuccess {
          onAuthSuccess()
        }.onFailure {
          errorMessage = it.message ?: "Authentication failed. Please check your credentials."
        }
      }
    }
  }

  val scrollState = rememberScrollState()

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.surface)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding()
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Top minimal bar with back navigation only
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        contentAlignment = Alignment.CenterStart
      ) {
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
      }

      // Constrain inner width for clean tablet/desktop & modern centered look
      Column(
        modifier = Modifier
          .widthIn(max = 420.dp)
          .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Single refined brand mark
        Box(
          modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
              width = 1.dp,
              color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
              shape = RoundedCornerShape(16.dp)
            )
            .padding(10.dp),
          contentAlignment = Alignment.Center
        ) {
          AsyncImage(
            model = DataRepository.LOGO_URL,
            contentDescription = "LiveVolume",
            modifier = Modifier.fillMaxSize()
          )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Clean typography header
        Text(
          text = if (isRegisterMode) "Create an account" else "Welcome back",
          style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
          ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = if (isRegisterMode) {
            "Sign up to experience high-fidelity spatial calls."
          } else {
            "Please enter your details to sign in."
          },
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Input Fields
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // Full Name (Only in Register mode)
          AnimatedVisibility(visible = isRegisterMode) {
            Column {
              Text(
                text = "Full Name",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
              )
              OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                placeholder = {
                  Text(
                    "Enter your name",
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                  )
                },
                leadingIcon = {
                  Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                  )
                },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("full_name_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                  focusedContainerColor = MaterialTheme.colorScheme.surface,
                  unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                  cursorColor = MaterialTheme.colorScheme.primary
                ),
                keyboardOptions = KeyboardOptions(
                  keyboardType = KeyboardType.Text,
                  imeAction = ImeAction.Next
                ),
                singleLine = true
              )
            }
          }

          // Email field
          Column {
            Text(
              text = "Email address",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            OutlinedTextField(
              value = emailOrPhone,
              onValueChange = { emailOrPhone = it },
              placeholder = {
                Text(
                  "name@example.com",
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.AlternateEmail,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                  modifier = Modifier.size(20.dp)
                )
              },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("email_phone_input"),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
              ),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
              ),
              singleLine = true
            )
          }

          // Password field
          Column {
            Text(
              text = "Password",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Medium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
            )
            OutlinedTextField(
              value = password,
              onValueChange = { password = it },
              placeholder = {
                Text(
                  "••••••••",
                  color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                )
              },
              leadingIcon = {
                Icon(
                  imageVector = Icons.Default.Lock,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.outline,
                  modifier = Modifier.size(20.dp)
                )
              },
              trailingIcon = {
                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                  Icon(
                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "Toggle password visibility",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                  )
                }
              },
              visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
              ),
              keyboardActions = KeyboardActions(
                onDone = {
                  focusManager.clearFocus()
                  handleAuth()
                }
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("password_input"),
              shape = RoundedCornerShape(12.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                cursorColor = MaterialTheme.colorScheme.primary
              ),
              singleLine = true
            )
          }

          // Helper row (Remember me & Forgot password in Login mode, Terms checkbox in Register mode)
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
                  colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outlineVariant
                  )
                )
                Text(
                  text = "Remember me",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }

              Text(
                text = "Forgot password?",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
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
                colors = CheckboxDefaults.colors(
                  checkedColor = MaterialTheme.colorScheme.primary,
                  uncheckedColor = MaterialTheme.colorScheme.outlineVariant
                )
              )
              Text(
                text = "I agree to the ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "Terms & Privacy Policy",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onTermsClick() }
              )
            }
          }
        }

        // Error message banner
        AnimatedVisibility(
          visible = errorMessage != null,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                .border(
                  1.dp,
                  MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                  RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Action Button
        Button(
          onClick = {
            focusManager.clearFocus()
            handleAuth()
          },
          enabled = !isLoading,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("auth_submit_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
          )
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
          } else {
            Text(
              text = if (isRegisterMode) "Create account" else "Sign in",
              style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold
              )
            )
          }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Subtle Divider
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
          )
          Text(
            text = "or continue with",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(horizontal = 12.dp)
          )
          HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Refined Social Buttons (Google & Apple)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
              )
              .clickable {
                scope.launch {
                  isLoading = true
                  errorMessage = null
                  authRepository.signIn("google.user@example.com", "Password123!")
                  isLoading = false
                  onAuthSuccess()
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "G",
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4285F4),
                fontSize = 17.sp
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Google",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .height(46.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(MaterialTheme.colorScheme.surface)
              .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
              )
              .clickable {
                scope.launch {
                  isLoading = true
                  errorMessage = null
                  authRepository.signIn("apple.user@example.com", "Password123!")
                  isLoading = false
                  onAuthSuccess()
                }
              },
            contentAlignment = Alignment.Center
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "",
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "Apple",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Bottom Mode Switcher
        Row(
          modifier = Modifier.padding(bottom = 20.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center
        ) {
          Text(
            text = if (isRegisterMode) "Already have an account? " else "Don't have an account? ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = if (isRegisterMode) "Sign in" else "Sign up",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
              errorMessage = null
              isRegisterMode = !isRegisterMode
            }
          )
        }
      }
    }
  }
}
