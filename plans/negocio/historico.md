# Feature — Histórico (sétimo slice)

Escopo: resumo de progresso **por dia** e **por semana** (tarefas finalizadas, blocos concluídos, tempo canônico, XP ganho); leitura agregada só do usuário autenticado; tela `/historico` com navegação de período.  
Fora deste slice: streak, ranking, gráficos avançados, exportação, calendário completo, filtros por prioridade/tarefa, materialização/job de resumo, cache, listagem raw do ledger (`XpEvento`), IA.

Dependências: `execucao.md` (execução `CONCLUIDO` + `concluidoEm` + `duracaoMinutos` do bloco), `xp.md` (ledger `XpEvento` com `createdAt` / `quantidade` / `tipo`).

---

## Conceito

1. Histórico é **somente leitura**: não grava progresso; agrega fontes já confiáveis (execução + ledger de XP).
2. **Sem entity nova e sem cache** — fonte única = dados de domínio; evita drift entre resumo e ledger.
3. Todo cálculo é **scoped pelo `usuarioId` do JWT**. Nunca aceitar `usuarioId` no path/query/body.
4. Fuso canônico: **`America/Sao_Paulo`** (igual execução/planner).
5. Semana: **segunda → domingo** (igual capacidade / `inicioSemana` no front).
6. Períodos **limitados e validados** no back (anti-abuso de consulta pesada).

---

## Fontes e regras de agregação

Não há tabela `Historico*`. O serviço agrega:

| Métrica | Fonte | Regra |
|---------|--------|--------|
| `tarefasFinalizadas` | `XpEvento` | count onde `tipo = TAREFA_FINALIZADA` e `createdAt` ∈ intervalo do período (BRT) |
| `blocosConcluidos` | `Execucao` | count onde `status = CONCLUIDO` e `concluidoEm` ∈ intervalo |
| `minutosTrabalhados` | `Execucao` + `BlocoPlano` | soma de `bloco.duracaoMinutos` das execuções do item acima (tempo **canônico**, não relógio real) |
| `xpGanho` | `XpEvento` | soma de `quantidade` de **todos** os tipos com `createdAt` ∈ intervalo |

### Por que essas fontes (qualidade + segurança)

- **Tarefa finalizada pelo ledger**, não por `tarefa.status` atual: status pode mudar depois (`PATCH`); o evento é imutável e marca o instante real da concessão.
- **Tempo canônico** (`duracaoMinutos`): alinhado à execução; não depende de usuário deixar o timer aberto (evita inflar métrica).
- **XP pelo ledger**: mesma verdade do saldo; inclui bloco + bônus no período em que foram creditados.
- Sempre filtrar `usuarioId = JWT` nas queries (JPA parametrizado). Outro usuário / id inexistente **não se aplica**: o endpoint só devolve o resumo do autenticado (não há id de recurso alheio).

### Intervalo temporal

Dado `LocalDate` em BRT:

- **Dia `D`:** `[D 00:00:00, D+1 00:00:00)` em `America/Sao_Paulo` → `Instant` início inclusivo, fim exclusivo.
- **Semana com `semanaInicio` = segunda `S`:** `[S 00:00:00, S+7 00:00:00)` BRT. Se `semanaInicio` **não** for segunda → **400**.

Defaults: `data` omitida → hoje BRT; `semanaInicio` omitido → segunda da semana corrente BRT.

### Validações de período (segurança)

- Datas inválidas / malformadas → **400**.
- `semanaInicio` no futuro além da semana corrente → permitido só até a semana que contém **hoje** (não “semana futura vazia” como erro; pode retornar zeros). **Não** exigir range máximo além de 7 dias no endpoint de semana (já é fixo).
- Endpoint de dia: **um** dia só (sem `de`/`ate` aberto neste slice).
- Sem endpoint genérico `de`/`ate` ilimitado no MVP (reduz risco de full-scan / DoS lógico).

---

## Entities

Nenhuma entity nova. Reutilizar:

- `Execucao` (`usuarioId`, `status`, `concluidoEm`, `blocoPlanoId`)
- `BlocoPlano` (`duracaoMinutos`)
- `XpEvento` (`usuarioId`, `tipo`, `quantidade`, `createdAt`)

Índices (Flyway **só se ainda não existirem** — activity B1):

- `xp_evento (usuario_id, created_at)` — agregação de XP/tarefas por período
- `execucao (usuario_id, status, concluido_em)` — agregação de blocos/tempo (parcial ou composto; o que o dialeto permitir com clareza)

Sem soft delete neste slice.

---

## Back-end

Todos autenticados (JWT). Sem token → **401**.

Prefixo novo: **`/historico`** (registrar em Vite + Nginx + `API_PROXY.md`).

Pacote: `historico` (service de agregação + controller + DTOs). Repositórios: métodos de agregação nos repos existentes de `xp` / `execucao` **ou** queries dedicadas no pacote histórico que usam as tabelas já existentes — sem Field Injection; construtor.

### Contratos

1. **GET /historico/dia**  
   - Query: `data` (opcional, `LocalDate`; default hoje BRT).  
   - 200:
     ```json
     {
       "periodo": {
         "escopo": "DIA",
         "inicio": "2026-10-27",
         "fim": "2026-10-27"
       },
       "tarefasFinalizadas": 4,
       "blocosConcluidos": 6,
       "minutosTrabalhados": 220,
       "xpGanho": 280
     }
     ```
   - Dia sem atividade → **200** com zeros (não 404).

