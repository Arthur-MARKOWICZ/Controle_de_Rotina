# Design

## Context

Motivação em proposal.md. O estado atual que importa aqui:

- O app (`app-mobile-android`) não guarda dados de domínio em disco. Plano e tarefas vivem nos view-models do grafo `AppDependencias`, criado de forma preguiçosa em `RotinaApplication.deps`. O DataStore guarda só o refresh cifrado (`KeystoreTokenStore`) e preferências não sensíveis (`DataStorePreferencias`).
- `ApiHttp` mantém o access em memória e renova pelo refresh com um `Mutex`. O backend **rotaciona** o refresh e, ao ver um refresh já revogado, **revoga a família inteira** (`RefreshTokenService.rotacionar`). Duas instâncias de `ApiHttp` renovando ao mesmo tempo deslogam o usuário.
- `GET /tarefas` já aceita `prazoDe`/`prazoAte` (`TarefaFiltro`), mas o `FiltroTarefas` do mobile não os expõe. `PaginaTarefas` não lê `totalElements`.
- O plano do dia normalmente não existe de manhã: é gerado sob demanda.
- `minSdk 26`, `targetSdk 37`. O manifest não declara `POST_NOTIFICATIONS`.

## Goals / Non-Goals

**Goals:**
- Toda a regra (quando disparar, se dispara, o que dizer) em `shared/commonMain`, testada na JVM com `Clock` e fuso fixos.
- A casca Android (worker, notificação, permissão) fica fina o bastante para não precisar de teste instrumentado além de um smoke manual.
- Zero chance de o lembrete causar logout por corrida de refresh.

**Non-Goals:**
- Horário exato garantido (alarme exato), lembrete de hábito ou por bloco, e cache offline.
- iOS: a interface do agendador fica em `commonMain`, mas só existe implementação Android.

## Decisions

### L1. Consultar a API no disparo, sem snapshot
No horário, o worker busca plano, execuções e tarefas na API. Um snapshot salvo na última abertura nunca teria o plano de hoje (gerado sob demanda) e deixaria o D-1 desatualizado quando o prazo muda na web. O custo é depender de rede e de sessão, e por isso existe a falha silenciosa (spec "Falha silenciosa sem dados confiáveis").
*Alternativa:* snapshot no DataStore. Foi descartada pelo motivo acima e pode voltar como fallback do D-1 se a falta de rede virar problema real.

### L2. Reusar o grafo do processo, nunca um `ApiHttp` novo
O worker roda no mesmo processo e pega `(applicationContext as RotinaApplication).deps.api`. Assim o `Mutex` de renovação é o mesmo da UI, e um app aberto mais um worker disparando renovam uma vez só. Com o processo frio, `deps` nasce no worker e o access vem de `restaurar()`/renovação no primeiro 401, que é o caminho normal.
*Alternativa:* um `ApiHttp` só para background. Foi descartada porque é exatamente o cenário de reuso de refresh que revoga a família.
Efeito colateral aceito: com o processo frio, instanciar `deps` cria os view-models, o que é barato (nenhuma chamada de rede é feita no construtor).

### L3. `OneTimeWorkRequest` reencadeado, não `PeriodicWorkRequest`
Cada tipo tem um trabalho único (`lembrete-resumo`, `lembrete-prazo`, `ExistingWorkPolicy.REPLACE`) com `initialDelay = proximoDisparo(agora, horario, fuso) - agora` e a restrição `NetworkType.CONNECTED`. Ao terminar, o worker agenda o próximo. Isso recalcula o horário a cada dia (horário de verão, troca de fuso, mudança de horário pelo usuário) sem a deriva do periódico de 24h. O WorkManager persiste os trabalhos após reboot.
*Alternativas:* `PeriodicWorkRequest(24h)` deriva e não acompanha a mudança de fuso. `AlarmManager.setExact` exige `SCHEDULE_EXACT_ALARM` no Android 14+, e a precisão de minuto não compensa para um resumo diário.

### L4. Orquestração pura em `shared`
```
 Worker (androidApp)                    shared/commonMain
 +-------------------+     +----------------------------------------+
 | doWork()          |---->| ExecutorLembrete.executar(tipo)        |
 |  deps.executor    |     |   1. prefs: ligado? senão -> Pular     |
 |  agenda o proximo |     |   2. ja disparou hoje? -> Pular        |
 +-------------------+     |   3. agora > horario+2h? -> Pular      |
          |                |   4. api (plano/execucoes|tarefas)     |
          v                |      ApiErro.* -> Pular                |
 +-------------------+     |   5. montarResumo / montarAvisoPrazo   |
 | NotificadorAndroid|<----|      null -> Pular                     |
 | (canal, PRIVATE)  |     |   6. notificador.exibir(...)           |
 +-------------------+     |      marca "disparou hoje"             |
                           +----------------------------------------+
```
- `AgendaLembretes`: funções puras `proximoDisparo(agora, horario, fuso)` e `dentroDaTolerancia(agora, horario, fuso, 2h)`.
- `ConteudoLembrete`: `montarResumo(plano, execucoes)` e `montarAvisoPrazo(tarefas)` devolvem `Notificacao?` (título, texto, destino). `null` significa não notificar.
- `ExecutorLembrete(api, preferencias, notificador, clock, fuso)` devolve um `Resultado` (`Exibida`, `Pulada(motivo)`) para os testes afirmarem o motivo.
- `interface Notificador { exibir(n); limparTodas(); permitido(): Boolean }` e `interface AgendadorLembretes { agendar(tipo, em); cancelar(tipo); cancelarTodos() }`, com fakes em `commonTest`.
- Marca "disparou hoje": `PreferenciasLocais` com a chave `lembrete.<tipo>.ultimoDia` = data ISO local. Isso evita duplicar em retry ou em duas execuções no mesmo dia.

