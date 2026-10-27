# Controle de Rotina

Assistente de **planejamento automático de rotina** com **gamificação leve por XP**.

Você informa o que precisa fazer, quanto tempo estima e até quando precisa estar pronto. O sistema gera um plano executável; você aprova ou ajusta e executa os blocos.

> **Diferencial:** o motor de planejamento — não é um Todoist com XP, nem um RPG de produtividade.

## Stack

| Camada | Tecnologia |
|--------|------------|
| Backend | Java 25, Spring Boot 4, Spring Security, JPA/Hibernate, Flyway |
| Frontend | React 19, Vite, TypeScript |
| Banco | PostgreSQL 16 |
| Infra | Docker Compose |

## Como subir

Com Docker (recomendado):

```bash
docker compose up --build
```

| Serviço | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| PostgreSQL | localhost:5432 (`controle` / `controle`, DB `controle_rotina`) |

## Estrutura do repositório

```text
backend/                 API Spring Boot
frontend/frontend-web/   App React (Vite)
ordem_execucao/          Ordem dos slices do MVP
plans/                   Planos de features (quando existirem)
```

O backend organiza o código por domínio (`usuario`, `tarefa`, `planner`, `execucao`, `xp`, etc.), com controller, service e repository dentro de cada pasta.

## Fluxo principal

```text
Cadastrar tarefas
       ↓
Informar duração, prazo e prioridade
       ↓
Sistema analisa capacidade + prazos + prioridades
       ↓
Gera um plano → usuário aprova ou ajusta
       ↓
Executa blocos → conclui e ganha XP
       ↓
Histórico de progresso
```

## Documentação

- Visão do produto: [`assistente_planejamento_rotina_gamificado.md`](assistente_planejamento_rotina_gamificado.md)
- Decisões de arquitetura: [`Decisoes_arqutetura.md`](Decisoes_arqutetura.md)
- Ordem dos slices: [`ordem_execucao/docs.md`](ordem_execucao/docs.md)
- Proxy front → API: [`frontend/frontend-web/API_PROXY.md`](frontend/frontend-web/API_PROXY.md)

## Desenvolvimento local (sem Docker completo)

**Backend** (com Postgres acessível):

```bash
cd backend
./mvnw spring-boot:run
```

**Frontend:**

```bash
cd frontend/frontend-web
npm install
npm run dev
```

Em dev, a API é acessada por paths relativos via proxy do Vite. Em Docker, o proxy fica no Nginx. Qualquer prefixo novo de controller (`@RequestMapping("/…")`) precisa ser registrado nos dois — veja `API_PROXY.md`.
