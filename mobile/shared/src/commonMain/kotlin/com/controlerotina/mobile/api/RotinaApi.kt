package com.controlerotina.mobile.api

import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/** Rotas da API que o app usa. As telas dependem desta interface, e os testes usam fakes. */
interface RotinaApi {
    suspend fun me(): MeResponse

    suspend fun plano(data: String): Plano?
    suspend fun gerarPlano(data: String): Plano
    suspend fun aprovarPlano(id: String): Plano

    suspend fun execucoes(data: String): ExecucoesDoDia
    suspend fun iniciarBloco(blocoId: String): Execucao
    suspend fun concluirExecucao(id: String): Execucao
    suspend fun abortarExecucao(id: String): Execucao

    suspend fun tarefas(pagina: Int, tamanho: Int, filtro: FiltroTarefas = FiltroTarefas()): PaginaTarefas
    suspend fun tarefa(id: String): Tarefa
    suspend fun criarTarefa(pedido: CriarTarefaRequest): Tarefa

    /** `PATCH` parcial: [campos] só com o que mudou; `JsonNull` limpa o campo (design D6b). */
    suspend fun atualizarTarefa(id: String, campos: JsonObject): Tarefa
    suspend fun iniciarTarefa(id: String, duracaoMinutos: Int, data: String): Execucao
    suspend fun concluirTarefaDireto(id: String, duracaoMinutos: Int, data: String): Execucao

    suspend fun habitosDoDia(data: String): List<HabitoDoDia>
    suspend fun iniciarHabito(id: String, data: String): Execucao
    suspend fun concluirHabitoDireto(id: String, data: String): Execucao

    suspend fun projetos(status: StatusProjeto): List<Projeto>
    suspend fun projeto(id: String): Projeto
    suspend fun criarProjeto(pedido: CriarProjetoRequest): Projeto
    suspend fun atualizarProjeto(id: String, campos: JsonObject): Projeto
    suspend fun arquivarProjeto(id: String): Projeto
    suspend fun reativarProjeto(id: String): Projeto

    suspend fun decidir(promovidos: Boolean): List<ItemDecidir>
    suspend fun criarDecidir(pedido: CriarDecidirRequest): ItemDecidir
    suspend fun atualizarDecidir(id: String, campos: JsonObject): ItemDecidir
    suspend fun excluirDecidir(id: String)
    suspend fun promoverDecidir(id: String, pedido: PromoverDecidirRequest): ItemDecidir
}

/** Listas curtas (projetos, Decidir) vêm numa página só, grande o bastante para o uso real. */
const val TAMANHO_LISTA_CURTA = 100

/** Status considerados "abertos" na lista de tarefas do app. */
val STATUS_ABERTOS = listOf(StatusTarefa.PENDENTE, StatusTarefa.EM_PROGRESSO, StatusTarefa.ATRASADA)

class KtorRotinaApi(private val http: ApiHttp) : RotinaApi {

    override suspend fun me() = http.requisitar(HttpMethod.Get, "/me", MeResponse.serializer())

    override suspend fun plano(data: String) =
        http.requisitarOuNulo(HttpMethod.Get, "/planos", Plano.serializer(), mapOf("data" to data))

    override suspend fun gerarPlano(data: String) =
        http.requisitar(HttpMethod.Post, "/planos/gerar", Plano.serializer(), mapOf("data" to data))

    override suspend fun aprovarPlano(id: String) =
        http.requisitar(HttpMethod.Post, "/planos/$id/aprovar", Plano.serializer())

    override suspend fun execucoes(data: String) =
        http.requisitar(HttpMethod.Get, "/execucoes", ExecucoesDoDia.serializer(), mapOf("data" to data))

    override suspend fun iniciarBloco(blocoId: String) = http.requisitar(
        HttpMethod.Post,
        "/execucoes/iniciar",
        Execucao.serializer(),
        corpo = jsonApi.encodeToString(IniciarExecucaoRequest.serializer(), IniciarExecucaoRequest(blocoId)),
    )

    override suspend fun concluirExecucao(id: String) =
        http.requisitar(HttpMethod.Post, "/execucoes/$id/concluir", Execucao.serializer())

    override suspend fun abortarExecucao(id: String) =
        http.requisitar(HttpMethod.Post, "/execucoes/$id/abortar", Execucao.serializer())

    override suspend fun tarefas(pagina: Int, tamanho: Int, filtro: FiltroTarefas) = http.requisitar(
        HttpMethod.Get,
        "/tarefas",
        PaginaTarefas.serializer(),
        mapOf(
            "page" to pagina,
            "size" to tamanho,
            "status" to filtro.status.map { it.name },
            "somenteRaizes" to filtro.somenteRaizes.takeIf { it },
            "parentId" to filtro.parentId,
            "projetoId" to filtro.projetoId,
            "prazoDe" to filtro.prazoDe,
            "prazoAte" to filtro.prazoAte,
        ),
    )

