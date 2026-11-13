import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";
import { defaultSessionPath } from "./session-store.js";

const VALID_URL = "http://localhost:8080";

describe("loadConfig", () => {
  it("deveAceitarUrlHttpECaminhoDeSessaoExplicito", () => {
    const config = loadConfig({
      CONTROLE_ROTINA_API_BASE_URL: "http://localhost:8080/",
      CONTROLE_ROTINA_SESSION_PATH: "/tmp/mcp-session.json",
    });
    expect(config).toEqual({
      apiBaseUrl: "http://localhost:8080",
      sessionPath: "/tmp/mcp-session.json",
    });
  });

  it("deveUsarCaminhoPadraoQuandoSessaoNaoInformada", () => {
    const config = loadConfig({
      CONTROLE_ROTINA_API_BASE_URL: "https://rotina.example.com",
    });
    expect(config.apiBaseUrl).toBe("https://rotina.example.com");
    expect(config.sessionPath).toBe(defaultSessionPath());
    expect(config.sessionPath).toMatch(/controle-rotina/);
  });

  it("naoDeveMutarEnvAoRemoverBarrasFinais", () => {
    const env = {
      CONTROLE_ROTINA_API_BASE_URL: "http://localhost:8080///",
      CONTROLE_ROTINA_SESSION_PATH: "/tmp/mcp-session.json",
    };
    const config = loadConfig(env);
    expect(config.apiBaseUrl).toBe("http://localhost:8080");
    expect(env.CONTROLE_ROTINA_API_BASE_URL).toBe("http://localhost:8080///");
  });

  it("deveTratarCaminhoDeSessaoSoComEspacosComoAusente", () => {
    const config = loadConfig({
      CONTROLE_ROTINA_API_BASE_URL: VALID_URL,
      CONTROLE_ROTINA_SESSION_PATH: "   ",
    });
    expect(config.sessionPath).toBe(defaultSessionPath());
  });

  it("deveAceitarHttpEmMaiusculasSemAlterarOEnv", () => {
    const env = { CONTROLE_ROTINA_API_BASE_URL: "HTTP://LOCALHOST:8080" };
    const config = loadConfig(env);
    expect(config.apiBaseUrl).toBe("HTTP://LOCALHOST:8080");
    expect(env.CONTROLE_ROTINA_API_BASE_URL).toBe("HTTP://LOCALHOST:8080");
  });

  it("deveLancarQuandoApiBaseUrlAusente", () => {
    expect(() => loadConfig({})).toThrow(/CONTROLE_ROTINA_API_BASE_URL is required/);
  });

  it("deveLancarQuandoApiBaseUrlNaoEHttp", () => {
    expect(() =>
      loadConfig({
        CONTROLE_ROTINA_API_BASE_URL: "ftp://example.com",
      }),
    ).toThrow(/must be an http\(s\) origin/);
  });

  it("deveLancarQuandoApiBaseUrlEFile", () => {
    expect(() =>
      loadConfig({
        CONTROLE_ROTINA_API_BASE_URL: "file:///tmp/api",
      }),
    ).toThrow(/must be an http\(s\) origin/);
  });

  it("deveLancarQuandoApiBaseUrlESoEspacos", () => {
    expect(() =>
      loadConfig({
        CONTROLE_ROTINA_API_BASE_URL: "   ",
      }),
    ).toThrow(/CONTROLE_ROTINA_API_BASE_URL is required/);
  });
});
