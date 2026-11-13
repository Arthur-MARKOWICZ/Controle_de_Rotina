#!/usr/bin/env node
import { resolve } from "node:path";
import { stdout as output } from "node:process";
import { fileURLToPath } from "node:url";
import { ControleRotinaApiClient } from "./api-client.js";
import { loadConfig, type McpConfig } from "./config.js";
import { type AskFn, type LoginFlowResult, runInteractiveLogin } from "./login-flow.js";
import { FileSessionStore } from "./session-store.js";
import { askVisible, readSecretWithBash } from "./terminal-prompt.js";

export type LoginCliDeps = {
  env?: NodeJS.ProcessEnv;
  ask: AskFn;
  writeLine: (line: string) => void;
  createClient?: (config: McpConfig) => ControleRotinaApiClient;
};

export function createAsk(
  readSecret: (prompt: string) => string = readSecretWithBash,
  readVisible: (prompt: string) => Promise<string> = askVisible,
): AskFn {
  return async (prompt, options) =>
    options?.secret ? readSecret(prompt) : readVisible(prompt);
}

export function formatLoginCliCrash(error: unknown): string {
  return error instanceof Error ? error.message : "login-cli failed";
}

export async function runLoginCli(deps: LoginCliDeps): Promise<LoginFlowResult> {
  const config = loadConfig(deps.env);
  deps.writeLine(`API: ${config.apiBaseUrl}`);
  deps.writeLine(`Sessão: ${config.sessionPath}`);
  const client =
    deps.createClient?.(config) ??
    new ControleRotinaApiClient({
      apiBaseUrl: config.apiBaseUrl,
      sessionStore: new FileSessionStore(config.sessionPath),
    });
  return runInteractiveLogin({
    client,
    ask: deps.ask,
    writeLine: deps.writeLine,
  });
}

async function main(): Promise<void> {
  const result = await runLoginCli({
    ask: createAsk(),
    writeLine: (line) => output.write(`${line}\n`),
  });
  process.exitCode = result.ok ? 0 : 1;
}

function isCliEntrypoint(): boolean {
  const entry = process.argv[1];
  if (!entry) {
    return false;
  }
  try {
    return fileURLToPath(import.meta.url) === resolve(entry);
  } catch {
    return false;
  }
}

if (isCliEntrypoint()) {
  main().catch((error: unknown) => {
    console.error(formatLoginCliCrash(error));
    process.exit(1);
  });
}
