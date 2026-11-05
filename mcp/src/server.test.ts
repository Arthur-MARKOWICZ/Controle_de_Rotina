import { describe, expect, it } from "vitest";
import { ApiError, AuthRequiredError } from "./api-client.js";
import { AUTH_REQUIRED_ACTION, errorResult, MCP_TOOL_NAMES } from "./server.js";

describe("MCP tool surface", () => {
  it("exposes only the allowed tools for this ADR cut", () => {
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

  it("does not expose forbidden tools", () => {
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
  it("AUTH_REQUIRED aponta para o CLI auth-login", () => {
    const result = errorResult(new AuthRequiredError());
    expect(result.isError).toBe(true);
    const payload = JSON.parse(result.content[0]!.text) as {
      code: string;
      action: string;
    };
    expect(payload.code).toBe("AUTH_REQUIRED");
    expect(payload.action).toBe(AUTH_REQUIRED_ACTION);
    expect(payload.action).toMatch(/auth-login/);
  });

  it("ApiError devolve status e body parseado", () => {
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
});
