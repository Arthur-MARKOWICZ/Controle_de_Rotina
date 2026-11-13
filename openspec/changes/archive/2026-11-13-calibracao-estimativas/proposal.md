# Proposal

## Why

O planner confia na duração que o usuário informa. Quem subestima recebe um plano que "cabe" no papel e estoura todo dia. O sistema já grava quando cada bloco começou e terminou (`Execucao.iniciadoEm` / `concluidoEm`), mas não devolve isso ao usuário. A issue [#71](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/71) (OPP-011) pede a fase 1: **mostrar** quanto o usuário costuma levar em relação ao estimado, sem mexer no motor e sem IA.

## What Changes

- Nova leitura `GET /historico/calibracao` (autenticada, só dados do próprio usuário) com a razão **tempo real ÷ tempo planejado** dos blocos de tarefa concluídos nos últimos 90 dias (BRT), em três recortes: **geral**, **por tag** e **por projeto**.
- Cada recorte devolve o **N de amostras**, a razão e o desvio médio em minutos. Abaixo do mínimo de **5 amostras** o recorte vem marcado como **sem dados** e **sem número** (razão e desvio nulos).
- Descarte de amostras não confiáveis: execuções abortadas/não concluídas, blocos de hábito, conclusões diretas (sem timer, início = fim) e outliers de razão fora de **[0,25 ; 4,0]** (clique acidental ou sessão esquecida aberta).
- Cartão **"Seu ritmo real"** na tela Histórico (web): frase geral (ex.: "você costuma levar 1,4× o estimado") e os recortes por tag/projeto que têm amostras suficientes; estado "ainda sem dados" com quantas amostras faltam.
- O desvio médio geral serve de baseline para a métrica de sucesso da issue (|real − planejado| por bloco concluído).
- Proxy: sem prefixo novo de controller (`/historico` já existe), mas o `vite.config.ts` lista subpaths exatos de `/historico/*` — entra `/historico/calibracao` lá e no `API_PROXY.md`. O `nginx.conf` já cobre via `location ^~ /historico/`.

Fora de escopo: aplicar o fator no motor de planejamento (fase 2, opt-in, issue separada); qualquer uso de IA; app mobile e MCP (leem a mesma API depois, se quiserem); escolher a janela de tempo pela UI; recorte por prioridade ou por tarefa individual.

## Capabilities

### New Capabilities

- `calibracao-estimativa`: leitura da razão real ÷ planejado por usuário (geral, por tag, por projeto) com seleção de amostras, descarte de outliers, mínimo de amostras e janela fixa.
- `ui-calibracao-estimativa`: cartão no Histórico que exibe a razão geral e os recortes por tag/projeto, com estados de carregamento, sem dados e erro.

### Modified Capabilities

(nenhuma — não há spec de histórico em `openspec/specs/`; os resumos dia/semana não mudam)

## Impact

- Backend: `historico` ganha endpoint, service e um calculador puro; `ExecucaoRepository` ganha uma consulta de amostras (execução + bloco + tarefa); usa `TarefaTagRepository`, `TagRepository` e `ProjetoRepository` para os rótulos. Sem migração Flyway.
- Frontend: `historicoApi.ts` (novo tipo + função), novo componente de cartão em `src/historico/`, `HistoricoPage.tsx` passa a renderizá-lo; testes de API, componente e página.
- Proxy: `frontend/frontend-web/vite.config.ts` e `API_PROXY.md` (subpath novo); `nginx.conf` sem mudança.
- Testes: regra 1 + 3 + 3 do AGENTS.md em calculador, service/endpoint, cliente de API e componente; gate de cobertura não pode cair.
