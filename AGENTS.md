## O que é este projeto

Assistente de **planejamento automático de rotina** com **gamificação leve por XP**.

O usuário informa o que precisa fazer, quanto tempo estima e até quando precisa estar pronto. O sistema gera um plano executável; o usuário aprova ou ajusta.

**Diferencial:** motor de planejamento — não um Todoist com XP, nem um RPG de produtividade.

Documento de visão: `assistente_planejamento_rotina_gamificado.md`  
Decisões de arquitetura: `Decisoes_arqutetura.md`  
Planos de features: `plans/`  
Ordem dos slices: `ordem_execucao/docs.md`  
Skills de plano: `.agents/criar_plano` (escrever/reescrever), `.agents/revisao_plano` (criticar antes de fechar)

## Proxy front → API (não esquecer)

Todo **prefixo novo** de controller (`@RequestMapping("/…")`) precisa entrar no proxy do front:

1. `frontend/frontend-web/vite.config.ts` (`server.proxy`) — `npm run dev`
2. `frontend/frontend-web/nginx.conf` — **rebuild** da imagem Docker do frontend
3. Atualizar a lista em `frontend/frontend-web/API_PROXY.md`

Sem isso, o front recebe `index.html` e aparece erro `Unexpected token '<', "<!doctype…"`.  
Detalhes e checklist: `frontend/frontend-web/API_PROXY.md`.
