# Revisão — módulo de Hábitos

Data: 2026-10-31  
Escopo: implementação vs `plans/negocio/habitos.md` e `plans/ui/ui-habitos.md` (backend, frontend, motor, execução/XP, streak).  
Método: leitura do plano + código + testes; prioridade a falhas com cenário concreto. Achados duvidosos foram omitidos ou marcados com confiança mais baixa.

**Veredito:** o CRUD e o proxy `/habitos` estão sólidos; há bugs reais de streak e de conflito na Home, e desvios relevantes do contrato de `itensNaoAlocados` / pool do motor.

---

## Legenda

| Campo | Valores |
|-------|---------|
| Severidade | Crítico · Alto · Médio · Baixo |
| Confiança | % de que o achado é real (não falso positivo), com Alta / Média / Baixa |
| Tipo | Bug · Não conformidade plano · Performance · UX · Teste |

---

## Achados

### 1. Quebra preguiçosa zera streak reiniciado no mesmo dia

| | |
|---|---|
| **Severidade** | Alto |
| **Confiança** | **92%** — Alta |
| **Tipo** | Bug (lógica de streak) |
| **Onde** | `HabitoStreakService.java` (`registrarCumprimento` + `aplicarQuebraPreguicosa`); disparado por `HabitoService.mapearComQuebraPreguicosa` em GET lista/detalhe |

**Problema:** Ao concluir em `D` com o dia agendado anterior faltoso, `registrarCumprimento` define `streakAtual = 1`. Em seguida, qualquer GET aplica a quebra preguiçosa olhando só o último dia agendado **antes de hoje**. Esse dia continua faltoso → zera o reinício legítimo.

**Cenário:** Hábito Seg/Ter; falta Segunda; conclui Terça → streak=1 na conclusão; `GET /habitos/{id}` no mesmo dia → streak=0 persistido.

**Evidência:** não há teste de interação “cumprimento + quebra preguiçosa”; T5 cobre só `registrarCumprimento` isolado.

**Nota:** a letra do plano descreve a quebra assim; semanticamente ela invalida o passo 2 do cumprimento no mesmo dia.

---

### 2. `itensNaoAlocados` no GET ≠ residual do motor

| | |
|---|---|
| **Severidade** | Alto |
| **Confiança** | **90%** — Alta |
| **Tipo** | Não conformidade plano / inconsistência de API |
| **Onde** | `PlanoConsultaService.montarItensNaoAlocados` (~L159–205); plano: “montado na geração/ajuste a partir do que restou na fila” |

**Problema:** O motor produz `ItemNaoAlocado`, mas isso **não é persistido**. O GET remonta a lista como “todo elegível do weekday/status que **não** está nos blocos deste plano”, sempre com `motivo=CAPACIDADE`. O flag `temConflito` vem do plano persistido e **não** é reconciliado com a lista remontada.

**Cenário:**
- Janela multi-dia: tarefa alocada só em D+1 aparece como “não alocada” no GET de D.
- Usuário remove bloco de hábito com capacidade sobrando → item volta a omitidos como `CAPACIDADE`.
- Capacidade 0 na geração → `temConflito=false` no motor (achado 4) + GET com omitidos preenchidos → resposta `temConflito=false` com lista não vazia.

---

### 3. CTA de conflito “Remover tarefa…” ignora hábitos (no-op)

| | |
|---|---|
| **Severidade** | Alto |
| **Confiança** | **90%** — Alta |
| **Tipo** | Bug / não conformidade UI (`ui-habitos.md` §5) |
| **Onde** | `justificativa.ts` `escolherBlocoMenorPrioridade`; `usePlanoDoDia.handleRemoverMenorPrioridade`; `ConflitoBanner.tsx` label |

**Problema:** A escolha filtra `bloco.tarefa != null`. Se o rascunho só tem hábitos, retorna `null` e o handler não remove nada. O label permanece “Remover **tarefa** de menor prioridade”, enquanto o plano pede remover **bloco** (tarefa ou hábito).

**Cenário:** Rascunho só com hábitos + omitidos → clique no CTA do banner não faz nada.

