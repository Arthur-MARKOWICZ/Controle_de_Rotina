import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it, vi } from "vitest";
import {
  ApiError,
  AuthRequiredError,
  ContaExcluidaError,
  ControleRotinaApiClient,
  TITULO_CONTA_EXCLUIDA,
} from "./api-client.js";
import { createMcpServer, errorResult, MCP_TOOL_NAMES } from "./server.js";
import { MemorySessionStore } from "./session-store.js";

function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ status, title, detail }), {
    status,
    headers: { "Content-Type": "application/problem+json" },
  });
}

function clientWithSession(fetchImpl: typeof fetch) {
  const store = new MemorySessionStore({
    accessToken: "access",
    refreshToken: "refresh-secret",
    refreshCookieName: "refresh_token",
    apiBaseUrl: "http://localhost:8080",
  });
  return {
    store,
    client: new ControleRotinaApiClient({ apiBaseUrl: "http://localhost:8080", sessionStore: store, fetchImpl }),
  };
}

describe("conta excluída no MCP", () => {
  it("401 Conta excluída: não chama refresh, limpa a sessão e lança ContaExcluidaError", async () => {
    const fetchImpl = vi.fn().mockResolvedValueOnce(problem(401, TITULO_CONTA_EXCLUIDA, "Esta conta foi excluída."));
    const { client, store } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(ContaExcluidaError);

    expect(fetchImpl).toHaveBeenCalledOnce();
    expect(store.load()).toBeNull();
  });

  it("chamada seguinte após conta excluída pede login (sessão já apagada), sem ir à API", async () => {
    const fetchImpl = vi.fn().mockResolvedValueOnce(problem(401, TITULO_CONTA_EXCLUIDA, "x"));
    const { client } = clientWithSession(fetchImpl);
    await expect(client.getUserEmail()).rejects.toBeInstanceOf(ContaExcluidaError);

    await expect(client.getUserEmail()).rejects.toBeInstanceOf(AuthRequiredError);
    expect(fetchImpl).toHaveBeenCalledOnce();
  });

  it("401 com corpo vazio continua tentando o refresh", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const { client } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(AuthRequiredError);
    expect(fetchImpl.mock.calls[1]?.[0]).toBe("http://localhost:8080/auth/refresh");
  });

  it("401 com HTML continua tentando o refresh", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("<!doctype html>", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const { client } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(AuthRequiredError);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
  });

  it("conta excluída descoberta depois do refresh também vira ContaExcluidaError", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(
        new Response(JSON.stringify({ accessToken: "novo" }), {
          status: 200,
          headers: { "set-cookie": "refresh_token=r2; Path=/auth; HttpOnly" },
        }),
      )
      .mockResolvedValueOnce(problem(401, TITULO_CONTA_EXCLUIDA, "x"));
    const { client, store } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(ContaExcluidaError);
    expect(store.load()).toBeNull();
  });

  it("500 continua virando ApiError", async () => {
    const fetchImpl = vi.fn().mockResolvedValueOnce(problem(500, "Erro", "x"));
    const { client } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(ApiError);
  });

  it("errorResult devolve código CONTA_EXCLUIDA sem token", () => {
    const result = errorResult(new ContaExcluidaError());

    expect(result.isError).toBe(true);
    const body = JSON.parse(result.content[0]!.text) as { code: string; message: string };
    expect(body.code).toBe("CONTA_EXCLUIDA");
    expect(body.message).not.toMatch(/access|refresh-secret/);
  });
});

describe("MCP não exclui conta nem exporta dados", () => {
  const fonte = (arquivo: string) =>
    readFileSync(fileURLToPath(new URL(`./${arquivo}`, import.meta.url)), "utf8");

  it("nenhuma ferramenta registrada fala em excluir conta ou exportar", () => {
    const server = createMcpServer(
      new ControleRotinaApiClient({
        apiBaseUrl: "http://localhost:8080",
        sessionStore: new MemorySessionStore(null),
      }),
    );
    const registradas = Object.keys(
      (server as unknown as { _registeredTools: Record<string, unknown> })._registeredTools,
    );

    expect(registradas.sort()).toEqual([...MCP_TOOL_NAMES].sort());
    for (const nome of registradas) {
      expect(nome).not.toMatch(/conta|account|export|delete_me|excluir/i);
    }
  });

  it("o cliente HTTP não tem método de excluir conta nem de exportar", () => {
    const metodos = Object.getOwnPropertyNames(ControleRotinaApiClient.prototype);

    expect(metodos.filter((m) => /conta|account|export|excluir/i.test(m))).toEqual([]);
  });

  it("nenhum código do MCP chama /me/export nem DELETE /me", () => {
    for (const arquivo of ["api-client.ts", "server.ts"]) {
      const codigo = fonte(arquivo);
      expect(codigo).not.toContain("/me/export");
      expect(codigo).not.toMatch(/["'`]\/me["'`][^;]*DELETE|DELETE[^;]*["'`]\/me["'`]/s);
    }
  });
});
