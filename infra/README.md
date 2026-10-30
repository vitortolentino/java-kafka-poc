# Azure infrastructure (Terraform)

What gets created (all in resource group `rg-poc-kafka`):

| Resource | Why |
|---|---|
| Cosmos DB account (MongoDB API, free tier) + database `poc` + collection `messages` | NoSQL storage; the app keeps the MongoDB driver |
| App Service plan (Linux, F1) + Web App (Java 25 SE) | Runs the Spring Boot jar |
| Entra ID app registration + federated credential + role assignment | Lets GitHub Actions deploy with OIDC, no stored password |

Kafka is **not** created here: the app connects to an external managed Kafka
(Confluent Cloud, Azure Event Hubs Standard, ...) through the `kafka_*` variables.

## 1. Open the tooling shell

Provisioning needs two tools: the **Azure CLI** (`az login`, queries, manual deploys) and
**Terraform** (applies the `.tf` files). Instead of installing them, the compose service `infra`
(image `docker/infra.Dockerfile`) gives a disposable shell with both, at pinned versions, so every run
uses the same tooling and nothing is installed in WSL.

From the project root in WSL:

```bash
docker compose run --rm --build infra
```

- Opens `bash` in `/app/infra` (the project is mounted, so Terraform reads and writes this folder).
- Runs as uid 1000, so files it creates (`.terraform/`, `terraform.tfstate`) belong to your WSL user.
- The `az login` session is kept in the `azure-cli` volume; `--rm` removes only the container.
- It is not part of `docker compose up` (profile `infra`): use it when creating, changing or destroying
  resources, or for a manual deploy. Regular deploys run in GitHub Actions.
- If `az` fails with a permission error on `~/.azure`, the volume was created by an older root image:
  `docker volume rm poc-kafka_azure-cli` and run again.

## 2. Log in to Azure

```bash
az login --use-device-code
az account show --query id -o tsv      # subscription id for terraform.tfvars
az webapp list-runtimes --os-type linux | grep -i java   # confirm JAVA|25 is offered
```

## 3. Create the infrastructure

```bash
cp terraform.tfvars.example terraform.tfvars   # then fill in the values
terraform init
terraform plan      # read it: what will be created, changed, destroyed
terraform apply
terraform output    # values for GitHub
```

## 4. Configure GitHub

In the repository: **Settings > Environments**, create `production`.
Then **Settings > Secrets and variables > Actions > Variables**, add
`AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID` and `AZURE_WEBAPP_NAME`
with the values from `terraform output`. None of them is a secret: without the
federated credential they grant nothing.

Every push to `main` then runs `.github/workflows/ci-cd.yaml`: tests, jar, deploy.

## Manual deploy (without GitHub)

Build the jar in the Dev Container (`mvn package -DskipTests`), then in the infra shell:

```bash
az webapp deploy --resource-group rg-poc-kafka --name "$(terraform output -raw AZURE_WEBAPP_NAME)" \
  --src-path /app/target/poc-kafka-0.0.1-SNAPSHOT.jar --type jar
az webapp log tail --resource-group rg-poc-kafka --name "$(terraform output -raw AZURE_WEBAPP_NAME)"
```

## Tear down

```bash
terraform destroy
```

## Free tier limits

- **App Service F1**: 60 CPU minutes per day and no Always On. The app sleeps after
  ~20 idle minutes (the Kafka consumers stop with it) and the next request pays a cold start.
  Kafka consumers poll continuously, so the CPU quota may run out; `app_service_sku = "B1"` fixes both (paid).
- **Cosmos DB free tier**: 1000 RU/s and 25 GB, one account per subscription.
  If the subscription already has one, set `cosmos_free_tier = false`.
