import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, type ControleRotinaApiClient } from "./api-client.js";
import { createAsk, formatLoginCliCrash, runLoginCli } from "./login-cli.js";

const API_URL = "http://localhost:8080";

function fakeClient(
  overrides: Partial<ControleRotinaApiClient> = {},
): ControleRotinaApiClient {
  return {
    login: vi.fn().mockResolvedValue({ authenticated: true }),
    ...overrides,
  } as unknown as ControleRotinaApiClient;
}

describe("createAsk", () => {
  it("deveLerSenhaOcultaQuandoSecret", async () => {
    const ask = createAsk(
      () => "s3nha",
      async () => "nao-usar",
    );
    await expect(ask("Senha: ", { secret: true })).resolves.toBe("s3nha");
  });

  it("deveLerEmailVisivelQuandoNaoSecret", async () => {
    const ask = createAsk(
      () => "nao-usar",
      async () => "user@example.com",
    );
    await expect(ask("E-mail: ")).resolves.toBe("user@example.com");
  });
});

describe("formatLoginCliCrash", () => {
  it("deveDevolverMensagemQuandoError", () => {
    expect(formatLoginCliCrash(new Error("ECONNREFUSED"))).toBe("ECONNREFUSED");
  });

  it("deveDevolverFallbackQuandoValorNaoEError", () => {
    expect(formatLoginCliCrash("boom")).toBe("login-cli failed");
  });
});

describe("runLoginCli", () => {
  let dir: string;

  afterEach(() => {
    if (dir) {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("deveCompletarLoginQuandoCredenciaisValidas", async () => {
    const client = fakeClient();
    const lines: string[] = [];
    const result = await runLoginCli({
      env: { CONTROLE_ROTINA_API_BASE_URL: API_URL },
      ask: vi
        .fn()
        .mockResolvedValueOnce("user@example.com")
        .mockResolvedValueOnce("never-print-this-password"),
      writeLine: (line) => lines.push(line),
      createClient: () => client,
    });

    expect(result).toEqual({ ok: true });
    expect(client.login).toHaveBeenCalledWith(
      "user@example.com",
      "never-print-this-password",
    );
    expect(lines.some((line) => /sessão salva/i.test(line))).toBe(true);
    expect(lines.join("\n")).not.toContain("never-print-this-password");
    expect(lines[0]).toBe(`API: ${API_URL}`);
  });

  it("naoDeveCriarArquivoDeSessaoQuandoEmailVazio", async () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-login-"));
    const sessionPath = join(dir, "session.json");
    const result = await runLoginCli({
      env: {
        CONTROLE_ROTINA_API_BASE_URL: API_URL,
        CONTROLE_ROTINA_SESSION_PATH: sessionPath,
      },
      ask: vi.fn().mockResolvedValueOnce("   "),
      writeLine: () => undefined,
    });

    expect(result).toEqual({ ok: false, message: "E-mail obrigatório." });
    expect(existsSync(sessionPath)).toBe(false);
  });

  it("naoDeveChamarApiQuandoSenhaVazia", async () => {
    const client = fakeClient({ login: vi.fn() });
    const result = await runLoginCli({
      env: { CONTROLE_ROTINA_API_BASE_URL: API_URL },
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce(""),
      writeLine: () => undefined,
      createClient: () => client,
    });

    expect(result).toEqual({ ok: false, message: "Senha obrigatória." });
    expect(client.login).not.toHaveBeenCalled();
  });

  it("deveReportar401QuandoCredenciaisInvalidas", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(401, '{"title":"Unauthorized"}')),
    });
    const lines: string[] = [];
    const result = await runLoginCli({
      env: { CONTROLE_ROTINA_API_BASE_URL: API_URL },
      ask: vi
        .fn()
        .mockResolvedValueOnce("user@example.com")
        .mockResolvedValueOnce("wrong-password"),
      writeLine: (line) => lines.push(line),
      createClient: () => client,
    });

    expect(result.ok).toBe(false);
    expect(result.message).toMatch(/HTTP 401/);
    expect(lines.join("\n")).not.toContain("wrong-password");
  });

  it("deveReportarQuandoApiEstaForaDoAr", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new Error("fetch failed")),
    });
    const result = await runLoginCli({
      env: { CONTROLE_ROTINA_API_BASE_URL: API_URL },
      ask: vi.fn().mockResolvedValueOnce("user@example.com").mockResolvedValueOnce("pw"),
      writeLine: () => undefined,
      createClient: () => client,
    });

    expect(result).toEqual({ ok: false, message: "Falha no login: fetch failed" });
  });

  it("deveLancarQuandoApiBaseUrlAusente", async () => {
    await expect(
      runLoginCli({
        env: {},
        ask: vi.fn(),
        writeLine: () => undefined,
      }),
    ).rejects.toThrow(/CONTROLE_ROTINA_API_BASE_URL is required/);
  });

  it("deveLancarQuandoApiBaseUrlInvalida", async () => {
    await expect(
      runLoginCli({
        env: { CONTROLE_ROTINA_API_BASE_URL: "ftp://x" },
        ask: vi.fn(),
        writeLine: () => undefined,
      }),
    ).rejects.toThrow(/http\(s\)/);
  });
});