---

### 4. Copy de recorrência no rascunho é placeholder

| | |
|---|---|
| **Severidade** | Alto |
| **Confiança** | **92%** — Alta |
| **Tipo** | Não conformidade UI (`ui-habitos.md` §4) |
| **Onde** | `HomePage.tsx` ~L325–327 |

**Problema:** Plano pede `Recorrente · [dias]`. A Home fixa `'Recorrente · dias do hábito'`. `HabitoResumoResponse` / resumo no bloco não expõe `diasSemana` para montar o texto. O teste `BlocoCard.habito.test.tsx` passa props corretas e **não** detecta o wiring da página.

**Cenário:** Rascunho com bloco `HABITO` → texto inútil em vez de “Seg, Qua”.

---

### 5. Capacidade 0 não registra hábitos omitidos no motor

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **85%** — Alta |
| **Tipo** | Bug / não conformidade motor |
| **Onde** | `MotorAlocacao.java` early-return `capacidadeMinutos <= 0` (~L141–143 e ~L244–247) |

**Problema:** Retorna `temConflito=false` e omitidos vazios, sem `registrarHabitosOmitidos`. Quando o pool zera **depois** das reservas (~L366–368), hábitos **são** registrados — comportamento inconsistente.

**Cenário:** Override 0h no weekday do hábito → geração sem blocos de hábito, sem omitidos no resultado do motor, `temConflito=false` (até o GET reinventar a lista — achado 2).

---

### 6. Motor só omite hábitos; tarefas nunca entram em `itensNaoAlocados` na geração

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **88%** — Alta |
| **Tipo** | Não conformidade plano |
| **Onde** | `MotorAlocacao.registrarHabitosOmitidos`; DTO do plano aceita `TAREFA` \| `HABITO` |

**Problema:** Na alocação, só hábitos vão para `omitidos`. Tarefas que não cabem usam `temConflito`/mensagem/truncamento, sem item estruturado. O GET inventa itens `TAREFA` (achado 2), mas a geração em si não produz a lista do plano para tarefas.

**Cenário:** Capacidade aperta só com tarefas → `itensNaoAlocados` do motor vazio; UI de conflito estruturado não reflete o omitido real da fila na geração.

---

### 7. Hábitos só competem com tarefas sem prazo (pool não unificado por score)

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **80%** — Média-Alta |
| **Tipo** | Não conformidade plano (motor) |
| **Onde** | `MotorAlocacao.alocarComPrazoDepoisSemPrazo` (~L351–378); fase 1 = todas com prazo; fase 2 = hábitos + só sem prazo |

**Problema:** Plano: pool livre = “tarefas ainda elegíveis com saldo + hábitos”, ordenados por score. Implementação: tarefa BAIXA com prazo longínquo (score ~35) consome **antes** de hábito (~100) só por ter prazo. T4 cobre urgente vs sem prazo MEDIA, não este caso.

**Cenário:** Capacidade justa; BAIXA com prazo daqui a 10 dias + hábito 60 min → tarefa alocada; hábito pode ir a omitidos.

**Nota:** se a intenção real for “fase prazo depois sem prazo” (como no comentário do código), o plano de negócio precisa ser atualizado — hoje o texto e o código divergem.

---

### 8. N+1 e escrita em leitura na listagem de hábitos

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **90%** — Alta |
| **Tipo** | Performance (efeito colateral em GET) |
| **Onde** | `HabitoService.listar` + `aplicarQuebraPreguicosa`; `Habito.diasSemana` `@ElementCollection(EAGER)` |

**Problema:** Cada item da página dispara `existsCumprimentoHabitoNaData`; `diasSemana` EAGER tipicamente gera select extra por hábito. GET lista é `@Transactional` **sem** `readOnly` e pode persistir quebra de streak.

**Cenário:** `GET /habitos?size=50` → dezenas de queries + possíveis updates de streak.

---

### 9. N+1 ao montar `itensNaoAlocados` (tarefas) no GET plano

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **90%** — Alta |
| **Tipo** | Performance |
| **Onde** | `PlanoConsultaService.montarItensNaoAlocados` (~L167–174) |

