# Feature — Auth UX e onboarding do primeiro acesso (UI/UX · issue #13)

Escopo: login/cadastro com proposta de valor; checklist de senha em tempo real; mostrar/ocultar senha; perfil em duas seções; onboarding guiado até o primeiro plano.  
Fora deste slice: recuperação de senha / confirmação de e-mail (`usuario.md`); mudar regra de “senha atual obrigatória” no update (permanece); multi-step wizard pesado tipo produto SaaS.

Dependências: `usuario.md`, `capacidade.md`, `tarefa.md`, `planner.md`, `ui-design-system.md`, `ui-estados-feedback.md`, `ui-quick-add.md`. Issue: #13 · pai #1.

---

## Entities

Nenhuma.

---

## Back-end

Sem mudança de contrato obrigatória.

Onboarding detecta “primeiro acesso” no front com dados já existentes, por exemplo:

- capacidade semanal ainda não configurada (lista vazia / defaults zerados conforme API atual);
- nenhuma tarefa;
- nenhum plano do dia.

Não criar flag `onboardingCompleted` no banco neste slice (evita migration); checklist **dispensável** com `localStorage` (chave por `usuarioId`) para “não mostrar de novo”.

---

## Front-end

1. **Login/Cadastro** — uma frase de valor: “Diga o que precisa fazer; o sistema monta o plano”.
2. **Senha** — checklist em tempo real (cada regra marcada ao atender); botão mostrar/ocultar; mesmas regras de `isSenhaForte` no back.
3. **Perfil** — duas seções UI: **Dados** (nome/email) e **Alterar senha**; senha atual continua obrigatória em qualquer submit que altere dados (regra de `usuario.md`), mas a UI não mistura os campos num bloco só.
4. **Onboarding** — checklist na Home (ou overlay leve), dispensável:
   1. Definir capacidade semanal (link/`ui-capacidade-visual`);
   2. Criar primeira tarefa (quick add);
   3. Gerar primeiro plano.
5. Atualizar testes de Login/Cadastro/Perfil.

---

## Decisões fechadas (revisão)

1. Sem entity `onboarding`; dismiss em `localStorage` por usuário.
2. Senha atual obrigatória no update permanece (só UX do perfil muda).
3. Proposta de valor só nas telas públicas de auth.
4. Onboarding não bloqueia o app (dispensável).
5. Sem endpoints novos / sem proxy novo.

---

## Testes automatizados

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Checklist de senha marca regra ao digitar caractere que a satisfaz | F1 | Critério da issue |
| T2 | Toggle mostrar/ocultar senha altera `type` | F1 | Usabilidade |
| T3 | Perfil renderiza seções Dados e Alterar senha distintas | F2 | Critério |
| T4 | Onboarding aparece para usuário “novo” (mocks sem capacidade/tarefa) e some ao dispensar | F3 | Primeiro acesso |
| T5 | Suites Login/Cadastro/Perfil atualizadas e verdes | F1–F2 | Regressão |

---

## Atividades

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Login/Cadastro UX** | Copy de valor; checklist senha; mostrar senha; **T1–T2, T5 (auth)** |
| **F2** | **Perfil em duas seções** | Dados vs senha; **T3, T5 (perfil)** |
| **F3** | **Onboarding primeiro acesso** | Checklist Home + localStorage; links para capacidade/tarefa/plano; **T4** |

### Ordem

```text
F1 → F2 → F3
```
