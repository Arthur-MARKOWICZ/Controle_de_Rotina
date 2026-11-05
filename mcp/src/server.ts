import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import {
  ApiError,
  AuthRequiredError,
  ControleRotinaApiClient,
  type Prioridade,
  type StatusTarefa,
} from "./api-client.js";

const uuidSchema = z.string().uuid();

const dateSchema = z
  .string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, "Use YYYY-MM-DD")
  .describe("Data YYYY-MM-DD");

const prioridadeSchema = z.enum(["BAIXA", "MEDIA", "ALTA"]);

const statusTarefaSchema = z.enum([
  "PENDENTE",
  "EM_PROGRESSO",
  "FINALIZADA",
  "CANCELADA",
  "ATRASADA",
]);

/** Texto de ação quando a sessão MCP está ausente/expirada. */
export const AUTH_REQUIRED_ACTION =
  "Rode `cd mcp && pnpm auth-login` no terminal (recomendado). Alternativa: tool login com e-mail e senha.";

function textResult(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}

export function errorResult(error: unknown) {
  if (error instanceof AuthRequiredError) {
    return {
      content: [
        {
          type: "text" as const,
          text: JSON.stringify(
            {
              code: "AUTH_REQUIRED",
              message: error.message,
              action: AUTH_REQUIRED_ACTION,
            },
            null,
            2,
          ),
        },
      ],
      isError: true as const,
    };
  }
  if (error instanceof ApiError) {
    return {
      content: [
        {
          type: "text" as const,
          text: JSON.stringify({ status: error.status, body: safeJson(error.body) }, null, 2),
        },
      ],
      isError: true as const,
    };
  }
  const message = error instanceof Error ? error.message : "Unknown error";
  return {
    content: [{ type: "text" as const, text: message }],
    isError: true as const,
  };
}

