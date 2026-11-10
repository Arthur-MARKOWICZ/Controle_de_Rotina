# Proposal

## Why

Tarefas no plano aprovado só oferecem **Iniciar**; falta **Concluir direto** (já existe para hábitos) e falta executar uma **sessão** a partir de `/tarefas` sem depender do planner. O usuário precisa escolher se trabalha com timer (tempo real) ou marca a sessão na hora (tempo planejado do bloco).

## What Changes

- No Home (plano `APROVADO`): blocos de **tarefa** ganham CTA **Concluir** ao lado de **Iniciar**, espelhando hábitos.
- Em `/tarefas`: CTAs **Iniciar** e **Concluir** para tarefas com restante > 0; antes de cada ação, um **picker de duração da sessão** (presets 15/25/30/45/60 + Restante + custom), com piso 1 min e teto = restante.
- Novo status de plano implícito **`TAREFA_AVULSA`** (espelho de `HABITO_AVULSO`): materializa bloco + execução 1:1 no dia, ignorado pela corrente do planner.
- Se a tarefa já tem bloco **aberto** (não `CONCLUIDO`) no plano `APROVADO` de hoje, Iniciar/Concluir da lista **reusam** esse bloco (sem picker).
- Contabilização de tempo da sessão:
  - **Iniciar → Concluir**: tempo efetivo = decorrido do timer (regra atual de `XpMotor.minutosEfetivos`, teto do bloco).
  - **Concluir direto**: tempo efetivo = duração planejada do bloco (crédito cheio; corrige o padrão de intervalo zero).
- Progresso da tarefa permanece por **soma de durações de blocos `CONCLUIDO`** (modelo sessão); `FINALIZADA` quando soma ≥ `tarefa.duracaoMinutos`.
- **Barra de sessão ativa global** no AppShell (qualquer rota): timer restante + Concluir + Abortar, reutilizando o painel que hoje só vive no Home.

Fora de escopo: pause/resume; concluir a tarefa inteira sem bloco/sessão; sessões acima do restante; execução avulsa multi-dia; mudança na fórmula 80% do XP no caminho com timer.

## Capabilities

### New Capabilities

- `tarefa-sessao`: iniciar e concluir sessões de trabalho em tarefa (bloco do plano ou avulso `TAREFA_AVULSA`), escolha de duração, regras de tempo efetivo e progresso.
- `ui-tarefa-sessao`: CTAs no Home e em `/tarefas`, picker de duração, barra global de sessão ativa.

### Modified Capabilities

- (nenhuma — inventário OpenSpec de specs principais ainda vazio; comportamento atual de execução/hábito fica documentado só por contraste no design)

## Impact

- Backend: `StatusPlano.TAREFA_AVULSA`; materialização de slot avulso; endpoints sob `/tarefas/{id}/iniciar` e `/tarefas/{id}/concluir-direto` (com `duracaoMinutos` e `data` opcional); conclusão direta de bloco de tarefa no `ExecucaoService`; crédito XP de concluir-direto = duração do bloco; proxy Vite/Nginx se prefixo já for `/tarefas` (sem prefixo novo).
- Frontend: `HomePage`/`BlocoCard` CTAs de tarefa; `TarefaCard` + dialog de duração; elevar sessão ativa para `AppShell`; API client e testes.
- MCP (opcional/follow-up): tools espelhando iniciar/concluir-direto de tarefa — fora do MVP desta change salvo se o apply incluir a lista ADR.
- Planos de negócio existentes (`execucao.md`, `habito-sem-plano.md`) são referência; esta change não os reescreve automaticamente.
