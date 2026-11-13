# Tasks

## 1. Decisão e documentação base

- [x] 1.1 ADR `docs/decisions/0002-app-mobile-kmp-android-primeiro.md` (nativo KMP vs PWA, o que fica para depois) + linha em `Decisoes_arqutetura.md`; verificar que o ADR linka a issue #41 e este change
- [ ] 1.2 Atualizar a issue #41 com o link do ADR e os critérios da fatia nativa (substituindo os da fatia PWA); verificar o comentário na issue

## 2. Scaffold do projeto `mobile/`

- [ ] 2.1 Criar `mobile/` com Gradle wrapper, `settings.gradle.kts`, `gradle/libs.versions.toml` (D7), módulos `shared` (KMP android + ios targets declarados) e `androidApp`, pacote `com.controlerotina.mobile`; verificar `./gradlew :androidApp:assembleDebug` gera APK que abre uma tela "Olá" no emulador
- [x] 2.2 Acrescentar ao `.gitignore` raiz `mobile/.env`, `mobile/local.properties`, `mobile/build/`, `mobile/**/build/`, `.gradle/` e `.kotlin/`; verificar com `git status` limpo após o build
- [x] 2.3 Resolver a URL da API por `mobile/.env` + `.env.example` (D4): precedência, validação de formato, HTTPS obrigatório em release, `network_security_config` só no debug; verificar que `assembleRelease` com URL `http://` falha com mensagem clara e que o debug com `local` compila
- [x] 2.4 `mobile/README.md`: pré-requisitos (JDK, SDK), `.env`, rodar no emulador/aparelho, testar contra API local (IP da máquina + network config), aviso de iOS não validado; verificar seguindo o README do zero num clone limpo

## 3. Cliente HTTP e sessão (`mobile-sessao`)

- [x] 3.1 DTOs `@Serializable` de auth, `/me`, Problem Details e `ApiClient` Ktor em `commonMain` com `ignoreUnknownKeys`; verificar teste `commonTest` com `MockEngine` desserializando respostas reais do backend
- [x] 3.2 Parser de `Set-Cookie` do refresh (nome dado pela resposta, `Max-Age=0`/valor vazio limpa); verificar testes portando os casos de `mcp/src/cookies.test.ts`
- [ ] 3.3 `SecureTokenStore` `expect`/`actual` com AES-GCM + Android Keystore + DataStore; verificar teste instrumentado (ou Robolectric) de salvar/ler/limpar e que o arquivo em disco não contém o token em claro
- [x] 3.4 `SessionManager`: Bearer fora de `/auth/login|refresh`, Cookie só em `/auth/*`, 401 → refresh único sob `Mutex` → repetir uma vez, falha → `SessaoExpirada` limpando o store; verificar testes `MockEngine`: renovação ok, duas 401 simultâneas geram um só refresh, refresh 401 limpa a sessão, cookie nunca vai para `/tarefas`
- [ ] 3.5 Tela de login + logout no Perfil + restaurar sessão no boot; mensagens genéricas da spec; verificar testes do ViewModel (credenciais inválidas, rede off, sessão expirada) e login manual no emulador contra a API local

## 4. Shell, tema e sessão ativa

- [ ] 4.1 Tema Material 3 claro/escuro com cores da marca, ícone adaptativo e nome do app; navegação inferior (ordem final na seção 9); verificar visualmente nos dois temas em 360dp e 411dp
- [ ] 4.2 `SessaoAtivaStore` + barra de sessão ativa (título, restante/excedido a partir de `iniciadoEm`, Concluir, Abortar com confirmação, feedback de XP); carregar via `GET /execucoes` no boot e no retorno ao primeiro plano; verificar testes do store (cálculo do tempo, conclusão com XP) e barra visível ao trocar de aba

## 5. Aba Plano (`mobile-hoje`)

- [x] 5.1 DTOs de plano/bloco/execução/XP + ViewModel do Plano (buscar por data local; 404 → vazio com Gerar; `RASCUNHO` → Aprovar; conflito; não alocados); verificar testes do ViewModel cobrindo cada cenário da spec
- [ ] 5.2 UI da aba Plano: lista de blocos com ações por estado (só em `APROVADO`), seção "Não coube hoje", puxar para atualizar, recarregar ao voltar ao primeiro plano; verificar teste das regras de ação por estado (D6) e fluxo manual gerar → aprovar → iniciar → concluir no emulador

## 6. Tela Tarefas (`mobile-tarefas`)