function safeJson(body: string): unknown {
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

export const MCP_TOOL_NAMES = [
  "login",
  "logout",
  "auth_status",
  "get_user_email",
  "list_tarefas",
  "get_tarefa",
  "create_tarefa",
  "update_tarefa",
  "get_capacidade_resolvida",
  "set_capacidade_override",
  "get_plano",
  "gerar_plano",
  "add_bloco_plano",
  "remove_bloco_plano",
  "aprovar_plano",
  "list_execucoes",
  "iniciar_execucao",
  "concluir_execucao",
  "abortar_execucao",
  "historico_dia",
  "historico_semana",
] as const;

export function createMcpServer(client: ControleRotinaApiClient): McpServer {
  const server = new McpServer({
    name: "controle-rotina",
    version: "0.1.0",
  });

  server.registerTool(
    "login",
    {
      title: "Entrar na API (fallback)",
      description:
        "Fallback de autenticação. Prefira `pnpm auth-login` no terminal para a senha não entrar no chat. Persiste access+refresh localmente. Não devolve tokens.",
      inputSchema: {
        email: z.string().email(),
        password: z.string().min(1),
      },
    },
    async ({ email, password }) => {
      try {
        return textResult(await client.login(email, password));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "logout",
    {
      title: "Sair",
      description: "Revoga a sessão na API (se possível) e apaga a sessão local do MCP.",
      inputSchema: {},
    },
    async () => {
      try {
        await client.logout();
        return textResult({ loggedOut: true });
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "auth_status",
    {
      title: "Status da sessão",
      description: "Indica se há sessão local salva (sem expor tokens).",
      inputSchema: {},
    },
    async () => textResult(client.authStatus()),
  );

  server.registerTool(
    "get_user_email",
    {
      title: "Consultar e-mail do usuário",
      description:
        "Retorna somente o e-mail da conta autenticada. Não expõe id, nome nem datas do perfil.",
      inputSchema: {},
    },
    async () => {
      try {
        return textResult(await client.getUserEmail());
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "list_tarefas",
    {
      title: "Listar tarefas",
      description: "Lista tarefas do usuário (sem incluir deletadas).",
      inputSchema: {
        status: z.array(statusTarefaSchema).optional(),
        prioridade: z.array(prioridadeSchema).optional(),
        page: z.number().int().min(0).optional(),
        size: z.number().int().min(1).max(100).optional(),
      },
    },
    async (input) => {
      try {
        return textResult(
          await client.listTarefas({
            status: input.status as StatusTarefa[] | undefined,
            prioridade: input.prioridade as Prioridade[] | undefined,
            page: input.page,
            size: input.size,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "get_tarefa",
    {
      title: "Detalhar tarefa",
      description: "Busca uma tarefa pelo id.",
      inputSchema: {
        id: uuidSchema,
      },
    },
    async ({ id }) => {
      try {
        return textResult(await client.getTarefa(id));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "create_tarefa",
    {
      title: "Criar tarefa",
      description: "Cria tarefa (titulo, prioridade, duracaoMinutos; prazo e projeto opcionais).",
      inputSchema: {
        titulo: z.string().min(1).max(120),
        descricao: z.string().max(2000).optional(),
        prioridade: prioridadeSchema,
        prazo: z.string().datetime({ offset: true }).optional().nullable(),
        duracaoMinutos: z.number().int().min(1),
        projetoId: uuidSchema.optional().nullable(),
      },
    },
    async (input) => {
      try {
        return textResult(
          await client.createTarefa({
            titulo: input.titulo,
            descricao: input.descricao,
            prioridade: input.prioridade as Prioridade,
            prazo: input.prazo,
            duracaoMinutos: input.duracaoMinutos,
            projetoId: input.projetoId,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "update_tarefa",
    {
      title: "Atualizar tarefa",
      description: "PATCH parcial da tarefa. Envie projetoId (ou null) para alterar vínculo.",
      inputSchema: {
        id: uuidSchema,
        titulo: z.string().min(1).max(120).optional(),
        descricao: z.string().max(2000).optional(),
        prioridade: prioridadeSchema.optional(),
        prazo: z.string().datetime({ offset: true }).optional().nullable(),
        status: statusTarefaSchema.optional(),
        duracaoMinutos: z.number().int().min(1).optional(),
        projetoId: uuidSchema.optional().nullable(),
      },
    },
    async (input) => {
      try {
        const { id, ...body } = input;
        return textResult(
          await client.updateTarefa(id, {
            titulo: body.titulo,
            descricao: body.descricao,
            prioridade: body.prioridade as Prioridade | undefined,
            prazo: body.prazo,
            status: body.status as StatusTarefa | undefined,
            duracaoMinutos: body.duracaoMinutos,
            projetoId: body.projetoId,
          }),
        );
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "get_capacidade_resolvida",
    {
      title: "Capacidade resolvida",
      description: "Consulta capacidade resolvida no intervalo (dataDe/dataAte opcionais).",
      inputSchema: {
        dataDe: dateSchema.optional(),
        dataAte: dateSchema.optional(),
      },
    },
    async (input) => {
      try {
        return textResult(await client.getCapacidadeResolvida(input));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "set_capacidade_override",
    {
      title: "Definir override de capacidade",
      description: "Cria overrides do dia (lista de { data, horas }).",
      inputSchema: {
        itens: z
          .array(
            z.object({
              data: dateSchema,
              horas: z.number().int().min(0),
            }),
          )
          .min(1),
      },
    },
    async ({ itens }) => {
      try {
        return textResult(await client.setCapacidadeOverride(itens));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "get_plano",
    {
      title: "Consultar plano do dia",
      description: "GET /planos?data=… (versao opcional).",
      inputSchema: {
        data: dateSchema,
        versao: z.number().int().min(1).optional(),
      },
    },
    async ({ data, versao }) => {
      try {
        return textResult(await client.getPlano(data, versao));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "gerar_plano",
    {
      title: "Gerar plano (rascunho)",
      description: "POST /planos/gerar?data=… — nova versão rascunho.",
      inputSchema: {
        data: dateSchema,
      },
    },
    async ({ data }) => {
      try {
        return textResult(await client.gerarPlano(data));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "add_bloco_plano",
    {
      title: "Adicionar bloco ao plano",
      description: "Adiciona bloco de tarefa (ou hábito) a um plano rascunho. Informe exatamente um de tarefaId/habitoId.",
      inputSchema: {
        planoId: uuidSchema,
        tarefaId: uuidSchema.optional(),
        habitoId: uuidSchema.optional(),
      },
    },
    async ({ planoId, tarefaId, habitoId }) => {
      try {
        return textResult(await client.addBlocoPlano(planoId, { tarefaId, habitoId }));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "remove_bloco_plano",
    {
      title: "Remover bloco do plano",
      description: "Remove bloco de um plano rascunho.",
      inputSchema: {
        planoId: uuidSchema,
        blocoId: uuidSchema,
      },
    },
    async ({ planoId, blocoId }) => {
      try {
        return textResult(await client.removeBlocoPlano(planoId, blocoId));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "aprovar_plano",
    {
      title: "Aprovar plano",
      description: "Aprova o rascunho do plano.",
      inputSchema: {
        planoId: uuidSchema,
      },
    },
    async ({ planoId }) => {
      try {
        return textResult(await client.aprovarPlano(planoId));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "list_execucoes",
    {
      title: "Listar execuções do dia",
      description: "Lista blocos/execuções do dia (data opcional; default hoje na API).",
      inputSchema: {
        data: dateSchema.optional(),
      },
    },
    async ({ data }) => {
      try {
        return textResult(await client.listExecucoes(data));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "iniciar_execucao",
    {
      title: "Iniciar execução",
      description: "Inicia execução de um bloco do plano aprovado.",
      inputSchema: {
        blocoId: uuidSchema,
      },
    },
    async ({ blocoId }) => {
      try {
        return textResult(await client.iniciarExecucao(blocoId));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "concluir_execucao",
    {
      title: "Concluir execução",
      description: "Conclui uma execução em andamento.",
      inputSchema: {
        id: uuidSchema.describe("UUID da execução"),
      },
    },
    async ({ id }) => {
      try {
        return textResult(await client.concluirExecucao(id));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "abortar_execucao",
    {
      title: "Abortar execução",
      description: "Aborta uma execução em andamento.",
      inputSchema: {
        id: uuidSchema.describe("UUID da execução"),
      },
    },
    async ({ id }) => {
      try {
        return textResult(await client.abortarExecucao(id));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "historico_dia",
    {
      title: "Histórico do dia",
      description: "Resumo de histórico do dia (data opcional).",
      inputSchema: {
        data: dateSchema.optional(),
      },
    },
    async ({ data }) => {
      try {
        return textResult(await client.historicoDia(data));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  server.registerTool(
    "historico_semana",
    {
      title: "Histórico da semana",
      description: "Resumo de histórico da semana (semanaInicio opcional).",
      inputSchema: {
        semanaInicio: dateSchema.optional(),
      },
    },
    async ({ semanaInicio }) => {
      try {
        return textResult(await client.historicoSemana(semanaInicio));
      } catch (error) {
        return errorResult(error);
      }
    },
  );

  return server;
}
