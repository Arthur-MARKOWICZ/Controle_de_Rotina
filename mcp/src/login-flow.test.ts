import { describe, expect, it, vi } from "vitest";
import { ApiError, type ControleRotinaApiClient } from "./api-client.js";
import { runInteractiveLogin } from "./login-flow.js";

function fakeClient(
  overrides: Partial<ControleRotinaApiClient> = {},
): ControleRotinaApiClient {
  return {
    login: vi.fn(),
    ...overrides,
  } as unknown as ControleRotinaApiClient;
}

describe("runInteractiveLogin", () => {
  it("deveAutenticarENaoImprimirSenhaQuandoCredenciaisValidas", async () => {
    const client = fakeClient({
      login: vi.fn().mockResolvedValue({ authenticated: true }),
    });
    const lines: string[] = [];
    const ask = vi
      .fn()
      .mockResolvedValueOnce("user@example.com")
      .mockResolvedValueOnce("never-print-this-password");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result).toEqual({ ok: true });
    expect(client.login).toHaveBeenCalledWith(
      "user@example.com",
      "never-print-this-password",
    );
    expect(lines.join("\n")).toMatch(/sessão salva/i);
    expect(lines.join("\n")).not.toContain("never-print-this-password");
  });

  it("deveEnviarEmailTrimadoSemAlterarASenha", async () => {
    const client = fakeClient({
      login: vi.fn().mockResolvedValue({ authenticated: true }),
    });
    const ask = vi
      .fn()
      .mockResolvedValueOnce("  user@example.com  ")
      .mockResolvedValueOnce("secret");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: () => undefined,
    });

    expect(result).toEqual({ ok: true });
    expect(client.login).toHaveBeenCalledWith("user@example.com", "secret");
  });

  it("deveRecusarEmailVazioSemChamarAApi", async () => {
    const client = fakeClient({ login: vi.fn() });
    const lines: string[] = [];
    const ask = vi.fn().mockResolvedValueOnce("   ");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result).toEqual({ ok: false, message: "E-mail obrigatório." });
    expect(client.login).not.toHaveBeenCalled();
    expect(lines).toEqual(["E-mail obrigatório."]);
    expect(ask).toHaveBeenCalledTimes(1);
  });

  it("deveRecusarSenhaVaziaSemChamarAApi", async () => {
    const client = fakeClient({ login: vi.fn() });
    const lines: string[] = [];
    const ask = vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result).toEqual({ ok: false, message: "Senha obrigatória." });
    expect(client.login).not.toHaveBeenCalled();
    expect(lines).toEqual(["Senha obrigatória."]);
  });

  it("naoDeveEcoarSenhaQuandoApiResponde401", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(401, '{"title":"Unauthorized"}')),
    });
    const lines: string[] = [];
    const ask = vi
      .fn()
      .mockResolvedValueOnce("user@example.com")
      .mockResolvedValueOnce("wrong-password");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: (line) => lines.push(line),
    });

    expect(result.ok).toBe(false);
    expect(result).toMatchObject({
      message: expect.stringMatching(/HTTP 401/),
    });
    expect(lines.join("\n")).toMatch(/401/);
    expect(lines.join("\n")).not.toContain("wrong-password");
    expect(lines.join("\n")).not.toContain("Unauthorized");
  });

  it("devePropagar404DaApiComStatusNaMensagem", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(404, '{"title":"Not Found"}')),
    });
    const lines: string[] = [];

    const result = await runInteractiveLogin({
      client,
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("pw"),
      writeLine: (line) => lines.push(line),
    });

    expect(result).toEqual({
      ok: false,
      message: "Falha no login (HTTP 404). Verifique e-mail e senha.",
    });
    expect(lines).toEqual([result.message]);
  });

  it("devePropagar409DaApiComStatusNaMensagem", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(409, '{"title":"Conflict"}')),
    });

    const result = await runInteractiveLogin({
      client,
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("pw"),
      writeLine: () => undefined,
    });

    expect(result.ok).toBe(false);
    expect(result.message).toBe("Falha no login (HTTP 409). Verifique e-mail e senha.");
  });

  it("devePropagarErroDeRedeComAMensagemOriginal", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new Error("ECONNREFUSED")),
    });

    const result = await runInteractiveLogin({
      client,
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("pw"),
      writeLine: () => undefined,
    });

    expect(result).toEqual({ ok: false, message: "Falha no login: ECONNREFUSED" });
  });

  it("deveDevolverMensagemGenericaQuandoErroNaoEInstanciaDeError", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue("boom"),
    });

    const result = await runInteractiveLogin({
      client,
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("pw"),
      writeLine: () => undefined,
    });

    expect(result).toEqual({ ok: false, message: "Falha no login." });
  });
});
