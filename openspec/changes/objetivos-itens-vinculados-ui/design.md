# Design

## Context

A motivação está em `proposal.md`, e o comportamento em `specs/ui-objetivo/spec.md`.

**O backend já está pronto.**
- `GET /objetivos/{id}/itens` devolve `ObjetivoItensResponse`. As tarefas vêm com `origem` e `projetoNome`.
- `TarefaResponse`, `HabitoResponse` e `ProjetoResponse` trazem `objetivos: ObjetivoResumoResponse[]` (`id`, `titulo`, `status`), só com os vínculos diretos.
- Os creates e os PATCHes aceitam `objetivoIds`. No PATCH é um replace-set, com o mesmo comportamento de `tagIds`.
- O backend valida ATIVO só nos ids **novos** (`incoming - atuais`).
- Subtarefa com objetivo recebe 400.
- Os tipos já estão em `src/api/schema.d.ts`, e o prefixo `/objetivos` já está no proxy.

**O que já existe no front:**
- `TagPicker`: chips mais `Popover` com busca. Mescla `catalogo` com `selectedTags` para mostrar chips que não estão no catálogo.
- O padrão de estado de tags nos forms: `tagIds` + `tagsSelecionadas` + snapshot `inicial` para o "alterado".
- O hub `ProjetoFormPage`, uma página própria em `/projetos/:id` com ações de ciclo de vida que atualizam o estado local.
- O `ObjetivoDrawer`, que cria e edita e se comunica com o pai por `useOutletContext<ObjetivosOutletContext>`.
- `PAGINAS_COM_DRAWER` no `AppShell`, que decide quando não mandar o foco para o `h1`.

## Goals / Non-Goals

**Goals:**
- Reaproveitar o `ObjetivoDrawer` na edição, mudando só a navegação de fechar.
- Um picker de objetivos com o mesmo formato visual e de props do `TagPicker`, para os forms ligarem como já ligam tags.
- O detalhe carrega o objetivo e os itens em paralelo, e uma falha num não esconde o outro.

**Non-Goals:**
- Um `MultiPicker` genérico compartilhado com tags.
- Cache ou store global de objetivos ativos (cada picker busca ao montar).
- Paginar os itens.

## Decisions

### 1. Rotas

```
/objetivos                    ObjetivosPage           (lista)
  nova                        ObjetivoDrawer criar    (filho, sobre a lista)
/objetivos/:id                ObjetivoDetalhePage     (rota própria, como /projetos/:id)
  editar                      ObjetivoDrawer editar   (filho, sobre o detalhe)
```

Em `App.tsx`, `:id` sai de baixo de `ObjetivosPage` e vira uma rota irmã com o filho `editar`.

O `ObjetivoDetalhePage` fornece o mesmo `ObjetivosOutletContext` (`aoSalvar`), então o drawer não precisa saber quem é o pai.

**Alternativa descartada:** manter `/objetivos/:id` como drawer e colocar o detalhe em `/objetivos/:id/detalhe`. Ficaria com uma URL estranha para a tela principal do objetivo, e a #94 e a #95 vão morar no detalhe.

### 2. Navegação de fechar do drawer

Em `voltarParaLista`, que passa a se chamar `fechar`:
- **criar:** sem mudança. Usa `daLista` → `navigate(-1)`, depois de criar → `/objetivos`, e senão volta para `/objetivos` mantendo o `search`.
- **editar:** sempre `navigate({ pathname: '/objetivos/:id' }, { replace: true, state })`. Não usa `navigate(-1)`: entrando pelo "Editar" do card, o passo anterior no histórico é a lista, e a spec manda voltar ao detalhe.

O `state` repassado leva `listaSearch` (ver 3) para o "Voltar" do detalhe continuar certo.

Na edição, o drawer continua buscando o objetivo por conta própria (`buscarObjetivo`). Assim ele não depende de o detalhe ter terminado de carregar, e os testes do drawer continuam valendo.

### 3. Voltar à mesma visão da lista

