# Factory Events — Backend

API REST para receber, registrar e consultar eventos enviados por equipamentos de uma fábrica. O projeto implementa o backend do desafio técnico Fullstack, com validação das requisições, prevenção de duplicidade pelo `eventId` e auditoria para eventos de qualidade.

## Funcionalidades

- Cadastro e consulta de eventos.
- Validação dos campos obrigatórios e dos tipos de dados.
- Eventos `PRODUCTION`: registro do evento.
- Eventos `QUALITY`: registro do evento e de uma auditoria.
- Bloqueio de `eventId` duplicado, inclusive em requisições simultâneas.
- Gravação do evento e da auditoria na mesma transação.
- Respostas de erro estruturadas, com mensagens em português.
- Banco H2 persistente em arquivo, inicializado junto com a aplicação.

## Tecnologias

| Tecnologia | Uso |
| --- | --- |
| Java 21 | Linguagem e runtime |
| Spring Boot 3.5.16 | Inicialização e configuração |
| Spring Web MVC | API REST |
| Spring Data JPA / Hibernate | Persistência e mapeamento das entidades |
| Jakarta Validation | Validação das requisições |
| H2 | Banco de dados local |
| Lombok | Redução de código repetitivo |
| Maven 3.9.16 / Maven Wrapper | Compilação e gerenciamento de dependências |
| JUnit 5, Mockito e AssertJ | Testes automatizados |
| MockMvc | Verificação dos contratos HTTP |

## Pré-requisitos

- JDK 21 instalado e `JAVA_HOME` configurado.
- Acesso à internet na primeira execução para baixar o Maven e as dependências.
- Porta `8080` disponível.

O Maven Wrapper está incluído. Não é necessário instalar Maven, Docker ou um servidor de banco separado.

## Executar o backend

Execute os comandos na pasta que contém o `pom.xml` e o Maven Wrapper.

### Linux e macOS

```bash
java -version
./mvnw spring-boot:run
```

No macOS, para selecionar o Java 21 quando houver outras versões instaladas:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Windows — PowerShell

Com `JAVA_HOME` apontando para o JDK 21:

```powershell
java -version
.\mvnw.cmd spring-boot:run
```

A API fica disponível em `http://localhost:8080/api/events`. Para encerrar a aplicação, pressione `Ctrl+C`.

### Executar o JAR

```bash
./mvnw clean verify
java -jar target/factory-events-backend-0.0.1-SNAPSHOT.jar
```

No Windows, substitua `./mvnw` por `.\mvnw.cmd`.

## Integração com o frontend

Este projeto contém somente o backend. O frontend deve ser executado separadamente; seu código e os comandos de instalação e execução não estão incluídos aqui.

Para executar a solução em conjunto:

1. Inicie este backend com as instruções acima.
2. Configure o frontend para consumir `http://localhost:8080/api/events`.
3. Inicie o frontend seguindo o README do respectivo projeto, usando a origem `http://localhost:4200`.

O CORS permite essa origem nas rotas `/api/**`, com os métodos `GET`, `POST` e `OPTIONS` e os headers `Content-Type` e `Accept`. Caso o frontend utilize outra origem, ajuste [WebConfig.java](src/main/java/com/factory/events/config/WebConfig.java).

O frontend pode utilizar `detail` para exibir a mensagem de erro, `code` para identificar o tipo de falha e `errors` para associar mensagens aos campos do formulário.

## API REST

| Método | Endpoint | Resultado |
| --- | --- | --- |
| `POST` | `/api/events` | `201 Created`, com o evento registrado |
| `GET` | `/api/events` | `200 OK`, com a lista de eventos |

### Cadastrar um evento

```bash
curl -i -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "EVT-1001",
    "eventType": "PRODUCTION",
    "description": "Production line started",
    "timestamp": "2026-05-20T10:00:00"
  }'
```

Exemplo de resposta, com HTTP `201`:

```json
{
  "eventId": "EVT-1001",
  "eventType": "PRODUCTION",
  "description": "Production line started",
  "timestamp": "2026-05-20T10:00:00"
}
```