### L5. Preferências e tela
`LembretesViewModel(preferencias, agendador, permissao, clock, fuso)` lê e grava `lembrete.<tipo>.ligado` e `lembrete.<tipo>.horario` (`HH:mm`) e reagenda ou cancela a cada mudança. A permissão entra no VM pela interface `PermissaoNotificacao` (`concedida`, `solicitar`, `abrirConfiguracoes`): `ligar(tipo)` pede a permissão quando ainda não há, e a recusa deixa o lembrete desligado e o estado `bloqueado = true`. O VM guarda a recusa: o diálogo do sistema pausa a Activity, e o `carregar()` disparado no `onResume` não pode apagar o aviso. Antes do diálogo, a tela mostra uma explicação (`precisaPedirPermissao()`). A tela é `LembretesTela` em `commonMain`, aberta a partir do Perfil via `Navegacao.abrir(Destino.Lembretes)`. No Android, `PermissaoAndroid` é criada no `Application`, e a `MainActivity` preenche o campo `solicitante` com o launcher de `RequestPermission` enquanto existe. As decisões da casca (permissão por versão do Android, atraso do agendamento, extra da notificação → aba) ficam em `lembrete/RegrasPlataforma.kt`, testadas na JVM; o `androidApp` só lê o estado do sistema.

### L6. Notificação Android
Um canal só, `lembretes` ("Lembretes do plano"), com importância padrão. `setVisibility(VISIBILITY_PRIVATE)` e `setPublicVersion` com o texto genérico. IDs fixos por tipo (resumo 1001, prazo 1002), então reexibir substitui em vez de empilhar. O `PendingIntent` abre a `MainActivity` com o extra `destino=PLANO|TAREFAS`. A `MainActivity` lê o extra em `onCreate`/`onNewIntent` e chama `deps.navegacao.selecionar(...)`. Se não houver sessão, o fluxo normal já mostra o login.

### L7. Filtro de prazo na API
`FiltroTarefas` ganha `prazoDe`/`prazoAte` (ISO-8601 UTC) e `status` opcional (padrão `STATUS_ABERTOS`). O D-1 usa `status = [PENDENTE, EM_PROGRESSO]`, `prazoDe = início de amanhã`, `prazoAte = início de depois de amanhã - 1ms` no fuso do aparelho, com `size = 50` e só a primeira página (o texto mostra até 3 títulos). `PaginaTarefas` passa a ler `totalElements` para a contagem. A ordenação por prazo é feita no cliente sobre a página.
*Ponto de atenção:* conferir na implementação se `prazoAte` é inclusivo no backend e ajustar o limite (spec "Prazo à meia-noite").

### L8. Logout e login
O callback de limpeza do `SessaoViewModel` (já usado para limpar os VMs) passa a chamar `agendador.cancelarTodos()` e `notificador.limparTodas()`. Depois de `login`/`restaurar` bem-sucedidos, `LembretesViewModel.reagendarLigados()`. As preferências não são apagadas: não são sensíveis, e apagá-las a cada sessão expirada obrigaria o usuário a reconfigurar.

## Risks / Trade-offs

- [Notificar demais leva à desinstalação] → desligado por padrão, no máximo uma por tipo por dia, sem notificação vazia ou de erro.
- [Doze/App Standby atrasa o worker] → a janela de 2h corta resumos irrelevantes. Aceito como limite da fatia local, e FCM (fatia 3) resolve.
- [Process death no meio da renovação, disparada pelo worker] → mesma mitigação do D3 do app: gravar o refresh novo antes de liberar o `Mutex`.
- [Mesmo aparelho, outro usuário herda as preferências] → só horário e liga/desliga, sem dado do usuário anterior. Os títulos vêm da API com a sessão nova.
- [`deps` criado no worker dispara coleta de `sessaoExpirada` sem UI] → o callback de limpeza só zera estado e cancela lembretes, sem depender de Activity.
- [O worker reagenda com `REPLACE` no próprio nome enquanto roda] → o WorkManager cancela a execução atual logo depois de a notificação ter saído. Inofensivo, porque `agendar` é o último passo do executor.
- [`sessaoExpirada` emitido antes de o coletor assinar, num processo frio] → o cancelamento pode se perder; o outro lembrete também é pulado por falta de sessão, e o próximo login reagenda. Aceito.
- [Títulos de tarefa na notificação] → visibilidade privada na tela bloqueada. No desbloqueio, mostrar os títulos é intencional.

## Migration Plan

Só acrescenta código no `mobile/`. Quem atualiza o app encontra os lembretes desligados, sem migração de dados. O rollback é distribuir o APK anterior. Trabalhos pendentes que apontam para um worker inexistente falham sem efeito visível; vale confirmar isso no smoke manual antes de depender dele.
