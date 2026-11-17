package com.controlerotina.mobile.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Valida os modelos de `Modelos.kt` contra `contract/openapi.json` (ver design D8). */
class ContratoModelosTest {

    private val contrato by lazy { VerificadorContrato.doArquivo(System.getProperty("contrato.arquivo")) }

    /** Modelo Kotlin → schema do contrato. Ao criar um modelo que espelha a API, adicione o par aqui. */
    private val respostas: List<Pair<SerialDescriptor, String>> = listOf(
        AccessTokenResponse.serializer().descriptor to "AccessTokenResponse",
        MeResponse.serializer().descriptor to "UsuarioResponse",
        TarefaResumo.serializer().descriptor to "TarefaResumoResponse",
        HabitoResumo.serializer().descriptor to "HabitoResumoResponse",
        BlocoPlano.serializer().descriptor to "BlocoPlanoResponse",
        ItemNaoAlocado.serializer().descriptor to "ItemNaoAlocadoResponse",
        Plano.serializer().descriptor to "PlanoResponse",
        XpProgresso.serializer().descriptor to "XpProgressoResponse",
        XpConclusao.serializer().descriptor to "XpConclusaoResponse",
        Execucao.serializer().descriptor to "ExecucaoResponse",
        BlocoExecucao.serializer().descriptor to "BlocoExecucaoResponse",
        ItemExecucao.serializer().descriptor to "ItemExecucaoResponse",
        ExecucoesDoDia.serializer().descriptor to "ExecucoesDoDiaResponse",
        Tarefa.serializer().descriptor to "TarefaResponse",
        MetadadosPagina.serializer().descriptor to "PageMetadata",
        PaginaTarefas.serializer().descriptor to "PagedModelTarefaResponse",
        Pagina.serializer(Projeto.serializer()).descriptor to "PagedModelProjetoResponse",
        Pagina.serializer(ItemDecidir.serializer()).descriptor to "PagedModelDecidirResponse",
        SlotExecucaoHabito.serializer().descriptor to "SlotExecucaoHabitoResponse",
        HabitoDoDia.serializer().descriptor to "HabitoDoDiaResponse",
        Projeto.serializer().descriptor to "ProjetoResponse",
        ItemDecidir.serializer().descriptor to "DecidirResponse",
    )

    private val requisicoes: List<Pair<SerialDescriptor, String>> = listOf(
        LoginRequest.serializer().descriptor to "LoginRequest",
        IniciarExecucaoRequest.serializer().descriptor to "IniciarExecucaoRequest",
        CriarTarefaRequest.serializer().descriptor to "CriarTarefaRequest",
        SessaoTarefaRequest.serializer().descriptor to "SessaoTarefaRequest",
        CriarProjetoRequest.serializer().descriptor to "CriarProjetoRequest",
        CriarDecidirRequest.serializer().descriptor to "CriarDecidirRequest",
        PromoverDecidirRequest.serializer().descriptor to "PromoverDecidirRequest",
    )

    private val enums: List<Triple<SerialDescriptor, String, String>> = listOf(
        Triple(StatusPlano.serializer().descriptor, "PlanoResponse", "status"),
        Triple(OrigemBloco.serializer().descriptor, "BlocoPlanoResponse", "origem"),
        Triple(StatusTarefa.serializer().descriptor, "TarefaResponse", "status"),
        Triple(Prioridade.serializer().descriptor, "TarefaResponse", "prioridade"),
        Triple(StatusExecucao.serializer().descriptor, "ExecucaoResponse", "status"),
        Triple(StatusProjeto.serializer().descriptor, "ProjetoResponse", "status"),
    )

    @Test
    fun modelosAtuaisDevemSerCompativeisComOContrato() {
        val problemas = respostas.flatMap { (d, s) -> contrato.verificarModelo(d, s, resposta = true) } +
            requisicoes.flatMap { (d, s) -> contrato.verificarModelo(d, s, resposta = false) } +
            enums.flatMap { (d, s, p) -> contrato.verificarEnum(d, s, p) }

        assertEquals(emptyList(), problemas, problemas.joinToString("\n"))
    }

    // ---- cenários do verificador com contratos sintéticos ----

    @Serializable
    private data class TarefaFake(val id: String, val parentTitulo: String? = null)

    @Serializable
    private data class TarefaPrazoObrigatorio(val id: String, val prazo: String)

    @Serializable
    private enum class StatusFake { PENDENTE, FINALIZADA }

    private fun sintetico(schemas: String) = VerificadorContrato.deTexto("""{"components":{"schemas":$schemas}}""")