**Problema:** Para cada tarefa elegível fora do plano, chama `somarDuracaoMinutosPorTarefaEStatus` individualmente.

**Cenário:** Muitas tarefas abertas + GET plano → N round-trips só para saldo.

---

### 10. Streak não exibido nem atualizado na Home após concluir hábito

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **88%** — Alta |
| **Tipo** | Não conformidade UI (`ui-habitos.md` §6) / UX |
| **Onde** | `HomePage.tsx` lista aprovada; `usePlanoDoDia` / merge otimista de execução |

**Problema:** `habitoResumo.streakAtual` existe no contrato, mas `BlocoCard` na Home não recebe streak. Ao concluir, o merge atualiza execução/XP e não refresca `habitoResumo`.

**Cenário:** Conclui bloco de hábito → toast XP ok; streak na Home não muda até reabrir `/habitos`.

---

### 11. Adicionar omitido sem toast quando gera/mantém conflito

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **85%** — Alta |
| **Tipo** | Não conformidade UI (`ui-habitos.md` §5) |
| **Onde** | `usePlanoDoDia.adicionarHabitoAoPlano` (~L546–567) |

**Problema:** Plano: Adicionar se couber; senão toast de conflito. Backend sempre persiste e marca `temConflito` em estouro. Front só `setPlano(atualizado)` — sem toast se `temConflito`.

**Cenário:** Usuário adiciona hábito omitido que estoura capacidade → bloco entra, banner muda, sem toast explícito.

---

### 12. Dirty-check do drawer por `JSON.stringify` nos dias

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **86%** — Alta |
| **Tipo** | Bug UX |
| **Onde** | `HabitoDrawer.tsx` ~L76; API devolve `Set`/`array` sem ordem estável |

**Problema:** `alterado = JSON.stringify(values) !== JSON.stringify(inicial)`. Mesmo conjunto de dias com ordem diferente (toggle no fim do array / ordem do back) dispara “Descartar alterações?”.

**Cenário:** Abre edição, liga/desliga o mesmo dia → ao fechar pede descarte sem mudança real.

---

### 13. Detecção frágil de origem HABITO na execução aprovada

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **72%** — Média |
| **Tipo** | Bug (defesa / contrato UI) |
| **Onde** | `HomePage.tsx` ~L267: `item.habitoResumo ? 'HABITO' : 'TAREFA'` |

**Problema:** No rascunho usa `origemDoBloco`. No aprovado depende só de `habitoResumo`. Se o enrich vier ausente, o card aparece como tarefa.

**Cenário:** Regressão ou enrich incompleto na API de execução → sem rótulo “Hábito” / link errado.

---

### 14. Testes de UI não cobrem o wiring da Home com hábitos

| | |
|---|---|
| **Severidade** | Médio |
| **Confiança** | **90%** — Alta |
| **Tipo** | Gap de teste / não conformidade (`ui-habitos` T3–T4) |
| **Onde** | `BlocoCard.habito.test.tsx` / `ConflitoBanner` unitários vs ausência de casos HABITO em `HomePage` |

**Problema:** T3/T4 passam no card/banner com props corretas. Copy hardcoded e CTA de conflito (achados 3 e 4) não quebram o CI.

**Cenário:** Regressão na Home com blocos HABITO passa despercebida.

---

### 15. Create navega sem preservar `search` da lista

| | |
|---|---|
| **Severidade** | Baixo |
| **Confiança** | **84%** — Alta |
| **Tipo** | Bug UX |
| **Onde** | `HabitoDrawer.tsx` ~L167 |

**Problema:** `navigate(\`/habitos/${criado.id}\`)` sem `location.search`. Filtro `status`/`somenteDeletadas` da URL some.

**Cenário:** Em Pausados, cria hábito → lista por baixo volta a Ativos com o drawer aberto.

---

### 16. Exclusão de hábito sem “Desfazer” no toast

| | |
|---|---|
| **Severidade** | Baixo |
| **Confiança** | **70%** — Média |
| **Tipo** | Não conformidade UX (paridade com tarefas) |
| **Onde** | `HabitosPage.tsx` exclusão |

