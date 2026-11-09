# Feature — Planner sob demanda (4º slice do épico `ui-tarefas-primeiro-epico.md`)

Escopo: tratar **Plano** (`/plano`) como destino explícito e complementar; CTAs a partir de Tarefas (e atalho leve em Projetos se couber sem escopo largo); copy/empty da agenda sem se apresentar como “casa” do app; onboarding alinhado a tarefas primeiro.  
Fora deste slice: regras/motor do planner; redesign profundo da timeline de blocos (`ui-v2-hoje-fluida` permanece); novos endpoints; mudar Capacidade além de link.  
Dependências: `ui-ia-nav-tarefas-primeiro.md` (`/plano` + nav); idealmente após `ui-criar-tarefa-titulo.md` (lista já é a casa).

---

## Conceito

Com a nav e a landing já em tarefas, falta o comportamento **4B**: o usuário **não** é empurrado ao plano no dia a dia; quando quiser organização automática, **pede** (CTA “Organizar meu dia” / ir a Plano).

A tela `/plano` continua com gerar / revisar / aprovar / executar — só muda o **papel** na jornada e a descoberta a partir da lista.

---

## Front-end

1. **CTA na lista de tarefas** (`TarefasPage` / `PageHeader` actions ou faixa discreta sob filtros):  
   - botão/link secundário **“Organizar meu dia”** → `/plano`;  
   - visível com tarefas pendentes; se lista vazia, o empty prioriza criar tarefa (já no slice anterior) e o CTA de plano fica secundário ou oculto.  
2. **Copy em `/plano`**  
   - títulos/subtítulos: plano como assistente (“Sugestão para hoje”, “Gere um plano a partir das suas tarefas”), não “sua home”;  
   - empty sem plano: CTA primário continua “Gerar plano…”, com secundário **“Voltar às tarefas”** → `/tarefas`.  
3. **Onboarding** (`OnboardingChecklist` / storage) — ordem dos passos: criar tarefa → (opcional) capacidade → gerar plano; links para `/tarefas` e `/plano` (não `/`). Não exigir abrir plano no primeiro paint pós-login.  
4. **Projetos (opcional fino)** — no hub `/projetos/:id` ou header da lista, link texto “Organizar no plano” → `/plano` **só se** já existir padrão de atalhos no hub; senão omitir neste slice (evitar escopo #46). **Decisão:** omitir em Projetos neste slice; só Tarefas + empty/onboarding do Plano.  
5. **Deep links / redirects legados** — qualquer bookmark copy “Hoje” restante aponta a `/plano`.  
6. Sem mudar APIs de `gerar` / `aprovar` / execução.

---

## Decisões fechadas (revisão)

1. Planner sob demanda = CTA a partir de Tarefas + destino `/plano`; não reabrir `/` como home.
2. Sem CTA novo obrigatório em Projetos neste slice.
3. Onboarding: tarefas antes de plano.
4. Sem backend.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Em `/tarefas` com lista não vazia (fixture), existe link/botão para `/plano` (“Organizar meu dia” ou nome fechado no copy) | F1 | Descoberta sob demanda |
| T2 | Empty de `/plano` oferece caminho de volta a `/tarefas` | F2 | Não prender no planner |
| T3 | Checklist/onboarding não aponta mais para `/` como Hoje; passos coerentes com tarefas primeiro | F3 | Pós-login |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **CTA “Organizar meu dia” em Tarefas** | Header/faixa; **T1** |
| **F2** | **Copy + empty de `/plano`** | Textos + Voltar às tarefas; **T2** |
| **F3** | **Onboarding / links legados** | Checklist e strings; **T3** |

### Ordem

```text
F1 → F2 → F3
```
