package com.controlerotina.mobile.estado

import com.controlerotina.mobile.api.ApiErro
import com.controlerotina.mobile.arquivo.SalvadorArquivo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSalvador(var salva: Boolean = true, var falha: Throwable? = null) : SalvadorArquivo {
    val salvos = mutableListOf<Pair<String, String>>()
    var bloqueio: CompletableDeferred<Unit>? = null

    override suspend fun salvar(nomeSugerido: String, mime: String, conteudo: ByteArray): Boolean {
        bloqueio?.await()
        falha?.let { throw it }
        if (salva) salvos += nomeSugerido to conteudo.decodeToString()
        return salva
    }
}

class PerfilViewModelTest {
    private val api = FakeRotinaApi()
    private val salvador = FakeSalvador()
    private var exclusoes = 0

    private fun TestScope.vm() = PerfilViewModel(api, salvador, { HOJE }, backgroundScope) { exclusoes++ }

    private fun PerfilViewModel.prepararExclusao(senha: String = "Senha@123", confirmacao: String = "EXCLUIR") {
        abrirExclusao()
        alterarSenha(senha)
        alterarConfirmacao(confirmacao)
    }

    @Test
    fun exportarSalvaArquivoDatadoComOConteudoDaApi() = runTest {
        val vm = vm()

        vm.exportar()
        runCurrent()

        assertEquals(listOf("controle-rotina-export-$HOJE.json" to """{"versao":1}"""), salvador.salvos)
        assertEquals(MSG_EXPORT_SALVO, vm.estado.value.avisoExport)
        assertFalse(vm.estado.value.exportando)
    }

    @Test
    fun cliqueDuploNoExportFazUmaSoRequisicao() = runTest {
        salvador.bloqueio = CompletableDeferred()
        val vm = vm()

        vm.exportar()
        runCurrent()
        vm.exportar()
        runCurrent()

        assertEquals(1, api.chamadas.count { it == "exportar" })
        assertTrue(vm.estado.value.exportando)
        salvador.bloqueio!!.complete(Unit)
    }

    @Test
    fun usuarioCancelaOSeletorSemAvisoNemErro() = runTest {
        salvador.salva = false
        val vm = vm()

        vm.exportar()
        runCurrent()

        assertNull(vm.estado.value.avisoExport)
        assertNull(vm.estado.value.erroExport)
    }

    @Test
    fun falhaDaApiNoExportMostraErroEVoltaOBotao() = runTest {
        api.falhaEm = mapOf("exportar" to ApiErro.Http(500, null))
        val vm = vm()

        vm.exportar()
        runCurrent()

        assertEquals(MSG_EXPORT_FALHOU, vm.estado.value.erroExport)
        assertFalse(vm.estado.value.exportando)
        assertTrue(salvador.salvos.isEmpty())
    }

    @Test
    fun falhaAoGravarOArquivoMostraErro() = runTest {
        salvador.falha = RuntimeException("disco cheio")
        val vm = vm()

        vm.exportar()
        runCurrent()

        assertEquals(MSG_EXPORT_FALHOU, vm.estado.value.erroExport)
    }

    @Test
    fun exclusaoSoLiberaComSenhaEPalavraExata() = runTest {
        val vm = vm()

        vm.prepararExclusao(confirmacao = "excluir")
        assertFalse(vm.estado.value.exclusaoLiberada)
        vm.confirmarExclusao()
        runCurrent()
        assertTrue(api.chamadas.none { it.startsWith("excluirConta") })

        vm.prepararExclusao(senha = "")
        assertFalse(vm.estado.value.exclusaoLiberada)

        vm.prepararExclusao()
        assertTrue(vm.estado.value.exclusaoLiberada)
    }

    @Test
    fun cancelarFechaSemChamarAApi() = runTest {
        val vm = vm()
        vm.prepararExclusao()

        vm.fecharExclusao()

        assertFalse(vm.estado.value.dialogoExclusaoAberto)
        assertEquals("", vm.estado.value.senha)
        assertTrue(api.chamadas.none { it.startsWith("excluirConta") })
        assertEquals(0, exclusoes)
    }

    @Test
    fun exclusaoBemSucedidaChamaAApiEEncerraASessao() = runTest {
        val vm = vm()
        vm.prepararExclusao()

        vm.confirmarExclusao()
        runCurrent()

        assertTrue("excluirConta:Senha@123" in api.chamadas)
        assertEquals(1, exclusoes)
        assertEquals(EstadoPerfil(), vm.estado.value)
    }

    @Test
    fun senhaErradaMantemDialogoComErroNoCampoESemEncerrar() = runTest {
        api.falhaEm = mapOf("excluirConta" to ApiErro.Http(401, null))
        val vm = vm()
        vm.prepararExclusao(senha = "errada")

        vm.confirmarExclusao()
        runCurrent()

        assertEquals(MSG_SENHA_INCORRETA, vm.estado.value.erroSenha)
        assertTrue(vm.estado.value.dialogoExclusaoAberto)
        assertEquals("", vm.estado.value.senha)
        assertEquals(0, exclusoes)
    }

    @Test
    fun erroDeServidorMantemSessaoEPermiteTentarDeNovo() = runTest {
        api.falhaEm = mapOf("excluirConta" to ApiErro.Http(500, null))
        val vm = vm()
        vm.prepararExclusao()

        vm.confirmarExclusao()
        runCurrent()

        assertEquals(MSG_EXCLUSAO_FALHOU, vm.estado.value.erroExclusao)
        assertTrue(vm.estado.value.exclusaoLiberada)
        assertEquals(0, exclusoes)
    }

    @Test
    fun semRedeNaExclusaoMostraErroSemEncerrar() = runTest {
        api.falhaEm = mapOf("excluirConta" to ApiErro.Rede(RuntimeException()))
        val vm = vm()
        vm.prepararExclusao()

        vm.confirmarExclusao()
        runCurrent()

        assertEquals(MSG_EXCLUSAO_FALHOU, vm.estado.value.erroExclusao)
        assertEquals(0, exclusoes)
    }

    @Test
    fun contaJaExcluidaNoMeioDoFluxoNaoMostraErro() = runTest {
        api.falhaEm = mapOf("excluirConta" to ApiErro.ContaExcluida())
        val vm = vm()
        vm.prepararExclusao()

        vm.confirmarExclusao()
        runCurrent()

        assertEquals(EstadoPerfil(), vm.estado.value)
    }
}
