# Feature — Testes de XP, JWT e Clock sem Testcontainers

Issue: [#18](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/18)

Escopo: testes unitários de `XpMotor` e `JwtService` sem Docker; injetar `Clock` em `UsuarioService` (cadastro/`atualizarMe`) em vez de `Instant.now()`.  
Fora deste slice: reescrever ITs existentes; mudar fórmulas de XP (#28); claims `iss`/`aud` além do que #15 já exigir (reusar Clock/`JwtService` alinhado).

Dependências: código atual de `xp`, `usuario`/JWT; idealmente após `seguranca-jwt-cookie.md` se `iss`/`aud` já existirem (senão testar o contrato vigente).

---

## Conceito

1. Núcleo puro (motor XP, JWT) testa sem subir Postgres.
2. Relógio de negócio/aplicação é o `Clock` já usado em `SecurityConfig` — `UsuarioService` não chama `Instant.now()`.
3. ITs com Testcontainers permanecem para HTTP/persistência; este slice só adiciona unitários.

---

## Back-end

### XpMotor (unitário)

- Pesos das três prioridades.
- XP do bloco e bônus da tarefa (fórmulas **vigentes** no código no momento do slice; se #28 ainda não rodou, cobrir `minutos × peso` e bônus 10%).
- Curva `10 * nivel`: limites (0 XP, exatamente o custo do nível, um XP acima) — evita off-by-one.

### JwtService (unitário + Clock fixo)

- Token válido.
- Expirado.
- Assinatura trocada.
- Subject vazio / malformado.
- (Se #15 feito) `iss`/`aud` inválidos.

### UsuarioService

- Injetar `Clock`; `created_at`/`updated_at` via `clock.instant()` no cadastro e `atualizarMe`.
- Teste unitário ou de serviço com Clock fixo prova o instante gravado.

### Front-end

- Nenhuma mudança.

---

## Decisões fechadas (revisão)

1. Unitários sem Testcontainers/Docker.
2. `UsuarioService` usa `Clock` da aplicação.
3. ITs HTTP atuais permanecem.
4. Issue #18.

---

## Testes automatizados

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | `XpMotor`: 3 prioridades + limites de nível (0, custo exato, +1) | B1 | Aceite XP |
| T2 | `JwtService` + Clock: válido, expirado, assinatura inválida, sub vazio/malformado | B2 | Aceite JWT |
| T3 | `UsuarioService` com Clock fixo: `createdAt`/`updatedAt` = `clock.instant()`; sem `Instant.now()` no service | B3 | Aceite Clock |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Testes XpMotor** | **T1** |
| **B2** | **Testes JwtService** | **T2** |
| **B3** | **Clock no UsuarioService** | Injeção + testes; **T3** |

### Ordem

```text
B1 → B2 → B3
```
