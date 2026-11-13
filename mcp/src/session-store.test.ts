import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { afterEach, describe, expect, it } from "vitest";
import {
  defaultSessionPath,
  FileSessionStore,
  MemorySessionStore,
  type McpSession,
} from "./session-store.js";

const sample: McpSession = {
  accessToken: "access",
  refreshToken: "refresh",
  refreshCookieName: "refresh_token",
  apiBaseUrl: "http://localhost:8080",
};

describe("MemorySessionStore", () => {
  it("deveSalvarCarregarELimparSessao", () => {
    const store = new MemorySessionStore();
    expect(store.load()).toBeNull();
    store.save(sample);
    expect(store.load()).toEqual(sample);
    store.clear();
    expect(store.load()).toBeNull();
  });

  it("naoDeveRefletirMutacaoDoObjetoOriginalAposSave", () => {
    const store = new MemorySessionStore();
    const input: McpSession = { ...sample };
    store.save(input);
    input.accessToken = "mutado";
    expect(store.load()?.accessToken).toBe("access");
  });

  it("deveCarregarSessaoInicialFornecida", () => {
    const store = new MemorySessionStore(sample);
    expect(store.load()).toEqual(sample);
  });
});

describe("FileSessionStore", () => {
  let dir: string;

  afterEach(() => {
    if (dir) {
      rmSync(dir, { recursive: true, force: true });
    }
  });

  it("devePersistirSessaoComPermissao600", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "session.json");
    const store = new FileSessionStore(path);

    store.save(sample);

    expect(store.load()).toEqual(sample);
    expect(statSync(path).mode & 0o777).toBe(0o600);
  });

  it("deveDevolverNullQuandoArquivoAusenteSemCriarArquivo", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "missing.json");
    const store = new FileSessionStore(path);
    expect(store.load()).toBeNull();
    expect(existsSync(path)).toBe(false);
  });

  it("deveDevolverNullQuandoJsonELixoSemReescreverOArquivo", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "session.json");
    const garbage = "isto nao e json {";
    writeFileSync(path, garbage);
    const store = new FileSessionStore(path);

    expect(store.load()).toBeNull();
    expect(readFileSync(path, "utf8")).toBe(garbage);
  });

  it("deveDevolverNullQuandoJsonParcialNaoTemTodosOsCampos", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "session.json");
    writeFileSync(path, '{"accessToken":"only"}\n');
    const store = new FileSessionStore(path);

    expect(store.load()).toBeNull();
    expect(readFileSync(path, "utf8")).toContain("only");
  });

  it("deveDevolverNullQuandoArquivoEstaVazio", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "session.json");
    writeFileSync(path, "");
    expect(new FileSessionStore(path).load()).toBeNull();
    expect(readFileSync(path, "utf8")).toBe("");
  });

  it("naoDeveLancarAoLimparArquivoInexistente", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "missing.json");
    const store = new FileSessionStore(path);
    expect(() => store.clear()).not.toThrow();
    expect(store.load()).toBeNull();
    expect(existsSync(path)).toBe(false);
  });

  it("deveApagarArquivoAoLimparSessaoPersistida", () => {
    dir = mkdtempSync(join(tmpdir(), "cr-mcp-session-"));
    const path = join(dir, "session.json");
    const store = new FileSessionStore(path);
    store.save(sample);
    store.clear();
    expect(existsSync(path)).toBe(false);
    expect(store.load()).toBeNull();
  });
});

describe("defaultSessionPath", () => {
  it("deveApontarParaConfigDoUsuario", () => {
    expect(defaultSessionPath()).toMatch(/\.config[/\\]controle-rotina[/\\]mcp-session\.json$/);
  });
});
