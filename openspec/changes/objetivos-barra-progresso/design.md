# Design

## Context

Motivação em `proposal.md`, comportamento em `specs/ui-objetivo/spec.md`.

Estado atual da branch `objetivos`:

- `ObjetivoResponse.progresso` já está tipado em `src/api/schema.d.ts` como `{ percentual, minutosConcluidos, minutosTotais } | null` e chega na listagem, no `GET /objetivos/{id}` e nas respostas de concluir, arquivar, reabrir e editar.
- `ObjetivoCard` tem o bloco `.objetivo-card__progresso`, tracejado e com `min-height: 2rem`, reservado para esta troca.
- `ObjetivoDetalhePage` mantém `objetivo` e `itens` em estados separados, com fetches independentes. Após uma ação, `objetivo` é substituído pela resposta da API. Após salvar no drawer de edição, os dois são relidos.
- `ObjetivoItensSecao` já cobre o caso "Nenhum item vinculado" com o convite de vínculo.
- Existem `role="progressbar"` em `XpBar` e `CapacidadeBar`, cada um com marcação e CSS próprios. Não há componente de barra genérico.
- `formatarCapacidadeMinutos` (`planner/data.ts`) gera `0min`, `45min`, `2h` e `1h 30min`.

## Goals / Non-Goals

**Goals:**

- Um componente de apresentação puro, sem fetch e sem relógio, testável só com props.
- A decisão do texto de "sem progresso" no detalhe fica na página, que é quem conhece os itens. O componente não recebe os itens.

**Non-Goals:**

- Extrair uma barra genérica que `XpBar` e `CapacidadeBar` passem a usar.
- Animar a mudança de valor além da transição de largura já usada no XP.

## Decisions

### 1. `ObjetivoProgresso` com variante e texto vazio injetado

```
ObjetivoProgresso({
  titulo: string,                     // compõe o aria-label
  progresso: ObjetivoProgresso | null,
  tamanho: 'compacto' | 'destaque',   // card | detalhe
  semProgresso: ReactNode | null,     // o que mostrar quando não há progresso
})
```

- O card passa o convite como `semProgresso`.
- O detalhe passa `null` ou o texto "ainda não somam tempo", conforme os itens (ver decisão 3).
- Com `semProgresso` nulo e sem progresso, o componente não renderiza nada. Assim o detalhe não fica com um bloco vazio.

**Alternativa descartada:** o componente receber `itens` e decidir o texto sozinho. Isso acoplaria o componente ao formato de `ObjetivoItens` e ao estado de carregamento da seção, e o card teria de passar um valor falso só para preencher a prop.

### 2. Normalização numa função pura

Uma função `normalizarProgresso(progresso)` devolve `{ pct, texto } | null`:

- devolve `null` quando `progresso` é `null` ou `minutosTotais <= 0`;
- `pct = clamp(Math.round(percentual), 0, 100)`. O `round` só existe para o caso de a API mandar um não inteiro, já que o contrato diz inteiro;
- `texto = "${formatar(minutosConcluidos)} de ${formatar(minutosTotais)} concluídas"`, com `formatarCapacidadeMinutos`. Os minutos ficam brutos, sem clamp: se a API mandar 45 de 30, o texto mostra isso, mas a barra para em 100.

O componente lê essa função, e o teste dela cobre os limites sem precisar de DOM.

**Alternativa descartada:** recalcular `concluidos / totais` no front. Com o floor do backend, os dois valores divergiriam em ±1 e o mesmo objetivo poderia mostrar números diferentes na lista e no MCP.

### 3. Texto "sem progresso" no detalhe

Na página:

```
itensCarregados = !carregandoItens && erroItens == null && itens != null
temItens        = itensCarregados && (tarefas + habitos + projetos) > 0

semProgresso = temItens ? TEXTO_NAO_SOMAM : null
```

- Com zero itens, a seção abaixo já mostra o convite. Repetir o convite logo acima seria ruído.
- Com os itens carregando ou em erro, não dá para saber o motivo do nulo, então a página não afirma nada.
- O total de itens usa a mesma conta de `ObjetivoItensSecao`. Se ela estiver inline, vira um helper exportado pelo módulo da seção para não duplicar a regra.

### 4. Marcação e acessibilidade

```html
<div class="objetivo-progresso objetivo-progresso--compacto">
  <div class="objetivo-progresso__track"
       role="progressbar"
       aria-label="Progresso de {titulo}"
       aria-valuemin="0" aria-valuemax="100" aria-valuenow="{pct}"
       aria-valuetext="{pct}%, {texto}">
    <div class="objetivo-progresso__fill" style="width: {pct}%"></div>
  </div>
  <p class="objetivo-progresso__legenda"><strong>{pct}%</strong> · {texto}</p>
</div>
```

- `aria-valuetext` leva o percentual e o tempo, porque o leitor de tela anuncia `valuetext` no lugar de `valuenow`.
- A legenda visível fica com `aria-hidden` para o texto não ser anunciado duas vezes.
- O nome com o título é necessário porque a lista tem várias barras e todas se chamariam "Progresso".

### 5. CSS

- Em `styles/objetivos.css`, o bloco tracejado vira `.objetivo-progresso`. A trilha segue a linguagem visual do XP (`--surface-muted` na trilha, `--cta` no preenchimento, `--radius-full`), com transição de largura desligada em `prefers-reduced-motion`.
- `--compacto` usa trilha de 0.375rem e legenda `--text-xs`.
- `--destaque` usa trilha de ~0.625rem e legenda `--text-sm`, com o percentual maior.
- O convite do card mantém a `min-height` atual para a lista não pular quando um card passa a ter barra.
- O arquivado já recebe opacidade em `.objetivo-card--arquivado`, então a barra herda essa atenuação sem regra extra.

## Risks / Trade-offs

- [O percentual da lista fica velho se o usuário executar uma tarefa em outra aba] → Aceito. A lista relê ao trocar de visão ou de página e o detalhe relê ao abrir. Não há polling.
- [O texto em tempo pode ficar comprido em telas estreitas, como "12h 30min de 40h 15min concluídas"] → A legenda quebra linha em vez de truncar. O card já é coluna no mobile.
- [O convite no card aparece também quando há itens que não somam tempo] → É a decisão registrada no proposal. O detalhe explica o caso.
- [O texto "concluídas" fica estranho num objetivo só com hábito, porque minutos de hábito são "dedicados"] → Aceito. Vale como termo único para o progresso, e o detalhe mostra a natureza dos itens.

## Migration Plan

Só front. Deploy normal da imagem web, sem flag. Rollback: reverter o commit. O backend continua devolvendo `progresso`, que volta a ser ignorado.

Antes do apply, a change `objetivos-itens-vinculados-ui` precisa estar arquivada, porque o delta de "Card de objetivo" parte da versão dela.
