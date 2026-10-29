# Feature — Feedback de XP na UI (UI/UX · issue #4)

Escopo: fechar o restante de `xp.md` (F1–F2) + contratos HTTP ainda faltantes — enriquecer `POST .../concluir` e `GET /me` com progresso; toast “+N XP” / level-up; nível + barra no shell.  
Fora deste slice: streaks, heatmap, ranking, RPG; histórico agregado (`historico.md` / `ui-historico-grafico.md`).

Dependências: `xp.md` (ledger + concessão no concluir já existem; UI e enrich de response/`/me` pendentes), `ui-layout-nav.md` (slot), `ui-estados-feedback.md` (toast). Issue: #4 · pai #1.

---

## Entities

Nenhuma nova. Reutiliza `Usuario.xpTotal` / `nivel` e ledger de `xp.md`.

---

## Back-end

Completar o que `xp.md` já especificou e ainda não está no contrato HTTP:

1. **POST /execucoes/{id}/concluir** — resposta inclui objeto `xp` com `ganhoNesteRequest`, `eventos[]`, `progresso` (`xpTotal`, `nivel`, `xpNoNivel`, `xpParaProximoNivel`) — ver `plans/negocio/xp.md`.
2. **GET /me** — incluir os mesmos campos de `progresso` (usuário novo: 0 / nível 1).

Sem prefixo `/xp`. Sem cache.

---

## Front-end

1. **Header/shell** — “Nível N” + barra `xpNoNivel / xpParaProximoNivel` no slot do `AppShell`; carregar via `/me`; atualizar com `xp.progresso` após concluir (sem depender só de refetch).
2. **Toast ao concluir** — se `ganhoNesteRequest > 0`: “+N XP” (e indicação de bônus se houver evento `TAREFA_FINALIZADA`); se o nível aumentou vs estado anterior: toast “Nível N+1!”.
3. **Dia completo** — quando todos os blocos do dia estiverem concluídos, estado no card do plano (ex.: “Plano de hoje concluído · +X XP” com soma do dia se já disponível via histórico/local; senão só mensagem de dia completo sem inventar total).
4. **Acessibilidade** — toast `role="status"`; animações respeitam `prefers-reduced-motion`.

---

## Decisões fechadas (revisão)

1. Este slice **fecha** F1–F2 de `xp.md` e as atividades B de enrich ainda abertas (`/me` + response do concluir).
2. Sem endpoint novo de XP; só enriquecer contratos existentes.
3. Level-up detectado no front comparando `nivel` antes/depois.
4. Streaks / sequência de dias: fora.
5. Gamificação leve (visão §8): sem personagem/moedas.

---

## Testes automatizados

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Mapper/DTO de progresso a partir do usuário (nível 1, xp 0) | B2 | Contrato `/me` |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T2 | `POST .../concluir` 200 com `xp.ganhoNesteRequest` / `eventos` / `progresso` | B1 | Feedback imediato |
| T3 | `GET /me` com `xpTotal`, `nivel`, `xpNoNivel`, `xpParaProximoNivel` | B2 | Header |
| T4 | Sem token → 401 | B1/B2 | Auth |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T5 | Após concluir com ganho > 0, UI mostra “+N XP” | F1 | Motivação |
| T6 | Shell reflete nível/barra a partir de `/me` e atualiza pós-concluir | F2 | Progressão visível |
| T7 | Subida de nível dispara feedback distinto | F1 | Critério da issue |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Enriquecer concluir** | Incluir `xp` na response de `POST /execucoes/{id}/concluir` usando resultado de `XpConcessaoService`; **T2, T4** |
| **B2** | **Enriquecer GET /me** | Estender `UsuarioResponse` + service; **T1, T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Toast +XP / level-up** | Ler `xp` do concluir; toasts; reduced-motion; **T5, T7** |
| **F2** | **Barra no shell** | Consumir `/me` + patch local; estado “dia completo” mínimo; **T6** |

### Ordem

```text
B1 → B2
 ↘
  F1 → F2
```

`F1` após **B1**. `F2` após **B2** (pode usar progresso só do concluir temporariamente, mas aceite exige `/me` no shell em todas as páginas).
