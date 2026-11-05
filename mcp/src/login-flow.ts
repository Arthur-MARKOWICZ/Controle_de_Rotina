import { ApiError, type ControleRotinaApiClient } from "./api-client.js";

export type AskFn = (prompt: string, options?: { secret?: boolean }) => Promise<string>;

export type LoginFlowDeps = {
  client: ControleRotinaApiClient;
  ask: AskFn;
  writeLine: (line: string) => void;
};

export type LoginFlowResult =
  | { ok: true }
  | { ok: false; message: string };

/**
 * Fluxo interativo de login para CLI local (sem MFA).
 * Não ecoa senha nem tokens nas mensagens.
 */
export async function runInteractiveLogin(
  deps: LoginFlowDeps,
): Promise<LoginFlowResult> {
  const email = (await deps.ask("E-mail: ")).trim();
  if (!email) {
    const message = "E-mail obrigatório.";
    deps.writeLine(message);
    return { ok: false, message };
  }

  const password = await deps.ask("Senha: ", { secret: true });
  if (!password) {
    const message = "Senha obrigatória.";
    deps.writeLine(message);
    return { ok: false, message };
  }

  try {
    await deps.client.login(email, password);
    deps.writeLine("Sessão salva (access ~15 min). Pode voltar ao chat.");
    return { ok: true };
  } catch (error) {
    const message = formatLoginError(error);
    deps.writeLine(message);
    return { ok: false, message };
  }
}

function formatLoginError(error: unknown): string {
  if (error instanceof ApiError) {
    return `Falha no login (HTTP ${error.status}). Verifique e-mail e senha.`;
  }
  if (error instanceof Error) {
    return `Falha no login: ${error.message}`;
  }
  return "Falha no login.";
}
