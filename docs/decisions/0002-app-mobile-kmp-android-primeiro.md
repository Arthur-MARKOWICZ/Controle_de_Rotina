# ADR-0002: App mobile nativo em Kotlin Multiplatform, Android primeiro

## Status

Aceito (planejado no change OpenSpec `app-mobile-android`)

## Data

2026-11-12

## Contexto

A issue [#41](https://github.com/Arthur-MARKOWICZ/Controle_de_Rotina/issues/41)
pede presença de app no celular (ícone na home, sensação nativa, gestos e, no
futuro, notificações de bloco/prazo), além do web responsivo já entregue em #12.
A issue deixava a escolha em aberto entre **PWA** (manifest + service worker,
reusando o React) e **app nativo/híbrido** consumindo a mesma API REST.

Restrições:

- A API Spring é o único contrato. O auth usa access JWT de 15 min e refresh
  rotacionado em cookie `refresh_token` (`HttpOnly`, `Path=/auth`), com
  revogação da família em caso de reuso (#16).
- O `mcp/` (ADR-0001) já mostra que um cliente fora do browser funciona sem
  mudar o backend: ele lê o `Set-Cookie` e reenvia o refresh como `Cookie`.
- O projeto-irmão `controle_gastos` já tem um app Kotlin Multiplatform +
  Compose Multiplatform, validado primeiro no Android (ADR-004 de lá), com a URL
  da API resolvida por `.env` no build (ADR-021 de lá).
- Não há macOS/Xcode no ambiente de desenvolvimento.

## Decisão

- Criar o pacote `mobile/` na raiz: projeto Gradle **Kotlin Multiplatform** com
  `shared/` (domínio, cliente HTTP Ktor, sessão, ViewModels e UI Compose
  Multiplatform) e `androidApp/` (ponto de entrada, Keystore, recursos).
  O alvo iOS fica só com a estrutura, sem build nem validação.
- **Android primeiro**: só o Android é compilado, testado e distribuído neste
  corte (APK/AAB assinado localmente, sem Play Store).
- O app consome a API REST existente **sem mudanças no backend**: o access fica
  em memória, o refresh é guardado cifrado com chave do Android Keystore e
  enviado como `Cookie` apenas em `/auth/*`, com renovação única e serializada
  em 401.
- A URL da API é definida no build por `mobile/.env` (`API_ENV=local|prod`).
  Release exige HTTPS, e HTTP sem TLS só é liberado no debug para hosts locais.
- MVP: login/sessão, Hoje (ver/gerar/aprovar plano, executar bloco, barra de
  sessão ativa, XP), Tarefas (lista, quick add, sessão avulsa) e Hábitos do dia.

## Alternativas consideradas

### PWA instalável (sugestão original da issue)

- Vantagem: reusa 100% do React e não tem build/loja separados.
- Desvantagem: a sensação nativa é limitada, o suporte a instalação/push no iOS
  Safari é restrito, o refresh fica preso ao cookie do browser e não há
  armazenamento seguro de plataforma. Também não deixa um caminho limpo para
  biometria e notificações locais de bloco.

### Android nativo puro (Jetpack Compose, um módulo)

- Vantagem: configuração inicial menor.
- Desvantagem: um iOS no futuro duplicaria domínio, cliente e UI. O padrão KMP
  já está resolvido no `controle_gastos`.

### Híbrido (Capacitor / React Native)

- Vantagem: reaproveita conhecimento de React.
- Desvantagem: contraria a decisão de usar Kotlin como linguagem principal do
  mobile e traz outra cadeia de build sem ganho sobre o KMP.

## Consequências

- Nenhuma entrega pode afirmar suporte a iOS sem compilação e testes em Mac.
- O proxy do front (vite/nginx, `API_PROXY.md`) **não** se aplica ao app: ele
  chama a API direto pela URL do build.
- Um prefixo novo de controller exige atualizar o cliente mobile só se o app for
  usar aquela rota.
- Fica para depois: push/notificações locais de bloco e prazo, biometria,
  offline-first com fila de sync, publicação na Play Store, cadastro e
  recuperação de senha no app, ajuste fino do rascunho, projetos/tags/Decidir/
  calendário/histórico.
- Se o backend passar a devolver o refresh no corpo para clientes nativos, o
  armazenamento continua o mesmo, e só o parser de cookie sai.
- Um job de CI novo, filtrado por `mobile/**`.

## Referências

- Change: `openspec/changes/app-mobile-android/` (proposal, design, specs `mobile-*`, tasks)
- [ADR-0001: MCP como cliente REST](0001-mcp-cliente-rest-stdio.md)
- `controle_gastos/docs/decisions/0004-kmp-android-primeiro.md` e `0021-ambientes-de-api-no-app-mobile.md`
