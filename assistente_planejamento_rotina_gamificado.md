# Projeto — Assistente de Planejamento de Rotina Gamificado

## 1. Resumo

Aplicativo de controle de rotina focado em **planejamento automático de tarefas**, com **gamificação leve por XP**.

A proposta não é criar apenas mais um gerenciador de tarefas nem transformar a produtividade em um RPG. O objetivo é permitir que o usuário cadastre de forma simples o que precisa fazer e deixe o sistema montar uma sugestão de planejamento para os próximos dias.

### Ideia central

> Eu informo o que preciso fazer, quanto tempo acredito que vai levar e até quando precisa estar pronto. O sistema organiza isso em um plano executável e eu aprovo ou ajusto.

Fluxo principal:

```text
Cadastrar tarefas
       ↓
Informar duração estimada, prazo e prioridade
       ↓
Sistema analisa capacidade + prazos + prioridades
       ↓
Sistema gera um plano
       ↓
Usuário aprova ou ajusta
       ↓
Usuário executa os blocos
       ↓
Conclui tarefas e ganha XP
       ↓
Histórico de progresso
```

---

## 2. Problema

Gerenciadores de tarefas normalmente resolvem bem o armazenamento e a visualização das tarefas, mas deixam uma parte importante para o usuário:

> decidir quando cada tarefa será executada.

Isso cria esforço de planejamento e pode levar a:

- excesso de tarefas concentradas no mesmo dia;
- tarefas grandes sem uma divisão clara;
- dificuldade de priorizar prazos;
- necessidade de reorganizar manualmente a rotina quando algo muda;
- sensação de ter uma lista de tarefas, mas não saber como transformá-la em um plano.

O projeto pretende atacar principalmente esse problema.

---

## 3. Posicionamento

A proposta é mais próxima de:

> **“Um assistente que organiza minhas tarefas nos próximos dias.”**

e não de:

> “Um RPG de produtividade.”

Também não deve se tornar apenas:

> “Um Todoist com XP.”

### Diferencial pretendido

O principal diferencial é o **motor de planejamento**.

A gamificação existe para incentivar o uso e fornecer feedback de progresso, mas não é o núcleo do produto.

---

## 4. Decisões já tomadas

### 4.1 Entrada de tarefas

O usuário deve conseguir registrar uma tarefa com informações simples.

Informações inicialmente consideradas:

- título;
- descrição curta;
- data limite;
- duração estimada;
- prioridade.

A duração estimada deve ser **informada pelo usuário**.

Exemplo:

```text
Título: Estudar Spring Security
Descrição: terminar capítulo 4
Duração estimada: 3h
Prazo: sexta-feira
Prioridade: alta
```

---

### 4.2 Planejamento

O sistema deve distribuir as tarefas automaticamente pelos dias.

O usuário **não precisa montar manualmente o planejamento inicial**.

Além disso, tarefas grandes podem ser **quebradas automaticamente em blocos menores**.

Exemplo:

```text
Tarefa:
Estudar Spring Security
Duração: 3h
Prazo: sexta

Plano:

Segunda
└── Spring Security — 1h

Terça
└── Spring Security — 1h

Quarta
└── Spring Security — 1h
```

---

### 4.3 Capacidade diária

O planner deve considerar quanto tempo o usuário possui disponível em cada dia.

Exemplo:

```text
Segunda: 4h
Terça: 5h
Quarta: 3h
Quinta: 4h
Sexta: 2h
```

Assim, o sistema não deve simplesmente distribuir tarefas sem considerar a capacidade.

---

### 4.4 Usuário como aprovador

O sistema **não deve reorganizar silenciosamente toda a rotina**.

A ideia é gerar uma sugestão que o usuário possa revisar.

Exemplo:

```text
Plano proposto

Segunda
- Projeto da faculdade — 2h
- Academia — 1h
- Estudar Java — 1h

[ Aprovar plano ] [ Ajustar ]
```

Quando houver conflito ou falta de capacidade, o sistema deve permitir intervenção do usuário.

---

### 4.5 Replanejamento

Caso o planejamento deixe de funcionar, o sistema pode propor um novo plano.

Por enquanto, a preferência é:

> o sistema sugere a mudança e o usuário decide se aceita.

---

### 4.6 Gamificação

A gamificação deve ser **leve**.

O objetivo principal do XP é:

- gerar sensação de progressão;
- motivar;
- permitir acompanhar evolução;
- criar um incentivo para conclusão das tarefas.

O usuário demonstrou interesse principalmente em:

- motivação;
- competição consigo mesmo;
- acompanhar sua própria evolução.

### Não faz parte da proposta inicial

Evitar transformar o produto em um RPG completo com:

- personagens;
- equipamentos;
- monstros;
- inventário;
- moedas;
- sistemas excessivamente complexos de progressão.

Exemplo simples:

