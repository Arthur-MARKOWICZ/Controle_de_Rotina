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

## TypeScript: mocks do Vitest (`vi.mocked`)

`npm run build` = `tsc -b && vite build`. O `tsconfig.app.json` inclui `src/`, então **os `*.test.ts` / `*.test.tsx` entram no typecheck**. O `npm test` (Vitest) passa; o Docker (`RUN npm run build`) falha.

Erro típico:

```
Property 'mock' does not exist on type '(filtro?: ...) => Promise<...>'.
Binding element 'filtro' implicitly has an 'any' type.
```

`vi.mock('../api/tarefaApi', ...)` substitui a implementação em runtime, **mas não muda o tipo TypeScript** do export. Por isso `.mock` no módulo importado não existe para o `tsc`.

**Errado:**

```ts
expect(tarefaApi.listarTarefas.mock.calls.some(([filtro]) => filtro?.parentId === 'x')).toBe(false)
```

**Certo** (padrão já usado no resto da suíte):

```ts
expect(
  vi.mocked(tarefaApi.listarTarefas).mock.calls.some(([filtro]) => filtro?.parentId === 'x'),
).toBe(false)
```

Mesma regra para `mock.calls`, `mock.results`, `mockResolvedValue`, `mockImplementation`, `mockReset`.

Depois de alterar teste do front, rode `npm run build` em `frontend/frontend-web` **antes** do `docker compose ... --build frontend`.
