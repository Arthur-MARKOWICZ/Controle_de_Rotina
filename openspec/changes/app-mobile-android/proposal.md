# Proposal

## Why

Hoje o planner só existe como web responsivo (React). No celular falta presença de app: ícone na home, sensação nativa, gestos e, no futuro, notificações de bloco/prazo. A issue [#41](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/41) pede escolher entre PWA e nativo. Este change escolhe **app nativo em Kotlin, Android first**, seguindo o padrão KMP + Compose Multiplatform que já roda no projeto-irmão `controle_gastos`. O app consome a mesma API REST e não muda as regras do backend.

## What Changes

- Novo pacote `mobile/` na raiz do monorepo: projeto Gradle Kotlin Multiplatform com `shared/` (domínio, cliente da API, view-models e UI Compose Multiplatform) e `androidApp/` (ponto de entrada, armazenamento seguro, rede Android). O alvo iOS fica só com a estrutura, sem compilar nem validar neste corte.
- Sessão nativa: login por e-mail/senha em `POST /auth/login`. O access token fica em memória. O refresh vem do `Set-Cookie` e é guardado criptografado (Android Keystore), e o app o reenvia como `Cookie` só nas rotas `/auth/*`. Em 401, renova uma vez e repete a chamada. Se falhar, volta para o login. Logout em `POST /auth/logout`.
- URL da API definida no build por `mobile/.env` (`API_ENV` local/prod). O build release exige HTTPS. HTTP só é liberado no debug, para os hosts locais.
- Fluxos do MVP:
  - **Navegação**: abre na aba **Tarefas**. Barra inferior Tarefas, Plano, Hábitos, Projetos, Decidir; Perfil por ícone na barra superior.
  - **Plano** (aba própria): plano do dia (`GET /planos?data=`), gerar quando não existe, aprovar o rascunho, ver capacidade/blocos.
  - **Executar bloco**: iniciar/concluir/abortar pelo plano (`/execucoes`), barra de sessão ativa e feedback de XP.
  - **Tarefas**: lista paginada em árvore (raízes + expandir subtarefas), quick add (título; duração, prazo e projeto opcionais), sessão avulsa (`/tarefas/{id}/iniciar`, `/concluir-direto`) com picker de duração e ações sempre alinhadas à esquerda do card.
  - **Editar tarefa**: detalhe com título, descrição, prioridade, prazo, duração e projeto (`PATCH /tarefas/{id}`).
  - **Subtarefas**: criar subtarefa no detalhe do pai (`POST /tarefas` com `parentId`), um nível só.
  - **Projetos**: listar ativos/arquivados, criar, editar, arquivar/reativar e ver as tarefas do projeto (`/projetos`).
  - **Decidir**: capturar, editar, descartar, promover a tarefa e ver decididos (`/decidir`).
  - **Hábitos**: hábitos do dia (`GET /habitos/do-dia`) com iniciar/concluir direto.
- ADR `docs/decisions/0002-app-mobile-kmp-android-primeiro.md` registrando por que nativo e não PWA, e o que fica para depois. Uma linha nova em `Decisoes_arqutetura.md`.

Fora de escopo: iOS validado/publicado; publicação na Play Store (só APK/AAB assinado localmente); push/notificações; biometria; offline-first com fila de sync; cadastro de conta, recuperação de senha e edição de perfil (continuam no web); ajuste fino do rascunho (adicionar/remover bloco, propostas de replanejamento); tags, reservas e esperas de projeto, reordenar tarefas/projetos, excluir/restaurar tarefa, mudar status de tarefa pela edição, calendário, histórico e capacidade semanal; PWA no front web.

## Capabilities

### New Capabilities

- `mobile-sessao`: login, persistência segura do refresh, renovação transparente, logout e expiração de sessão no app Android, além da configuração da URL da API por ambiente.
- `mobile-navegacao`: aba inicial Tarefas, ordem das abas e Perfil na barra superior.
- `mobile-hoje`: aba Plano com plano do dia (gerar/aprovar), lista de blocos, execução de bloco (iniciar/concluir/abortar), barra de sessão ativa e feedback de XP.
- `mobile-tarefas`: lista de tarefas em árvore, quick add, edição, subtarefas e sessão avulsa de tarefa com picker de duração.
- `mobile-projetos`: aba Projetos (listar, criar, editar, arquivar/reativar, tarefas do projeto) e seletor de projeto na tarefa.
- `mobile-decidir`: aba Decidir (capturar, editar, descartar, promover, ver decididos).
- `mobile-habitos`: hábitos do dia com iniciar e concluir direto.

### Modified Capabilities

(nenhuma. O backend e as specs web/API ficam como estão, e o app só consome contratos que já existem)

## Impact

- **Novo código**: `mobile/` (Gradle KMP, AGP, Compose Multiplatform, coroutines, Ktor ou `HttpURLConnection` + kotlinx.serialization, androidx.security/Keystore).
- **Backend**: sem mudança obrigatória. O refresh por cookie já funciona para cliente nativo (mesmo esquema do `mcp/`, ADR-0001). CORS não se aplica a app nativo.
- **Proxy front (vite/nginx)**: não muda, porque o app chama a API direto.
- **Infra/CI**: build Android (JDK 21+, Android SDK) e lint/testes do `shared` num job à parte. Docker compose não muda.
- **Docs**: ADR 0002, `Decisoes_arqutetura.md`, `mobile/README.md` e `mobile/.env.example`.
- **Issue #41**: os critérios da fatia PWA são trocados pela fatia nativa. Vale atualizar a issue com o link do ADR.
