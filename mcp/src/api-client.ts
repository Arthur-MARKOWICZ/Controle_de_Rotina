import { parseRefreshCookie, readSetCookieHeaders } from "./cookies.js";
import type { SessionStore } from "./session-store.js";

export type Prioridade = "BAIXA" | "MEDIA" | "ALTA";

export type StatusTarefa =
  | "PENDENTE"
  | "EM_PROGRESSO"
  | "FINALIZADA"
  | "CANCELADA"
  | "ATRASADA";

export type CreateTarefaInput = {
  titulo: string;
  descricao?: string;
  prioridade: Prioridade;
  prazo?: string | null;
  duracaoMinutos: number;
  projetoId?: string | null;
};

export type UpdateTarefaInput = {
  titulo?: string;
  descricao?: string;
  prioridade?: Prioridade;
  prazo?: string | null;
  status?: StatusTarefa;
  duracaoMinutos?: number;
  /** Se a chave estiver presente (mesmo null), a API trata desvínculo/vínculo. */
  projetoId?: string | null;
};

export type CapacidadeOverrideItem = {
  data: string;
  horas: number;
};

export type LoginSuccess = {
  authenticated: true;
};

export type ApiClientOptions = {
  apiBaseUrl: string;
  sessionStore: SessionStore;
  fetchImpl?: typeof fetch;
};

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: string,
  ) {
    super(`API request failed with status ${status}`);
    this.name = "ApiError";
  }
}

export class AuthRequiredError extends Error {
  constructor(
    message = "Sessão ausente ou expirada. Rode `cd mcp && pnpm auth-login` no terminal.",
  ) {
    super(message);
    this.name = "AuthRequiredError";
  }
}

/**
 * Cliente HTTP mínimo da API do controle de rotina (paths na raiz: /auth, /tarefas, …).
 * Sessão estilo mobile: access + refresh local; refresh em 401.
 * Não registra tokens, cookies, senhas nem corpos.
 */
export class ControleRotinaApiClient {
  private readonly apiBaseUrl: string;
  private readonly sessionStore: SessionStore;
  private readonly fetchImpl: typeof fetch;
  private refreshInFlight: Promise<void> | null = null;

