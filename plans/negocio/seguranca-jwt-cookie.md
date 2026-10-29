# Feature — Segredo JWT e cookie de refresh

Issue: [#15](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/15)

Escopo: tirar segredo JWT do YAML versionado; exigir segredo externo fora de local; cookie de refresh `Secure` + `Path` restrito; claims `iss`/`aud` no access token.  
Fora deste slice: reuso de refresh / limpeza de tabela (#16); ProblemDetail (#14) além do necessário para testes.

Dependências: `usuario.md` (auth JWT + refresh cookie).

---

## Conceito

1. Segredo HS256 **não** vive no repositório como valor de produção.
2. Perfil não-local falha ao subir sem `APP_SECURITY_JWT_SECRET` com tamanho suficiente.
3. Cookie de refresh só vai para paths de sessão (`/auth/refresh`, `/auth/logout`).
4. Access token exige `iss` e `aud` corretos; token só com assinatura válida e `sub` não basta.

---

## Back-end

### Config

- Remover literal de segredo real de `application.yml` versionado.
- Dev local: valor apenas em perfil/arquivo **local** (não usado no deploy), ou default explícito só sob perfil `local`/`dev` documentado.
- Não-local: binding de `APP_SECURITY_JWT_SECRET`; validar comprimento mínimo HS256 na subida (fail-fast).
- `refresh-cookie.secure`: `true` fora de local; `path` restrito aos endpoints de refresh/logout; manter `HttpOnly` + `SameSite=Lax`.

### JwtService

- Ao emitir: incluir `iss` e `aud` (valores de config, ex. `app.security.jwt.issuer` / `audience`).
- Ao validar: recusar ausência ou valor errado de `iss`/`aud` (mesmo com assinatura correta).

### Front-end

- Sem mudança de fluxo se o cookie Path continuar alcançando `/auth/refresh` e `/auth/logout` via proxy.
- Confirmar que chamadas de refresh usam esses paths (já existentes).
- Sem prefixo novo.

---

## Decisões fechadas (revisão)

1. Nenhum segredo real em `application.yml` versionado.
2. Perfil não-local não sobe sem segredo de ambiente adequado.
3. Cookie: `Secure` fora de local; `Path` só sessão; `HttpOnly` + `SameSite=Lax`.
4. Access token com `iss`/`aud` obrigatórios na validação.
5. Issue #15.

---

## Testes automatizados

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Token com assinatura válida e `iss`/`aud` errados ou ausentes → rejeitado | B2 | Aceite da issue |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T2 | Login emite cookie com Path/Secure coerentes com o perfil de teste | B3 | Cookie restrito |
| T3 | Refresh/logout funcionam com o Path novo | B3 | Não quebrar sessão |

### Config

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | Contexto não-local sem secret → falha na subida (teste de property/validação) | B1 | Fail-fast |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Config + fail-fast** | Remover secret do YAML; env/perfil local; validação na subida; **T4** |
| **B2** | **iss/aud no JwtService** | Emitir e validar claims; **T1** |
| **B3** | **Cookie refresh** | Secure/Path; ITs de login/refresh/logout; **T2–T3** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Smoke sessão** | Confirmar refresh/logout ainda funcionam com Path restrito (manual ou teste existente) |

### Ordem

```text
B1 → B2 → B3 → F1
```
