package com.example.hogarscan.auth.presentation

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hogarscan.auth.domain.model.AuthFailure
import com.example.hogarscan.auth.domain.model.AuthResult
import com.example.hogarscan.auth.domain.model.AuthUser
import com.example.hogarscan.auth.domain.model.LogoutOutcome
import com.example.hogarscan.auth.domain.model.SessionSnapshot
import com.example.hogarscan.auth.domain.model.SessionState
import com.example.hogarscan.auth.domain.repository.AuthRepository
import com.example.hogarscan.auth.domain.repository.SessionReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val store = ViewModelStore()
    private val repository = FakeAuthRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel(): AuthViewModel = ViewModelProvider(
        store,
        viewModelFactory {
            initializer {
                AuthViewModel(repository, repository)
            }
        }
    )[AuthViewModel::class.java]

    @Test
    fun `restaura una vez y bloquea credenciales durante el inicio`() = runTest {
        val vm = viewModel()

        assertFalse(vm.uiState.value.canSubmitCredentials)
        assertEquals(AuthOperation.RESTORE, vm.uiState.value.operation)

        vm.login(EMAIL, PASSWORD)
        vm.register(EMAIL, PASSWORD, PASSWORD)
        advanceUntilIdle()

        assertEquals(1, repository.restoreCalls)
        assertEquals(0, repository.loginCalls)
        assertEquals(0, repository.registerCalls)
        assertSame(vm, viewModel())
        assertEquals(1, repository.restoreCalls)
        assertTrue(vm.uiState.value.canSubmitCredentials)
    }

    @Test
    fun `invitado publicado antes del fin de restauracion sigue bloqueado`() = runTest {
        val release = CompletableDeferred<Unit>()

        repository.onRestore = {
            repository.state.value = SessionState.Guest
            release.await()
            AuthResult.Success(Unit)
        }

        val vm = viewModel()
        runCurrent()

        assertSame(SessionState.Guest, vm.uiState.value.session)
        assertFalse(vm.uiState.value.canSubmitCredentials)

        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.canSubmitCredentials)
    }

    @Test
    fun `fallo de restauracion queda disponible como aviso`() = runTest {
        repository.onRestore = {
            repository.state.value = SessionState.Guest
            AuthResult.Failure(AuthFailure.STORAGE)
        }

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            AuthNotice.Failure(AuthFailure.STORAGE),
            vm.uiState.value.notice
        )
        assertNull(vm.uiState.value.operation)
    }

    @Test
    fun `sesion ya inicializada no se restaura ni acepta otro login`() = runTest {
        repository.state.value = SessionState.Authenticated(SESSION)

        val vm = viewModel()
        vm.login(EMAIL, PASSWORD)
        advanceUntilIdle()

        assertEquals(0, repository.restoreCalls)
        assertEquals(0, repository.loginCalls)
        assertEquals(
            SessionState.Authenticated(SESSION),
            vm.uiState.value.session
        )
    }

    @Test
    fun `invitado ya inicializado no vuelve a leer el almacenamiento`() = runTest {
        repository.state.value = SessionState.Guest

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(0, repository.restoreCalls)
        assertTrue(vm.uiState.value.canSubmitCredentials)
    }

    @Test
    fun `registro conserva entradas y muestra aviso sin iniciar sesion`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.register("  FIXTURE@EXAMPLE.COM ", PASSWORD, PASSWORD)
        advanceUntilIdle()

        assertEquals(
            "  FIXTURE@EXAMPLE.COM ",
            repository.receivedEmail
        )
        assertEquals(PASSWORD, repository.receivedPassword)
        assertEquals(PASSWORD, repository.receivedConfirmation)
        assertEquals(1, repository.registerCalls)
        assertEquals(0, repository.loginCalls)
        assertSame(SessionState.Guest, vm.uiState.value.session)
        assertEquals(AuthNotice.AccountCreated, vm.uiState.value.notice)

        vm.dismissNotice()

        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `un resultado de login no sustituye el estado publicado por el almacen`() = runTest {
        repository.onLogin = { AuthResult.Success(SESSION) }

        val vm = viewModel()
        advanceUntilIdle()

        vm.login(EMAIL, PASSWORD)
        advanceUntilIdle()

        assertSame(SessionState.Guest, vm.uiState.value.session)

        repository.state.value = SessionState.Authenticated(SESSION)
        runCurrent()

        assertEquals(
            SessionState.Authenticated(SESSION),
            vm.uiState.value.session
        )
    }

    @Test
    fun `login refleja la sesion persistida y libera el indicador de operacion`() = runTest {
        repository.onLogin = {
            repository.state.value = SessionState.Authenticated(SESSION)
            AuthResult.Success(SESSION)
        }

        val vm = viewModel()
        advanceUntilIdle()

        vm.login(EMAIL, PASSWORD)

        assertEquals(AuthOperation.LOGIN, vm.uiState.value.operation)

        advanceUntilIdle()

        assertEquals(
            SessionState.Authenticated(SESSION),
            vm.uiState.value.session
        )
        assertNull(vm.uiState.value.operation)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `conserva el motivo del fallo y el plazo de rate limit`() = runTest {
        repository.onLogin = {
            AuthResult.Failure(AuthFailure.RATE_LIMITED, 60)
        }

        val vm = viewModel()
        advanceUntilIdle()

        vm.login(EMAIL, PASSWORD)
        advanceUntilIdle()

        assertEquals(
            AuthNotice.Failure(AuthFailure.RATE_LIMITED, 60),
            vm.uiState.value.notice
        )
        assertTrue(vm.uiState.value.canSubmitCredentials)
    }

    @Test
    fun `doble envio antes de ejecutar la coroutine solo inicia una solicitud`() = runTest {
        val release = CompletableDeferred<Unit>()

        repository.onLogin = {
            release.await()
            AuthResult.Failure(AuthFailure.INVALID_CREDENTIALS)
        }

        val vm = viewModel()
        advanceUntilIdle()

        vm.login(EMAIL, PASSWORD)
        vm.login(EMAIL, PASSWORD)
        vm.register(EMAIL, PASSWORD, PASSWORD)
        runCurrent()

        assertEquals(1, repository.loginCalls)
        assertEquals(0, repository.registerCalls)

        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AuthNotice.Failure(AuthFailure.INVALID_CREDENTIALS),
            vm.uiState.value.notice
        )
    }

    @Test
    fun `logout conserva aviso local mientras no se confirma revocacion`() = runTest {
        repository.state.value = SessionState.Authenticated(SESSION)
        val release = CompletableDeferred<Unit>()

        repository.onLogout = {
            repository.state.value = SessionState.Guest
            release.await()
            AuthResult.Success(LogoutOutcome.REMOTE_UNCONFIRMED)
        }

        val vm = viewModel()
        runCurrent()

        vm.logout(SESSION)
        runCurrent()

        assertSame(SessionState.Guest, vm.uiState.value.session)
        assertEquals(AuthOperation.LOGOUT, vm.uiState.value.operation)

        vm.login(EMAIL, PASSWORD)

        assertEquals(0, repository.loginCalls)

        release.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            AuthNotice.LoggedOut(LogoutOutcome.REMOTE_UNCONFIRMED),
            vm.uiState.value.notice
        )
        assertTrue(vm.uiState.value.canSubmitCredentials)
    }

    @Test
    fun `fallo de almacenamiento al salir se muestra aunque la sesion siga activa`() = runTest {
        repository.state.value = SessionState.Authenticated(SESSION)
        repository.onLogout = {
            AuthResult.Failure(AuthFailure.STORAGE)
        }

        val vm = viewModel()
        runCurrent()

        vm.logout(SESSION)
        advanceUntilIdle()

        assertEquals(
            AuthNotice.Failure(AuthFailure.STORAGE),
            vm.uiState.value.notice
        )

        repository.state.value = SessionState.Authenticated(
            SESSION.copy(summaryGeneration = 1)
        )
        runCurrent()

        assertEquals(
            AuthNotice.Failure(AuthFailure.STORAGE),
            vm.uiState.value.notice
        )
    }

    @Test
    fun `evento logout de otro contexto no se envia al repositorio`() = runTest {
        repository.state.value = SessionState.Authenticated(
            SESSION.copy(contextId = "new")
        )

        val vm = viewModel()
        vm.logout(SESSION)
        advanceUntilIdle()

        assertEquals(0, repository.logoutCalls)
    }

    @Test
    fun `respuesta de logout anterior no muestra aviso sobre una sesion nueva`() = runTest {
        repository.state.value = SessionState.Authenticated(SESSION)
        val replacement = SESSION.copy(contextId = "new")

        repository.onLogout = {
            repository.state.value =
                SessionState.Authenticated(replacement)
            AuthResult.Success(LogoutOutcome.REMOTE_CONFIRMED)
        }

        val vm = viewModel()
        runCurrent()

        vm.logout(SESSION)
        advanceUntilIdle()

        assertEquals(
            SessionState.Authenticated(replacement),
            vm.uiState.value.session
        )
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `limpiar ViewModel cancela restauracion sin fabricar un error`() = runTest {
        repository.onRestore = { awaitCancellation() }

        val vm = viewModel()
        runCurrent()

        store.clear()
        advanceUntilIdle()

        assertNull(vm.uiState.value.notice)
        assertNull(vm.uiState.value.operation)
        assertSame(SessionState.Restoring, vm.uiState.value.session)
    }

    private class FakeAuthRepository : AuthRepository, SessionReader {
        override val state =
            MutableStateFlow<SessionState>(SessionState.Restoring)

        var restoreCalls = 0
        var registerCalls = 0
        var loginCalls = 0
        var logoutCalls = 0

        var receivedEmail: String? = null
        var receivedPassword: String? = null
        var receivedConfirmation: String? = null

        var onRestore: suspend () -> AuthResult<Unit> = {
            state.value = SessionState.Guest
            AuthResult.Success(Unit)
        }

        var onLogin: suspend () -> AuthResult<SessionSnapshot> = {
            AuthResult.Failure(AuthFailure.INVALID_CREDENTIALS)
        }

        var onLogout: suspend () -> AuthResult<LogoutOutcome> = {
            state.value = SessionState.Guest
            AuthResult.Success(LogoutOutcome.REMOTE_CONFIRMED)
        }

        override suspend fun restoreSession(): AuthResult<Unit> {
            restoreCalls++
            return onRestore()
        }

        override suspend fun register(
            email: String,
            password: String,
            confirmation: String
        ): AuthResult<AuthUser> {
            registerCalls++
            receivedEmail = email
            receivedPassword = password
            receivedConfirmation = confirmation

            return AuthResult.Success(
                AuthUser("fixture-user", EMAIL, 1_000L)
            )
        }

        override suspend fun login(
            email: String,
            password: String
        ): AuthResult<SessionSnapshot> {
            loginCalls++
            return onLogin()
        }

        override suspend fun logout(
            expected: SessionSnapshot
        ): AuthResult<LogoutOutcome> {
            logoutCalls++
            return onLogout()
        }
    }

    companion object {
        private const val EMAIL = "fixture@example.com"
        private const val PASSWORD =
            "  Clave de prueba suficientemente larga  "

        private val SESSION = SessionSnapshot(
            "fixture-context",
            "fixture-user",
            EMAIL,
            10_000L,
            0
        )
    }
}