| Campo | Tipo | Validação |
| --- | --- | --- |
| `eventId` | String | Obrigatório, não pode conter apenas espaços, máximo de 255 caracteres e único no banco |
| `eventType` | Enum | Obrigatório: `PRODUCTION` ou `QUALITY` |
| `description` | String | Obrigatória, não pode conter apenas espaços e tem máximo de 255 caracteres |
| `timestamp` | Data/hora | Obrigatório, em ISO 8601 sem fuso horário, como `2026-05-20T10:00:00` |

O `eventId` é fornecido pelo equipamento ou pelo cliente da API. Não é gerado pelo backend. O timestamp usa `LocalDateTime` e não representa um instante com fuso horário.

Para cadastrar um evento de qualidade, envie outro `eventId` e utilize `"eventType": "QUALITY"`. Além do evento, será criada uma auditoria contendo seu `eventId`, a data/hora do processamento no servidor e a ação `QUALITY`.

### Consultar eventos

```bash
curl -i http://localhost:8080/api/events
```

A resposta é uma lista de objetos com os mesmos campos apresentados no cadastro. Quando não há registros, a API retorna `[]` com HTTP `200`. Atualmente a consulta retorna todos os eventos, sem paginação, filtros ou ordenação garantida.

### Duplicidade e transações

Ao reenviar um evento, mantenha o mesmo `eventId`. Se esse identificador já estiver registrado, a API retorna HTTP `409`, mesmo que os demais campos sejam diferentes. O registro existente não é atualizado e nenhuma auditoria adicional é criada.

A verificação ocorre no service e é reforçada pela restrição única `uk_events_event_id` no banco. A restrição também impede que duas requisições simultâneas registrem o mesmo evento. Eventos com `eventId` diferentes são aceitos mesmo quando os outros campos são iguais.

O cadastro usa `@Transactional`: se a auditoria de um evento `QUALITY` falhar, o evento também é desfeito. Nesse caso, uma nova tentativa pode utilizar o mesmo `eventId`.

## Tratamento de erros

Os erros tratados pela aplicação usam `application/problem+json` e `ProblemDetail`.

| HTTP | Código da aplicação | Situação |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | Campos obrigatórios ausentes ou inválidos |
| `400` | `INVALID_JSON` | Corpo ausente, JSON inválido, tipo ou data/hora inválidos |
| `400` | `UNSUPPORTED_CONTENT_TYPE` | Tipo de conteúdo não suportado pela API |
| `409` | `DUPLICATE_EVENT` | `eventId` já registrado |
| `409` | `DATA_INTEGRITY_ERROR` | Outra violação de integridade no banco |
| `503` | `DATABASE_ERROR` | Falha ao acessar os dados |
| `500` | `INTERNAL_ERROR` | Erro inesperado |

Exemplo de duplicidade:

```json
{
  "type": "about:blank",
  "title": "Evento duplicado",
  "status": 409,
  "detail": "Já existe um evento registrado com este eventId.",
  "instance": "/api/events",
  "code": "DUPLICATE_EVENT"
}
```

Erros de validação também incluem `errors`, uma lista de objetos com `field` e `message`. Falhas internas são registradas nos logs do servidor; stack traces e detalhes do banco não são enviados ao cliente. Os demais erros de protocolo seguem o comportamento do Spring MVC, como HTTP `405` para um método não permitido.

## Banco de dados H2

O H2 inicia junto com o Spring Boot. O Hibernate cria as tabelas na primeira execução e utiliza `ddl-auto: update` nas seguintes. Os dados permanecem após reiniciar a aplicação.

| Configuração | Valor padrão |
| --- | --- |
| JDBC URL | `jdbc:h2:file:./data/factory-events` |
| Arquivo | `data/factory-events.mv.db`, relativo ao diretório de execução |
| Usuário | `sa` |
| Senha | Vazia |
| Console | `http://localhost:8080/h2-console` |

Com a aplicação em execução, abra o console e informe a mesma JDBC URL, usuário e senha da tabela acima.

### Entidades

| Tabela | Entidade | Identificadores e dados |
| --- | --- | --- |
| `events` | `Event` | `id` interno `Long` gerado pelo banco com `IDENTITY`; `eventId` externo único; tipo, descrição e timestamp |
| `event_audits` | `EventAudit` | `id` interno `Long` gerado pelo banco com `IDENTITY`; `eventId`, data/hora da auditoria e ação |

Os IDs internos não fazem parte dos DTOs da API. A auditoria armazena o `eventId` externo, sem relacionamento bidirecional entre entidades.