- [x] 6.1 DTO `TarefaResponse` + regras puras de elegibilidade (D6); verificar testes: bloqueada, `temFilhos`, restante zero, deletada, finalizada, elegível
- [x] 6.2 Lista paginada + estado vazio + indicação "Bloqueada"; verificar teste do ViewModel de paginação (anexa a próxima página, para no fim)
- [x] 6.3 Quick add (título obrigatório ≤120, duração e prazo opcionais) com erros de Problem Details no campo; verificar testes do ViewModel (só título, título vazio não envia, erro 400 mapeado ao campo)
- [ ] 6.4 Picker de duração (15/25/30/45/60, Restante, custom, presets > restante desabilitados) + iniciar/concluir direto + tratamento de recusa da API recarregando o card; verificar testes do picker (limites 1..restante) e do erro, e fluxo manual no emulador

## 7. Tela Hábitos (`mobile-habitos`)

- [ ] 7.1 DTO `HabitoDoDiaResponse` + ViewModel + UI (streak, cumprido, estado vazio, Iniciar/Concluir direto só quando não cumprido e sem sessão em andamento); verificar testes do ViewModel e fluxo manual concluir → streak/XP atualizados

## 8. CI e verificação integrada

- [ ] 8.1 Workflow `.github/workflows/mobile.yml` (paths `mobile/**`, JDK + Android SDK fixos, `:shared:allTests :androidApp:assembleDebug lint`); verificar que o workflow passa num PR
- [ ] 8.2 Smoke end-to-end em aparelho físico Android contra a API publicada (HTTPS, build release assinado localmente): login abrindo em Tarefas, gerar/aprovar plano na aba Plano, executar bloco, quick add, editar tarefa, criar e iniciar subtarefa, criar/arquivar projeto, capturar e promover item do Decidir, sessão avulsa de tarefa, hábito, logout, reabrir app com sessão; fazer depois das seções 9–14; verificar com o checklist da issue #41 marcado

## 9. Navegação (`mobile-navegacao`)

- [x] 9.1 Reordenar `Aba` para Tarefas, Plano, Hábitos, Projetos, Decidir (Projetos e Decidir com tela placeholder "Em breve") e renomear o rótulo "Hoje" para "Plano"; verificar a ordem das abas no emulador
- [x] 9.2 Aba inicial `Aba.TAREFAS` no `Shell` (login e sessão restaurada); verificar logando e reabrindo o app que a aba selecionada é Tarefas
- [ ] 9.3 Tirar Perfil da barra inferior e abrir por ícone na `TopAppBar`, com voltar retornando à aba anterior; verificar no emulador que o Sair continua funcionando
- [x] 9.4 Trocar "tela Hoje" por "aba Plano" nos textos da UI (`HojeTela`, cabeçalho, estados vazios); verificar `grep -rn '"Hoje' mobile/shared/src/commonMain` sem sobras de rótulo

## 10. Ajuste no card de tarefa

- [ ] 10.1 `TarefaCard`: `Column` com `fillMaxWidth()` e `Row` de ações sem `align(End)` (começando na margem esquerda); verificar no emulador uma tarefa de título curto e outra de título longo com Iniciar na mesma posição

## 11. Editar tarefa (`mobile-tarefas`)

- [x] 11.1 Ampliar o DTO `Tarefa` com `descricao`, `projetoId`, `parentId`, `parentTitulo` (todos opcionais); verificar teste de desserialização com uma resposta real de `GET /tarefas/{id}`
- [x] 11.2 Função pura `diffPatch` (D6b) que gera `JsonObject` só com campos alterados e `JsonNull` para limpar; verificar testes: sem mudança → vazio, só título, limpar → `null`, trocar projeto → `projetoId`
- [x] 11.3 `RotinaApi.atualizarTarefa(id, JsonObject)` com `PATCH /tarefas/{id}`; verificar teste `MockEngine` do método e do corpo enviado
- [x] 11.4 `EditarTarefaViewModel`: carregar por id, alterar campos, validar título (vazio/≤120), salvar com `diffPatch`, mapear Problem Details por campo; verificar testes do ViewModel (salvar só título, título vazio não envia, erro 400 no campo)
- [ ] 11.5 Estado `alterado` no ViewModel + diálogo "Descartar alterações?" ao fechar; verificar teste do ViewModel (`alterado` liga/desliga) e o diálogo no emulador
- [ ] 11.6 Tela de detalhe da tarefa (título, descrição, prioridade, prazo — trocar, sem remover, porque a API não limpa prazo no `PATCH` —, duração só leitura quando `temFilhos`) aberta ao tocar no card; verificar no emulador editar título e prazo e ver o card atualizado ao voltar
- [x] 11.7 Após salvar, atualizar o item na lista do `TarefasViewModel` sem recarregar tudo; verificar teste do `TarefasViewModel` (`substituir(tarefa)`)

