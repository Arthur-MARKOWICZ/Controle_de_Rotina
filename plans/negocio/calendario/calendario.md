# Feature — Calendário: visão semanal/mensal do plano e dos prazos

Issue: [#38](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/38)

Escopo: leitura agregada do horizonte multi-dia — **semana** (prioridade) e **mês** (indicadores leves); sobrepor plano **APROVADO**, carga vs capacidade e prazos; rota `/calendario` com detalhe do dia **no próprio calendário**; modo alternativo listando só tarefas que **vencem naquele dia**.  
Fora deste slice: sync Google/Outlook; recorrência/hábitos; drag-and-drop / reagendar; edição ou aprovação de plano; deep-link para a Home; plano `RASCUNHO` na grade; entity/tabela `Calendario*`; cache; plano UI pixel-perfect separado (`plans/ui/` fica para refinement futuro se necessário).

Dependências: `planner.md` (+ janela multi-dia), `capacidade.md`, `tarefa.md` (+ sem prazo), `ui-layout-nav.md`, `ui-home-agenda.md` / `ui-capacidade-visual.md` (semântica visual de sobrecarga).

---

## Conceito

1. Calendário é **somente leitura**: não grava plano nem tarefa; agrega fontes já confiáveis.
2. **Sem entity nova e sem cache** — agregação na leitura (mesmo espírito de `historico.md`).
3. Escopo sempre pelo `usuarioId` do JWT. Nunca aceitar `usuarioId` no path/query/body.
4. Fuso canônico: **`America/Sao_Paulo`**. Semana: **segunda → domingo**.
5. Por dia civil `D`, a grade usa **apenas** o plano com `status = APROVADO` (maior `versao` se houver mais de um). Rascunho **não** aparece na célula nem no painel.
6. Duas lentes na UI (mesmo payload da API):
   - **Plano** — blocos do aprovado (títulos + duração) + carga/capacidade + chip `APROVADO` quando houver plano.
   - **Prazos** — só tarefas cujo prazo civil (BRT) cai em `D` (não finalizadas / não deletadas).
7. Clique no dia → **painel/detalhe no calendário** (não navega para `/`).
8. Mês entra neste slice com densidade menor (contagens / marcadores), sem listar todos os títulos na célula.

---

## Fontes e regras de agregação

| Dado no dia `D` | Fonte | Regra |
|-----------------|--------|--------|
| Plano | `Plano` + `BlocoPlano` + título da `Tarefa` | `status = APROVADO`, maior `versao` em `(usuarioId, data = D)`; blocos por `ordem` asc |
| Capacidade | resolução já usada pelo planner | `capacidadeMinutos = horasResolvidas * 60` (`VAZIO` / 0 → 0); origem `SEMANAL` \| `OVERRIDE` \| `VAZIO` |
| Minutos alocados | soma `BlocoPlano.duracaoMinutos` do plano aprovado do dia | 0 se não houver aprovado |
| Sobrecarga | comparação | `minutosAlocados > capacidadeMinutos` (igual espírito da `CapacidadeBar` na Home) |
| Chip de status | derivado | `APROVADO` se existir plano aprovado; senão dia sem chip de plano |
| Prazos do dia | `Tarefa` | `deletedAt == null`, `status ≠ FINALIZADA`, `prazo != null` e dia civil do `prazo` em BRT = `D` |

Tarefas **sem prazo** não entram na lente Prazos. Soft-deleted não entram.

---

## Entity / Entities

Nenhuma entity nova. Reutilizar:

- `Plano` / `BlocoPlano` / `StatusPlano`
- Capacidade resolvida (serviço existente)
- `Tarefa` (`prazo`, `status`, `deletedAt`, `titulo`, `duracaoMinutos`, `prioridade`)

Índices (Flyway **só se ainda não existirem** — activity B1):

- suporte a listar planos por `(usuario_id, data)` no intervalo + filtro `status`
- suporte a tarefas ativas com `prazo` no intervalo Instant do período BRT  
  (ex.: parcial `(usuario_id, deleted_at, prazo)` — o que o dialeto permitir com clareza)

---

## Back-end

Todos autenticados (JWT). Sem token → **401**.

Prefixo novo: **`/calendario`** (registrar em Vite + Nginx + `API_PROXY.md`).

Pacote: `calendario` (agregador + controller + DTOs). Reutilizar repositórios/serviços de plano, capacidade e tarefa via construtor — sem Field Injection. Sem cache.

### Contrato

1. **GET /calendario**  
   - Query obrigatória: `dataDe`, `dataAte` (`LocalDate`).  
   - Validações:
     - ausentes / malformadas → **400**
     - `dataAte` &lt; `dataDe` → **400**
     - quantidade de dias no intervalo (`inclusive`) &gt; **31** → **400** (mesmo teto de `GET /capacidades/resolvida`)
   - Defaults: nenhum (front envia a semana ou o mês visível).
   - 200: lista **contígua** de dias de `dataDe` a `dataAte` (dia sem plano/prazo ainda assim aparece, com listas vazias / zeros).

Resposta (shape sugerido):

```json
{
  "periodo": { "inicio": "2026-10-26", "fim": "2026-11-01" },
  "dias": [
    {
      "data": "2026-10-27",
      "capacidadeMinutos": 240,
      "origemCapacidade": "SEMANAL",
      "minutosAlocados": 180,
      "sobrecarga": false,
      "statusPlano": "APROVADO",
      "plano": {
        "id": "…",
        "versao": 2,
        "temConflito": false,
        "blocos": [
          {
            "id": "…",
            "tarefaId": "…",
            "titulo": "Estudar Spring",
            "duracaoMinutos": 60,
            "ordem": 1,
            "prioridade": "ALTA"
          }
        ]
      },
      "tarefasComPrazo": [
        {
          "id": "…",
          "titulo": "Entregar relatório",
          "prazo": "2026-10-27T23:59:59Z",
          "status": "PENDENTE",
          "prioridade": "ALTA",
          "duracaoMinutos": 120
        }
      ]
    }
  ]
}
```

Regras do DTO:

- Sem plano aprovado: `statusPlano = null`, `plano = null`, `minutosAlocados = 0`, `sobrecarga = false` (salvo se quiser marcar sobrecarga só com alocação — aqui sem alocação não há sobrecarga).
- `sobrecarga = minutosAlocados > capacidadeMinutos`.
- `tarefasComPrazo` ordenadas por `prazo` asc, depois `titulo`.
- Campos numéricos nunca `null` (usar `0`). Listas nunca `null` (usar `[]`).
- Não expor rascunho neste endpoint.

Sem POST/PATCH/DELETE. Sem job.

---

## Front-end

Fluxo mínimo neste slice de negócio (detalhe visual fino pode evoluir depois em `plans/ui/`):

1. **Rota autenticada `/calendario`** + item na nav do `AppShell` (desktop e bottom bar).
2. **Toggle de período:** Semana | Mês.
3. **Toggle de lente:** Plano | Prazos (estado de UI; mesmos dados do `GET /calendario`).
4. **Semana** — 7 colunas (seg–dom); em lente Plano: títulos dos blocos + duração na célula; chip `APROVADO` quando houver; indicador de sobrecarga (token/semântica da Home/Capacidade, não só cor). Em lente Prazos: títulos das tarefas com prazo no dia.
5. **Mês** — grade do mês; indicadores leves por dia (ex.: nº de blocos ou ponto “tem plano”, marcador “tem prazo”, destaque de sobrecarga). Sem listar todos os títulos na célula.
6. **Clique no dia** — abre painel/drawer/seção de detalhe **na própria página**: lista completa do dia conforme a lente ativa (blocos ou tarefas a vencer) + resumo capacidade/alocado/sobrecarga.
7. Navegação anterior/próximo (semana ±7; mês ±1); query opcional `dataDe`/`visao`/`lente` na URL para deep-link interno do calendário.
8. Viewport estreita: usável no shell responsivo (#12) — semana pode scrollar horizontalmente ou empilhar; sem scroll horizontal da shell.
9. **Proxy** — prefixo `/calendario` em `vite.config.ts`, `nginx.conf`, `API_PROXY.md`.

Sem arrastar blocos. Sem gerar/aprovar plano nesta tela.

---

## Decisões fechadas (revisão)

1. Só negócio neste arquivo (`plans/negocio/calendario/calendario.md`); UI detalhada extra fica opcional depois.
2. Agregação no back: **`GET /calendario?dataDe&dataAte`** (teto 31 dias); sem N×`GET /planos`.
3. Semana **e** mês no mesmo slice (mês = indicadores leves).
4. Grade/painel mostram **somente** plano `APROVADO` + chip de status no dia; rascunho fora.
5. Clique no dia → detalhe **dentro** do calendário (não Home).
6. Semana em lente Plano lista **títulos** dos blocos nas células.
7. Lente **Prazos**: só tarefas com prazo civil em `D` (BRT), ativas, não finalizadas.
8. Sobrecarga = `minutosAlocados > capacidadeMinutos` (reuso da semântica Home/Capacidade).
9. Sem entity/cache; auth JWT; prefixo `/calendario` + proxy.
10. Fora: sync externo, recorrência, DnD, escrita no plano.
11. Issue #38.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Agregador: dia com `APROVADO` + blocos → DTO com títulos, minutos, `statusPlano=APROVADO`; ignora `RASCUNHO` no mesmo dia | B1 | Aceite “só aprovado” |
| T2 | Agregador: `minutosAlocados > capacidadeMinutos` → `sobrecarga=true`; caso igual/menor → `false` | B1 | Critério de sobrecarga |
| T3 | Agregador: tarefas com `prazo` no dia BRT entram em `tarefasComPrazo`; sem prazo / `FINALIZADA` / soft-deleted ficam de fora | B1 | Lente Prazos |
| T4 | Validação de intervalo: invertido ou &gt; 31 dias → rejeitado | B1 | Anti-abuso (paridade capacidade) |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | `GET /calendario`: semana com dias contíguos; dia sem plano → `plano=null`, listas/zeros ok | B2 | Contrato |
| T6 | Sem token → **401**; usuário A não vê planos/tarefas de B | B2 | Auth / isolamento |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T7 | `/calendario` renderiza semana a partir da API (blocos com título) | F2 | Aceite semana |
| T8 | Toggle lente Prazos mostra tarefas a vencer do dia; clique no dia abre painel interno | F3 | Aceite #7 + detalhe no calendário |
| T9 | Visão mês mostra indicadores leves e navega o período | F4 | Mês no slice |

---

## Atividades

Regra: agregação testável sem HTTP primeiro; **no máximo um endpoint novo por B***; índices só se faltarem. Entregar os T* na atividade indicada.

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Motor + índices** | `CalendarioAgregador` (plano aprovado, capacidade, prazos BRT, sobrecarga); validador de intervalo ≤ 31; Flyway de índices se necessário; **sem** controller; **T1–T4** |
| **B2** | **GET /calendario** | Controller + DTO + wiring JWT; dias contíguos; **T5, T6** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Proxy + rota + nav** | `/calendario` em Vite/Nginx/`API_PROXY.md`; rota autenticada; item no `AppShell` |
| **F2** | **Visão semana (lente Plano)** | Grade 7 dias, títulos de blocos, chip, sobrecarga; consome B2; **T7** |
| **F3** | **Lente Prazos + painel do dia** | Toggle Plano/Prazos; detalhe no calendário ao clicar; **T8** |
| **F4** | **Visão mês + navegação** | Indicadores leves; anterior/próximo; query de período; **T9** |

### Ordem

```text
B1 → B2
      ↘
       F1 → F2 → F3 → F4
```

`F1` pode em paralelo assim que o path existir no back (ou junto de B2). `F2`+ dependem de **B2**.
