# Tasks

## 1. Persistência e create

- [x] 1.1 Criar migration Flyway `V20__tarefa_parent.sql` (`parent_id` nullable, FK para `tarefa`, índice `(usuario_id, parent_id)`); verificar migration aplica limpa e o app sobe
- [x] 1.2 Estender entity `Tarefa` + `CriarTarefaRequest` / `TarefaResponse` com `parentId`, `parentTitulo`, `temFilhos`; verificar compile e mapper de tarefa sem pai (campos nulos/false)
- [x] 1.3 Implementar create com `parentId`: um nível, mesmo usuário, pai não deletado, herda `projetoId` se omitido, não copia tags, reabre pai `FINALIZADA`; verificar IT: filho criado, neto 400, pai de outro usuário 404, herda projeto, pai finalizado volta a `PENDENTE`
- [x] 1.4 Recusar create se o pai tiver bloco com execução `CONCLUIDO`; verificar IT 409/400 e que nenhum filho é persistido

## 2. Duração derivada e listagem

- [x] 2.1 Recalcular `duracaoMinutos` do pai na soma dos filhos não deletados (manter valor ao perder o último); rejeitar PATCH de duração no pai com filhos; verificar testes de serviço/IT: 60+60=120, PATCH duração no pai falha, último filho deletado deixa 90
- [x] 2.2 Filtros `parentId` e `somenteRaizes` no GET `/tarefas`; enrich `parentTitulo` e `temFilhos` em lote na página; verificar IT: raízes sem filhos na página, filhos por `parentId`, `parentTitulo` preenchido, sem N+1 óbvio

## 3. Cascata de status, excluir e restaurar

- [x] 3.1 PATCH `FINALIZADA`/`CANCELADA` no pai aplica nos filhos ativos; PATCH reabrir pai não mexe nos filhos; reabrir filho reabre pai `FINALIZADA`; sem XP no PATCH; verificar IT dos quatro caminhos e ausência de `xp_evento`
- [x] 3.2 Ao finalizar o último filho por sessão (`ExecucaoService`), marcar o pai `FINALIZADA`; verificar IT/domínio: segundo filho concluído finaliza o pai
- [x] 3.3 Soft delete do pai cascateia filhos ativos; restore do pai restaura todos os filhos deletados; restore do filho restaura o pai e deixa irmãos deletados; verificar IT dos três cenários

## 4. Planner, atraso e sessão

- [x] 4.1 Excluir pais com filhos não deletados da geração (`PlannerGeracaoService`); verificar teste/IT: blocos só nas folhas, pai omitido
- [x] 4.2 Job de atraso não marca pai por prazo próprio (`NOT EXISTS` filho ativo); verificar teste do job: pai com prazo passado permanece, filho atrasado segue a regra atual
- [x] 4.3 Recusar `POST /tarefas/{id}/iniciar` e `concluir-direto` em pai com filhos; folha inalterada; verificar IT 409 no pai e sessão ok na subtarefa

## 5. UI drawer

- [x] 5.1 Client `tarefaApi`: `parentId` no create, query `parentId`/`somenteRaizes`, campos novos no tipo; verificar testes do client
- [x] 5.2 Seção subtarefas no drawer do pai (lista + criar; duração do pai read-only com filhos); verificar testes do drawer: cria filho, não cria no card da lista, duração não editável
- [x] 5.3 Diálogos `useConfirmDialog` ao finalizar, cancelar e excluir pai com `temFilhos`; sem diálogo extra se não houver filhos; verificar testes de confirmação (voltar não chama API; confirmar dispara cascata)

## 6. UI lista

- [x] 6.1 Visão árvore em `/tarefas` (`visao=todas` sem filtros isoladores): `somenteRaizes`, card do pai com expandir (default recolhido), filhos via `parentId`; verificar testes da página: recolhido esconde filhos, expandir mostra, sem Iniciar/Concluir no pai
- [x] 6.2 Visão filtrada: filho solto com “Subtarefa de {pai}” se o pai não está no resultado; aninhado se o pai está; indicador abre o drawer do pai; verificar testes dos dois casos
- [x] 6.3 Folha (aninhada ou solta) mantém Iniciar/Concluir de sessão; verificar teste do card da subtarefa

## 7. Integração

- [x] 7.1 Rodar suíte backend relevante (tarefa + planner + execução + atraso) e suíte frontend (drawer, lista, sessão); corrigir regressões desta change

## Workflow follow-up

- Archive a change após review/implementação (`/openspec-archive-change`).
- Não há issue GitHub numerada neste corte; se abrir, fechar quando o comportamento estiver em main.
