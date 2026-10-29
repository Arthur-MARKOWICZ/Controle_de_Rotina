# Feature — Reuso de refresh token e limpeza

Issue: [#16](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/16)

Escopo: invalidar família de refresh no reuso de token já rotacionado; login timing-safe para e-mail inexistente; limpeza de tokens expirados/revogados antigos.  
Fora deste slice: segredo JWT / Path do cookie (#15); concorrência genérica (#17).

Dependências: `usuario.md`; idealmente após `seguranca-jwt-cookie.md` (#15).

---

## Conceito

1. Refresh já revogado reapresentado = possível roubo → revogar **todos** os refresh daquele usuário; exigir login de novo.
2. O refresh emitido na rotação “roubada” deixa de funcionar junto com a família.
3. Login: tempo de resposta não distingue e-mail inexistente de senha errada (sempre passa pelo encoder contra hash real ou fictício); resposta 401 genérico.
4. Tabela `refresh_token` não cresce para sempre: apagar expirados/revogados além de janela curta (job na rotação ou scheduler dedicado). Sem cache.

---

## Back-end

### RefreshTokenService.rotacionar

- Se o token apresentado está **revogado** (ou política de reuso da família): revogar todos os refresh ativos do usuário; lançar credenciais inválidas (401).
- Fluxo feliz inalterado: revoga o apresentado, emite novo.

### AuthService.login

- Se e-mail não existe: ainda assim `passwordEncoder.matches(senha, HASH_FICTICIO)` (constante interna); depois 401.
- Se existe e senha errada: matches no hash real → 401 idêntico.

### Limpeza

- Na própria rotação e/ou job: delete de linhas com `expires_at` / `revoked_at` anteriores a janela (ex. 7 dias). Índice novo só se a query de limpeza exigir (avaliar `expires_at`/`revoked_at`); `idx_refresh_token_usuario` permanece para revogar família.

### Front-end

- Em 401 de refresh após reuso: fluxo já deve mandar para login (`ui-auth-onboarding`). Garantir mensagem genérica, sem “token reutilizado”.
- Sem prefixo novo.

---

## Decisões fechadas (revisão)

1. Reuso de refresh revogado → invalida família do usuário.
2. Login timing-safe com hash fictício; 401 idêntico.
3. Limpeza de tokens velhos (expirados/revogados).
4. Sem cache; issue #16.

---

## Testes automatizados

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Rotacionar, reapresentar o refresh antigo → 401 e família revogada; novo token da rotação roubada também falha | B1 | Aceite reuso |
| T2 | Login e-mail inexistente vs senha errada → mesmo 401 (status + corpo genérico) | B2 | Timing/enumeração |
| T3 | Após limpeza, tokens expirados/revogados antigos não permanecem | B3 | Aceite limpeza |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Detecção de reuso** | Revogar família; **T1** |
| **B2** | **Login timing-safe** | Hash fictício; **T2** |
| **B3** | **Limpeza** | Delete na rotação e/ou job; **T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Sessão inválida** | Confirmar redirect para login quando refresh falha após reuso |

### Ordem

```text
B1 → B2 → B3 → F1
```
