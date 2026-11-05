import { describe, expect, it } from "vitest";
import { parseRefreshCookie } from "./cookies.js";

describe("parseRefreshCookie", () => {
  it("parses local and secure refresh cookie names", () => {
    expect(
      parseRefreshCookie([
        "refresh_token=abc.def; Path=/auth; HttpOnly; SameSite=Lax",
      ]),
    ).toEqual({ name: "refresh_token", value: "abc.def" });

    expect(
      parseRefreshCookie([
        "__Secure-refresh_token=xyz; Path=/auth; Secure; HttpOnly; SameSite=Lax",
      ]),
    ).toEqual({ name: "__Secure-refresh_token", value: "xyz" });
  });

  it("ignores unrelated cookies and empty values", () => {
    expect(parseRefreshCookie(["session=1; Path=/"])).toBeNull();
    expect(parseRefreshCookie(["refresh_token=; Path=/auth"])).toBeNull();
  });
});