O título e o "Editar" do card passam `state: { listaSearch: location.search }`. O `ObjetivoDetalhePage` lê esse valor e monta o link "Voltar a Objetivos" como `/objetivos` + `listaSearch`, com `/objetivos` puro quando não houver. O "Editar" do detalhe repassa o mesmo `state`.

Isso vai em `location.state`, e não em query string da URL do detalhe, para não sujar uma URL que pode ser compartilhada.

O `daLista` do card de edição deixa de existir: só a criação usa.

### 4. Foco no `AppShell`

Mudanças em `PAGINAS_COM_DRAWER`:
- **Lista:** a regex `drawer` passa de `/^\/objetivos\/[^/]+$/` para `/^\/objetivos\/nova$/`. Sem isso, lista → detalhe seria tratado como "mesma página com drawer", e o foco não iria para o `h1` do detalhe.
- **Detalhe:** entra uma nova entrada. Hoje `base` é uma string, então o tipo passa a aceitar `base: string | RegExp` e `drawer: RegExp`.
  - Detalhe ↔ editar só conta como a mesma página se o `:id` for igual. Para isso, `mesmaPaginaComDrawer` compara o prefixo `/objetivos/:id`.

O caminho mais simples é uma função `mesmoObjetivo(anterior, atual)` dedicada, em vez de generalizar a tabela inteira. Quem implementar escolhe, desde que os testes de `AppShell` cubram:
- lista → detalhe (foco vai para o `h1`);
- detalhe → editar e editar → detalhe (foco não pula);
- detalhe A → detalhe B (foco vai para o `h1`).

### 5. `ObjetivoDetalhePage`

O estado é independente: `objetivo` / `erroObjetivo` / `carregandoObjetivo` e `itens` / `erroItens` / `carregandoItens`. Duas buscas em paralelo no `useEffect([id, recarregarEm])`, com `ativo` para descartar respostas antigas quando o `id` muda.

**Ações:**
- Copiam o fluxo do hub de projeto: `confirm` em Concluir e Arquivar, trava `acaoRef` contra clique duplo, `setObjetivo(atualizado)` mais toast no sucesso.
- No erro: `InlineAlert` com `ApiError.message` ou o fallback, e recarga só do objetivo.
- Os rótulos e a matriz status × ações vêm de `ObjetivoCard`, extraídos para `objetivo/acoesObjetivo.ts` e compartilhados pelo card e pelo detalhe.

`aoSalvar` do outlet incrementa `recarregarEm` e recarrega o objetivo e os itens. É barato e evita uma rota especial.

**Prazo:** reusa `prazoObjetivo.ts` (vencido, "vence hoje", "concluído em").

**Itens** ficam num `ObjetivoItensSecao` à parte, para testar estados e grupos isolados da página:
- Links: tarefa `/tarefas/:id`, hábito `/habitos/:id`, projeto `/projetos/:id`.
- Selo `via projeto {projetoNome}` quando `origem === 'VIA_PROJETO'`.
- Grupo vazio não é renderizado. Os três vazios mostram o estado vazio.

**Rótulos de status dos itens:** hoje `STATUS_LABEL` está duplicado dentro de `ProjetosPage`, `ProjetoFormPage` e `HabitoCard`. Esta change não unifica isso. A seção de itens reusa `STATUS_TAREFA_LABEL` (`home/labels`) para tarefas e declara só os mapas de hábito e projeto, tipados pelos enums do `schema.d.ts`; evita mexer nas telas fora do escopo.

### 6. `ObjetivoPicker`

Fica em `objetivo/ObjetivoPicker.tsx`, com a mesma forma de props do `TagPicker`:

```ts
type Props = {
  selectedIds: string[]
  selectedObjetivos?: ObjetivoResumo[]   // do item carregado: inclui concluídos/arquivados
  onChange: (ids: string[], objetivos: ObjetivoResumo[]) => void
  disabled?: boolean
}
```

- **Opções:** `listarObjetivos({ status: 'ATIVO', size: 100 })`. 100 é o `SIZE_MAX` do backend, e não há paginação no picker porque o uso é pessoal.
- **Mescla:** os chips vêm de `opcoes ∪ selectedObjetivos`, como o `mesclarTags`. Os chips cujo `status !== 'ATIVO'` mostram o status em texto ("Concluído" / "Arquivado").
  - Esses objetivos nunca entram em `filtradas`, porque não estão em `opcoes`, e a regra "não pode ser escolhido de novo" sai disso sem nenhum código extra.
  - Remover o chip tira o id da lista, e o PATCH seguinte desvincula.
