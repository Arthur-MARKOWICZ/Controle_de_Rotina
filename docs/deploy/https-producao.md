# Deploy com HTTPS em VPS compartilhada

Publica o **Controle de Rotina** em um subdomínio próprio, sem alterar o
`controle-gastos` já em produção.

Isolamento na VPS:

| Recurso | controle-gastos | controle-rotina |
| --- | --- | --- |
| Pasta | `/srv/controle-gastos` | `/srv/controle-rotina` |
| Compose project | `controle-gastos` | `controle-rotina` |
| Postgres host | `127.0.0.1:5432` | `127.0.0.1:5433` |
| Backend host | `127.0.0.1:8080` | `127.0.0.1:8081` |
| Frontend host | `127.0.0.1:3000` | `127.0.0.1:9080` |
| Nginx site | `controle-gastos.conf` | `controle-rotina.conf` |

Substitua `<DOMINIO>` (ex.: `rotina.arthurmlopes.systems`) e `<EMAIL>`.

## 1. DNS e firewall

Crie um registro DNS `A` de `<DOMINIO>` apontando para `103.199.187.185`.
Confirme:

```bash
dig +short A <DOMINIO>
```

Firewall Hostinger (já usado pelo gastos): TCP `22`, `80`, `443`.
Não abra `5433`, `8081` nem `9080` para a internet.

## 2. GitHub Environment `production`

Em **Settings → Environments → production**:

| Nome | Tipo | Valor |
| --- | --- | --- |
| `PUBLIC_APP_URL` | Variable | `https://<DOMINIO>` |
| `DEPLOY_HOST` | Secret | `103.199.187.185` |
| `DEPLOY_PORT` | Secret | `22` |
| `DEPLOY_USER` | Secret | `deploy` |
| `DEPLOY_SSH_PRIVATE_KEY` | Secret | chave privada exclusiva da Action |
| `DEPLOY_KNOWN_HOSTS` | Secret | saída de `ssh-keyscan -p 22 103.199.187.185` |
| `POSTGRES_DB` | Secret | `controle_rotina` |
| `POSTGRES_USER` | Secret | `controle` |
| `POSTGRES_PASSWORD` | Secret | senha aleatória |
| `APP_SECURITY_JWT_SECRET` | Secret | ≥ 32 bytes aleatórios |

Gere segredos:

```bash
openssl rand -base64 48   # POSTGRES_PASSWORD
openssl rand -base64 48   # APP_SECURITY_JWT_SECRET
```

Pode reutilizar `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`,
`DEPLOY_SSH_PRIVATE_KEY` e `DEPLOY_KNOWN_HOSTS` do repositório
`controle_gastos`, se o mesmo usuário `deploy` já tem acesso à VPS.
Ainda assim é preciso cadastrá-los neste repositório.

Na VPS, o usuário `deploy` precisa de sudo NOPASSWD para criar
`/srv/controle-rotina` (mesmo padrão do gastos).

## 3. Deploy dos containers

Push em `main` ou **Actions → Validar e implantar aplicação**.

```bash
docker compose --project-name controle-rotina -f /srv/controle-rotina/compose.yaml ps
curl --fail http://127.0.0.1:8081/actuator/health/readiness
curl --fail http://127.0.0.1:9080/
```

## 4. Nginx + Let's Encrypt (uma vez)

Copie `infra/nginx/` para a VPS e execute:

```bash
sudo ./install-https.sh <DOMINIO> <EMAIL>
```

O script só cria/atualiza `controle-rotina.conf`. O site do gastos permanece intacto.

## 5. Verificação

```bash
curl -fsS -o /dev/null -w "%{http_code}\n" https://<DOMINIO>/
curl -fsS -o /dev/null -w "%{http_code}\n" https://gastos.arthurmlopes.systems/
```

Os dois devem responder `200`.
