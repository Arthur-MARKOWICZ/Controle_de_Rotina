# frontend-web

React + TypeScript + Vite.

## Dev

```bash
npm install
npm run dev
```

API no backend (`localhost:8080`) via proxy do Vite. Ver **[API_PROXY.md](./API_PROXY.md)**.

## Docker

O `nginx.conf` faz o mesmo proxy. Após mudar prefixos da API:

```bash
docker compose up -d --build frontend
```

## Scripts

- `npm run test` — Vitest
- `npm run build` — build de produção
