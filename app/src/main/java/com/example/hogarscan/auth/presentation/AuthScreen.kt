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
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.policy.AuthInputPolicy

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
            .padding(20.dp),
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
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HogarNotice(
                    title = if (notice is AuthNotice.Failure) {
                        "No se pudo completar la operación"
                    } else {
                        "Aviso de la cuenta"
                    },
                    body = notice.message(),
                    error = notice is AuthNotice.Failure,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                )

                TextButton(onClick = onDismissNotice) {
                    Text(stringResource(R.string.auth_dismiss))
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

                HogarButton(
                    text = stringResource(R.string.auth_logout),
                    onClick = { onLogout(session.session) },
                    enabled = state.operation == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_logout")
                )
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
    val registering = page?.let {
        it == AccountPage.Register
    } ?: localRegistering

    var email by rememberSaveable { mutableStateOf("") }

    // Las contraseñas permanecen únicamente en memoria.
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    var inputFailure by remember {
        mutableStateOf<AuthFailure?>(null)
    }

    val focus = LocalFocusManager.current

    fun clearSecrets() {
        password = ""
        confirmation = ""
        visible = false
        focus.clearFocus()
    }

    LaunchedEffect(state.notice) {
        if (state.notice == AuthNotice.AccountCreated) {
            localRegistering = false
            inputFailure = null
            clearSecrets()
        }
    }

    val submit: () -> Unit = {
        if (state.canSubmitCredentials) {
            onDismissNotice()

            val normalizedEmail = AuthInputPolicy.normalizeEmail(email)
            val failure = if (registering) {
                AuthInputPolicy.validateRegistration(
                    normalizedEmail,
                    password,
                    confirmation
                )
            } else {
                AuthInputPolicy.validateLogin(
                    normalizedEmail,
                    password
                )
            }

            inputFailure = failure

            if (failure == null) {
                val submittedPassword = password
                val submittedConfirmation = confirmation

                clearSecrets()

                if (registering) {
                    onRegister(
                        email,
                        submittedPassword,
                        submittedConfirmation
                    )
                } else {
                    onLogin(email, submittedPassword)
                }
            }
        }
    }

    if (page == null) {
        Text(
            stringResource(
                if (registering) {
                    R.string.auth_register_title
                } else {
                    R.string.auth_login_title
                }
            ),
            style = MaterialTheme.typography.titleLarge
        )
    }

    val emailError = inputFailure == AuthFailure.INVALID_EMAIL
    val passwordError = inputFailure == AuthFailure.INVALID_PASSWORD
    val confirmationError =
        inputFailure == AuthFailure.PASSWORD_CONFIRMATION_MISMATCH

    HogarField(
        value = email,
        onValueChange = {
            email = it
            inputFailure = null
        },
        label = stringResource(R.string.auth_email),
        help = if (emailError) {
            stringResource(R.string.auth_error_email)
        } else if (registering) {
            "Usa un correo al que tengas acceso."
        } else {
            "El correo con el que creaste tu cuenta."
        },
        enabled = state.canSubmitCredentials,
        isError = emailError,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth(),
        fieldModifier = Modifier.testTag("auth_email")
    )

    val passwordHelp = stringResource(
        if (passwordError) {
            R.string.auth_error_password
        } else if (registering) {
            R.string.auth_password_registration_hint
        } else {
            R.string.auth_password_login_hint
        }
    )

    val passwordTransformation = if (visible) {
        VisualTransformation.None
    } else {
        PasswordVisualTransformation()
    }

    HogarField(
        value = password,
        onValueChange = {
            password = it
            inputFailure = null
        },
        label = stringResource(R.string.auth_password),
        help = passwordHelp,
        enabled = state.canSubmitCredentials,
        isError = passwordError,
        visualTransformation = passwordTransformation,
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = if (registering) {
                ImeAction.Next
            } else {
                ImeAction.Done
            }
        ),
        keyboardActions = KeyboardActions(
            onDone = { submit() }
        ),
        modifier = Modifier.fillMaxWidth(),
        fieldModifier = Modifier.testTag("auth_password")
    )

    if (registering) {
        HogarField(
            value = confirmation,
            onValueChange = {
                confirmation = it
                inputFailure = null
            },
            label = stringResource(R.string.auth_confirmation),
            help = if (confirmationError) {
                stringResource(R.string.auth_error_confirmation)
            } else {
                "Escribe nuevamente la misma contraseña."
            },
            enabled = state.canSubmitCredentials,
            isError = confirmationError,
            visualTransformation = passwordTransformation,
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { submit() }
            ),
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = Modifier.testTag("auth_confirmation")
        )
    }

    if (page != null) {
        TextButton(
            onClick = { visible = !visible },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmitCredentials
        ) {
            Text(
                if (visible) {
                    "Ocultar contraseña"
                } else {
                    "Mostrar contraseña"
                }
            )
        }
    }

    HogarButton(
        text = stringResource(
            if (registering) {
                R.string.auth_register
            } else {
                R.string.auth_login
            }
        ),
        onClick = submit,
        enabled = state.canSubmitCredentials &&
                email.isNotBlank() &&
                password.isNotEmpty() &&
                (!registering || confirmation.isNotEmpty()),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_submit")
    )

    HogarButton(
        text = stringResource(
            if (registering) {
                R.string.auth_switch_login
            } else {
                R.string.auth_switch_register
            }
        ),
        onClick = {
            clearSecrets()
            inputFailure = null
            onDismissNotice()

            if (page == null) {
                localRegistering = !registering
            } else {
                onPage(
                    if (registering) {
                        AccountPage.Login
                    } else {
                        AccountPage.Register
                    }
                )
            }
        },
        enabled = state.canSubmitCredentials,
        secondary = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_switch_mode")
    )
}
