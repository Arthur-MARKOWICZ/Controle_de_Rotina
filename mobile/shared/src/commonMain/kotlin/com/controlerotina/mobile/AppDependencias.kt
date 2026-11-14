package com.controlerotina.mobile

import com.controlerotina.mobile.api.ApiHttp
import com.controlerotina.mobile.api.KtorRotinaApi
import com.controlerotina.mobile.api.RotinaApi
import com.controlerotina.mobile.dominio.hojeIso
import com.controlerotina.mobile.estado.DecidirDetalheViewModel
import com.controlerotina.mobile.estado.DecidirViewModel
import com.controlerotina.mobile.estado.EditarTarefaViewModel
import com.controlerotina.mobile.estado.HabitosViewModel
import com.controlerotina.mobile.estado.Navegacao
import com.controlerotina.mobile.estado.OpcoesProjeto
import com.controlerotina.mobile.estado.ProjetoDetalheViewModel
import com.controlerotina.mobile.estado.ProjetosViewModel
import com.controlerotina.mobile.estado.HojeViewModel
import com.controlerotina.mobile.estado.SessaoAtivaStore
import com.controlerotina.mobile.estado.SessaoViewModel
import com.controlerotina.mobile.estado.TarefasViewModel
import com.controlerotina.mobile.lembrete.AgendadorLembretes
import com.controlerotina.mobile.lembrete.ExecutorLembrete
import com.controlerotina.mobile.lembrete.LembretesViewModel
import com.controlerotina.mobile.lembrete.Notificador
import com.controlerotina.mobile.lembrete.PermissaoNotificacao
import com.controlerotina.mobile.lembrete.PreferenciasLembretes
import com.controlerotina.mobile.lembrete.SemNotificacao
import com.controlerotina.mobile.sessao.PreferenciasLocais
import com.controlerotina.mobile.sessao.SecureTokenStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * DI manual (design D5): um grafo criado no Application e vivo enquanto o processo vive,
 * então os view-models sobrevivem a rotação de tela sem biblioteca de lifecycle.
 */
class AppDependencias(
    engine: HttpClientEngine,
    baseUrl: String,
    tokenStore: SecureTokenStore,
    preferencias: PreferenciasLocais,
    private val notificador: Notificador = SemNotificacao,
    private val agendador: AgendadorLembretes = SemNotificacao,
    permissao: PermissaoNotificacao = SemNotificacao,
    /** Relógio e fuso dos lembretes; os testes passam valores fixos. */
    clock: Clock = Clock.System,
    fuso: () -> TimeZone = { TimeZone.currentSystemDefault() },
    val escopo: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val http = ApiHttp(engine, baseUrl, tokenStore)
    val api: RotinaApi = KtorRotinaApi(http)
    private val hoje: () -> String = { hojeIso() }

    val sessaoAtiva = SessaoAtivaStore(api, preferencias, hoje)
    val hojeVm = HojeViewModel(api, sessaoAtiva, hoje, escopo)
    val tarefasVm = TarefasViewModel(api, sessaoAtiva, hoje, escopo)
    val habitosVm = HabitosViewModel(api, sessaoAtiva, hoje, escopo)

    val navegacao = Navegacao()
    val opcoesProjeto = OpcoesProjeto(api)
    val projetosVm = ProjetosViewModel(api, sessaoAtiva, escopo, aoMudarProjetos = { opcoesProjeto.carregar() })
    val projetoDetalheVm = ProjetoDetalheViewModel(api, sessaoAtiva, escopo) { projeto ->
        projetosVm.aplicar(projeto)
        opcoesProjeto.carregar()
    }
    val editarTarefaVm = EditarTarefaViewModel(
        api,
        sessaoAtiva,
        escopo,
        aoSalvar = { tarefa ->
            tarefasVm.substituir(tarefa)
            projetoDetalheVm.aplicarTarefa(tarefa)
        },
        aoMudarFilhos = { paiId -> tarefasVm.atualizarPai(paiId) },
    )
    val decidirVm = DecidirViewModel(api, sessaoAtiva, escopo)
    val decidirDetalheVm = DecidirDetalheViewModel(
        api,
        sessaoAtiva,
        escopo,
        aoMudar = { item, removido -> decidirVm.aplicar(item, removido) },
        aoPromover = { tarefasVm.recarregar() },
    )

    private val preferenciasLembretes = PreferenciasLembretes(preferencias)
    val lembretesVm = LembretesViewModel(preferenciasLembretes, agendador, permissao, clock, fuso)

    /** Usado pelo worker de lembrete no mesmo processo: mesmo `ApiHttp`, mesmo Mutex de refresh (design L2). */
    val executorLembrete = ExecutorLembrete(api, preferenciasLembretes, notificador, agendador, clock, fuso)

    // Logout ou sessão expirada: nada do usuário anterior pode aparecer para o próximo login.
    val sessao = SessaoViewModel(
        http,
        escopo,
        aoAutenticar = { lembretesVm.reagendarLigados() },
        aoEncerrar = ::encerrarSessaoLocal,
    )

    private suspend fun encerrarSessaoLocal() {
        agendador.cancelarTodos()
        notificador.limparTodas()
        sessaoAtiva.limpar()
        hojeVm.limpar()
        tarefasVm.limpar()
        habitosVm.limpar()
        projetosVm.limpar()
        decidirVm.limpar()
        opcoesProjeto.limpar()
        navegacao.limpar()
    }

    private val _retornos = MutableStateFlow(0)

    /** Incrementa quando o app volta ao primeiro plano; as telas recarregam. */
    val retornosAoPrimeiroPlano: StateFlow<Int> = _retornos.asStateFlow()

    fun aoVoltarAoPrimeiroPlano() {
        _retornos.value += 1
    }
}