  constructor(options: ApiClientOptions) {
    this.apiBaseUrl = options.apiBaseUrl.replace(/\/+$/, "");
    this.sessionStore = options.sessionStore;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async login(email: string, password: string): Promise<LoginSuccess> {
    const response = await this.rawFetch("/auth/login", {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ email, senha: password }),
    });
    const text = await response.text();
    if (!response.ok) {
      throw new ApiError(response.status, text);
    }
    const body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    this.persistSessionFromAuthResponse(response, body);
    return { authenticated: true };
  }

  async logout(): Promise<void> {
    const session = this.sessionStore.load();
    try {
      if (session) {
        try {
          await this.rawFetch("/auth/logout", {
            method: "POST",
            headers: {
              Authorization: `Bearer ${session.accessToken}`,
              Accept: "application/json",
              Cookie: `${session.refreshCookieName}=${session.refreshToken}`,
            },
          });
        } catch {
          // best-effort: limpa sessão local mesmo se a API estiver inacessível
        }
      }
    } finally {
      this.sessionStore.clear();
    }
  }

  authStatus(): { hasSession: boolean } {
    return { hasSession: this.sessionStore.load() !== null };
  }

  /** Retorna somente o e-mail; demais campos de /me são descartados. */
  async getUserEmail(): Promise<{ email: string }> {
    const user = await this.requestJson<{ email?: unknown }>("/me");
    if (typeof user.email !== "string" || user.email.length === 0) {
      throw new Error("API response did not include a usable email");
    }
    return { email: user.email };
  }

  listTarefas(query: {
    status?: StatusTarefa[];
    prioridade?: Prioridade[];
    page?: number;
    size?: number;
  } = {}): Promise<unknown> {
    const params: Record<string, string | undefined> = {
      page: query.page?.toString(),
      size: query.size?.toString(),
    };
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "") {
        search.set(key, value);
      }
    }
    for (const status of query.status ?? []) {
      search.append("status", status);
    }
    for (const prioridade of query.prioridade ?? []) {
      search.append("prioridade", prioridade);
    }
    const qs = search.toString();
    return this.requestJson(qs ? `/tarefas?${qs}` : "/tarefas");
  }

  getTarefa(id: string): Promise<unknown> {
    return this.requestJson(`/tarefas/${encodeURIComponent(id)}`);
  }

  createTarefa(body: CreateTarefaInput): Promise<unknown> {
    return this.requestJson("/tarefas", {
      method: "POST",
      body: JSON.stringify(body),
    });
  }

  updateTarefa(id: string, body: UpdateTarefaInput): Promise<unknown> {
    return this.requestJson(`/tarefas/${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: JSON.stringify(body),
    });
  }

  getCapacidadeResolvida(query: {
    dataDe?: string;
    dataAte?: string;
  } = {}): Promise<unknown> {
    return this.requestJson(
      this.withQuery("/capacidades/resolvida", {
        dataDe: query.dataDe,
        dataAte: query.dataAte,
      }),
    );
  }

  setCapacidadeOverride(itens: CapacidadeOverrideItem[]): Promise<unknown> {
    return this.requestJson("/capacidades/overrides", {
      method: "POST",
      body: JSON.stringify({ itens }),
    });
  }

  getPlano(data: string, versao?: number): Promise<unknown> {
    return this.requestJson(
      this.withQuery("/planos", {
        data,
        versao: versao?.toString(),
      }),
    );
  }

  gerarPlano(data: string): Promise<unknown> {
    return this.requestJson(this.withQuery("/planos/gerar", { data }), {
      method: "POST",
    });
  }

  addBlocoPlano(
    planoId: string,
    body: { tarefaId?: string; habitoId?: string },
  ): Promise<unknown> {
    return this.requestJson(`/planos/${encodeURIComponent(planoId)}/blocos`, {
      method: "POST",
      body: JSON.stringify(body),
    });
  }

  removeBlocoPlano(planoId: string, blocoId: string): Promise<unknown> {
    return this.requestJson(
      `/planos/${encodeURIComponent(planoId)}/blocos/${encodeURIComponent(blocoId)}`,
      { method: "DELETE" },
    );
  }

  aprovarPlano(planoId: string): Promise<unknown> {
    return this.requestJson(`/planos/${encodeURIComponent(planoId)}/aprovar`, {
      method: "POST",
    });
  }

  listExecucoes(data?: string): Promise<unknown> {
    return this.requestJson(this.withQuery("/execucoes", { data }));
  }

  iniciarExecucao(blocoId: string): Promise<unknown> {
    return this.requestJson("/execucoes/iniciar", {
      method: "POST",
      body: JSON.stringify({ blocoId }),
    });
  }

  concluirExecucao(id: string): Promise<unknown> {
    return this.requestJson(`/execucoes/${encodeURIComponent(id)}/concluir`, {
      method: "POST",
    });
  }

  abortarExecucao(id: string): Promise<unknown> {
    return this.requestJson(`/execucoes/${encodeURIComponent(id)}/abortar`, {
      method: "POST",
    });
  }

  historicoDia(data?: string): Promise<unknown> {
    return this.requestJson(this.withQuery("/historico/dia", { data }));
  }

  historicoSemana(semanaInicio?: string): Promise<unknown> {
    return this.requestJson(
      this.withQuery("/historico/semana", { semanaInicio }),
    );
  }

  private persistSessionFromAuthResponse(
    response: Response,
    body: Record<string, unknown>,
  ): void {
    if (typeof body.accessToken !== "string" || body.accessToken.length === 0) {
      throw new Error("Resposta de autenticação sem accessToken");
    }
    const cookie = parseRefreshCookie(readSetCookieHeaders(response.headers));
    if (!cookie) {
      throw new Error("Resposta de autenticação sem cookie de refresh");
    }
    this.sessionStore.save({
      accessToken: body.accessToken,
      refreshToken: cookie.value,
      refreshCookieName: cookie.name,
      apiBaseUrl: this.apiBaseUrl,
    });
  }

  private async refreshSession(): Promise<void> {
    if (this.refreshInFlight) {
      await this.refreshInFlight;
      return;
    }
    this.refreshInFlight = this.doRefresh().finally(() => {
      this.refreshInFlight = null;
    });
    await this.refreshInFlight;
  }

  private async doRefresh(): Promise<void> {
    const session = this.sessionStore.load();
    if (!session) {
      throw new AuthRequiredError();
    }
    const response = await this.rawFetch("/auth/refresh", {
      method: "POST",
      headers: {
        Accept: "application/json",
        Cookie: `${session.refreshCookieName}=${session.refreshToken}`,
      },
    });
    const text = await response.text();
    if (!response.ok) {
      this.sessionStore.clear();
      throw new AuthRequiredError();
    }
    const body = text ? (JSON.parse(text) as Record<string, unknown>) : {};
    try {
      this.persistSessionFromAuthResponse(response, body);
    } catch {
      this.sessionStore.clear();
      throw new AuthRequiredError();
    }
  }

  private withQuery(
    path: string,
    params: Record<string, string | undefined>,
  ): string {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "") {
        search.set(key, value);
      }
    }
    const qs = search.toString();
    return qs ? `${path}?${qs}` : path;
  }

  private async requestJson<T = unknown>(
    path: string,
    init: RequestInit = {},
    allowRefresh = true,
  ): Promise<T> {
    const session = this.sessionStore.load();
    if (!session) {
      throw new AuthRequiredError();
    }

    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${session.accessToken}`);
    headers.set("Accept", "application/json");
    if (init.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    const response = await this.rawFetch(path, {
      ...init,
      headers,
    });

    if (response.status === 401 && allowRefresh) {
      await this.refreshSession();
      return this.requestJson(path, init, false);
    }

    const text = await response.text();
    if (!response.ok) {
      if (response.status === 401) {
        this.sessionStore.clear();
        throw new AuthRequiredError();
      }
      throw new ApiError(response.status, text);
    }
    if (!text) {
      return null as T;
    }
    return JSON.parse(text) as T;
  }

  private rawFetch(path: string, init: RequestInit): Promise<Response> {
    return this.fetchImpl(`${this.apiBaseUrl}${path}`, init);
  }
}
