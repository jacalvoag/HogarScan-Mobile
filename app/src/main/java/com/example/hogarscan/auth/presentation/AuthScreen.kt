package com.example.hogarscan.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hogarscan.R
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.ui.components.*

enum class AccountPage { Account, Login, Register }

@Composable
fun AuthRoute(
    viewModel: AuthViewModel,
    modifier: Modifier = Modifier,
    page: AccountPage = AccountPage.Account,
    onPage: (AccountPage) -> Unit = {},
    localCount: Int? = null,
    onHistory: () -> Unit = {},
    deleteContent: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.session, state.notice, page) {
        if (page != AccountPage.Account && state.session is SessionState.Authenticated) {
            onPage(AccountPage.Account)
        } else if (page == AccountPage.Register && state.notice == AuthNotice.AccountCreated) {
            onPage(AccountPage.Login)
        }
    }

    AuthScreen(
        state = state,
        onLogin = viewModel::login,
        onRegister = viewModel::register,
        onLogout = viewModel::logout,
        onDismissNotice = viewModel::dismissNotice,
        modifier = modifier,
        page = page,
        onPage = onPage,
        localCount = localCount,
        onHistory = onHistory,
        deleteContent = deleteContent
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onLogout: (SessionSnapshot) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
    page: AccountPage? = null,
    onPage: (AccountPage) -> Unit = {},
    localCount: Int? = null,
    onHistory: () -> Unit = {},
    deleteContent: @Composable () -> Unit = {}
) {
    val credentials = page == AccountPage.Login || page == AccountPage.Register
    BackHandler(enabled = credentials) { onPage(AccountPage.Account) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (credentials) {
            HogarBackHeader(stringResource(if (page == AccountPage.Register) R.string.auth_register_title else R.string.auth_login_title),
                { onPage(AccountPage.Account) })
            if (page == AccountPage.Login) {
                HogarIconImage(HogarIcon.Logo, tint = false, size = 64.dp)
                Text("Accede a tus evaluaciones y estadísticas.")
            } else {
                Text("Tus revisiones, organizadas", style = MaterialTheme.typography.headlineMedium)
            }
        } else {
            HogarBrandHeader()
            Text("Cuenta y datos", style = MaterialTheme.typography.headlineMedium)
        }

        state.operation?.let { operation ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )

                Text(
                    stringResource(
                        when (operation) {
                            AuthOperation.RESTORE -> R.string.auth_restoring
                            AuthOperation.REGISTER -> R.string.auth_registering
                            AuthOperation.LOGIN -> R.string.auth_logging_in
                            AuthOperation.LOGOUT -> R.string.auth_logging_out
                        }
                    )
                )
            }
        }

        state.notice?.let { notice ->
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = notice.message(),
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
                        color = if (notice is AuthNotice.Failure) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )

                    TextButton(onClick = onDismissNotice) {
                        Text(stringResource(R.string.auth_dismiss))
                    }
                }
            }
        }

        when (val session = state.session) {
            SessionState.Restoring ->
                Text(stringResource(R.string.auth_please_wait))

            SessionState.Guest -> if (page == AccountPage.Account) {
                HogarNotice("Modo invitado", "Tus revisiones se conservan en este teléfono.")
                HogarButton("Iniciar sesión", { onPage(AccountPage.Login) }, Modifier.fillMaxWidth())
                HogarButton("Crear una cuenta", { onPage(AccountPage.Register) }, Modifier.fillMaxWidth(), secondary = true)
                HogarButton("Gestionar revisiones locales", onHistory, Modifier.fillMaxWidth(), secondary = true)
            } else {
                AuthForm(state, onLogin, onRegister, onDismissNotice, page,
                    onPage = onPage)
                if (page == AccountPage.Login) {
                    TextButton(onClick = { onPage(AccountPage.Account) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar sin cuenta")
                    }
                }
            }

            is SessionState.Authenticated -> {
                HogarCard {
                    Text(stringResource(R.string.auth_signed_in), style = MaterialTheme.typography.bodySmall)
                    Text(session.session.email, style = MaterialTheme.typography.titleLarge)
                }
                if (page != null) {
                    Text("Tus datos", style = MaterialTheme.typography.titleLarge)
                    HogarCard {
                        Text("En este teléfono", style = MaterialTheme.typography.titleLarge)
                        Text("Direcciones, conexiones y evaluaciones de este perfil.")
                        localCount?.let { Text("$it revisiones locales.") }
                        HogarButton("Gestionar revisiones locales", onHistory, Modifier.fillMaxWidth(), secondary = true)
                    }
                    HogarCard {
                        Text("En tu cuenta", style = MaterialTheme.typography.titleLarge)
                        Text("Fechas y conteos de los resúmenes que recibió el servidor.")
                    }
                    deleteContent()
                }

                Button(
                    onClick = { onLogout(session.session) },
                    enabled = state.operation == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_logout")
                ) {
                    Text(stringResource(R.string.auth_logout))
                }
            }
        }
    }
}

