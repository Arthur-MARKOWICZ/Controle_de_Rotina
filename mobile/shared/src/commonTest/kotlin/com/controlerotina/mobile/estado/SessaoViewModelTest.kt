package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SessaoViewModelTest {
    private val auth = FakeAutenticador()

    @Test
    fun semSessaoGuardadaVaiParaLogin() = runTest {
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.iniciar()
        assertEquals(EstadoApp.Login(), vm.estado.value)
    }

    @Test
    fun sessaoGuardadaValidaAbreAutenticado() = runTest {
        auth.guardada = true
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.iniciar()
        assertEquals(EstadoApp.Autenticado, vm.estado.value)
    }

    @Test
    fun semRedeNoBootPermiteTentarDeNovo() = runTest {
        auth.guardada = true
        auth.restaura = { throw ApiErro.Rede(RuntimeException()) }
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.iniciar()
        assertEquals(EstadoApp.SemConexaoNoBoot, vm.estado.value)
    }

    @Test
    fun credenciaisInvalidasMostramMensagemGenericaEApagamSenha() = runTest {
        auth.loginFalha = ApiErro.Http(401, null)
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.alterarEmail("x@y.z")
        vm.alterarSenha("errada")
        vm.entrar()
        assertEquals(MSG_CREDENCIAIS, vm.form.value.erro)
        assertEquals("", vm.form.value.senha)
        assertEquals("x@y.z", vm.form.value.email)
    }

    @Test
    fun loginSemRedeMostraFalhaDeConexao() = runTest {
        auth.loginFalha = ApiErro.Rede(RuntimeException())
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.alterarEmail("x@y.z")
        vm.alterarSenha("s")
        vm.entrar()
        assertEquals(MSG_SEM_CONEXAO, vm.form.value.erro)
    }

    @Test
    fun loginValidoAutentica() = runTest {
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.alterarEmail("x@y.z")
        vm.alterarSenha("certa")
        vm.entrar()
        assertEquals(EstadoApp.Autenticado, vm.estado.value)
    }

    @Test
    fun sessaoExpiradaDuranteUsoVoltaAoLoginComAviso() = runTest {
        auth.guardada = true
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.iniciar()
        runCurrent()
        auth.sessaoExpirada.emit(Unit)
        runCurrent()
        assertEquals(EstadoApp.Login(MSG_SESSAO_EXPIRADA), vm.estado.value)
    }

    @Test
    fun sairChamaLogoutEVoltaAoLogin() = runTest {
        auth.guardada = true
        val vm = SessaoViewModel(auth, backgroundScope)
        vm.iniciar()
        vm.sair()
        assertEquals(1, auth.logouts)
        assertEquals(EstadoApp.Login(), vm.estado.value)
    }

    @Test
    fun sairEExpirarApagamDadosDoUsuario() = runTest {
        auth.guardada = true
        var limpezas = 0
        val vm = SessaoViewModel(auth, backgroundScope) { limpezas++ }
        vm.iniciar()
        runCurrent()
        assertEquals(0, limpezas, "boot válido não apaga nada")

        auth.sessaoExpirada.emit(Unit)
        runCurrent()
        assertEquals(1, limpezas)

        vm.sair()
        assertEquals(2, limpezas)
    }

    @Test
    fun sairSemRedeAindaVoltaAoLoginEApagaDados() = runTest {
        auth.guardada = true
        auth.logoutFalha = ApiErro.Rede(RuntimeException())
        var limpezas = 0
        val vm = SessaoViewModel(auth, backgroundScope) { limpezas++ }
        vm.iniciar()
        vm.sair()
        assertEquals(1, auth.logouts)
        assertEquals(1, limpezas)
        assertEquals(EstadoApp.Login(), vm.estado.value)
    }

    @Test
    fun sessaoGuardadaInvalidaNoBootApagaDados() = runTest {
        auth.guardada = true
        auth.restaura = { false }
        var limpezas = 0
        val vm = SessaoViewModel(auth, backgroundScope) { limpezas++ }
        vm.iniciar()
        assertEquals(1, limpezas)
        assertEquals(EstadoApp.Login(MSG_SESSAO_EXPIRADA), vm.estado.value)
    }
}
