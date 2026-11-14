# Tasks

Regra de testes do projeto: cada unidade com lógica tem ≥ 7 cenários (1 feliz + 3 anomalias + 3 erros), com `Clock`/fuso fixos nos limites. Rodar com `cd mobile && ./gradlew :shared:jvmTest`.

## 1. Filtro de prazo na API (shared)

- [x] 1.1 Adicionar `prazoDe`, `prazoAte` e `status` opcional em `FiltroTarefas` e `totalElements` em `PaginaTarefas`. Enviar os parâmetros em `KtorRotinaApi.tarefas` só quando não nulos e manter `STATUS_ABERTOS` como padrão. Verificar em `RotasEdicaoTest`/`ApiHttpTest` (engine mock) a query enviada, a ausência dos parâmetros quando nulos, a leitura de `totalElements` e a matriz 2xx/400/401→refresh/404/500/JSON inválido
- [x] 1.2 Conferir no backend (`TarefaService`/IT existente) se `prazoAte` é inclusivo e fixar o limite usado pelo D-1 (design L7). Verificar com o teste de `intervaloAmanha` da tarefa 2.1 cobrindo o prazo às 00:00 de depois de amanhã

## 2. Regras puras de horário e conteúdo (shared/commonMain)

- [x] 2.1 Criar `AgendaLembretes` (`proximoDisparo`, `dentroDaTolerancia`, `intervaloAmanha`) em `dominio/`. Verificar com `AgendaLembretesTest` (≥7): horário ainda não passou, já passou, exatamente agora, 23:59→00:00, atraso de 1h59 vs 2h01, troca de fuso, virada de mês/ano, horário de verão (fuso com DST, ex. `America/New_York`)
- [x] 2.2 Criar `ConteudoLembrete` (`montarResumo(plano, execucoes)`, `montarAvisoPrazo(tarefas, total)`) devolvendo `Notificacao?` com título, texto e destino. Verificar com `ConteudoLembreteTest` (≥7): sem plano, rascunho, aprovado com pendentes (contagem, `formatarMinutos`, próximo pela ordem), aprovado todo concluído/abortado → null, bloco em andamento conta como pendente, 1 vs 5 tarefas (3 títulos, prazo mais cedo primeiro), lista vazia → null, finalizada/cancelada ignorada, título nulo/vazio

## 3. Orquestração e preferências (shared/commonMain)

- [x] 3.1 Criar as interfaces `Notificador` e `AgendadorLembretes`, o enum `TipoLembrete` e as chaves de preferência (`lembrete.<tipo>.ligado|horario|ultimoDia`), com fakes em `commonTest/lembrete/FakesLembrete.kt`. Verificar que `:shared:jvmTest` compila com os fakes
- [x] 3.2 Criar `ExecutorLembrete(api, preferencias, notificador, agendador, clock, fuso)` seguindo o fluxo do design L4, sempre reagendando o próximo dia e devolvendo `Resultado.Exibida`/`Pulada(motivo)`. Verificar com `ExecutorLembreteTest` (≥7): feliz do resumo e do D-1, desligado, já disparou hoje (nenhuma segunda exibição), fora da tolerância, sem permissão, `ApiErro.Rede`, `ApiErro.SessaoExpirada`, `ApiErro.Http(500)`, conteúdo null; em todas as rotas de pulo, afirmar que o `ultimoDia` não mudou e que o próximo disparo foi agendado
- [x] 3.3 Criar `LembretesViewModel` (estado por tipo: ligado, horário, bloqueado; `carregar`, `ligar` (pede a permissão e guarda a recusa), `desligar`, `mudarHorario`, `reagendarLigados`, `precisaPedirPermissao`, `abrirConfiguracoes`), recebendo uma `PermissaoNotificacao`. Verificar com `LembretesViewModelTest` (≥7): padrões desligados 08:00/19:00, ligar agenda, mudar horário reagenda e cancela o anterior, desligar um não afeta o outro, permissão negada mantém desligado e `bloqueado` (inclusive depois de recarregar a tela), `reagendarLigados` só agenda os ligados, horário inválido nas preferências cai no padrão
- [x] 3.4 Ligar no `AppDependencias`: expor `lembretesVm` e `executorLembrete`; no callback de limpeza do `SessaoViewModel`, chamar `agendador.cancelarTodos()` e `notificador.limparTodas()`; após login/restaurar bem-sucedidos, chamar `reagendarLigados()`. Verificar em `SessaoViewModelTest` que o logout cancela e limpa, que o login reagenda e que um login falho não reagenda