```text
Nível 12

820 / 1000 XP

Esta semana
+640 XP

Tarefas concluídas
27

Sequência
6 dias
```

---

## 5. XP

Ainda não existe uma fórmula definitiva.

Uma possibilidade levantada foi combinar:

- duração;
- dificuldade.

Exemplo conceitual:

```text
XP = duração × dificuldade
```

Mas essa regra **não está definida** e deve ser validada durante o desenvolvimento.

Não assumir que essa fórmula é necessariamente a correta.

---

## 6. Exemplo de funcionamento

Entrada:

```text
Projeto faculdade
Duração: 3h
Prazo: quarta
Prioridade: alta

Estudar Java
Duração: 2h
Prazo: sexta
Prioridade: média

Academia
Duração: 1h
Recorrente

Ler livro
Duração: 2h
Sem prazo
Prioridade: baixa
```

Capacidade:

```text
Segunda: 4h
Terça: 4h
Quarta: 3h
```

O planner poderia gerar:

```text
SEGUNDA — 4h

Projeto faculdade
└── 2h

Academia
└── 1h

Estudar Java
└── 1h


TERÇA — 4h

Projeto faculdade
└── 1h

Estudar Java
└── 1h

Ler livro
└── 2h
```

O plano deve ser apresentado ao usuário para aprovação.

---

## 7. MVP

O MVP deve permanecer pequeno e concentrado no problema principal.

### 7.1 Tarefas

Campos iniciais:

```text
id
título
descrição
prazo
duração estimada
prioridade
status
```

---

### 7.2 Capacidade

Configuração de disponibilidade por dia.

Exemplo:

```text
Segunda: 4h
Terça: 5h
Quarta: 3h
...
```

---

### 7.3 Planner

Recebe:

```text
tarefas
+
duração
+
prazo
+
prioridade
+
capacidade diária
```

e produz:

```text
blocos de trabalho distribuídos pelos dias
```

---

### 7.4 Aprovação do plano

O usuário visualiza o plano e decide:

- aprovar;
- ajustar.

---

### 7.5 Execução

Cada bloco pode ser iniciado e acompanhado.

Exemplo:

```text
Projeto faculdade

Tempo restante: 1h32

[ Iniciar ]
```

Após conclusão:

```text
+120 XP
```

---

### 7.6 Histórico

Exemplo:

```text
Hoje
4 tarefas concluídas
3h40 trabalhadas
+280 XP

Esta semana
18 tarefas
21h10 trabalhadas
+1.420 XP
```

---

## 8. O que NÃO fazer no MVP

Evitar transformar o projeto em um sistema gigantesco.

Inicialmente não adicionar, sem necessidade clara:

- calendário completo;
- integração com dezenas de serviços;
- rede social;
- equipes;
- projetos complexos;
- árvores de habilidades;
- sistema completo de hábitos;
- recompensas físicas;
- marketplace;
- notificações avançadas;
- IA para decidir tudo;
- dezenas de filtros e dashboards.

A regra geral deve ser:

> **Se a funcionalidade não melhora a capacidade do sistema de transformar tarefas em um bom plano, ela deve ser questionada.**

---

## 9. Motor de planejamento

Essa é a principal parte técnica e de produto do projeto.

O primeiro planner deve ser **determinístico**, baseado em regras claras.

Uma primeira hipótese de processo:

```text
1. Identificar tarefas disponíveis
2. Considerar prazos
3. Considerar prioridade
4. Considerar duração estimada
5. Considerar capacidade diária
6. Dividir tarefas grandes em blocos
7. Distribuir os blocos
8. Detectar conflitos
9. Gerar uma proposta
10. Apresentar o plano ao usuário
```

Essa sequência é apenas uma hipótese inicial.

As regras reais precisam ser definidas e testadas.

---

## 10. Casos que o planner deverá tratar

O projeto deve testar principalmente situações em que não existe uma solução perfeita.

Exemplos:

### Capacidade insuficiente

```text
Tarefas restantes: 12h
Capacidade até o prazo: 8h
```

O sistema precisa indicar que não consegue cumprir tudo.

---

### Muitos prazos próximos

Várias tarefas importantes vencendo no mesmo dia.

---

### Tarefa muito grande

Uma tarefa de 6h em um dia com apenas 2h disponíveis.

---

### Tarefa sem prazo

Como tarefas sem prazo devem disputar espaço com tarefas urgentes?

---

### Atraso

Uma tarefa planejada para terça não foi concluída.

O sistema deve propor como reorganizar.

---

### Alteração de capacidade

O usuário informa que hoje só poderá trabalhar 1h em vez das 4h planejadas.

O planner precisa conseguir gerar uma nova proposta.

---

## 11. IA

### Não usar IA no núcleo do MVP

A primeira versão do planner deve funcionar sem IA.

Motivos:

