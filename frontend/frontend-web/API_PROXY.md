# Proxy da API (Vite + Nginx)

O front chama a API em paths relativos (`/tarefas`, `/planos`, …).  
No **dev** (Vite) e no **Docker** (Nginx) esses paths precisam ser **encaminhados ao backend**.  
Se faltarem, o servidor devolve o `index.html` da SPA e o browser quebra com:

`Unexpected token '<', "<!doctype "... is not valid JSON`

## Checklist obrigatório ao criar endpoint novo

Sempre que o backend ganhar um **prefixo de rota novo** (ex.: `/execucoes`, `/xp`):

1. Adicionar o prefixo em `vite.config.ts` → `server.proxy`
2. Adicionar o mesmo `location` em `nginx.conf`
3. Manter a lista abaixo sincronizada
4. **Rebuild** (não basta restart):
   - Dev Vite: parar e subir de novo `npm run dev`
   - Docker: `docker compose up -d --build frontend`  
     (`restart` / `up -d` **sem** `--build` mantém a imagem antiga e o problema continua)

> Neste projeto a porta **5173** costuma ser o **container Docker** (nginx), não o Vite.  
> Reiniciar o PC ou o container sem `--build` **não** aplica `nginx.conf` novo.

## Health / readiness (Actuator — não proxyar)

Probe operacional do backend: **`GET /actuator/health`** (e `/actuator/health/liveness`, `/actuator/health/readiness`).

- Consome **direto no backend** (`http://localhost:8080/actuator/health` ou rede interna do Compose/K8s).
- **Não** entra no proxy Vite/Nginx: orquestração e healthcheck não passam pelo front.
- Só `health` é exposto; demais endpoints Actuator (`/actuator/env`, etc.) exigem autenticação.
- Resposta inclui componente `db` (UP/DOWN) via `show-components=always` em `application.properties`; `show-details` fica `never` (sem URL JDBC / paths).
- Indicadores extras (disk, ssl, …) desligados; ficam `db` + estados de liveness/readiness.
- No Compose, o serviço `backend` tem `healthcheck` nesse path (imagem runtime com `curl`).

```bash
# Direto no backend — esperado: 200 e "status":"UP" com Postgres ok
curl -s "http://localhost:8080/actuator/health"
# Compose: status do serviço
docker compose ps backend
```

## Rate limiting (cadastro e login)

Proteção contra abuso em **`POST /usuarios`** e **`POST /auth/login`** (5 req/min por IP).  
`POST /auth/refresh` **não** é limitado.

| Camada | Onde | Como |
|--------|------|------|
| Nginx (edge) | `nginx.conf` | `limit_req_zone` `auth_abuse` + `limit_req` em `/usuarios` e `= /auth/login`; status `429` |
| Spring (API) | `app.rate-limit` em `application.yml` | Filtro por IP; cobre acesso direto à porta `8080` |

Propriedades do backend:

```yaml
app:
  rate-limit:
    enabled: true
    requests-per-minute: 5
```

Em testes automatizados o filtro fica `enabled: false` (`src/test/resources/application.yml`) para não quebrar ITs que compartilham `localhost`.

Após alterar `nginx.conf`: `docker compose up -d --build frontend`.

## Prefixos atuais (manter iguais nos dois arquivos)

| Prefixo         | Controllers / uso                          |
|-----------------|--------------------------------------------|
| `/usuarios`     | cadastro                                   |
| `/auth`         | login, refresh, logout                     |
| `/me`           | perfil                                     |
| `/tarefas`      | CRUD tarefas                               |
| `/capacidades`  | semanal, overrides, resolvida              |
| `/planos`       | planner (consulta, gerar, ajuste, aprovar) |
| `/execucoes`    | execução de blocos (listar, iniciar, concluir, abortar) |
| `/historico/dia`, `/historico/semana` | resumo de progresso (a tela SPA é `/historico` exato) |

Arquivos:

- Dev: `vite.config.ts`
- Docker: `nginx.conf` (copiado no `Dockerfile` para `/etc/nginx/conf.d/default.conf`)

## Como validar rápido

```bash
# Deve responder do backend (ex.: 401 sem token), NÃO HTML
curl -sI "http://localhost:5173/capacidades/resolvida?dataDe=2026-10-20&dataAte=2026-10-20" | head -5
curl -s "http://localhost:5173/capacidades/resolvida?dataDe=2026-10-20&dataAte=2026-10-20"
curl -sI "http://localhost:5173/planos?data=2026-10-20" | head -5
```

- `Server: nginx` + status `401`/`404`/`200` do Spring → proxy ok  
- Corpo `{"detail":"Não autenticado."}` sem token → esperado (precisa JWT da app)  
- Corpo começando com `<!doctype` → prefixo faltando ou front sem rebuild

## Não abra esses URLs como “página” no browser

`/capacidades/...`, `/planos`, `/tarefas`, etc. são **API**, não telas.  
Abrir no browser sem header `Authorization` → **401** (antes podia parecer página em branco).  
Use a UI logada (`/capacidade`, `/`) ou o DevTools → Network com a sessão ativa.

| Path | O que é |
|------|---------|
| `/capacidade` | Tela React (SPA) |
| `/capacidades/...` | API (proxy → backend) |
| `/historico` | Tela React (SPA) |
| `/historico/dia`, `/historico/semana` | API (proxy → backend) |

## Erro “API devolveu a página do front (SPA)”

Causas comuns:

1. **Cache do browser** de uma resposta HTML antiga (quando o proxy ainda faltava) — use **Ctrl+Shift+R** ou aba anônima.
2. Prefixo ausente no nginx/vite — rebuild: `docker compose up -d --build frontend`.
3. Backend fora do ar (antes era HTML 502; agora deve ser JSON).