2. **GET /historico/semana**  
   - Query: `semanaInicio` (opcional, `LocalDate` = segunda; default segunda da semana corrente BRT).  
   - Se informado e não for segunda-feira → **400** com mensagem clara.  
   - 200: mesmo shape, `escopo: "SEMANA"`, `inicio` = segunda, `fim` = domingo (`inicio + 6`).  
   - Semana vazia → **200** com zeros.

Sem POST/PATCH/DELETE. Sem job.

### Resposta — formatação

- Front exibe tempo (ex. `3h40`) a partir de `minutosTrabalhados`; back devolve **só minutos** (inteiro `>= 0`).
- Campos numéricos nunca `null` (usar `0`).

---

## Front-end

1. **Rota autenticada `/historico`** — dois blocos visuais alinhados à visão: **Hoje** e **Esta semana** (números: tarefas finalizadas, tempo formatado, XP; blocos concluídos como métrica secundária ou na mesma grade).
2. **Navegação** — dia anterior/próximo (não além de hoje, se quiser evitar “futuro”; permitido mostrar zeros); semana anterior/próxima (`semanaInicio` ± 7, reutilizar helpers de `capacidade/semana`).
3. **Proxy** — prefixo `/historico` em `vite.config.ts`, `nginx.conf`, `API_PROXY.md` (rebuild conforme `API_PROXY.md`).
4. Link na navegação autenticada (header/menu existente).

Sem pixel-perfect; reutilizar sessão/auth já existentes.

---

## Decisões fechadas (revisão)

1. Métricas MVP = visão 7.6 + `blocosConcluidos` explícito; **sem streak** (fica fora para não misturar regra frágil de “dia ativo”).
2. `tarefasFinalizadas` = count de `XpEvento` `TAREFA_FINALIZADA` no período (não status atual da tarefa).
3. Tempo = soma de `duracaoMinutos` dos blocos com execução `CONCLUIDO` no período (canônico).
4. XP do período = soma do ledger no intervalo (`createdAt`).
5. Sem entity/materialização/cache — agregação na leitura.
6. Dois GETs: `/historico/dia` e `/historico/semana`; defaults BRT; semana seg–dom; `semanaInicio` inválido → 400.
7. Sem query aberta `de`/`ate` neste slice (superfície e custo controlados).
8. Auth: só JWT; dados sempre do autenticado; dia/semana vazios → 200 com zeros.
9. Prefixo `/historico` + proxy obrigatório.
10. UI: página `/historico` com navegação de período.
11. Este é o **sétimo slice**.

---

## Testes automatizados

Testes que **não podem faltar** neste slice. Cada item amarra à atividade (B*/F*) que o entrega.

### Unitários (sem HTTP)

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T1 | Conversão dia/semana → intervalo `[inicio, fim)` Instant em BRT (bordas: meia-noite, virada de domingo→segunda) | B1 | Fuso errado corrompe todo o resumo |
| T2 | Agregador: dadas listas/fixture de eventos + execuções, calcula as 4 métricas; período vazio → zeros | B1 | Regra de produto |
| T3 | `semanaInicio` que não é segunda → rejeitado na validação de input do serviço/helper | B1 | Contrato de semana |

### Integração / API

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T4 | `GET /historico/dia`: 200 com métricas coerentes após concluir bloco (XP + minutos + blocos); dia vazio → zeros | B2 | Contrato do dia |
| T5 | `GET /historico/semana`: agrega vários dias; `semanaInicio` não-segunda → 400; default = semana corrente | B3 | Contrato da semana |
| T6 | Sem token → 401 nos dois endpoints | B2 | Auth |
| T7 | Usuário A não vê XP/execuções do usuário B (seed dois usuários; A consulta → só os próprios totais) | B2 | Isolamento / segurança |

### Front

| # | O que cobrir | Atividade | Por que não pode faltar |
|---|--------------|-----------|-------------------------|
| T8 | Página `/historico` renderiza Hoje + Esta semana a partir da API | F2 | Entrega da visão |
| T9 | Navegação de dia/semana chama API com `data` / `semanaInicio` corretos | F3 | Período certo |

Se o harness front não cobrir UI: T8–T9 checklist manual até existir; **T1–T7 obrigatórios** no back.

---

## Atividades

Regra: motor/agregação sem HTTP primeiro; **no máximo um endpoint novo por B***; índices só se faltarem. Entregar os T* na atividade indicada.

### Backend

| # | Atividade | Escopo |
|---|-----------|--------|
| **B1** | **Motor + intervalo + índices** | Helpers BRT dia/semana → `Instant`; validador de segunda; serviço puro/agregador das 4 métricas (testável sem HTTP); Flyway de índices se necessário; **sem** controller; **T1–T3** |
| **B2** | **GET /historico/dia** | Endpoint + wiring auth/JWT; default hoje; 200 com DTO; **T4, T6, T7** |
| **B3** | **GET /historico/semana** | Endpoint; default semana corrente; 400 se não-segunda; **T5** |

### Frontend

| # | Atividade | Escopo |
|---|-----------|--------|
| **F1** | **Proxy `/historico`** | `vite.config.ts` + `nginx.conf` + `API_PROXY.md`; rebuild conforme doc |
| **F2** | **Página `/historico`** | Hoje + Esta semana; formatação de minutos; link na nav; **T8** |
| **F3** | **Navegação de período** | Anterior/próximo dia e semana; query params; **T9** |

### Ordem

```text
B1 → B2 → B3
       ↘
        F1 → F2 → F3
```

`F1` pode em paralelo após o path existir no back (ou junto de B2). `F2` após **B2** (mínimo dia); semana na UI completa após **B3**.
