# Feature — UI hábito no calendário e CTAs sem plano

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43) (UI)

Escopo: lente **Hábitos** no calendário; hábitos sempre no **painel do dia**; CTAs **Iniciar / Concluir / Abortar** e **Concluir direto**; Home mostra hábitos de hoje mesmo sem plano `APROVADO`; estados de cumprimento/streak; alinhado ao design system v2.  
Fora deste slice: endpoints novos além de `habito-sem-plano.md`; DnD; edição de hábito dentro do calendário; redesign geral da shell.

Dependências: `plans/negocio/habito-sem-plano.md`; `calendario/calendario.md` (página existente); `ui-habitos.md` / Home execução; `ui-estados-feedback`.

---

## Entities

Nenhuma. Consumir `GET /calendario` (`habitosDoDia`), `GET /habitos/do-dia`, `POST /habitos/{id}/iniciar`, `POST /habitos/{id}/concluir-direto`, `POST /execucoes/...`.

---

## Back-end

Sem endpoints novos neste arquivo.

---

## Front-end

1. **Lentes do calendário** — toggle **Plano | Prazos | Hábitos** (query `lente=habitos` no deep-link interno).
2. **Lente Hábitos (semana)** — células listam títulos dos `habitosDoDia` (+ duração ou horário preferencial curto se couber); marcador visual de `cumprido` (não só cor).
3. **Lente Hábitos (mês)** — indicador leve “tem hábito” / “todos cumpridos” por dia (contagem ou ponto); sem listar todos os títulos na célula.
4. **Painel do dia** — **sempre** seção “Hábitos” com a lista `habitosDoDia`, **independente** da lente ativa (Plano/Prazos/Hábitos). Plano aprovado continua na seção de blocos quando a lente/contexto for plano.
5. **Card / linha do hábito no painel** — título, duração, horário preferencial (dica), streak atual, badges `cumprido` / `no plano` se `noPlanoAprovado`.
6. **CTAs (slot avulso)**  
   - Não iniciado / abortado: **Iniciar** + **Concluir direto**.  
   - Em andamento: **Concluir** + **Abortar** (timer/restante como bloco da Home).  
   - Concluído (avulso): só estado; sem reabrir.  
   - Se existir `execucaoNoPlano` em andamento/não iniciado: link/atalho discreto “No plano de hoje” (reusa fluxo da Home) — não esconder o slot avulso (coexistência).
7. **Home** — sem plano `APROVADO`: bloco/lista “Hábitos de hoje” via `GET /habitos/do-dia` com os mesmos CTAs. Com aprovado: hábitos do plano seguem nos cards de bloco; seção avulsa só para hábitos do weekday **ainda sem** bloco no aprovado **ou** sempre listar avulsos com badge (preferir: listar todos do dia com estado avulso + indicação se já está no plano, para não sumir o Concluir direto).
8. **Feedback** — toast `+N XP` ao concluir / concluir-direto; erro 409 com mensagem clara (“já concluído hoje” / “já em andamento”); skeleton no painel.
9. **A11y** — nomes de botão com título do hábito; lente anunciada; cumprimento não só por cor.
10. Viewport estreita usável; tokens v2; cards só onde há interação.

---

## Decisões fechadas (revisão)

1. Lente **Hábitos** além de Plano e Prazos.
2. Painel do dia **sempre** mostra hábitos (`habitosDoDia`), qualquer lente.
3. CTAs avulsos = timer completo **e** concluir direto.
4. Coexistência com bloco no plano visível (badge / atalho), sem fundir num único botão ambíguo.
5. Home sem aprovado ainda executa hábito.
6. Issue #43 (UI).

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Lente Hábitos na semana lista título vindo de `habitosDoDia` sem `plano` | F1 | Aceite calendário |
| T2 | Painel do dia mostra seção hábitos com lente Plano ativa | F1 | Sempre no detalhe |
| T3 | Concluir direto e Iniciar disparam APIs corretas e atualizam UI | F2 | CTAs |
| T4 | Home sem aprovado renderiza hábitos do dia com CTA iniciar | F3 | Sem plano |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Calendário: lente + células + painel** | Toggle Hábitos; semana/mês; seção fixa no painel; **T1, T2** |
| **F2** | **CTAs no painel do calendário** | Iniciar / concluir / abortar / concluir-direto + toasts; **T3** |
| **F3** | **Home: hábitos sem aprovado** | `GET /habitos/do-dia` + CTAs; **T4** |

### Ordem

```text
F1 → F2 → F3
```

`F1` após B7 de `habito-sem-plano.md`; `F2`/`F3` após B4/B5/B8.