@Composable
private fun AuthForm(
    state: AuthUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onDismissNotice: () -> Unit,
    page: AccountPage?,
    onPage: (AccountPage) -> Unit
) {
    var localRegistering by rememberSaveable { mutableStateOf(false) }
    val registering = page?.let { it == AccountPage.Register } ?: localRegistering
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    val focus = LocalFocusManager.current

    LaunchedEffect(state.notice) {
        if (state.notice == AuthNotice.AccountCreated) {
            localRegistering = false
            password = ""
            confirmation = ""
            focus.clearFocus()
        }
    }

    val submit: () -> Unit = {
        if (state.canSubmitCredentials) {
            val submittedPassword = password
            val submittedConfirmation = confirmation

            password = ""
            confirmation = ""
            focus.clearFocus()

            if (registering) {
                onRegister(email, submittedPassword, submittedConfirmation)
            } else {
                onLogin(email, submittedPassword)
            }
        }
    }

    if (page == null) Text(
        stringResource(
            if (registering) {
                R.string.auth_register_title
            } else {
                R.string.auth_login_title
            }
        ),
        style = MaterialTheme.typography.titleLarge
    )

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.auth_email)) },
        enabled = state.canSubmitCredentials,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_email")
    )

    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.auth_password)) },
        supportingText = {
            Text(
                stringResource(
                    if (registering) {
                        R.string.auth_password_registration_hint
                    } else {
                        R.string.auth_password_login_hint
                    }
                )
            )
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        enabled = state.canSubmitCredentials,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = if (registering) ImeAction.Next else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_password")
    )

    if (registering) {
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            label = {
                Text(stringResource(R.string.auth_confirmation))
            },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            enabled = state.canSubmitCredentials,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_confirmation")
        )
    }

    if (page != null) TextButton(
        onClick = { visible = !visible }, modifier = Modifier.fillMaxWidth(),
        enabled = state.canSubmitCredentials
    ) { Text(if (visible) "Ocultar contraseña" else "Mostrar contraseña") }

    Button(
        onClick = submit,
        enabled = state.canSubmitCredentials &&
                email.isNotBlank() &&
                password.isNotEmpty() &&
                (!registering || confirmation.isNotEmpty()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_submit")
    ) {
        Text(
            stringResource(
                if (registering) R.string.auth_register
                else R.string.auth_login
            )
        )
    }

    TextButton(
        onClick = {
            password = ""
            confirmation = ""
            visible = false
            focus.clearFocus()
            onDismissNotice()
            if (page == null) localRegistering = !registering
            else onPage(if (registering) AccountPage.Login else AccountPage.Register)
        },
        enabled = state.canSubmitCredentials,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_switch_mode")
    ) {
        Text(
            stringResource(
                if (registering) R.string.auth_switch_login
                else R.string.auth_switch_register
            )
        )
    }
}
