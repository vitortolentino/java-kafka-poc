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

Everything runs in Docker (Azure CLI + Terraform), from the project root in WSL:

```bash
docker compose run --rm --build infra
```

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