    override suspend fun atualizarTarefa(id: String, campos: JsonObject) = http.requisitar(
        HttpMethod.Patch,
        "/tarefas/$id",
        Tarefa.serializer(),
        corpo = jsonApi.encodeToString(JsonObject.serializer(), campos),
    )

    override suspend fun tarefa(id: String) = http.requisitar(HttpMethod.Get, "/tarefas/$id", Tarefa.serializer())

    override suspend fun criarTarefa(pedido: CriarTarefaRequest) = http.requisitar(
        HttpMethod.Post,
        "/tarefas",
        Tarefa.serializer(),
        corpo = jsonApi.encodeToString(CriarTarefaRequest.serializer(), pedido),
    )

    override suspend fun iniciarTarefa(id: String, duracaoMinutos: Int, data: String) = http.requisitar(
        HttpMethod.Post,
        "/tarefas/$id/iniciar",
        Execucao.serializer(),
        corpo = jsonApi.encodeToString(SessaoTarefaRequest.serializer(), SessaoTarefaRequest(duracaoMinutos, data)),
    )

    override suspend fun concluirTarefaDireto(id: String, duracaoMinutos: Int, data: String) = http.requisitar(
        HttpMethod.Post,
        "/tarefas/$id/concluir-direto",
        Execucao.serializer(),
        corpo = jsonApi.encodeToString(SessaoTarefaRequest.serializer(), SessaoTarefaRequest(duracaoMinutos, data)),
    )

    override suspend fun habitosDoDia(data: String) = http.requisitar(
        HttpMethod.Get,
        "/habitos/do-dia",
        ListSerializer(HabitoDoDia.serializer()),
        mapOf("data" to data),
    )

    override suspend fun iniciarHabito(id: String, data: String) =
        http.requisitar(HttpMethod.Post, "/habitos/$id/iniciar", Execucao.serializer(), mapOf("data" to data))

    override suspend fun concluirHabitoDireto(id: String, data: String) =
        http.requisitar(HttpMethod.Post, "/habitos/$id/concluir-direto", Execucao.serializer(), mapOf("data" to data))

    // ---- Projetos ----

    override suspend fun projetos(status: StatusProjeto) = http.requisitar(
        HttpMethod.Get,
        "/projetos",
        Pagina.serializer(Projeto.serializer()),
        mapOf("status" to status.name, "page" to 0, "size" to TAMANHO_LISTA_CURTA),
    ).content

    override suspend fun projeto(id: String) = http.requisitar(HttpMethod.Get, "/projetos/$id", Projeto.serializer())

    override suspend fun criarProjeto(pedido: CriarProjetoRequest) = http.requisitar(
        HttpMethod.Post,
        "/projetos",
        Projeto.serializer(),
        corpo = jsonApi.encodeToString(CriarProjetoRequest.serializer(), pedido),
    )

    override suspend fun atualizarProjeto(id: String, campos: JsonObject) = http.requisitar(
        HttpMethod.Patch,
        "/projetos/$id",
        Projeto.serializer(),
        corpo = jsonApi.encodeToString(JsonObject.serializer(), campos),
    )

    override suspend fun arquivarProjeto(id: String) =
        http.requisitar(HttpMethod.Post, "/projetos/$id/arquivar", Projeto.serializer())

    override suspend fun reativarProjeto(id: String) =
        http.requisitar(HttpMethod.Post, "/projetos/$id/reativar", Projeto.serializer())

    // ---- Decidir ----

    override suspend fun decidir(promovidos: Boolean) = http.requisitar(
        HttpMethod.Get,
        "/decidir",
        Pagina.serializer(ItemDecidir.serializer()),
        mapOf("promovidos" to promovidos, "page" to 0, "size" to TAMANHO_LISTA_CURTA),
    ).content

    override suspend fun criarDecidir(pedido: CriarDecidirRequest) = http.requisitar(
        HttpMethod.Post,
        "/decidir",
        ItemDecidir.serializer(),
        corpo = jsonApi.encodeToString(CriarDecidirRequest.serializer(), pedido),
    )

    override suspend fun atualizarDecidir(id: String, campos: JsonObject) = http.requisitar(
        HttpMethod.Patch,
        "/decidir/$id",
        ItemDecidir.serializer(),
        corpo = jsonApi.encodeToString(JsonObject.serializer(), campos),
    )

    override suspend fun excluirDecidir(id: String) = http.enviar(HttpMethod.Delete, "/decidir/$id")

    override suspend fun promoverDecidir(id: String, pedido: PromoverDecidirRequest) = http.requisitar(
        HttpMethod.Post,
        "/decidir/$id/promover",
        ItemDecidir.serializer(),
        corpo = jsonApi.encodeToString(PromoverDecidirRequest.serializer(), pedido),
    )
}
