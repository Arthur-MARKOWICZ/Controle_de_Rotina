# Feature — Link opcional para GitHub Issue/PR (Projeto e Tarefa)

Issue: [#40](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/40) (parte GitHub)

Escopo: campo opcional de URL GitHub em **Projeto** e em **Tarefa**; validar formato; persistir e devolver na API; UI abre em nova aba.  
Fora deste slice: OAuth GitHub; sync de labels/status; fechar issue automaticamente; credencial no backend; webhooks; reserva de tempo / CRUD de projeto (já em `projetos.md`).

Dependências: `projetos/projetos.md` (entity Projeto + `projetoId` na tarefa).

---

## Conceito

1. Link é **só referência** para o usuário acompanhar entrega técnica — sem integração de API.
2. Pode existir no projeto (contexto geral) **e** na tarefa (issue específica). Independentes.
3. Vazio/`null` = sem link. Limpar no PATCH com `null` ou string vazia → gravar `null`.

---

## Entity / Entities

### Projeto (extensão)

| Campo           | Tipo   | Observação |
|-----------------|--------|------------|
| githubIssueUrl  | String | nullable; max 500 |

### Tarefa (extensão)

| Campo           | Tipo   | Observação |
|-----------------|--------|------------|
| githubIssueUrl  | String | nullable; max 500 |

### Validação de URL (create/PATCH quando não nulo)

Aceitar apenas HTTPS apontando para GitHub, por exemplo:

- `https://github.com/{owner}/{repo}/issues/{n}`
- `https://github.com/{owner}/{repo}/pull/{n}`

Rejeitar (`400` + Problem Detail): http, host ≠ `github.com` (e `www.github.com` normalizado ou rejeitado de forma consistente — **decisão:** aceitar `github.com` e `www.github.com`, normalizar gravando sem `www.` se trivial; senão aceitar ambos), path que não seja issue/PR, espaços, javascript:, etc.

Sem checagem se a issue existe (sem token GitHub).

---

## Back-end

Sem prefixo novo. Estender contratos já existentes:

1. **POST/PATCH /projetos** (+ response GET) — campo `githubIssueUrl` opcional.
2. **POST/PATCH /tarefas** (+ response GET) — campo `githubIssueUrl` opcional.
3. Flyway: colunas nullable nas duas tabelas.
4. Listagens já existentes devolvem o campo quando preenchido.

Auth/escopo inalterados.

---

## Front-end

1. Formulário de projeto e de tarefa: input opcional “Link GitHub (issue/PR)”.
2. Detalhe/lista/card: se houver URL, link externo (`target="_blank"`, `rel="noopener noreferrer"`).
3. Mensagem de erro de validação alinhada ao Problem Detail.
4. Sem proxy novo.

Detalhe visual pode ser absorvido por `ui-projetos.md` se ainda não implementado; senão atividade F* aqui.

---

## Decisões fechadas (revisão)

1. Campo nos **dois**: Projeto e Tarefa.
2. Só URL; sem OAuth/sync/credencial.
3. Validação de formato issue/PR HTTPS GitHub; sem verificar existência remota.
4. Slice separado de `projetos.md`.
5. Issue #40 (parte GitHub).

---

## Testes automatizados

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Validador: issue/PR HTTPS ok; http / host estranho / path inválido → rejeita | B1 | Aceite “link válido” |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T2 | PATCH projeto e tarefa grava e devolve `githubIssueUrl`; `null` limpa | B2 | Persistência |
| T3 | Body com URL inválida → 400 | B2 | Contrato |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | Com URL, âncora abre com `target="_blank"` (ou atributo equivalente no componente) | F1 | Aceite UX |

Se não houver harness front: T4 manual até existir.

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Colunas + validador** | Flyway Projeto/Tarefa; validador puro; **T1**; sem endpoint novo isolado |
| **B2** | **Contrato HTTP** | Aceitar/devolver/limpar nos POST/PATCH/GET de projeto e tarefa; **T2–T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Campo + link** | Inputs nos forms; exibir link externo; **T4** |

### Ordem

```text
B1 → B2 → F1
```
)