- permite entender o problema de verdade;
- facilita testes;
- evita depender de respostas probabilísticas;
- torna as regras explicáveis;
- cria uma oportunidade real de estudar algoritmos e modelagem.

Uma possível evolução futura é comparar:

```text
Planner baseado em regras
vs.
Planner assistido por IA
```

A IA poderia futuramente ajudar em tarefas como:

- interpretar entradas mais naturais;
- sugerir duração;
- explicar conflitos;
- sugerir replanejamento;
- auxiliar na criação de tarefas.

Mas a inteligência principal do planejamento deve continuar sendo compreensível.

---

## 12. Relação com ferramentas existentes

Algumas referências de mercado levantadas durante a ideação:

### Habitica

Forte foco em gamificação e transformação das tarefas em elementos de RPG.

### LifeUp

Gamificação ainda mais profunda, com XP e sistemas típicos de jogos.

### Todoist

Gerenciador de tarefas maduro, com recursos de produtividade e gamificação mais leve.

### TickTick

Combina tarefas, calendário e produtividade.

### Amazing Marvin

É um concorrente especialmente relevante porque possui recursos relacionados a duração estimada, planejamento, capacidade e gamificação.

### Observação

O objetivo deste projeto não é competir por quantidade de funcionalidades.

A oportunidade está em explorar melhor:

> **planejamento automático + revisão humana + gamificação leve.**

As funcionalidades e posicionamento dos produtos existentes podem mudar ao longo do tempo e devem ser reavaliados antes de decisões comerciais.

---

## 13. Princípios do produto

### 1. Simplicidade de entrada

Cadastrar uma tarefa deve ser rápido.

### 2. Sistema faz o trabalho de organização

O usuário informa as restrições; o sistema monta uma proposta.

### 3. Usuário continua no controle

O sistema sugere, mas não toma decisões irreversíveis silenciosamente.

### 4. Gamificação como incentivo

XP serve para motivar e medir progresso, não para transformar tudo em um RPG.

### 5. Planejamento explicável

O usuário deve conseguir entender por que determinada tarefa foi colocada em determinado lugar.

### 6. Menos funcionalidades, mais valor

O MVP deve resolver muito bem o problema central.

---

## 14. Perguntas ainda em aberto

Estas decisões ainda não foram fechadas:

- Como determinar a ordem das tarefas?
- Como pesar prazo vs. prioridade?
- Qual tamanho ideal de um bloco?
- O usuário pode definir blocos mínimos/máximos?
- Como tratar tarefas sem prazo?
- Como tratar tarefas recorrentes?
- Como calcular XP?
- A dificuldade será informada pelo usuário ou determinada pelo sistema?
- Como lidar com tarefas atrasadas?
- O planner deve otimizar para concluir mais tarefas, evitar atrasos ou distribuir melhor a carga?
- Como lidar com imprevistos?
- Quando um plano deve ser automaticamente recalculado?
- Como o sistema explica o motivo de cada decisão?

Essas perguntas devem ser respondidas **antes ou durante a implementação do planner**, e não simplesmente delegadas à IA.

---

## 15. Direção técnica sugerida

O projeto deve dar prioridade a:

- modelagem de domínio;
- regras de negócio;
- algoritmo de planejamento;
- testes automatizados;
- persistência;
- histórico;
- observabilidade;
- tratamento de casos extremos.

A arquitetura e stack ainda não precisam ser congeladas nesta fase.

---

## 16. Uso de IA durante o desenvolvimento

O projeto pode seguir um fluxo de desenvolvimento orientado pelo princípio:

```text
Usuário pensa/projeta
        ↓
IA revisa e aponta problemas
        ↓
Usuário analisa os argumentos
        ↓
Usuário decide
        ↓
IA implementa
        ↓
Usuário revisa
        ↓
Testes/CI
```

A IA não deve decidir automaticamente:

- arquitetura;
- regras de negócio;
- algoritmo do planner;
- bibliotecas;
- padrões de projeto.

O papel da IA é principalmente:

- revisar;
- questionar;
- implementar;
- ajudar a explorar alternativas;
- encontrar casos extremos.

---

## 17. Critério principal de sucesso

O projeto não deve ser considerado bem-sucedido apenas porque:

> “as tarefas estão salvas e aparecem na tela”.

A pergunta principal é:

> **O sistema consegue pegar uma lista realista de obrigações e transformar isso em um plano que pareça realmente útil para o usuário?**

Esse é o teste central do produto.

---

## 18. Próximo passo

Antes de começar a construir telas ou endpoints, o próximo trabalho mais importante é especificar o **planner**.

O ideal é definir:

```text
Entrada
↓
Restrições
↓
Critérios de prioridade
↓
Algoritmo
↓
Plano gerado
↓
Conflitos
↓
Replanejamento
```

Depois disso, o domínio e a implementação podem ser desenhados em torno desse núcleo.