**Problema:** Restore existe (visão Excluídos); tarefas oferecem undo imediato no toast. Fluxo pós-delete de hábito é mais frágil.

**Cenário:** Exclui por engano → precisa ir a Excluídos em vez de Desfazer.

---

### 17. Validação do form só no alert, sem âncora nos campos

| | |
|---|---|
| **Severidade** | Baixo |
| **Confiança** | **80%** — Alta |
| **Tipo** | UX / a11y |
| **Onde** | `HabitoDrawer` + `HabitoCampos` |

**Problema:** `validarHabitoForm` calcula erros por campo; UI mostra só `primeiroErroHabito` num alert. Dias/duração sem `aria-invalid` / mensagem local.

**Cenário:** Submit sem dias → alerta genérico; chips não sinalizam o problema.

---

### 18. Entity `BlocoPlano` sem invariante XOR em Java

| | |
|---|---|
| **Severidade** | Baixo |
| **Confiança** | **85%** — Alta |
| **Tipo** | Arquitetura leve |
| **Onde** | `BlocoPlano` vs CHECK em `V14__create_habito.sql` e `BlocoAlocado` |

**Problema:** Plano pede CHECK + validação no service. API/paths principais protegem; a entity aceita ambos null/ambos set — erro só no flush.

**Cenário:** Código interno/teste montando bloco inválido → falha tardia no banco.

---

## O que foi conferido e parece conforme

| Área | Status |
|------|--------|
| CRUD `/habitos` (create ATIVO, pausar/reativar 409, soft delete/restore, 404 cross-user) | OK (`HabitoIT`) |
| Proxy `/habitos` (Vite + Nginx + `API_PROXY.md`) | OK |
| Score base 85 + duração; empate tarefa → hábito → `createdAt` | OK (T1) |
| All-or-nothing de hábito (sem bloco parcial) | OK (T3) |
| Hábitos fora da fase de reserva de projeto / só pool livre (após fase prazo) | OK no desenho atual |
| XP bloco hábito peso MEDIA; sem `TAREFA_FINALIZADA` | OK (T6 / `HabitoXpTest`) |
| Execução não muta `Tarefa` | OK |
| Schema V14: tabela filha dias, CHECK XOR, unique `(plano_id, habito_id)`, índices | OK |
| Nav `/habitos`, lista/form básicos, streak na lista/detalhe do módulo | OK no módulo dedicado |
| Remover bloco hábito pelo card + re-add | OK |

---

## Prioridade sugerida de correção

1. **Streak:** quebra preguiçosa não pode anular cumprimento de hoje / reinício no ciclo atual (achado 1).  
2. **Conflito Home:** CTA remove bloco (tarefa ou hábito) e não no-op (achado 3); copy real dos dias ou DTO com `diasSemana` (achado 4).  
3. **`itensNaoAlocados`:** devolver residual do motor (ou snapshot) e alinhar `temConflito`; capacidade 0 deve omitir hábitos (achados 2, 5, 6).  
4. **Pool:** unificar por score conforme plano **ou** atualizar o plano para documentar fase prazo (achado 7).  
5. **Home execução:** streak + toast ao adicionar omitido com conflito (achados 10, 11).  
6. **Performance:** batch de cumprimentos/saldos; evitar escrita desnecessária em GET lista (achados 8, 9).  
7. **UX form:** dirty-check estável, preservar query no create, feedback por campo (achados 12, 15, 17).  
8. **Testes:** HomePage com bloco HABITO + banner só-hábitos (achado 14) para travar 3 e 4.

---

## Suposições

- Volume típico de hábitos por usuário é baixo; N+1 na lista (achado 8) é real, mas impacto prático cresce com `size` e com hábitos ativos no GET plano.  
- Achado 7 assume que o texto do plano (“pool unificado por score”) prevalece sobre o comentário “Fase 1 prazo / Fase 2 sem prazo” no código.  
- Achado 13 só se materializa se `habitoResumo` faltar; o caminho feliz atual enriquece.
)
