import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
    pool: "threads",
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "html", "lcov", "json-summary"],
      include: ["src/**/*.ts"],
      // index.ts é só o bootstrap stdio (design D3).
      exclude: ["src/**/*.test.ts", "src/index.ts"],
      // Catraca (ver AGENTS.md — Testes): só sobe, nunca desce. Alvo: 70/60.
      thresholds: {
        lines: 53,
        branches: 60,
      },
    },
  },
});
