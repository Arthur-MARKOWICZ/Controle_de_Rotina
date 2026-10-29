# Feature — Usuário (primeiro slice)

Escopo: autenticação mínima para o restante do produto.  
Fora do MVP deste slice: recuperação de senha, confirmação de e-mail, admin, listagem de usuários.

---

## Entity

| Campo       | Tipo     | Observação                                      |
|-------------|----------|-------------------------------------------------|
| id          | UUID     | PK                                              |
| email       | String   | único, normalizado (trim + lowercase)           |
| senhaHash   | String   | bcrypt (salt embutido no hash; sem salt separado) |
| nome        | String   |                                                 |
| createdAt   | Instant  |                                                 |
| updatedAt   | Instant  |                                                 |

Sem `saltHash`. Bcrypt já inclui o salt.

---

## Back-end

### Autenticação

- JWT de **access** (15 min) + **refresh** (5 dias).
- Refresh token em **cookie httpOnly**.
- **Logout** invalida/revoga o refresh (e limpa o cookie).
- Hash de senha: **bcrypt** (sem salt manual no banco).
- Cadastro e login: **públicos** (sem autenticação).
- Demais endpoints de usuário: autenticados; o usuário só acessa **a si** (`/me`).

### Endpoints

1. **POST cadastro**  
   - Service valida campos (email, senha forte, tamanhos).  
   - Proteção a SQL injection fica na persistência (JPA parametrizado), não como “filtro” no service.  
   - Repository salva no banco.  
   - Resposta: conta criada (ex.: 201). **Não** devolve JWT. Cliente vai para a tela de login.

2. **POST login**  
   - Busca usuário por email, compara bcrypt.  
   - Credenciais inválidas **sempre 401** (genérico), exista ou não o usuário. **Nunca 404** no login.  
   - Sucesso: devolve access JWT + seta refresh no cookie httpOnly.

3. **POST logout** (autenticado ou com refresh cookie)  
   - Revoga refresh e limpa cookie.

4. **POST refresh** (ou equivalente)  
   - Usa refresh do cookie; emite novo access (e, se houver rotação, novo refresh).

5. **GET /me**  
   - Devolve **nome** e **email** do usuário autenticado.

6. **PUT/PATCH /me** (atualizar)  
   - **Senha atual sempre obrigatória** para qualquer alteração (nome, email e/ou nova senha).  
   - Se trocar senha: campos nova senha + confirmação (confirmação pode ser só no front; no back validar força/tamanho da nova senha).  
   - Email permanece único.

### Regras compartilhadas

- Critérios de **senha forte** iguais no front e no back.
- Email único no banco (constraint).
- Rate limit / proteção a brute force no login: desejável neste slice ou logo em seguida (não deixar aberto).

---

## Front-end

1. **Cadastro** — formulário: nome, email, senha, confirmar senha.  
   Validação de email e senha forte. Sucesso → redireciona para **login**.

2. **Login** — email + senha.  
   Sucesso → guarda/usa access token conforme padrão do app; refresh fica no cookie.

3. **Atualizar dados** — carrega `/me` (nome, email).  
   Sempre pede **senha atual**.  
   Pode alterar nome, email e/ou senha (nova senha + confirmar).

4. **Logout** — chama endpoint de logout e limpa estado local do access token.

Rotas autenticadas dependem do access; em 401, tentar refresh via cookie; se falhar, ir para login.

---

## Decisões fechadas (revisão)

1. Acesso só a si (`/me`).
2. Login: sempre 401 genérico (sem 404).
3. Sem `saltHash`; só bcrypt em `senhaHash`.
4. Refresh em cookie httpOnly + logout com revogação.
5. Senha atual obrigatória em qualquer update.
6. Cadastro só cria conta; JWT só no login.
7. `createdAt` / `updatedAt` como `Instant`.
8. Recuperação de senha e confirmação de email: fora.
9. Este é o **primeiro slice** do projeto.

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Cadastro (modelo + API)** | Entidade, Flyway, email unique, DTOs/validações (email + senha forte), `POST` cadastro com bcrypt → 201 sem JWT |
| **B2** | **Login + sessão JWT** | `POST` login (401 genérico), access 15 min, refresh 5 dias em cookie httpOnly, `POST refresh`, `POST logout` com revogação |
| **B3** | **/me + segurança + testes** | Spring Security (públicos vs autenticados), `GET /me`, update `/me` com senha sempre obrigatória, testes do slice |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Telas de auth** | Cadastro e Login; validação; cadastro → login |
| **F2** | **Sessão e rotas** | Access token, refresh via cookie, rotas protegidas, logout |
| **F3** | **Atualizar perfil** | `/me`, senha atual obrigatória, nome/email/senha |

### Ordem

```text
B1 → B2 → B3
      ↘
       F1 → F2 → F3
```
