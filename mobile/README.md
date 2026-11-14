# Rotina — app Android

App nativo do planner, em Kotlin Multiplatform + Compose Multiplatform, **Android primeiro**
([ADR-0002](../docs/decisions/0002-app-mobile-kmp-android-primeiro.md); change OpenSpec
`openspec/changes/app-mobile-android/`). Ele consome a mesma API REST da web, sem nenhuma
mudança no backend.

> **iOS:** o alvo existe em `shared/` (`iosArm64`, `iosSimulatorArm64`) e há um ponto de
> entrada em `iosApp/`, mas nada é compilado nem testado sem macOS/Xcode. Nenhuma entrega
> afirma suporte a iOS.

## Estrutura

```
mobile/
  shared/        Kotlin Multiplatform: API (Ktor), sessão, regras, view-models e telas Compose
    commonMain/  api/ sessao/ dominio/ estado/ ui/
    commonTest/  testes rodados na JVM (:shared:jvmTest), sem emulador
  androidApp/    Application, Activity, Keystore + DataStore, recursos, network config
  iosApp/        só o ponto de entrada (não validado)
```

## Pré-requisitos

- Android SDK com a plataforma 37 (`sdk.dir` em `mobile/local.properties`, ou `ANDROID_HOME`).
- JDK para rodar o Gradle. O daemon pede **JDK 25** (`gradle/gradle-daemon-jvm.properties`)
  e baixa um automaticamente se não houver. Um JRE sem `javac` não basta.
- Backend rodando (`docker compose up` na raiz, ou o Spring local na porta 8080).

## Configurar a URL da API

```bash
cp .env.example .env
```

| Chave | Uso |
|---|---|
| `API_ENV` | `local` ou `prod` |
| `API_BASE_URL_LOCAL` | padrão `http://10.0.2.2:8080` (o host visto pelo emulador) |
| `API_BASE_URL_PROD` | só a origem HTTPS, sem porta nem caminho |

Precedência: `-PAPI_BASE_URL=…` → `API_ENV`/`.env` → `http://10.0.2.2:8080`.
O build **release** falha se a URL não for HTTPS. HTTP só é aceito no **debug**, e só para os
hosts de `androidApp/src/debug/res/xml/network_security_config.xml`.

### Aparelho físico contra a API da sua máquina

1. `API_BASE_URL_LOCAL=http://<IP-da-máquina>:8080` no `.env`.
2. Acrescente esse IP como `<domain>` em `androidApp/src/debug/res/xml/network_security_config.xml`.

## Rodar

```bash
./gradlew :androidApp:installDebug          # emulador ou aparelho conectado (adb)
./gradlew :shared:jvmTest                   # testes de API, sessão, regras e view-models
./gradlew :shared:koverHtmlReportJvm :shared:koverVerifyJvm   # cobertura (shared/build/reports/kover) + gate
./gradlew :androidApp:lint
./gradlew :androidApp:connectedDebugAndroidTest   # Keystore: precisa de aparelho/emulador
```

Regra mínima de cenários (1 feliz + 3 anomalias + 3 erros) e catraca de cobertura: `AGENTS.md` → Testes.

Release (APK sem assinatura; assine localmente, sem Play Store neste corte):

```bash
./gradlew :androidApp:assembleRelease -PAPI_ENV=prod
```

## Sessão e segurança

- O access JWT fica só em memória.
- O refresh chega em `Set-Cookie` e é guardado cifrado (AES-GCM, chave no Android Keystore)
  no DataStore. Ele só é enviado como `Cookie` para as rotas `/auth/…`.
- Em 401, o app faz uma renovação (serializada, para não reusar um refresh rotacionado) e
  repete a chamada uma vez. Se a renovação falhar, volta ao login com "Sua sessão expirou".
- O backup na nuvem e a transferência entre aparelhos ficam desligados
  (`res/xml/data_extraction_rules.xml`).
- O app não registra tokens, cookies nem senha em log.

## O que está no app

Login e logout, **Hoje** (ver/gerar/aprovar o plano, iniciar/concluir/abortar blocos),
**Tarefas** (lista, criação rápida, sessão avulsa com picker de duração), **Hábitos** do dia e
uma barra de sessão ativa com o XP ganho. Cadastro, projetos, tags, Decidir, calendário,
histórico e ajuste fino do rascunho continuam na web.

## Lembretes

Em **Perfil > Lembretes** há dois avisos locais, os dois desligados por padrão
(change `mobile-lembretes-locais`):

| Lembrete | Horário padrão | O que diz | Quando não sai |
|---|---|---|---|
| Resumo do dia | 08:00 | sem plano, rascunho para aprovar, ou blocos pendentes e o próximo | plano aprovado todo executado |
| Prazo amanhã | 19:00 | tarefas pendentes/em progresso com prazo amanhã (até 3 títulos) | nenhuma tarefa vence amanhã |

- No horário, o `LembreteWorker` (WorkManager) busca os dados na API usando o **mesmo grafo
  e a mesma sessão** do app (`RotinaApplication.deps`). Nunca crie outro `ApiHttp` para
  background: dois clientes renovando o refresh ao mesmo tempo revogam a sessão inteira no backend.
- A regra (quando disparar, o que dizer, quando pular) fica em `shared/.../lembrete` e
  `shared/.../dominio/{AgendaLembretes,ConteudoLembrete}.kt`, testada na JVM.
- Tolerância de 2 h: se o sistema só executar mais de 2 h depois do horário (sem rede,
  economia de bateria), o aviso do dia é pulado. Sem rede, erro da API ou resposta inválida
  também pulam o aviso, sem notificação de erro. No máximo um aviso de cada tipo por dia.
- Cada execução agenda a seguinte (`OneTimeWorkRequest` único por tipo, `REPLACE`), então o
  horário acompanha mudança de fuso e horário de verão.
- Na tela bloqueada aparece só "Você tem um lembrete do Rotina". Tocar abre Plano ou Tarefas.
- Logout ou sessão expirada cancelam os agendamentos e limpam a bandeja. As preferências ficam
  salvas, e o próximo login reagenda.
- A permissão de notificação (Android 13+) é pedida só ao ligar um lembrete, depois de uma
  explicação. Se for recusada, o lembrete fica desligado e a tela oferece abrir as configurações.

### Testar sem esperar o horário

Ligue um lembrete com o horário do minuto atual e force a execução do trabalho agendado:

```bash
adb shell dumpsys jobscheduler | grep -A3 com.controlerotina.mobile
adb shell cmd jobscheduler run -f com.controlerotina.mobile <JOB_ID>
```

O `JOB_ID` aparece na primeira saída. A notificação só sai se a execução cair na janela do
horário escolhido (de 5 min antes até 2 h depois) e se ainda não houve aviso daquele tipo hoje.
