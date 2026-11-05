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

