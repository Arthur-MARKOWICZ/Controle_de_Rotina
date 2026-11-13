import { describe, expect, it } from "vitest";
import { parseRefreshCookie, readSetCookieHeaders } from "./cookies.js";

describe("parseRefreshCookie", () => {
  it("deveExtrairCookieLocalRefreshToken", () => {
    expect(
      parseRefreshCookie(["refresh_token=abc.def; Path=/auth; HttpOnly; SameSite=Lax"]),
    ).toEqual({ name: "refresh_token", value: "abc.def" });
  });

  it("deveExtrairCookieSecureRefreshToken", () => {
    expect(
      parseRefreshCookie([
        "__Secure-refresh_token=xyz; Path=/auth; Secure; HttpOnly; SameSite=Lax",
      ]),
    ).toEqual({ name: "__Secure-refresh_token", value: "xyz" });
  });

  it("deveReconhecerNomeQueContemRefresh", () => {
    expect(parseRefreshCookie(["X-Refresh=abc123"])).toEqual({
      name: "X-Refresh",
      value: "abc123",
    });
  });

  it("deveIgnorarCookieNaoRelacionadoSemEscolherOutro", () => {
    expect(parseRefreshCookie(["session=1; Path=/"])).toBeNull();
  });

  it("deveIgnorarRefreshComValorVazio", () => {
    expect(parseRefreshCookie(["refresh_token=; Path=/auth"])).toBeNull();
  });

  it("deveIgnorarListaVaziaDeCabecalhos", () => {
    expect(parseRefreshCookie([])).toBeNull();
  });

  it("deveIgnorarParSemIgualEContinuar", () => {
    expect(parseRefreshCookie(["HttpOnly", "refresh_token=ok"])).toEqual({
      name: "refresh_token",
      value: "ok",
    });
  });

  it("deveIgnorarCabecalhoQueComecaComIgual", () => {
    expect(parseRefreshCookie(["=sem-nome", "session=1"])).toBeNull();
  });

  it("devePegarRefreshDepoisDeCookieNaoRelacionado", () => {
    expect(
      parseRefreshCookie(["session=abc; Path=/", "refresh_token=token; Path=/auth"]),
    ).toEqual({ name: "refresh_token", value: "token" });
  });
});

describe("readSetCookieHeaders", () => {
  it("deveUsarGetSetCookieQuandoDisponivel", () => {
    const headers = new Headers();
    headers.append("set-cookie", "session=1; Path=/");
    headers.append("set-cookie", "refresh_token=abc; Path=/auth");
    expect(readSetCookieHeaders(headers)).toEqual([
      "session=1; Path=/",
      "refresh_token=abc; Path=/auth",
    ]);
    expect(parseRefreshCookie(readSetCookieHeaders(headers))).toEqual({
      name: "refresh_token",
      value: "abc",
    });
  });

  it("deveCairNoGetUnicoQuandoGetSetCookieAusente", () => {
    const headers = {
      get(name: string) {
        return name.toLowerCase() === "set-cookie"
          ? "refresh_token=unico; Path=/auth"
          : null;
      },
    } as Headers;
    expect(readSetCookieHeaders(headers)).toEqual(["refresh_token=unico; Path=/auth"]);
  });

  it("deveDevolverListaVaziaQuandoNaoHaSetCookie", () => {
    const headers = {
      get() {
        return null;
      },
    } as unknown as Headers;
    expect(readSetCookieHeaders(headers)).toEqual([]);
    expect(parseRefreshCookie(readSetCookieHeaders(headers))).toBeNull();
  });
});
