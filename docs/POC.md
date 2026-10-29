# POC - Spring Boot + Kafka + NoSQL

Study project. Original requirements first, then the decisions taken and the current state,
so work can resume without previous context.

## Constraints
- No Node.js part (the original spec had Node.js 24/25 with Express/Fastify and class-validator: dropped).
- Everything runs in Docker: nothing besides Docker is installed in WSL (build, tests, Kafka, database and
  Azure/Terraform tooling run in containers).
- Cloud: **Azure** (chosen to learn it). Infrastructure as code: **Terraform**.

## Stack
- Java 25 (latest version)
- Spring Boot 4.x, deployed to a free cloud tier (Azure App Service)
  - Bean Validation (annotations)
- NoSQL (Azure: Cosmos DB with the MongoDB API; MongoDB 8 locally)
  - schema and performance best practices
- GitHub Actions (CI/CD)
  - build the jar package
  - deploy to the cloud
  - frontend deploy (Azure Blob Storage): **not applicable**, there is no frontend without the Node.js part
- Kafka (producer [topics/partitions] / consumer)
  - concepts: consumer groups, retry, dead-letter

## Scope
1 CRUD web service (REST: GET/POST/PUT/DELETE) to send the message.

Message delivery: 1 producer, 2 consumers.

## Steps

| # | Step | Status |
|---|---|---|
| 1 | Build the CRUD (using records and Bean Validation) and persist to the NoSQL database. | Done |
| 2 | Build the CI/CD pipeline and deploy to a serverless platform (Azure). | Code done, never applied/run (see Open items) |
| 3 | Run Kafka in Docker or use a cloud-hosted one (if free). | Done (Docker) |
| 4 | Change the CRUD so that POST publishes a message to the queue. | Done |
| 5 | Create two consumers that read the queue and save to the database (record whether it was saved by Consumer A or Consumer B). | Done |
| 6 | (Optional) Add a Circuit Breaker (Resilience4j) to query https://www.visualcrossing.com/weather-api/ | Not started |
| 7 | (Advanced) Implement a hexagonal architecture with Spring Boot and Kafka. | Not started |

## Create payload
```json
{
  "name": "",        // @NotNull @NotEmpty @Size(min = 5)
  "city": "",
  "note": ""
}
```

## Current design

### Versions
Spring Boot 4.1.1, spring-kafka 4.1.1 (Jackson 3: `JacksonJsonSerializer`/`JacksonJsonDeserializer`),
kafka-clients 4.2.1, MongoDB 8, `apache/kafka:4.1.0`, Terraform 1.16.5, azurerm 5.x, azuread 3.x.

### Packages (`src/main/java/poc_kafka`)
| Package | Content |
|---|---|
| `controller` | `MessageController` (`/messages`), `ApiExceptionHandler` (RFC 9457 Problem Details), `dto/MessageRequest` (validated record), `dto/MessageResponse` |
| `domain` | `Message` record, Mongo document in collection `messages` (`city` indexed) |
| `repository` | `MessageRepository` (Spring Data MongoDB) |
| `service` | `MessageService`, `MessageNotFoundException` |
| `messaging` | `KafkaConfig`, `MessageProducer`, `MessageConsumers`, `MessageCreatedEvent` |

### REST API
| Method | Path | Behavior |
|---|---|---|
| GET | `/messages?city=&page=&size=&sort=` | Paged list (`PagedModel`), optional city filter |
| GET | `/messages/{id}` | 200 or 404 |
| POST | `/messages` | Generates a UUID, publishes to Kafka, returns **202 Accepted** + `Location`; does not touch the DB |
| PUT | `/messages/{id}` | Synchronous update in the DB (keeps `createdAt` and `savedBy`) |
| DELETE | `/messages/{id}` | 204 or 404 |

Validation errors: 400 with `errors.<field>`.
Document fields: `id, name, city, note, createdAt, updatedAt, savedBy`.

### Kafka flow
- Topic `messages`, **2 partitions**; record key = message id (same message, same partition).
  Replication factor is the broker default (no `replicas(1)`, so managed brokers requiring 3 work).
- Producer: `acks=all`, idempotence on, `send(...).join()` (POST waits for the broker ack).
- Consumers `consumer-a` and `consumer-b`: two `@KafkaListener`s in the **same group** `message-savers`;
  each owns one partition, so each message is saved exactly once and `savedBy` records which one.
