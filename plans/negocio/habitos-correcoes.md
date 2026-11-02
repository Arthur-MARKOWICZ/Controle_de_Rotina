# Feature — Correções pós-revisão do módulo Hábitos (domínio / motor / API)

Issue: [#43](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/43) (correção)

Escopo: corrigir bugs e desvios confirmados em `revisao_modulo_habitos.md` no **backend** (streak, motor, `itensNaoAlocados`, performance de leitura, invariante XOR).  
Fora deste slice: UI/UX da Home e do drawer (ver `ui-habitos-correcoes.md`); RRULE; social; redesenho de score; cache.

Dependências: `habitos.md`, `planner.md`, `execucao.md`. Código de hábitos já no repo; este plano **não** reimplementa o CRUD.

Origem: achados **1, 2, 5, 6, 7, 8, 9, 18** de `revisao_modulo_habitos.md` (confirmados em 2026-10-31).

---

## Conceito

1. **Quebra preguiçosa** não pode anular um reinício legítimo de streak no mesmo dia do cumprimento.
2. **`itensNaoAlocados`** é o residual do motor (e dos ajustes), não “todo elegível ausente do plano” inventado no GET.
3. Capacidade **0** e pool apertado: hábitos elegíveis omitidos entram na lista e `temConflito` fica coerente.
4. No **pool livre** (após reservas de projeto), candidatos = tarefas com saldo + hábitos do dia, ordenados por **score** (texto de `habitos.md`) — não “todas com prazo antes de qualquer hábito”.
5. Tarefas que não cabem na geração também geram item estruturado `TAREFA` em `itensNaoAlocados` (não só hábitos).
6. Leituras de lista/plano não fazem N+1 evitável nem gravam streak sem necessidade.

---

## Entity / Entities

### Plano (extensão de persistência)

| Campo | Tipo | Observação |
|-------|------|------------|
| itensNaoAlocados | JSONB (lista do DTO) **ou** tabela filha `(plano_id, tipo, origem_id, titulo, duracao_minutos, motivo)` | Snapshot gravado na **geração** e em cada **ajuste** que recalcula conflito. Sem entidade de domínio rica. Preferir JSONB se o time aceitar o padrão Hibernate/`@JdbcTypeCode(SqlTypes.JSON)`; senão tabela filha mínima. |

`temConflito` e `mensagem` continuam colunas do `Plano`; na gravação devem refletir omitidos + estouro/truncamento.

### BlocoPlano

Sem colunas novas. Acrescentar **invariante em Java** (factory/`compact`/validação no service): exatamente um de `tarefaId` / `habitoId` (espelha CHECK do Flyway e `BlocoAlocado`).

### HabitoResumoResponse (DTO)

| Campo | Tipo | Observação |
|-------|------|------------|
| diasSemana | `Set`/`List` de `DiaSemana` | Necessário para a UI montar “Recorrente · Seg, Qua” (consumo em `ui-habitos-correcoes.md`). |
| streakAtual | int | Já existe; mantém. |

---

## Back-end

### Streak — `HabitoStreakService.aplicarQuebraPreguicosa`

Regra fechada:

1. Se `streakAtual <= 0` → return.
2. Se o weekday de **hoje** ∈ `diasSemana` **e** existe cumprimento `CONCLUIDO` do hábito **hoje** → **não zerar** (reinício/continuidade do ciclo atual).
3. Caso contrário: `anterior = ultimoDiaAgendadoAntes(hoje)`; se `anterior` sem cumprimento → `streakAtual = 0` (melhor streak intacto).

`registrarCumprimento` permanece; o GET deixa de desfazer o passo 2 do cumprimento no mesmo dia.

Leitura (`listar` / `buscarPorId`):

- Manter aplicação da quebra, mas **só persistir** se o streak mudou.
- Preferir `@Transactional(readOnly = true)` no caminho de leitura quando não houver update; se houver update de quebra, transação de escrita só nesse ramo (ou `save` explícito após detectar mudança).

### Motor — capacidade 0

Em `alocar` / `alocarJanela`, early-return com `capacidadeMinutos <= 0`:

- Chamar `registrarHabitosOmitidos` para hábitos elegíveis do dia.
- `temConflito = !omitidos.isEmpty()` (ou true se houver elegíveis omitidos).
- Mensagem alinhada a hábito omitido / sem tempo.
- Não devolver lista vazia + `temConflito=false` quando há hábitos do weekday.

### Motor — tarefas em `itensNaoAlocados`

Na geração do dia, para cada tarefa elegível com saldo restante **não** alocado naquele dia (após a alocação do dia), registrar `ItemNaoAlocado(TAREFA, …, CAPACIDADE)` além dos hábitos omitidos.  
Truncamento/mensagem de conflito de prazo existentes **permanecem**; a lista estruturada passa a incluir tarefas.

### Motor — pool livre unificado por score

Dentro do pool livre (após reservas):

- Candidatos = tarefas ainda elegíveis com saldo **com ou sem prazo** + hábitos do dia.
- Ordenar por score desc; empate: `TAREFA` antes de `HABITO`; depois `createdAt` asc.
- Remover a fase “todas com prazo, depois hábitos + sem prazo” que faz BAIXA com prazo longínquo vencer hábito ~100.

Atualizar comentário no código e, se necessário, uma linha em `habitos.md` § Motor para deixar explícito que “fase 1” = só reservas de projeto, não “com prazo”.

### Persistência / GET de `itensNaoAlocados`

1. **Geração** (`PlannerGeracaoService`): gravar `alocacao.itensNaoAlocados()` no plano do dia.
2. **Ajuste** (add/remove bloco): recalcular snapshot:
   - hábitos do weekday do plano não presentes nos blocos → omitidos `HABITO`;
   - tarefas elegíveis com saldo > 0 não presentes nos blocos **deste** plano → omitidos `TAREFA` **somente** se a capacidade do dia estiver estourada **ou** o item acabou de sair do plano (remoção) / residual coerente com estouro;
   - regra mínima aceitável: após ajuste, omitidos = (hábitos weekday fora do plano) ∪ (tarefas com saldo fora do plano **se** `somaBlocos >= capacidade` ou `temConflito`); evitar listar no GET de D tarefas que só cabem/estão em D+1 da janela **sem** estouro em D.
3. **GET** (`PlanoConsultaService`): devolver snapshot persistido; **não** remontar com N queries de saldo por tarefa. Reconciliar resposta: se snapshot não vazio e política de conflito exigir, `temConflito` coerente com o persistido (não inventar lista divergente do flag).

Detalhe de implementação da regra 2 pode ser fino na atividade B*; o aceite é: GET pós-geração = residual do motor; GET pós-remover hábito com capacidade sobrando **não** marca omitido só como se a geração tivesse falhado por capacidade sem reconciliar `temConflito`; janela multi-dia não lista em D tarefa alocada só em D+1 como omitida de D.

### Performance lista de hábitos

- Batch: uma query (ou `IN`) de cumprimentos nas datas “anterior agendado” relevantes da página, em vez de `existsCumprimentoHabitoNaData` por item.
- Avaliar `@ElementCollection(EAGER)` → `LAZY` + fetch join na listagem, se não quebrar outros caminhos.

### Performance GET plano

Eliminada pelo snapshot (sem `somarDuracaoMinutosPorTarefaEStatus` em loop no GET).

---

## Front-end

Nenhum neste arquivo. Ver `ui-habitos-correcoes.md` (consome `diasSemana` no resumo).

---

## Decisões fechadas (revisão)

1. Achados da revisão confirmados no código; este plano cobre só domínio/motor/API.
2. **Pool:** prevalece o texto de `habitos.md` (pool livre unificado por score). Código da fase “prazo depois sem prazo” é corrigido; não se atualiza o plano de negócio para documentar a fase antiga.
3. **`itensNaoAlocados`:** snapshot na geração/ajuste; GET lê snapshot (sem remontar inventando elegíveis).
4. **Capacidade 0:** registra hábitos omitidos e `temConflito` coerente.
5. **Tarefas** entram em `itensNaoAlocados` na geração quando ficam de fora por capacidade.
6. Sem cache (Redis/Caffeine/`@Cacheable` etc.).
7. Achados só de UI (3, 4, 10–17) ficam em `ui-habitos-correcoes.md`.

---

## Testes automatizados

Testes que **não podem faltar**. Cada item amarra à atividade B*.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Seg/Ter: falta Seg, conclui Ter (`streak=1`); `aplicarQuebraPreguicosa` no mesmo dia **não** zera | B1 | Regressão do bug de streak |
| T2 | Capacidade 0 + hábito elegível → omitido `HABITO` + `temConflito=true` | B2 | Early-return inconsistente |
| T3 | Capacidade justa: BAIXA com prazo longínquo + hábito score alto → hábito alocado (ou empate tarefa-primeiro só se scores iguais) | B3 | Pool unificado |
| T4 | Tarefa que não cabe → item `TAREFA` em `itensNaoAlocados` | B2 | Contrato da lista |
| T5 | `BlocoPlano` / factory rejeita ambos null ou ambos set (se validação em Java) | B5 | Invariante XOR |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T6 | Geração persiste omitidos; `GET /planos/{id}` devolve a **mesma** lista (não inventa tarefa de D+1 no plano de D) | B4 | Contrato GET |
| T7 | `GET /habitos` após cumprimento do dia com dia anterior faltoso mantém `streakAtual=1` | B1 | Streak ponta a ponta |
| T8 | `HabitoResumoResponse` no bloco inclui `diasSemana` | B6 | Contrato UI |

---

## Atividades

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Streak: quebra preguiçosa + leitura** | Regra T1; persistir só se mudou; batch de exists na listagem (parte do achado 8); **T1**, **T7** |
| **B2** | **Motor: capacidade 0 + tarefas omitidas** | Early-return com `registrarHabitosOmitidos`; tarefas residual → `itensNaoAlocados`; **T2**, **T4** |
| **B3** | **Motor: pool livre por score** | Unificar fase; **T3** |
| **B4** | **Persistir snapshot + GET** | Migration JSONB ou filha; gravação na geração/ajuste; GET sem remonte N+1; **T6** |
| **B5** | **XOR em `BlocoPlano` (Java)** | Validação alinhada ao CHECK; **T5** |
| **B6** | **DTO `diasSemana` no resumo** | `HabitoResumoResponse` (+ serialização bloco/execução); **T8** |

### Ordem

```text
B1 → B2 → B3 → B4 → B5
              ↘
               B6   (pode paralelo a B5; UI depende de B6)
```

Front de correção (`ui-habitos-correcoes.md`) pode começar após **B6** (copy dias) e **B4** (conflito coerente); streak na Home usa enrich já existente + B1.
)
