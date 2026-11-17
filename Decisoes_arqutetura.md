# Decisoes gerais
Sera um sistema web  

Utilizar docker com docker compose para subir os componetes da applicacao




## Linguagens e framework

### **back-end**
 -   Java 25

 - Spring boot v 4.x.x

    - Spring web 
    - Spring security
    - jpa/Hibernate
    - flyway
### Front-end web 
 - node 24.x.x
 - react
 - API via paths relativos; proxy no Vite (dev) e Nginx (Docker). **Todo prefixo novo de endpoint** deve ser registrado nos dois — ver `frontend/frontend-web/API_PROXY.md`.
### Mobile
 - Kotlin Multiplatform + Compose Multiplatform, **Android primeiro** (iOS só estruturado, não validado). Pacote `mobile/` na raiz.
 - Cliente direto da API REST (sem proxy vite/nginx); URL por `mobile/.env` no build, HTTPS obrigatório em release.
 - Sessão: access em memória, refresh cifrado (Android Keystore) reenviado como cookie só em `/auth/*`; sem mudança no backend.
 - Decisão: [`docs/decisions/0002-app-mobile-kmp-android-primeiro.md`](docs/decisions/0002-app-mobile-kmp-android-primeiro.md); change: `openspec/changes/app-mobile-android/`.

## Banco de dados
- postgres

## Arquitetura de pastas
 - seguir o padrao de pastas onde tem uma pasta principal com o nome do item (exemplo: usuario) e dentro fica o repository, controller , service e etc.

## MCP (agentes)
 - Pacote `mcp/` na raiz: servidor MCP **stdio** em TypeScript que é **cliente** da API REST (não embute MCP no Spring).
 - Sessão local (access + refresh), sem token só no `mcp.json` para uso cotidiano.
 - Auth preferida via CLI `pnpm auth-login` (senha no terminal); tool `login` só como fallback.
 - Decisão: [`docs/decisions/0001-mcp-cliente-rest-stdio.md`](docs/decisions/0001-mcp-cliente-rest-stdio.md); plano: [`plans/negocio/mcp.md`](plans/negocio/mcp.md).
 - Estratégia espelhada do projeto-irmão controle de gastos (ADR-023 + CLI de login).

## Contrato da API (OpenAPI)
 - `contract/openapi.json` é gerado pelo backend (springdoc 3.2, OpenAPI 3.1) e versionado. O `ContratoOpenApiIT` falha se ele divergir do backend.
 - Front (tag, tarefa, páginas) e MCP usam tipos gerados com `openapi-typescript` (versão fixa, `.d.ts` commitado porque o build Docker do front não enxerga a raiz). Mobile: `ContratoModelosTest` valida `Modelos.kt`, sem geração de Kotlin.
 - Respostas: todo campo é obrigatório e o nulo só vem com `@Nullable` (JSpecify). Requests: obrigatório só o que a validação exige.
 - Paginação: `PagedModel<T>` (`{ content, page }`), nunca `Page<T>` serializado direto.
 - `/v3/api-docs` desligado em produção (só perfil `local` e testes). Sem Swagger UI.
 - Change: `openspec/changes/archive/2026-11-17-contrato-openapi-tipos-gerados/` (issue #76).