    @Test
    fun deveFalharNomeandoCampoQueNaoExisteMaisNoBackend() {
        val c = sintetico("""{"TarefaResponse":{"properties":{"id":{"type":"string"}},"required":["id"]}}""")

        val problemas = c.verificarModelo(serializer<TarefaFake>().descriptor, "TarefaResponse", resposta = true)

        assertEquals(1, problemas.size)
        assertContains(problemas.single(), "TarefaFake.parentTitulo")
    }

    @Test
    fun deveFalharQuandoCampoNaoAnulavelNoModeloEAnulavelNoContrato() {
        val c = sintetico(
            """{"TarefaResponse":{"properties":{"id":{"type":"string"},"prazo":{"type":["null","string"]}},
               "required":["id","prazo"]}}""",
        )

        val problemas = c.verificarModelo(serializer<TarefaPrazoObrigatorio>().descriptor, "TarefaResponse", true)

        assertEquals(listOf("TarefaPrazoObrigatorio.prazo: é não anulável no modelo mas anulável em TarefaResponse"), problemas)
    }

    @Test
    fun deveFalharQuandoCampoObrigatorioNoModeloEOpcionalNoContrato() {
        val c = sintetico("""{"T":{"properties":{"id":{"type":"string"},"prazo":{"type":"string"}},"required":["id"]}}""")

        val problemas = c.verificarModelo(serializer<TarefaPrazoObrigatorio>().descriptor, "T", true)

        assertContains(problemas.single(), "opcional em T")
    }

    @Test
    fun naoDeveExigirObrigatoriedadeEmModeloDeRequisicao() {
        val c = sintetico("""{"T":{"properties":{"id":{"type":"string"},"prazo":{"type":"string"}}}}""")

        assertEquals(emptyList(), c.verificarModelo(serializer<TarefaPrazoObrigatorio>().descriptor, "T", false))
    }

    @Test
    fun deveFalharNomeandoEnumEValorAusente() {
        val c = sintetico(
            """{"TarefaResponse":{"properties":{"status":{"type":"string","enum":["FINALIZADA","PENDENTE","ARQUIVADA"]}}}}""",
        )

        val problemas = c.verificarEnum(serializer<StatusFake>().descriptor, "TarefaResponse", "status")

        assertEquals(listOf("StatusFake: falta o valor ARQUIVADA (existe em TarefaResponse.status)"), problemas)
    }

    @Test
    fun deveFalharQuandoEnumDoModeloTemValorQueOContratoNaoTem() {
        val c = sintetico("""{"T":{"properties":{"status":{"type":"string","enum":["PENDENTE"]}}}}""")

        val problemas = c.verificarEnum(serializer<StatusFake>().descriptor, "T", "status")

        assertEquals(listOf("StatusFake: valor FINALIZADA não existe em T.status"), problemas)
    }

    @Test
    fun deveFalharComMensagemClaraQuandoSchemaNaoExiste() {
        val c = sintetico("""{}""")

        val problemas = c.verificarModelo(serializer<TarefaFake>().descriptor, "Sumiu", true)

        assertEquals(listOf("TarefaFake: schema 'Sumiu' não existe no contrato"), problemas)
    }

    @Test
    fun deveFalharComComandoDeRegeneracaoQuandoArquivoNaoExiste() {
        val erro = assertFailsWith<IllegalArgumentException> { VerificadorContrato.doArquivo("/nao/existe.json") }

        assertContains(erro.message.orEmpty(), "ContratoOpenApiIT")
    }

    @Test
    fun deveFalharQuandoPropriedadeDoArquivoNaoFoiConfigurada() {
        assertFailsWith<IllegalArgumentException> { VerificadorContrato.doArquivo(null) }
    }

    @Test
    fun deveFalharQuandoContratoNaoTemSchemas() {
        assertFailsWith<IllegalArgumentException> { VerificadorContrato.deTexto("""{"openapi":"3.1.0"}""") }
    }

    @Test
    fun deveAceitarNuloDescritoPorOneOf() {
        val c = sintetico(
            """{"T":{"properties":{"id":{"type":"string"},"prazo":{"oneOf":[{"type":"string"},{"type":"null"}]}},
               "required":["id","prazo"]}}""",
        )

        assertTrue(c.verificarModelo(serializer<TarefaPrazoObrigatorio>().descriptor, "T", true).isNotEmpty())
        assertEquals(emptyList(), c.verificarModelo(String.serializer().descriptor, "T", false))
    }
}
