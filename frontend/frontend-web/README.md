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

## TypeScript: `null` da API ≠ `undefined`

Campos opcionais do backend (ex.: `prazo`) chegam como JSON `null` e nos tipos do front ficam `string | null`.

Em props/funções React, `campo?: string` significa `string | undefined` — **não aceita `null`**. O `tsc` falha no `npm run build` (Docker incluso) com:

`Type 'string | null' is not assignable to type 'string' | 'string | undefined'`

**Como evitar:**

1. Ao tipar prop/helper que recebe dado da API, use `string | null` (ou `string | null | undefined` se também for omitível).
2. Ou normalize na borda: `valor ?? undefined` / `valor ?? ''` antes de passar adiante.
3. Rode `npm run build` localmente antes do Docker — o Vite/dev não aplica o mesmo rigor do `tsc -b`.
