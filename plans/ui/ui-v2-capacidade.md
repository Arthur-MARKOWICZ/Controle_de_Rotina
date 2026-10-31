# Feature — UI v2: Capacidade sem recarga total (5º slice do épico `ui-v2-epico.md`)

Status: **feito** (stepper de **1h**, alinhado ao `int horas` da API — desvio acordado vs. 0,5h do texto abaixo).

Escopo: `/capacidade` — grade semanal com stepper de horas, barra de salvar fixa quando há rascunho, exceções e reservas atualizadas localmente (sem recarregar as 5 fontes a cada ação), visual novo da grade.  
Fora deste slice: regras de capacidade/reserva no backend; tela própria de reservas; Calendário; ajuste “capacidade de hoje” da Home (já tratado em `ui-v2-hoje-fluida.md`).  
Dependências: `ui-v2-fundacao-visual.md`, `ui-v2-shell-nav.md` (`PageHeader`); `ui-capacidade-visual.md` e `ui-projetos.md` (feitos — semana visual, exceções, reservas por dia).

---

## Conceito

Hoje qualquer mudança (salvar semana, remover exceção, criar/atualizar reserva) incrementa `recarregarEm`, o que dispara de novo **as 5 leituras** da página (`listarCapacidadesSemanais`, `consultarCapacidadeResolvida`, `listarCapacidadesOverride`, `listarReservas`, `listarProjetos`) com estado de carregamento. Horas são digitadas em `<input type="number">` sem feedback de quanto o dia ficou.

No v2: mutações usam a resposta da API (os endpoints de criar/atualizar devolvem o recurso) e só a **capacidade resolvida** da semana é relida em silêncio, porque é derivada no servidor.

---

## Front-end

1. **Grade semanal** — 7 colunas (desktop) / lista vertical (mobile) com: dia + data, barra vertical de horas (já existe, restilizada com `--accent`), valor grande, **stepper** `−`/`+` de 0,5h (botões ≥ 44px, `aria-label` “Diminuir horas de segunda”) e o input numérico mantido para digitação direta; setas ↑/↓ no input ajustam 0,5h. Origem (“padrão”, “exceção”) como badge.
2. **Atalhos de preenchimento** — os botões existentes (preset dias úteis, copiar semana anterior) viram um grupo de ações secundárias com ícones no `PageHeader`.
3. **Barra de salvar fixa** — quando `sujo`, aparece barra fixa acima da bottom bar/rodapé: “2 dias alterados · Descartar · Salvar semana”; some ao salvar/descartar. `beforeunload` avisa ao fechar a aba com rascunho.
4. **Salvar semana** — `Promise.all` já existente; em sucesso atualiza a lista de semanais com as respostas, limpa rascunho, toast “Capacidade salva”, relê **só** `consultarCapacidadeResolvida` da semana em silêncio. Sem `setCarregando(true)`.
5. **Exceções (overrides)** — remover: item sai na hora + toast “Exceção removida · Desfazer” (já existe); erro restaura. Relê só a resolvida.
6. **Reservas** — criar/atualizar: linha mostra o novo valor na hora (pendente), substitui pela resposta; erro reverte + toast. Relê só a resolvida.
7. **Troca de semana** — navegação ‹ › com rótulo “3–9 nov”; mantém a grade visível esmaecida (`aria-busy`) até chegar a nova semana; guarda de corrida por id de requisição. Skeleton só na primeira carga.
8. **CTA de proposta** — o aviso existente (`mostrarCtaProposta`) vira banner com ícone e link para Hoje.

---

## Decisões fechadas (revisão)

1. Após mutação, relê apenas `consultarCapacidadeResolvida` da semana visível; demais listas são atualizadas com a resposta da API.
2. Stepper de 0,5h + input direto mantido.
3. Salvar semana continua explícito (barra fixa), sem autosave — evita disparar replanejamento a cada clique.
4. Aviso de saída só via `beforeunload` (o app usa `BrowserRouter`, sem `useBlocker`).
5. Sem endpoint novo.

---

## Testes automatizados

Harness: `CapacidadePage.test.tsx` (existente, adaptado).

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Stepper `+` em segunda soma 0,5h no rascunho e mostra a barra “1 dia alterado” | F1 | Interação nova |
| T2 | Descartar volta aos valores salvos e esconde a barra | F1 | Rascunho seguro |
| T3 | Salvar semana: chama criar/atualizar dos dias alterados e depois **só** `consultarCapacidadeResolvida` (as outras 4 leituras não são repetidas) | F2 | Fim da recarga total |
| T4 | Atualizar reserva mostra o novo valor antes da resposta; erro reverte e mostra toast | F3 | Otimismo + rollback |
| T5 | Remover exceção some na hora; “Desfazer” chama restaurar | F3 | Regressão do desfazer |
| T6 | Duas trocas rápidas de semana: só a última é aplicada | F4 | Corrida |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Grade + stepper + barra de salvar** | Visual da grade, stepper, setas, barra fixa, `beforeunload`; `PageHeader` com presets; **T1, T2** |
| **F2** | **Salvar sem recarga total** | Atualizar semanais com resposta; reler só resolvida; remover `recarregarEm` desse fluxo; **T3** |
| **F3** | **Exceções e reservas otimistas** | Remoção/desfazer e criar/atualizar reserva com rollback; **T4, T5** |
| **F4** | **Troca de semana fluida** | Grade esmaecida em recarga; guarda de corrida; banner de proposta; **T6** |

### Ordem

```text
F1 → F2 → F3
F1 → F4
```