Para bancos criados por versões anteriores do projeto, `H2SchemaMigration` adapta o esquema antes de o Hibernate inicializar: atribui IDs internos aos eventos existentes, transfere a chave primária para `id`, mantém `eventId` único e remove a coluna da antiga chave de idempotência. Os registros existentes são preservados. Em bancos novos, o Hibernate cria diretamente o modelo atual.

As configurações locais estão em [application.yml](src/main/resources/application.yml). Console habilitado, senha vazia e atualização automática do esquema são escolhas para desenvolvimento local. Uma publicação em produção exige revisar essas configurações.

## Testes automatizados

Executar os testes:

```bash
./mvnw test
```

Compilar, executar os testes e empacotar:

```bash
./mvnw clean verify
```

Gerar o JAR sem executar os testes:

```bash
./mvnw clean package -DskipTests
```

Os testes utilizam H2 em memória, sem alterar o banco local em arquivo. A suíte verifica:

- Inicialização do contexto Spring e criação das tabelas.
- Persistência, geração dos IDs internos e consulta de eventos.
- Registro de auditoria apenas para eventos `QUALITY`.
- Validação dos campos e mensagens de erro HTTP.
- Bloqueio de duplicidade por `eventId`, incluindo requisições simultâneas.
- Rollback do evento quando a auditoria falha e possibilidade de nova tentativa.
- Migração do esquema H2 antigo com preservação dos dados.

## Arquitetura e organização

A aplicação utiliza um monólito em camadas, em um único módulo Maven:

```text
Controller → Service → Repository → H2
```

| Camada | Responsabilidade |
| --- | --- |
| `controller` | Receber requisições HTTP, validar a entrada e retornar DTOs |
| `service` | Aplicar regras de processamento, duplicidade e transação |
| `repository` | Consultar e persistir os dados com Spring Data JPA |
| `entity` | Mapear as tabelas do banco |
| `dto` | Definir contratos de entrada e saída com Java Records |
| `exception` | Centralizar respostas de erro |
| `config` | Configurar banco, atualização do esquema antigo e CORS |
| `enums` | Definir os tipos de evento |

```text
src/
├── main/
│   ├── java/com/factory/events/
│   │   ├── FactoryEventsApplication.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── enums/
│   │   ├── exception/
│   │   ├── repository/
│   │   └── service/
│   └── resources/application.yml
└── test/java/com/factory/events/
    ├── FactoryEventsApplicationTests.java
    ├── config/H2SchemaMigrationTests.java
    ├── controller/EventControllerTests.java
    └── service/
        ├── EventDuplicateTests.java
        └── EventServiceTests.java
```

## Principais decisões técnicas

- **Monólito em camadas:** mantém a solução simples e separa HTTP, regras de negócio e persistência.
- **H2 em arquivo:** permite execução local sem infraestrutura externa e preserva os dados entre reinicializações.
- **ID interno separado do `eventId`:** o banco gera a chave primária; o identificador fornecido pelo equipamento permite reconhecer reenvios.
- **Verificação de existência e restrição única:** oferece mensagem de duplicidade e garante integridade sob concorrência.
- **Transação para evento e auditoria:** evita registros parciais quando o processamento de `QUALITY` falha.
- **DTOs e validação:** mantém o contrato HTTP separado das entidades JPA e rejeita entradas inválidas antes do processamento.
- **ProblemDetail:** padroniza a estrutura das falhas e facilita o tratamento pelo frontend.
- **Injeção por construtor e service concreto:** explicita as dependências sem introduzir interfaces ou padrões desnecessários.

## Possíveis melhorias futuras

- Integrar o frontend e adicionar instruções completas para executar a solução Fullstack.
- Adicionar paginação, filtros e ordenação à consulta de eventos.
- Retornar HTTP `415 Unsupported Media Type` para tipos de conteúdo não suportados; atualmente esse caso retorna `400`.
- Ampliar os testes HTTP para os demais cenários de erro e para CORS.
- Adicionar logs de processamento e identificadores de correlação para facilitar diagnóstico.
- Evoluir a atualização do esquema para migrações versionadas, caso o projeto cresça.
- Separar configurações por ambiente e revisar banco, credenciais e console H2 antes de uma implantação em produção.
