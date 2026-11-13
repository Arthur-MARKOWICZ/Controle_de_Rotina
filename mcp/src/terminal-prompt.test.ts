import { describe, expect, it, vi } from "vitest";
import { readSecretWithBash } from "./terminal-prompt.js";

describe("readSecretWithBash", () => {
  it("deveDevolverSenhaSemQuebraDeLinhaFinal", () => {
    const spawn = vi.fn().mockReturnValue({
      status: 0,
      stdout: "s3nha-secreta\n",
      stderr: "",
    });

    const value = readSecretWithBash("Senha: ", spawn);

    expect(value).toBe("s3nha-secreta");
    expect(spawn).toHaveBeenCalledOnce();
    const args = spawn.mock.calls[0]?.[1] as string[];
    expect(args.some((part) => part.includes("read -r -s"))).toBe(true);
    expect(args.at(-1)).toBe("Senha: ");
  });

  it("deveRemoverApenasCRLFFinal", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "abc\r\n", stderr: "" });
    expect(readSecretWithBash("Senha: ", spawn)).toBe("abc");
  });

  it("deveManterSenhaSemQuebraDeLinhaComoEsta", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "sem-nl", stderr: "" });
    expect(readSecretWithBash("Senha: ", spawn)).toBe("sem-nl");
  });

  it("deveDevolverStringVaziaQuandoStdoutVazioEStatusZero", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: "", stderr: "" });
    expect(readSecretWithBash("Senha: ", spawn)).toBe("");
  });

  it("deveTratarStdoutAusenteComoVazioSemLancar", () => {
    const spawn = vi.fn().mockReturnValue({ status: 0, stdout: undefined, stderr: "" });
    expect(readSecretWithBash("Senha: ", spawn)).toBe("");
  });

  it("deveLancarQuandoBashRetornaStatusDiferenteDeZero", () => {
    const spawn = vi.fn().mockReturnValue({ status: 1, stdout: "nao-usar", stderr: "fail" });
    expect(() => readSecretWithBash("Senha: ", spawn)).toThrow(
      /Não foi possível ler a senha no terminal/,
    );
  });

  it("deveLancarQuandoStatusDoBashENulo", () => {
    const spawn = vi.fn().mockReturnValue({ status: null, stdout: "x", stderr: "" });
    expect(() => readSecretWithBash("Senha: ", spawn)).toThrow(/senha/i);
  });

  it("deveLancarQuandoStatusDoBashENegativo", () => {
    const spawn = vi.fn().mockReturnValue({ status: -1, stdout: "", stderr: "" });
    expect(() => readSecretWithBash("Senha: ", spawn)).toThrow(/senha/i);
  });
});
