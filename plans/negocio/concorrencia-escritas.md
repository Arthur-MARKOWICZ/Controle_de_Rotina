# Feature — Cadastro, plano, execução e XP sem corrida

Issue: [#17](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/17)

Escopo: tratar corridas check-then-act em cadastro/e-mail, geração de plano, início de execução e concessão de XP; 409 (ou retry idempotente) em vez de 500; lock/`@Version` no saldo XP do usuário.  
Fora deste slice: advisory lock do job diário (#19); limpeza de refresh (#16). Sem cache.

Dependências: `api-problem-detail.md` (#14) para 409 consistente; slices usuario/planner/execução/xp.

---

## Conceito

1. Uniques do banco são a última barreira; a aplicação traduz violação em conflito de domínio (409) ou retry quando a operação é idempotente.
2. Início de execução já existente e XP já concedido: comportamento idempotente/conflito explícito — sem linha duplicada e sem 500.
3. Concessão de XP: travar a linha do `Usuario` (`PESSIMISTIC_WRITE` ou `@Version`) antes de somar `xp_total`/`nivel`.
4. Geração de plano: próxima `versao` na mesma transação de forma que duas chamadas não gravem o mesmo `(usuario_id, data, versao)`; se já houver aprovado, manter `PlanoJaAprovadoException`.

---

## Back-end

### UsuarioService

- `cadastrar` / troca de e-mail: capturar unique `uk_usuario_email` → `EmailJaCadastradoException` (409). Sem retry de insert duplicado.

### PlannerGeracaoService.gerar

- Calcular `max(versao)+1` e persistir na mesma transação; em unique `uk_plano_usuario_data_versao` → 409 ou retry controlado **uma** vez buscando novo max (escolher uma estratégia e documentar no código). Nunca 500.
- Plano aprovado existente: regra atual (`PlanoJaAprovadoException`) intacta.

### ExecucaoService.iniciar

- Corrida em `findByBlocoPlanoId` + `save`: unique `uk_execucao_bloco_plano` → tratar como execução já existente (conflito ou retornar estado coerente sem duplicar). Dois inícios → um sucesso e um conflito de domínio.

### XpConcessaoService

- Antes de somar progresso: lock pessimista ou `@Version` em `Usuario`.
- Unique `uk_xp_evento_tipo_origem`: segunda concessão → não duplica evento; não perde soma (lock garante).

### Front-end

- Tratar 409 nos fluxos de cadastro, gerar plano, iniciar bloco (mensagem já via ProblemDetail se #14 feito).
- Sem prefixo novo.

---

## Decisões fechadas (revisão)

1. Uniques → 409 / retry idempotente; nunca 500 nesses fluxos.
2. XP: lock de linha ou `@Version` no usuário ao atualizar progresso.
3. Geração: versões distintas ou 409 explícito sob corrida.
4. Sem cache; issue #17.

---

## Testes automatizados

### Integração / API (ou serviço com threads)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Dois cadastros simultâneos mesmo e-mail → 201 e 409 | B1 | Aceite |
| T2 | Dois inícios do mesmo bloco → um ok, outro conflito; uma linha | B2 | Aceite execução |
| T3 | Duas concessões XP do mesmo bloco → um evento; `xp_total` correto | B3 | Aceite XP (obrigatório) |
| T4 | Duas gerações simultâneas do dia → versões distintas ou 409; nunca 500 | B4 | Aceite plano |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Cadastro/e-mail** | Unique → 409; **T1** |
| **B2** | **Iniciar execução** | Corrida no unique do bloco; **T2** |
| **B3** | **XP com lock** | Pessimistic/`@Version` + unique; **T3** |
| **B4** | **Geração de plano** | Versão na TX + tratamento unique; **T4** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Mensagens 409** | Cadastro / iniciar / gerar plano |

### Ordem

```text
B1 → B2 → B3 → B4 → F1
```
