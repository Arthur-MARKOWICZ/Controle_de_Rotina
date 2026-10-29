# Feature — Estados, toasts e confirmação (UI/UX · issue #11)

Escopo: componentes reutilizáveis de toast, skeleton e alerta inline; regra desfazer vs confirmar; diálogo acessível para ações irreversíveis; unificar feedback de erro/sucesso.  
Fora deste slice: animações de XP (`ui-xp-feedback.md`); desfazer exclusão específico da lista de tarefas além do contrato genérico do toast (`ui-lista-tarefas.md` consome o toast); libs externas de UI.

Dependências: `ui-layout-nav.md` (montar provider/host no shell); `ui-design-system.md` (tokens/botões). Issue: #11 · pai #1.

---

## Entities

Nenhuma.

---

## Back-end

Sem endpoints novos. Consome restore/delete já existentes quando o toast “Desfazer” for ligado nas telas consumidoras.

---

## Front-end

1. **ToastProvider** — host no `AppShell`; API `toast.success|error|info` com ação opcional (“Desfazer”); `role="status"` (ou `alert` para erro crítico); dispensável.
2. **Skeleton** — variantes mínimas: lista de blocos (Home), lista de tarefas, grade de capacidade; substituir texto “Carregando…” que causa salto de layout.
3. **Alerta inline** — um componente para erro de página (substituir `plano-dia-erro` / `auth-error` / `form-error` divergentes).
4. **Regra de produto**  
   - Reversível → toast com Desfazer (ex.: soft delete de tarefa, remover override).  
   - Irreversível na UI → `<dialog>` nativo com confirmação (abortar execução; aprovar plano — também reforçado em `ui-revisao-rascunho.md`).
5. **Dialog** — foco preso, `Esc` fecha, botões explícitos Confirmar/Cancelar; classes do design system.
6. **Botões disabled** — `cursor: wait` só enquanto loading real; não no disabled estático.
7. **Sem dependências novas** — React + CSS.

Aplicar nas telas principais neste slice o mínimo: host global + 1–2 consumos de exemplo (ex.: erro de Home + confirmação de abortar). Demais telas migram ao tocar cada slice de domínio, **mas** a regra “nada destrutivo sem confirm/desfazer” deve ficar documentada e coberta onde a ação já existe.

---

## Decisões fechadas (revisão)

1. Toast único no layout; sem libs (sonner/react-hot-toast etc.).
2. Reversível = desfazer; irreversível = diálogo.
3. Aprovar plano e abortar execução = confirmação (irreversíveis na UX atual).
4. Skeletons só para as três superfícies listadas neste MVP de feedback.
5. `prefers-reduced-motion` respeitado se houver animação de entrada do toast.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `toast` com ação Desfazer dispara callback ao clicar | F1 | Contrato do desfazer |
| T2 | Dialog de confirmação: Confirmar chama ação; Cancelar/Esc não chama | F2 | Ações destrutivas |
| T3 | Abortar execução (ou fluxo já testado na Home) passa a exigir confirmação | F3 | Critério da issue |
| T4 | Estado de loading da Home (ou lista) usa skeleton em vez de só texto, sem quebrar testes de fluxo | F3 | Layout estável |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Toast + provider** | API, host no shell, acessibilidade, dismiss; **T1** |
| **F2** | **Dialog de confirmação** | `<dialog>` acessível + helper/hook; **T2** |
| **F3** | **Skeleton + alerta + wire ações críticas** | Skeletons; alerta inline; abortar (e excluir se já na tela) com confirm/desfazer; cursor loading; **T3–T4** |

### Ordem

```text
F1 → F2 → F3
```
