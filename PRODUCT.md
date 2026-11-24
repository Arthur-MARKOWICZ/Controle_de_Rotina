# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Uso pessoal: o próprio autor do projeto é o usuário principal, sem público externo previsto por enquanto. Ele tem estudos, trabalho e projetos pessoais misturados e usa o app para decidir quando cada coisa vai ser feita.

## Product Purpose

Rotina é um assistente de planejamento automático de rotina. O usuário informa o que precisa fazer, quanto tempo estima e até quando precisa estar pronto; o sistema monta um plano executável para os próximos dias, e o usuário aprova ou ajusta, executa os blocos e acompanha o progresso. Sucesso é sair de uma lista de tarefas para um dia planejado sem precisar decidir manualmente quando fazer cada coisa.

## Positioning

O diferencial é o motor de planejamento (capacidade + prazos + prioridade → plano por dia, com revisão e aprovação). Não é "um Todoist com XP" nem um RPG de produtividade. A gamificação por XP é feedback leve de progresso, não o núcleo.

## Operating Context

- Fluxo: cadastrar tarefas → duração, prazo e prioridade → gerar plano → revisar e aprovar → executar blocos com timer → concluir e ganhar XP → histórico.
- Superfícies: tarefas (casa do app), decidir, projetos, objetivos, plano do dia, hábitos, capacidade semanal, calendário, histórico, tags, revisão da semana.
- Clientes: web (React/Vite) e app Android (Compose Multiplatform, `mobile/`), mais um servidor MCP para agentes. O app abre em Tarefas; o plano é sob demanda (`plans/ui/ui-ia-nav-tarefas-primeiro.md`).
- Temas claro e escuro, ambos suportados.

## Capabilities and Constraints

- Capacidade semanal por dia + exceções por data; contas novas nascem com seg–sex 4h.
- Plano multi-dia com conflito "não coube até o prazo", propostas de replanejamento e aprovação explícita.
- XP por bloco/tarefa concluída, nível e histórico; hábitos leves por dia da semana.
- Contrato OpenAPI (`contract/openapi.json`) é a fonte dos formatos da API.

## Brand Commitments

- Nome "Rotina".
- Nenhuma cor, logo ou tipografia é fixa: a identidade visual pode ser substituída (confirmado em 2026-11-24).

## Evidence on Hand

Sem depoimentos, clientes ou métricas de uso: não inventar.

## Product Principles

1. O plano é o produto: toda tela deve aproximar a pessoa de um dia planejado e aprovado.
2. Sugerir, nunca impor: o sistema propõe, o usuário revisa e aprova.
3. Gamificação discreta: XP reconhece progresso sem transformar a rotina em jogo.
4. Honestidade sobre o tempo: conflitos e capacidade aparecem com clareza, sem alarme exagerado.

## Accessibility & Inclusion

Meta WCAG AA nos dois temas; prioridade e estado nunca comunicados só por cor (já há teste de contraste dos tokens).
