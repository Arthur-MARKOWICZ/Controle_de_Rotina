# Proposal

## Why

A API já calcula o progresso do objetivo, ponderado por tempo (#93, change `objetivos-progresso`), e o devolve em `ObjetivoResponse.progresso`. A tela ainda não mostra esse dado: o card exibe um espaço reservado e a página de detalhe (#92) não tem progresso nenhum. Esta change é a issue #94, que torna o progresso visível. Ela fecha o item "Barra de completude do objetivo" da #65.

## What Changes

- **Componente `ObjetivoProgresso`**, usado no card e no detalhe:
  - barra com o percentual e um texto de apoio em tempo, por exemplo "2h de 5h concluídas" (mesmo formato de horas e minutos usado na capacidade do dia);
  - acessível: `role="progressbar"` com `aria-valuenow`, `aria-valuemin` 0, `aria-valuemax` 100, `aria-valuetext` com o texto em tempo e um nome que cita o título do objetivo;
  - usa o `percentual` que vem da API, sem recalcular. Valores fora de 0–100 são limitados a esse intervalo na barra e no `aria-valuenow`;
  - `progresso` nulo, ou `minutosTotais` ≤ 0, conta como "sem progresso": não há barra nem 0%.
- **Card na lista:** a barra entra no lugar do espaço reservado, nas três visões. Concluídos e arquivados mostram o valor congelado que a API devolve, e no arquivado a barra fica atenuada junto com o card. Sem progresso, o card mostra o convite "Vincule tarefas, hábitos ou projetos para medir o progresso.", sem barra.
- **Página de detalhe:** a barra aparece entre o cabeçalho e a seção de itens, em tamanho maior. Sem progresso, o texto depende dos itens:
  - sem nenhum item, não há texto extra no lugar da barra, porque a seção de itens já mostra o convite de vínculo;
  - com itens que não somam tempo (só tarefas canceladas, hábitos pausados, projeto sem tarefas), aparece "Os itens vinculados ainda não somam tempo. Tarefas canceladas e hábitos pausados não contam.";
  - enquanto os itens carregam, ou se a busca deles falhar, não há texto extra.
- A barra se atualiza com o objetivo: depois de editar, concluir, arquivar ou reabrir no detalhe, ela mostra o valor que veio na resposta.

**Decisão registrada:** a issue pede "convite para vincular itens" quando `progresso` é nulo. Só que nulo também acontece **com** itens vinculados. No card, que não conhece os itens, o convite foi mantido. No detalhe, o texto distingue os dois casos para não contradizer a lista de itens que aparece logo abaixo.

**Fora do escopo:**
- progresso por item na seção de itens;
- sugerir "Concluir" quando chegar a 100%;
- histórico e evolução do progresso (#95);
- mudanças no backend, no contrato, no MCP ou no mobile.

**Dependência:** esta change modifica o requisito "Card de objetivo" na forma em que a change `objetivos-itens-vinculados-ui` (#92) o deixou. Essa change precisa ser arquivada antes desta, para que o delta parta da spec principal já atualizada.

## Capabilities

### New Capabilities
<!-- Nenhuma: progresso na tela é comportamento de objetivo na UI e fica em `ui-objetivo`. -->

### Modified Capabilities
- `ui-objetivo`:
  - "Card de objetivo" deixa de ter um espaço reservado e passa a mostrar a barra ou o convite.
  - Entram requisitos novos: progresso no detalhe e acessibilidade e limites do indicador de progresso.

## Impact

- **Frontend web:**
  - `src/objetivo/`: novo `ObjetivoProgresso.tsx` com teste, ajustes em `ObjetivoCard.tsx` e `pages/ObjetivoDetalhePage.tsx`, e os testes de cada um. As fixtures com `progresso: null` continuam valendo.
  - `styles/objetivos.css`: troca do bloco tracejado `.objetivo-card__progresso` pela barra, mais a variante maior do detalhe.
  - `formatarCapacidadeMinutos` (`planner/data.ts`) é reaproveitado no texto em tempo.
- **Backend, contrato, proxy, MCP e mobile:** nada muda. O `progresso` já está em `contract/openapi.json` e em `src/api/schema.d.ts`.
- **Cobertura:** os testes novos contam para o gate do frontend, e o limite só sobe.
