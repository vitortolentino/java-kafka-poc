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

`docker compose up` starts only `mongo`, `kafka` and `app`. The other services are tools for specific
tasks: they sit behind a [profile](https://docs.docker.com/compose/how-tos/profiles/) and run on demand:

| Service | Profile | Purpose | Run it |
|---|---|---|---|
| `infra` | `infra` | Azure CLI + Terraform shell to create/change the Azure resources | `docker compose run --rm --build infra` |
| `trivy` | `tools` | Security scan | `docker compose run --rm trivy` |

The Dev Container service (`ide`) lives in `.devcontainer/docker-compose.devcontainer.yaml` and is started by the IDE.

### Dev Container (IntelliJ)

Open the project with Dev Containers (`.devcontainer/`): the IDE backend runs in the `ide`
container, with the same JDK 25 and Maven cache as the app.

## Tests

Inside the Dev Container (needs the compose MongoDB; Kafka runs embedded in the test JVM):

```bash
mvn test
```

Do not run `mvn clean`: `target/` is a volume mount point.

## Quality checks

| Check | Tool | Run it |
|---|---|---|
| Formatting | Spotless + palantir-java-format | `mvn spotless:apply` (fix), `mvn spotless:check` |
| Static analysis + security patterns | SpotBugs + FindSecBugs | `mvn verify` (exclusions with a reason in `spotbugs-exclude.xml`) |
| Code conventions (naming, imports, coding practices) | Checkstyle, Google style rules without layout | `mvn checkstyle:check` (rules in `checkstyle.xml`) |
| Dependency CVEs, Dockerfile/Terraform misconfig, secrets | Trivy | `docker compose run --rm trivy` (from WSL; config in `trivy.yaml`) |
| Commit messages | Conventional Commits | `commit-msg` hook / CI |

`mvn verify` runs tests, Spotless, Checkstyle and SpotBugs together, the same as CI. GitHub also runs CodeQL
and Dependabot (`.github/`).

### Git hooks

Versioned in `.githooks/`; the Dev Container enables them on creation. In an existing container:

```bash
git config core.hooksPath .githooks
```

| Hook | Runs | Time |
|---|---|---|
| `pre-commit` | `mvn spotless:check` | seconds |
| `commit-msg` | `scripts/check-commit-msg.sh` | instant |
| `pre-push` | `mvn verify` (tests need the compose MongoDB) | ~1 min |

Hooks skip with a warning when `mvn` is not available (git run outside the Dev Container).
Bypass on purpose with `--no-verify`.

### Commit messages

`<type>(<optional scope>): <description>`, header up to 100 characters.
Types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.

```text
feat(kafka): add retry topic for failed messages
fix: return 404 when the message does not exist
feat(api)!: rename fields          # "!" marks a breaking change
```

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

Then follow [infra/README.md](infra/README.md). The infra shell is only for creating or changing the
infrastructure; after that, every push to `main` deploys through GitHub Actions.
