import { describe, expect, it, vi } from "vitest";
import {
  ApiError,
  AuthRequiredError,
  type ControleRotinaApiClient,
} from "./api-client.js";
import { AUTH_REQUIRED_ACTION, createMcpServer, errorResult, MCP_TOOL_NAMES } from "./server.js";
import { MemorySessionStore, type McpSession } from "./session-store.js";

const TAREFA_ID = "11111111-1111-1111-1111-111111111111";
const PLANO_ID = "22222222-2222-2222-2222-222222222222";

const sessao: McpSession = {
  accessToken: "access",
  refreshToken: "refresh",
  refreshCookieName: "refresh_token",
  apiBaseUrl: "http://localhost:8080",
};

function fakeClient(
  overrides: Partial<ControleRotinaApiClient> = {},
): ControleRotinaApiClient {
  return {
    login: vi.fn(),
    logout: vi.fn(),
    authStatus: vi.fn().mockReturnValue({ hasSession: true }),
    getUserEmail: vi.fn(),
    listTarefas: vi.fn(),
    getTarefa: vi.fn(),
    createTarefa: vi.fn(),
    updateTarefa: vi.fn(),
    getCapacidadeResolvida: vi.fn(),
    setCapacidadeOverride: vi.fn(),
    getPlano: vi.fn(),
    gerarPlano: vi.fn(),
    addBlocoPlano: vi.fn(),
    removeBlocoPlano: vi.fn(),
    aprovarPlano: vi.fn(),
    listExecucoes: vi.fn(),
    iniciarExecucao: vi.fn(),
    concluirExecucao: vi.fn(),
    abortarExecucao: vi.fn(),
    historicoDia: vi.fn(),
    historicoSemana: vi.fn(),
    ...overrides,
  } as unknown as ControleRotinaApiClient;
}

type ToolResult = {
  content: { type: string; text: string }[];
  isError?: boolean;
};

function registeredHandler(
  server: ReturnType<typeof createMcpServer>,
  name: string,
): (args: Record<string, unknown>) => Promise<ToolResult> {
  const tools = (
    server as unknown as {
      _registeredTools: Record<
        string,
        { handler: (args: Record<string, unknown>, extra?: unknown) => Promise<ToolResult> }
      >;
    }
  )._registeredTools;
  const tool = tools[name];
  if (!tool) {
    throw new Error(`tool ${name} not registered`);
  }
  return (args) => tool.handler(args, {});
}

function payloadOf(result: ToolResult): Record<string, unknown> {
  return JSON.parse(result.content[0]!.text) as Record<string, unknown>;
}

describe("MCP tool surface", () => {
  it("deveExporSomenteAsToolsPermitidasNesteCorte", () => {
    expect([...MCP_TOOL_NAMES].sort()).toEqual(
      [
        "abortar_execucao",
        "add_bloco_plano",
        "aprovar_plano",
        "auth_status",
        "concluir_execucao",
        "create_tarefa",
        "gerar_plano",
        "get_capacidade_resolvida",
        "get_plano",
        "get_tarefa",
        "get_user_email",
        "historico_dia",
        "historico_semana",
        "iniciar_execucao",
        "list_execucoes",
        "list_tarefas",
        "login",
        "logout",
        "remove_bloco_plano",
        "set_capacidade_override",
        "update_tarefa",
      ].sort(),
    );
  });

  it("naoDeveExporToolsProibidas", () => {
    const forbidden = [
      "verify_mfa",
      "update_user",
      "get_user",
      "delete_tarefa",
      "restore_tarefa",
      "list_projetos",
      "create_projeto",
      "list_habitos",
      "get_calendario",
      "aceitar_proposta",
    ];
    for (const name of forbidden) {
      expect(MCP_TOOL_NAMES).not.toContain(name);
    }
  });
});

describe("errorResult", () => {
  it("deveDevolverAUTH_REQUIREDApontandoParaOCli", () => {
    const result = errorResult(new AuthRequiredError());
    expect(result.isError).toBe(true);
    const payload = JSON.parse(result.content[0]!.text) as {
      code: string;
      action: string;
      message: string;
    };
    expect(payload.code).toBe("AUTH_REQUIRED");
    expect(payload.action).toBe(AUTH_REQUIRED_ACTION);
    expect(payload.action).toMatch(/auth-login/);
    expect(payload.message).toMatch(/auth-login/);
  });

  it("deveDevolverStatusEBodyParseadoDeApiError", () => {
    const result = errorResult(
      new ApiError(400, '{"title":"Bad Request","detail":"campo inválido"}'),
    );
    expect(result.isError).toBe(true);
    const payload = JSON.parse(result.content[0]!.text) as {
      status: number;
      body: { title: string };
    };
    expect(payload.status).toBe(400);
    expect(payload.body.title).toBe("Bad Request");
  });

  it("deveManterBodyComoTextoQuandoJsonDaApiEInvalido", () => {
    const result = errorResult(new ApiError(500, "<html>oops</html>"));
    const payload = JSON.parse(result.content[0]!.text) as {
      status: number;
      body: unknown;
    };
    expect(payload.status).toBe(500);
    expect(payload.body).toBe("<html>oops</html>");
  });

  it("devePropagar401ComTitleDoProblemDetail", () => {
    const result = errorResult(new ApiError(401, '{"title":"Unauthorized"}'));
    const payload = JSON.parse(result.content[0]!.text) as {
      status: number;
      body: { title: string };
    };
    expect(result.isError).toBe(true);
    expect(payload.status).toBe(401);
    expect(payload.body.title).toBe("Unauthorized");
  });

  it("devePropagar404ComTitleDoProblemDetail", () => {
    const result = errorResult(new ApiError(404, '{"title":"Tarefa não encontrada"}'));
    const payload = JSON.parse(result.content[0]!.text) as {
      status: number;
      body: { title: string };
    };
    expect(payload.status).toBe(404);
    expect(payload.body.title).toBe("Tarefa não encontrada");
  });

  it("devePropagar409ComTitleDoProblemDetail", () => {
    const result = errorResult(new ApiError(409, '{"title":"Plano já aprovado"}'));
    const payload = JSON.parse(result.content[0]!.text) as {
      status: number;
      body: { title: string };
    };
    expect(payload.status).toBe(409);
    expect(payload.body.title).toBe("Plano já aprovado");
  });

  it("deveDevolverMensagemDeErrorGenerico", () => {
    const result = errorResult(new Error("ECONNREFUSED"));
    expect(result.isError).toBe(true);
    expect(result.content[0]!.text).toBe("ECONNREFUSED");
  });

  it("deveDevolverUnknownErrorQuandoValorNaoEError", () => {
    const result = errorResult("boom");
    expect(result.isError).toBe(true);
    expect(result.content[0]!.text).toBe("Unknown error");
  });
});

