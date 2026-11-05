import { describe, expect, it, vi } from "vitest";
import {
  ApiError,
  AuthRequiredError,
  ControleRotinaApiClient,
} from "./api-client.js";
import { MemorySessionStore } from "./session-store.js";

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...headers },
  });
}

function clientWithSession(
  fetchImpl: typeof fetch,
  accessToken = "secret-token",
) {
  const store = new MemorySessionStore({
    accessToken,
    refreshToken: "refresh-secret",
    refreshCookieName: "refresh_token",
    apiBaseUrl: "http://localhost:8080",
  });
  return {
    store,
    client: new ControleRotinaApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: store,
      fetchImpl,
    }),
  };
}

describe("ControleRotinaApiClient", () => {
  it("getUserEmail returns only email from /me", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse({
        id: "11111111-1111-1111-1111-111111111111",
        nome: "Ada",
        email: "user@example.com",
      }),
    );
    const { client } = clientWithSession(fetchImpl);

    const result = await client.getUserEmail();

    expect(result).toEqual({ email: "user@example.com" });
    expect(Object.keys(result)).toEqual(["email"]);
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/me");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer secret-token");
  });

  it("login persists session from Set-Cookie and does not return tokens", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse(
        { accessToken: "new-access" },
        200,
        {
          "set-cookie":
            "refresh_token=new-refresh; Path=/auth; HttpOnly; SameSite=Lax",
        },
      ),
    );
    const store = new MemorySessionStore();
    const client = new ControleRotinaApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: store,
      fetchImpl,
    });

    const result = await client.login("user@example.com", "password-long");

    expect(result).toEqual({ authenticated: true });
    expect(JSON.stringify(result)).not.toContain("new-access");
    expect(JSON.stringify(result)).not.toContain("new-refresh");
    expect(store.load()).toEqual({
      accessToken: "new-access",
      refreshToken: "new-refresh",
      refreshCookieName: "refresh_token",
      apiBaseUrl: "http://localhost:8080",
    });
    expect(fetchImpl.mock.calls[0]?.[0]).toBe("http://localhost:8080/auth/login");
    expect(JSON.parse(String((fetchImpl.mock.calls[0]?.[1] as RequestInit).body))).toEqual({
      email: "user@example.com",
      senha: "password-long",
    });
  });

  it("refreshes once on 401 and retries the original call", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(
        jsonResponse(
          { accessToken: "refreshed-access" },
          200,
          {
            "set-cookie":
              "refresh_token=rotated-refresh; Path=/auth; HttpOnly; SameSite=Lax",
          },
        ),
      )
      .mockResolvedValueOnce(jsonResponse({ email: "user@example.com" }));

    const { client, store } = clientWithSession(fetchImpl, "expired-access");
    const result = await client.getUserEmail();

    expect(result).toEqual({ email: "user@example.com" });
    expect(fetchImpl).toHaveBeenCalledTimes(3);
    expect(fetchImpl.mock.calls[1]?.[0]).toBe("http://localhost:8080/auth/refresh");
    expect(new Headers((fetchImpl.mock.calls[1]?.[1] as RequestInit).headers).get("Cookie")).toBe(
      "refresh_token=refresh-secret",
    );
    expect(store.load()?.accessToken).toBe("refreshed-access");
    expect(store.load()?.refreshToken).toBe("rotated-refresh");
    expect(new Headers((fetchImpl.mock.calls[2]?.[1] as RequestInit).headers).get("Authorization")).toBe(
      "Bearer refreshed-access",
    );
  });

  it("asks for login again when refresh fails", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(new Response("{}", { status: 401 }))
      .mockResolvedValueOnce(new Response("{}", { status: 401 }));
    const { client, store } = clientWithSession(fetchImpl);

    await expect(client.listTarefas()).rejects.toBeInstanceOf(AuthRequiredError);
    expect(store.load()).toBeNull();
  });

  it("requires login when there is no session", async () => {
    const client = new ControleRotinaApiClient({
      apiBaseUrl: "http://localhost:8080",
      sessionStore: new MemorySessionStore(),
      fetchImpl: vi.fn(),
    });
    await expect(client.listTarefas()).rejects.toBeInstanceOf(AuthRequiredError);
  });

  it("logout clears local session even when the API fails", async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error("network down"));
    const { client, store } = clientWithSession(fetchImpl);

    await client.logout();
    expect(store.load()).toBeNull();
  });

  it("logout sends Bearer and Cookie then clears", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    const { client, store } = clientWithSession(fetchImpl);

    await client.logout();

    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("http://localhost:8080/auth/logout");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer secret-token");
    expect(new Headers(init.headers).get("Cookie")).toBe("refresh_token=refresh-secret");
    expect(store.load()).toBeNull();
  });

  it("surfaces API errors without attaching the token", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response('{"title":"Bad Request"}', { status: 400 }),
    );
    const { client } = clientWithSession(fetchImpl, "super-secret");

    try {
      await client.listTarefas();
      expect.unreachable("expected ApiError");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        status: 400,
        body: '{"title":"Bad Request"}',
      });
      expect(String(error)).not.toContain("super-secret");
    }
  });

  it("maps tarefa/plano/execucao domain paths", async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ content: [] }))
      .mockResolvedValueOnce(jsonResponse({ id: "t1" }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: "p1" }))
      .mockResolvedValueOnce(jsonResponse({ id: "p2" }, 201))
      .mockResolvedValueOnce(jsonResponse({ id: "e1" }, 201));
    const { client } = clientWithSession(fetchImpl, "tok");

    await client.listTarefas({ status: ["PENDENTE"], page: 0, size: 20 });
    await client.createTarefa({
      titulo: "Estudar",
      prioridade: "ALTA",
      duracaoMinutos: 60,
    });
    await client.getPlano("2026-11-05");
    await client.aprovarPlano("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    await client.iniciarExecucao("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    expect(fetchImpl.mock.calls[0]?.[0]).toBe(
      "http://localhost:8080/tarefas?page=0&size=20&status=PENDENTE",
    );
    expect(fetchImpl.mock.calls[1]?.[0]).toBe("http://localhost:8080/tarefas");
    expect((fetchImpl.mock.calls[1]?.[1] as RequestInit).method).toBe("POST");
    expect(fetchImpl.mock.calls[2]?.[0]).toBe(
      "http://localhost:8080/planos?data=2026-11-05",
    );
    expect(fetchImpl.mock.calls[3]?.[0]).toBe(
      "http://localhost:8080/planos/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/aprovar",
    );
    expect((fetchImpl.mock.calls[3]?.[1] as RequestInit).method).toBe("POST");
    expect(fetchImpl.mock.calls[4]?.[0]).toBe("http://localhost:8080/execucoes/iniciar");
    expect(JSON.parse(String((fetchImpl.mock.calls[4]?.[1] as RequestInit).body))).toEqual({
      blocoId: "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
    });
  });
});