- **Sem criação inline e sem "Gerenciar":** no estado vazio aparece "Nenhum objetivo ativo" com um `Link` para `/objetivos`.
  - Busca sem resultado mostra "Nenhum objetivo com esse termo".
  - Falha ao carregar mostra `role="alert"` e mantém os chips.
- **Ids de acessibilidade:** usa `useId()`, e não um id fixo como o `tag-picker-label`, porque o hub de projeto pode montar dois forms.

**Alternativa descartada:** generalizar o `TagPicker`. As diferenças são criar inline, "Gerenciar", `TagChip` com cor e opções filtradas por status. Generalizar viraria um componente cheio de flags para dois usos.

### 7. Ligação nos forms

O mesmo padrão das tags em cada form: estado `objetivoIds` + `objetivosSelecionados`, campo no snapshot `inicial` e na comparação de `alterado`, e o picker renderizado logo abaixo do `TagPicker`.

| Form | Onde | Envio |
|---|---|---|
| Tarefa | `TarefaForm` → `EdicoesAvancadasDialog` (onde já ficam as tags; props novas) + `TarefaDrawer` | criar: `objetivoIds`; editar: `objetivoIds` **só se** `!tarefa.parentId` |
| Hábito | `HabitoCampos` (props novas) + `HabitoDrawer` | criar e editar: `objetivoIds` |
| Projeto | `ProjetoCampos` (props opcionais, como tags) + `ProjetoDrawer` e `ProjetoFormPage` | criar e editar: `objetivoIds` |

**Subtarefa:**
- `TarefaForm` recebe `mostrarObjetivos` (false quando `tarefa?.parentId`), e o drawer omite `objetivoIds` do PATCH.
- Omitir é diferente de mandar `[]`. Os dois passam no backend, mas omitir não toca nos vínculos e deixa claro que a subtarefa não tem esse campo.

**Erro 409 "Objetivo não está ativo":** cai no tratamento que os forms já têm (`ApiError.message` no alerta do form, que continua aberto). Não precisa de código novo além do teste.

**Fora:** QuickAdd e InlineAdd não recebem o picker, e seguem criando sem `objetivoIds`.

### 8. Cliente

Em `objetivoApi.ts`:
- `ObjetivoItens = Schemas['ObjetivoItensResponse']`;
- `ObjetivoResumo = Schemas['ObjetivoResumoResponse']`;
- `listarItensObjetivo(id)` com o fallback "Não foi possível carregar os itens vinculados.".

O teste segue a matriz padrão de cliente: 2xx, 401→refresh, 404, 500/timeout e HTML do proxy.

## Risks / Trade-offs

- **[Testes da #90 que usam `/objetivos/:id` como drawer quebram]** → Fazem parte das tarefas: atualizar `ObjetivosPage.test`, `ObjetivoDrawer.test`, `ObjetivoCard.test`, `AppShell.test` e `documentTitle.test` na mesma tarefa da rota.
- **[Link antigo `/objetivos/:id` salvo pelo usuário]** → Agora abre o detalhe, que tem "Editar" a um clique. Não precisa de redirecionamento.
- **[Cada form monta um picker que faz `GET /objetivos?status=ATIVO`]** → É uma requisição por abertura de form, igual às tags. Aceito.
- **[Mais de 100 objetivos ativos]** → Os excedentes não aparecem como opção. Na escala pessoal isso não deve acontecer, e o risco fica registrado.
- **[A seção de itens fica velha se outra aba mudar vínculos]** → Recarrega ao montar e depois de salvar no drawer. Não há polling, como no resto do app.
- **[Rótulos de status de hábito e projeto seguem duplicados]** → Aceito. Unificar é uma limpeza à parte.

## Migration Plan

É só frontend. Deploy normal da imagem do front. O rollback é reverter o deploy, e não há dados nem contrato envolvidos.
