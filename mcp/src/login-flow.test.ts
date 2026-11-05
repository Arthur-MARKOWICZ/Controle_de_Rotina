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
  it("autentica e não imprime senha", async () => {
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

  it("rejeita e-mail vazio sem chamar a API", async () => {
    const client = fakeClient({
      login: vi.fn(),
    });
    const ask = vi.fn().mockResolvedValueOnce("   ");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: () => undefined,
    });

    expect(result.ok).toBe(false);
    expect(client.login).not.toHaveBeenCalled();
  });

  it("rejeita senha vazia sem chamar a API", async () => {
    const client = fakeClient({
      login: vi.fn(),
    });
    const ask = vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("");

    const result = await runInteractiveLogin({
      client,
      ask,
      writeLine: () => undefined,
    });

    expect(result.ok).toBe(false);
    expect(client.login).not.toHaveBeenCalled();
  });

  it("em erro de API reporta status sem ecoar a senha", async () => {
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
    expect(lines.join("\n")).toMatch(/401/);
    expect(lines.join("\n")).not.toContain("wrong-password");
  });
});