## 12. Subtarefas (`mobile-tarefas`)

- [x] 12.1 `tarefas(...)` aceita `somenteRaizes` e `parentId`; a lista principal passa `somenteRaizes=true`; verificar teste `MockEngine` dos parâmetros de query
- [x] 12.2 `TarefasViewModel.expandir(paiId)`/`recolher(paiId)` guardando filhos por pai (D6c); recarregar mantém os pais abertos e busca os filhos deles de novo (recolhidos perdem o cache); verificar testes (carrega uma vez, recolher não refaz chamada, recarregar rebusca os abertos)
- [ ] 12.3 Card do pai com controle expandir/recolher e filhos aninhados com recuo e ações de sessão; verificar no emulador expandir um pai e iniciar uma subtarefa
- [x] 12.4 `CriarTarefaRequest` com `parentId` opcional + `criarSubtarefa(paiId, titulo, duracao)` no ViewModel de detalhe; verificar testes (envia `parentId`, erro da API mantém título)
- [ ] 12.5 Seção Subtarefas no detalhe da raiz (lista + campo adicionar); no detalhe de subtarefa, só "Subtarefa de {pai}" sem adicionar; verificar no emulador criar subtarefa e ver o pai virar expansível na lista

## 13. Projetos (`mobile-projetos`)

- [x] 13.1 DTO `Projeto` + `RotinaApi.projetos(status)`; verificar teste de desserialização com resposta real paginada de `GET /projetos`
- [x] 13.2 `ProjetosViewModel` com lista e filtro Ativos/Arquivados; verificar testes (troca de filtro refaz a busca com o status certo, lista vazia)
- [ ] 13.3 Aba Projetos (lista, chips de filtro, estado vazio, puxar para atualizar) no lugar do placeholder; verificar no emulador
- [x] 13.4 `criarProjeto` na API + sheet Novo projeto (nome obrigatório ≤80, descrição ≤500, erros por campo); verificar testes do ViewModel (nome vazio não envia, erro 400 no campo)
- [ ] 13.5 Detalhe do projeto com editar nome/descrição via `PATCH /projetos/{id}` (reusando `diffPatch`); verificar teste do ViewModel e renomear no emulador
- [x] 13.6 Arquivar (com confirmação) e Reativar no detalhe; verificar testes do ViewModel (projeto troca de lista) e mensagem de recusa da API
- [ ] 13.7 Tarefas do projeto no detalhe via `tarefas(projetoId=)`, reusando `TarefaCard`; verificar teste `MockEngine` do parâmetro e lista no emulador
- [ ] 13.8 Seletor de projeto (ativos + Sem projeto) no quick add e no detalhe da tarefa; verificar testes (criar envia `projetoId`, Sem projeto na edição envia `projetoId: null`)

## 14. Decidir (`mobile-decidir`)

- [x] 14.1 DTO `ItemDecidir` + `RotinaApi.decidir(promovidos)`; verificar teste de desserialização com resposta real de `GET /decidir`
- [x] 14.2 Função pura `decidirVencido(decidirAte, agora)`; verificar testes (sem data, ontem, hoje mais tarde)
- [x] 14.3 `DecidirViewModel` com lista e filtro Abertos/Decididos; verificar testes (troca de filtro, lista vazia)
- [ ] 14.4 Aba Decidir (lista, destaque de vencido, chips, estado vazio) no lugar do placeholder; verificar no emulador
- [x] 14.5 `criarDecidir` na API + sheet Novo item (título ≤120, descrição, data limite opcional); verificar testes do ViewModel (título vazio não envia, erro 400 no campo)
- [ ] 14.6 Detalhe do item aberto com editar via `PATCH /decidir/{id}` (limpar data → `null`); detalhe de decidido só leitura; verificar testes do ViewModel e edição no emulador
- [x] 14.7 Descartar com confirmação (`DELETE /decidir/{id}`); verificar teste do ViewModel (item sai da lista) e no emulador
- [ ] 14.8 Virar tarefa: diálogo com duração, prazo e prioridade opcionais → `POST /decidir/{id}/promover`, item sai dos abertos e a aba Tarefas recarrega; verificar teste do ViewModel e fluxo no emulador

## Workflow follow-up

- Rodar `/openspec-verify-change` antes de arquivar.
- Arquivar o change com `/openspec-archive-change` depois do merge.