describe("createMcpServer tool errors", () => {
  it("deveDevolverTarefaQuandoGetTarefaOk", async () => {
    const client = fakeClient({
      getTarefa: vi.fn().mockResolvedValue({ id: TAREFA_ID, titulo: "Estudar" }),
    });
    const handler = registeredHandler(createMcpServer(client), "get_tarefa");
    const result = await handler({ id: TAREFA_ID });
    expect(result.isError).toBeUndefined();
    expect(payloadOf(result)).toMatchObject({ id: TAREFA_ID, titulo: "Estudar" });
  });

  it("deveDevolverAuthStatusSemExporTokens", async () => {
    const client = fakeClient({
      authStatus: vi.fn().mockReturnValue({ hasSession: true }),
    });
    const handler = registeredHandler(createMcpServer(client), "auth_status");
    const result = await handler({});
    expect(payloadOf(result)).toEqual({ hasSession: true });
    expect(JSON.stringify(result)).not.toMatch(/access|refresh/i);
  });

  it("deveListarTarefasNoCaminhoFeliz", async () => {
    const client = fakeClient({
      listTarefas: vi.fn().mockResolvedValue({ content: [] }),
    });
    const handler = registeredHandler(createMcpServer(client), "list_tarefas");
    const result = await handler({ status: ["PENDENTE"], page: 0, size: 20 });
    expect(payloadOf(result)).toEqual({ content: [] });
    expect(client.listTarefas).toHaveBeenCalledWith({
      status: ["PENDENTE"],
      prioridade: undefined,
      page: 0,
      size: 20,
    });
  });

  it("deveLogoutComSucesso", async () => {
    const client = fakeClient({
      logout: vi.fn().mockResolvedValue(undefined),
    });
    const handler = registeredHandler(createMcpServer(client), "logout");
    const result = await handler({});
    expect(payloadOf(result)).toEqual({ loggedOut: true });
    expect(client.logout).toHaveBeenCalledOnce();
  });

  it("devePropagarAUTH_REQUIREDQuandoSessaoExpiraNoMeioDoFluxoSemAlterarSessaoLocal", async () => {
    const store = new MemorySessionStore(sessao);
    const client = fakeClient({
      getTarefa: vi.fn().mockRejectedValue(new AuthRequiredError()),
    });
    const handler = registeredHandler(createMcpServer(client), "get_tarefa");

    const result = await handler({ id: TAREFA_ID });

    expect(result.isError).toBe(true);
    expect(payloadOf(result)).toMatchObject({ code: "AUTH_REQUIRED" });
    expect(store.load()).toEqual(sessao);
  });

  it("devePropagar404DaApiComTitleAoBuscarTarefa", async () => {
    const client = fakeClient({
      getTarefa: vi
        .fn()
        .mockRejectedValue(new ApiError(404, '{"title":"Tarefa não encontrada"}')),
    });
    const handler = registeredHandler(createMcpServer(client), "get_tarefa");
    const result = await handler({ id: TAREFA_ID });
    expect(result.isError).toBe(true);
    expect(payloadOf(result)).toEqual({
      status: 404,
      body: { title: "Tarefa não encontrada" },
    });
  });

  it("devePropagar409DaApiComTitleAoAprovarPlano", async () => {
    const client = fakeClient({
      aprovarPlano: vi
        .fn()
        .mockRejectedValue(new ApiError(409, '{"title":"Plano já aprovado"}')),
    });
    const handler = registeredHandler(createMcpServer(client), "aprovar_plano");
    const result = await handler({ planoId: PLANO_ID });
    expect(result.isError).toBe(true);
    expect(payloadOf(result)).toEqual({
      status: 409,
      body: { title: "Plano já aprovado" },
    });
  });

  it("devePropagar401DaApiComTitleNoLogin", async () => {
    const client = fakeClient({
      login: vi.fn().mockRejectedValue(new ApiError(401, '{"title":"Unauthorized"}')),
    });
    const handler = registeredHandler(createMcpServer(client), "login");
    const result = await handler({ email: "user@example.com", password: "secret" });
    expect(result.isError).toBe(true);
    expect(payloadOf(result)).toEqual({
      status: 401,
      body: { title: "Unauthorized" },
    });
    expect(JSON.stringify(result)).not.toContain("secret");
  });
});
