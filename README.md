# poc-kafka

Study POC: a Spring Boot 4 / Java 25 REST CRUD whose POST publishes to Kafka; two consumers
(A and B, same consumer group) save the messages to MongoDB (Cosmos DB on Azure).

- Spec, decisions and current status: [docs/POC.md](docs/POC.md)
- Azure deploy (Terraform + GitHub Actions): [infra/README.md](infra/README.md)

Everything runs in Docker; nothing else needs to be installed.

## Run locally

```bash
docker compose up --build
```

| Service | Address | Notes |
|---|---|---|
| App | http://localhost:8080 | Hot reload: saving a file under `src/main` recompiles and restarts |
| Debug | `localhost:5005` | Remote JVM debug |
| MongoDB | `localhost:27017` | Database `poc` |
| Kafka | `localhost:9094` (host), `kafka:9092` (containers) | No volume: data is lost when the container is removed |

Changes to `pom.xml` require `docker compose restart app`.

### Dev Container (IntelliJ)

Open the project with Dev Containers (`.devcontainer/`): the IDE backend runs in the `ide`
container, with the same JDK 25 and Maven cache as the app.

## Tests

Inside the Dev Container (needs the compose MongoDB; Kafka runs embedded in the test JVM):

```bash
mvn test
```

Do not run `mvn clean`: `target/` is a volume mount point.

## API

```bash
# Create: queued in Kafka, returns 202 Accepted with the generated id
curl -i -X POST localhost:8080/messages -H 'Content-Type: application/json' \
  -d '{"name": "Maria Silva", "city": "Recife", "note": "first"}'

# Read (savedBy shows which consumer saved it: consumer-a or consumer-b)
curl localhost:8080/messages/{id}

# List, paged, optional city filter
curl 'localhost:8080/messages?city=Recife&page=0&size=10&sort=createdAt,desc'

# Update / delete
curl -X PUT localhost:8080/messages/{id} -H 'Content-Type: application/json' \
  -d '{"name": "Maria Souza", "city": "Olinda", "note": "edited"}'
curl -X DELETE localhost:8080/messages/{id}
```

Validation: `name` is required, at least 5 characters. Errors use RFC 9457 Problem Details
(`400` with `errors.<field>`, `404` for unknown ids).

## Watching Kafka

```bash
# Which consumer received each message, and from which partition
docker compose logs -f app | grep received

# Consumer group: partition owners and lag (unread records)
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group message-savers

# Dead-letter: type invalid JSON here; the app logs "Dead-letter record"
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic messages

# Read the dead-letter topic
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic messages.DLT --from-beginning
```

## Deploy to Azure

```bash
docker compose run --rm --build infra   # shell with Azure CLI + Terraform
```

Then follow [infra/README.md](infra/README.md).