- Consumer save is idempotent: if the id already exists, a redelivery is ignored (does not overwrite a PUT).
- Errors: `DefaultErrorHandler` retries twice (1 s apart), then `DeadLetterPublishingRecoverer` sends the
  record to `messages.DLT` (same partition number). Values are read through `ErrorHandlingDeserializer`, so
  malformed JSON goes straight to the DLT. A `dead-letter-logger` listener logs DLT records.
- `ProducerFactory` uses `DelegatingByTypeSerializer`: `byte[]` raw (to forward poison pills unchanged), anything else JSON without type headers.
- Consumer: `auto-offset-reset=earliest`, JSON default type `MessageCreatedEvent`, type headers ignored.

### Configuration (environment variables)
| Variable | Local default | Azure |
|---|---|---|
| `MONGODB_URI` | `mongodb://localhost:27017/poc` (compose: `mongo:27017`) | Cosmos DB primary connection string |
| `MONGODB_DATABASE` | `poc` | `poc` (Cosmos connection string has no DB in the path) |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9094` (compose: `kafka:9092`) | external managed Kafka |
| `KAFKA_SECURITY_PROTOCOL` | `PLAINTEXT` | `SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | `PLAIN` | `PLAIN` / `SCRAM-*` |
| `KAFKA_SASL_JAAS_CONFIG` | empty | broker credentials |
| `SERVER_PORT` | 8080 | 80 |

### Local environment (Docker)
- `docker-compose.yaml`: `mongo`, `kafka` (KRaft single node; `kafka:9092` for containers,
  `localhost:9094` for the host; no volume), `app` (dev target with hot reload and debug on 5005),
  `infra` (profile `infra`: Azure CLI 2.90 + Terraform, `docker compose run --rm --build infra`).
- Dev Container (`.devcontainer/`): service `ide` (IntelliJ backend), same JDK/Maven cache.
- Tests: `mvn test` inside the Dev Container. They need the compose MongoDB (`mongo:27017`); Kafka is
  **embedded** (`@EmbeddedKafka`) because the Dev Container has no Docker. Do not use `mvn clean`
  (`target/` is a volume).
- Tests: `MessageControllerTest` (CRUD, waits with Awaitility for the async save), `MessageKafkaFlowTest`
  (savedBy matches the owner of each record's partition; malformed record lands in `messages.DLT`),
  `PocKafkaApplicationTests`.

### Azure (`infra/`, details in `infra/README.md`)
- Resource group `rg-poc-kafka`, names suffixed with a random string (global uniqueness), region `brazilsouth`.
- Cosmos DB: MongoDB API 6.0, free tier, Session consistency, database `poc` with 400 RU/s shared,
  collection `messages` with indexes `_id` (unique) and `city`, no shard key (tiny data).
- App Service: Linux plan **F1**, Java SE 25, HTTPS only, FTP/WebDeploy basic auth disabled,
  `always_on = false` on F1.
- GitHub OIDC: Entra app registration + service principal + federated credential for
  `repo:<github_repository>:environment:production` + role "Website Contributor" on the resource group.
- Kafka is **not** provisioned: no free Kafka on Azure (Event Hubs needs Standard). The app takes an
  external broker through the `kafka_*` Terraform variables.
- Local Terraform state (git-ignored); `terraform.tfvars` git-ignored, template in `terraform.tfvars.example`.

### CI/CD (`.github/workflows/ci-cd.yaml`)
- `build`: MongoDB service container, Temurin 25, `mvn -B verify`, uploads the jar.
- `deploy`: only on `main` and only when the variable `AZURE_WEBAPP_NAME` exists; environment `production`;
  `azure/login@v3` with OIDC (`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` as repository
  variables from `terraform output`), then `azure/webapps-deploy@v3`.

## Open items
- Create the GitHub repository on the user's personal account, then commit and push (done by the user).
- Choose a managed Kafka for the cloud (e.g. Confluent Cloud trial) and fill `terraform.tfvars`.
- Run `terraform apply`; confirm Java 25 is offered (`az webapp list-runtimes --os-type linux`) and that
  the account can create Entra app registrations (otherwise deploy manually with `az webapp deploy`).
- Never executed: `docker compose up` with the Kafka container, the `infra` image build, the GitHub workflow.
- Known free-tier limits: F1 has 60 CPU min/day and sleeps when idle (consumers stop with it); B1 if it hurts.
  Cosmos DB free tier: one account per subscription.
- POST returns 500 if Kafka is unreachable (blocks up to `max.block.ms`, 60 s); could map to 503.
- Next steps: 6 (Resilience4j circuit breaker for the Visual Crossing weather API), 7 (hexagonal architecture).
