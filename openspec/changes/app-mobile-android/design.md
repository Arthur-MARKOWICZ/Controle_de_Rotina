# Design

## Context

- A API Spring (`backend/`) é o único contrato. Ela não tem prefixo `/api`, o access JWT dura 15 min e o refresh dura 5 dias, em cookie `refresh_token` (`HttpOnly`, `Path=/auth`), rotacionado a cada `POST /auth/refresh`. Reusar um refresh revogado invalida a família inteira (#16).
- O `mcp/` deste repo (ADR-0001) já prova que um cliente fora do browser funciona sem mudar o backend: ele lê o `Set-Cookie`, persiste o refresh e reenvia como `Cookie`.
- O projeto-irmão `controle_gastos/mobile` tem um app KMP Android-first em produção (ADR-004 e ADR-021 de lá), com `shared/` + `androidApp/` + `iosApp/` vazio, versões em `gradle/libs.versions.toml` e URL da API resolvida por `.env`. Este design segue esse padrão de propósito, para trocar conhecimento entre os dois repositórios.
- A máquina de desenvolvimento não tem macOS/Xcode. O iOS não é validável aqui.
- Motivação e escopo: ver `proposal.md`. Comportamento: ver `specs/mobile-*`.

## Goals / Non-Goals

**Goals:**
- Projeto `mobile/` que compila e roda no emulador e em aparelho Android com um único comando Gradle.
- Lógica de domínio, cliente HTTP, sessão e telas em `commonMain`, testáveis sem emulador.
- Paridade de regra com a web nos fluxos do MVP (elegibilidade de sessão, picker de duração, barra de sessão ativa).

**Non-Goals:**
- Mudar endpoints ou o formato de auth do backend.
- Reproduzir na mão o design system da web: o app usa Material 3 com as cores da marca e não copia componentes.
- Cache local/offline de dados de domínio (só a sessão é persistida).

## Decisions

### D1. Kotlin Multiplatform + Compose Multiplatform, validado só no Android
Estrutura `mobile/shared` (KMP: `commonMain`, `androidMain`, `iosMain`) + `mobile/androidApp` (Application, Activity, manifest, recursos) + `mobile/iosApp` (só o ponto de entrada, não compilado na CI).
- *Por quê*: é o mesmo padrão do `controle_gastos`. Domínio e UI ficam compartilhados, e um iOS no futuro não vira uma reescrita.
- *Alternativa*: Android puro com Jetpack Compose. É mais simples no início, mas o iOS teria que duplicar tudo. Descartada porque o padrão KMP já está resolvido no outro projeto.
- *Alternativa*: PWA (o que a issue #41 sugeria). Reusa o React, mas não entrega sensação nativa, armazenamento seguro, nem um caminho limpo para notificações/biometria depois. O registro fica no ADR 0002.

### D2. Ktor Client + kotlinx.serialization em `commonMain`
O cliente da API (`ApiClient`) fica em `commonMain` com Ktor, usando engine OkHttp no Android e Darwin no iOS no futuro. Os DTOs ficam em `@Serializable`, espelhando os records do backend (`PlanoResponse`, `TarefaResponse`, `HabitoDoDiaResponse`, `ExecucaoResponse`, `XpConclusaoResponse`, `ProblemDetail`).
- *Por quê*: o `controle_gastos` usa `HttpURLConnection` em `androidApp`, o que deixa a lógica de refresh fora do código compartilhado e sem teste comum. Com Ktor, o refresh/retry é testado em `commonTest` com `MockEngine`.
- *Alternativa*: Retrofit/OkHttp só no Android, que obriga o mesmo split do gastos. Descartada.
- Campos desconhecidos são ignorados (`ignoreUnknownKeys`), para o app não quebrar quando a API ganhar campos novos.

### D3. Sessão: access em memória, refresh criptografado, cookie só em `/auth/*`
- `ApiHttp` (common) guarda o access em memória e usa a interface `SecureTokenStore`, implementada no `androidApp` (`KeystoreTokenStore`) e em memória nos testes. Uma interface em vez de `expect`/`actual` porque a implementação Android precisa do `Context`.
- Android `actual`: AES-GCM com chave do Android Keystore e o valor cifrado no DataStore Preferences. `EncryptedSharedPreferences` está deprecated, por isso não é usado.
- No login e no refresh, o app lê `Set-Cookie`, extrai `nome=valor` do cookie de refresh (nome dado pela resposta, igual ao `mcp/src/cookies.ts`) e guarda. Um `Max-Age=0` ou valor vazio apaga o que estava guardado.
- Um plugin Ktor anexa `Authorization: Bearer` fora de `/auth/login|refresh` e `Cookie` só em `/auth/*`. Em 401, uma renovação protegida por `Mutex` e uma repetição. Se falhar, emite `SessaoExpirada` e a navegação volta ao login.
- *Alternativa*: mudar o backend para devolver o refresh no corpo quando o cliente for mobile (o gastos acabou expondo `refreshToken` no corpo). Fica para depois, porque com cookie já funciona e não abre superfície nova.
- *Alternativa*: usar o `HttpCookies` do Ktor com storage persistente. Descartado porque tiraria o controle de onde o cookie é enviado e de como ele é cifrado.

### D4. URL da API por `.env` no build (espelho do ADR-021 do gastos)
`mobile/.env` (fora do Git) + `mobile/.env.example` versionado com `API_ENV=local|prod`, `API_BASE_URL_LOCAL` e `API_BASE_URL_PROD`. A precedência é `-PAPI_BASE_URL`, depois `API_ENV`, depois o fallback `http://10.0.2.2:8080`. O valor resolvido vai para o `BuildConfig`, e o Gradle valida o formato e exige HTTPS em release. Um `network_security_config.xml` no source set `debug` libera cleartext só para `10.0.2.2`, `localhost` e `127.0.0.1`.
- O backend local responde direto na porta 8080, sem proxy Nginx. Em prod, a URL é o host HTTPS publicado (ver `docs/deploy/`).

### D5. UI e estado
- Compose Multiplatform + Material 3. Tema claro/escuro seguindo o sistema, com cores da marca derivadas dos tokens da web.
- Navegação com barra inferior: **Tarefas** (aba inicial), **Plano**, **Hábitos**, **Projetos**, **Decidir**. Material 3 recomenda no máximo cinco destinos na barra, por isso o Perfil (e-mail + Sair) sai dela e vira um ícone na `TopAppBar`, aberto como tela por cima da aba atual. A barra de sessão ativa fica acima da barra inferior, em todas as telas autenticadas.
- Detalhes (tarefa, projeto, item de Decidir) abrem como tela cheia empilhada sobre a aba, com voltar do sistema fechando o detalhe. Sem biblioteca de navegação: um estado `destino` no `Shell` basta para um nível de pilha.
- Um view-model por tela, como classe Kotlin simples (sem `androidx.lifecycle`), expondo `StateFlow` e funções `suspend` testadas direto em `commonTest`. Eles vivem no `AppDependencias`, criado no `Application`, e por isso sobrevivem à rotação de tela sem a biblioteca de lifecycle. Um `SessaoAtivaStore` único, compartilhado entre as telas, guarda a execução `EM_ANDAMENTO`.
- A sessão ativa vem de `GET /execucoes` (blocos do plano aprovado) e de `GET /habitos/do-dia` (hábito avulso). A sessão avulsa de **tarefa** não aparece em nenhuma listagem da API, então um resumo dela (id, título, duração, início, data) fica no DataStore para sobreviver ao processo morto. Esse resumo só vale para o mesmo dia e é descartado quando concluir ou abortar recebe 400/404/409.
- "Voltar ao primeiro plano" é sinalizado pela `MainActivity.onResume` para o grafo, e as telas recarregam. Também não precisa de lifecycle-compose.
- DI manual (um `AppGraph` montado no `Application`). Koin/Hilt seria cerimônia demais para o tamanho do app.
- O cronômetro da sessão ativa é calculado a partir do início que a API informa, nunca de um contador local que só incrementa, para sobreviver a processo morto.
- A data de "hoje" é a data local do aparelho (`kotlinx-datetime`), igual à web.

### D6. Regras de elegibilidade compartilhadas
As regras "pode iniciar/concluir" (tarefa: não deletada, não bloqueada, `restanteMinutos > 0`, sem filhos, status de progresso; bloco: estado da execução) ficam como funções puras em `commonMain/dominio`, cobertas em `commonTest`. Elas espelham `ui-tarefa-sessao`, mas a API continua sendo a autoridade (spec `mobile-tarefas`, "Recusa da API na sessão").

### D6b. Edição e PATCH parcial
O backend distingue campo ausente de `null` (`projetoIdPresente`, `decidirAtePresente`). O app monta o corpo do `PATCH` como `JsonObject` só com os campos que mudaram em relação ao que foi carregado, com `JsonNull` explícito para "limpar". Uma função pura `diffPatch(original, editado)` em `commonMain/dominio` faz isso e é coberta em `commonTest`. O mesmo vale para projeto e Decidir. Exceção conhecida: o `PATCH /tarefas` não tem `prazoPresente`, então `prazo: null` mantém o prazo; o app só troca a data (Decidir tem `decidirAtePresente` e limpa normalmente).
- *Alternativa*: data class com todos os campos opcionais e `explicitNulls = false`. Descartada porque não dá para enviar `null` de propósito.

### D6c. Subtarefas na lista
A lista principal pede `somenteRaizes=true`. Expandir um pai faz `GET /tarefas?parentId=` uma vez e guarda os filhos no estado do ViewModel por id do pai. Recarregar (puxar, sessão iniciada/concluída) mantém os pais abertos e busca os filhos deles de novo, para a árvore não fechar sozinha a cada ação. Não há "Subtarefa de" na lista, porque ela não usa filtros que soltem o filho (o web precisa disso por causa dos filtros).

### D7. Versões e build
`gradle/libs.versions.toml` partindo das versões do gastos (AGP 9.1, Kotlin 2.4.10, Compose MP 1.12), mais Ktor 3.6, kotlinx-serialization 1.13, kotlinx-datetime 0.8 e DataStore 1.3. O daemon do Gradle usa JDK 25 (`gradle-daemon-jvm.properties`, como no gastos). Também há um alvo `jvm()` em `shared` só para rodar `commonTest` sem emulador. `minSdk 26`, `targetSdk`/`compileSdk` na maior versão suportada pelo AGP. Pacote `com.controlerotina.mobile`. Gradle wrapper versionado.

### D8. CI
Um workflow novo, `.github/workflows/mobile.yml`, disparado só por mudanças em `mobile/**`, roda `./gradlew :shared:allTests :androidApp:assembleDebug lint`. O `deploy.yml` atual não muda. Docker compose também não: o app não roda em contêiner.

## Risks / Trade-offs

- [Rotação do refresh + morte do processo no meio do refresh: o novo cookie não chega a ser salvo e o antigo, já revogado, dispara a revogação da família] → gravar o novo refresh antes de liberar o `Mutex` e antes de repetir a chamada. Se mesmo assim cair, o usuário só precisa logar de novo (sessão expirada genérica).
- [Backup/transferência do DataStore para outro aparelho deixaria um refresh indecifrável] → `data_extraction_rules.xml` exclui tudo, e o login é refeito no aparelho novo.
- [Duas instâncias do app (ou app + web) com sessões diferentes] → cada login cria uma família de refresh própria no backend, e o reuso só afeta a família daquele login. Sem ação.
- [Parsing de `Set-Cookie` à mão] → teste unitário com os formatos reais (`Path`, `Max-Age`, `SameSite`, `Secure`, nome `__Secure-…`), reaproveitando os casos de `mcp/src/cookies.test.ts`.
- [Divergência de regra entre web e mobile] → as regras de D6 viram testes. A API recusa o que escapar, e o app mostra o Problem Details.
- [iOS declarado mas não compilado] → o README e o ADR deixam claro: nenhuma entrega afirma suporte a iOS sem build em Mac.
- [AGP/SDK local diferente do suportado] → a CI fixa a versão do SDK. Aviso local é aceito, como no gastos.
- [Fuso: aparelho em fuso diferente do usado na web] → mesmo comportamento da web (data local). Documentado, sem tratamento extra.

## Migration Plan

Só acrescenta código: nada no backend/web muda e não há migração de dados. Rollback é não distribuir o APK. A distribuição neste corte é APK debug/release assinado localmente, instalado via `adb` ou arquivo. A Play Store fica fora.

## Open Questions

- Ícone adaptativo final: usar o `LogoRotina` da web vetorizado ou desenhar um ícone próprio? Não muda specs nem tasks (a task de ícone existe nos dois casos).
- Nome de exibição do app no launcher ("Rotina" ou "Controle de Rotina").