## 4. UI de configuração (shared/commonMain)

- [x] 4.1 Adicionar `Destino.Lembretes` à `Navegacao`, a entrada "Lembretes" no `PerfilTela` e a `LembretesTela` (switch + seletor de horário por tipo, aviso "notificações bloqueadas" com ação para as configurações), com explicação antes do diálogo de permissão. Verificar em `NavegacaoTest` a abertura e o voltar, e no emulador que a tela abre a partir do Perfil

## 5. Casca Android (androidApp)

- [x] 5.1 Adicionar `androidx.work:work-runtime-ktx` ao `libs.versions.toml` e ao `androidApp`, e `POST_NOTIFICATIONS` ao manifest. Verificar com `./gradlew :androidApp:assembleDebug`
- [x] 5.2 Implementar `AgendadorWorkManager` (trabalho único por tipo, `REPLACE`, `initialDelay`, `NetworkType.CONNECTED`) e `LembreteWorker` (`CoroutineWorker` que chama `deps.executorLembrete.executar(tipo)` e sempre retorna `success`), reusando `RotinaApplication.deps` (design L2). Verificar com `adb shell dumpsys jobscheduler`/App Inspection que o trabalho aparece com o atraso certo depois de ligar o lembrete
- [x] 5.3 Implementar `NotificadorAndroid`: canal `lembretes`, IDs fixos por tipo, `VISIBILITY_PRIVATE` + versão pública genérica, `PendingIntent` com o extra `destino`, `permitido()` via `NotificationManagerCompat.areNotificationsEnabled()`. Verificar no emulador com o aparelho bloqueado (texto genérico) e desbloqueado (títulos)
- [x] 5.4 Na `MainActivity`: launcher de `RequestPermission` exposto à `PermissaoAndroid` (campo `solicitante`, só no Android 13+), `launchMode="singleTop"`, e leitura do extra `destino` em `onCreate`/`onNewIntent` → `navegacao.selecionar(Aba.PLANO|TAREFAS)`. Verificar no emulador API 33+ (conceder, recusar, revogar depois) e API 31 (sem diálogo), e que o toque na notificação abre a aba certa

## 6. Docs, cobertura e smoke

- [x] 6.1 Adicionar a seção "Lembretes" ao `mobile/README.md` (o que dispara, padrões, tolerância de 2h, como testar forçando o worker com `adb shell cmd jobscheduler run`). Verificar que o comando documentado dispara o lembrete no emulador
- [x] 6.2 Rodar `cd mobile && ./gradlew :shared:jvmTest :shared:koverHtmlReportJvm :shared:koverVerifyJvm` e subir os limites de `kover {}` em `shared/build.gradle.kts` para o novo piso medido, arredondado para baixo. Verificar que o `koverVerifyJvm` passa com os limites novos
- [x] 6.3 Smoke em aparelho/emulador contra a API local: ligar os dois lembretes com horário daqui a 2 minutos, com plano aprovado e uma tarefa com prazo amanhã → as duas notificações chegam uma vez cada; sem rede → nada chega; logout → a bandeja limpa e nada dispara; login → volta a agendar. Verificar marcando os critérios de aceite da issue #73

## Workflow follow-up

- Arquivar `app-mobile-android` antes ou junto, para as specs `mobile-*` existirem em `openspec/specs/`.
- Arquivar esta change depois da revisão e do smoke.
