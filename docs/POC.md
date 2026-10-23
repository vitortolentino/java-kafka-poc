# POC - Spring Boot + Kafka + NoSQL

## Restrições
- Sem a parte de Node.js.
- Tudo roda em Docker: nada além do Docker é instalado no WSL (build, testes, Kafka e banco em containers).

## Stack
- Java 25 (a versão mais recente)
- Spring Boot 4.x, com publicação em nuvem gratuita (Azure App Service ou AWS Elastic Beanstalk)
  - Bean Validation (annotations)
- NoSQL (AWS: DocumentDB, DynamoDB... / Azure: Cosmos DB)
  - boas práticas de schema e performance
- GitHub Actions (CI/CD)
  - gerar o pacote jar
  - deploy na nuvem
  - deploy de frontend (AWS S3 ou Azure Blob Storage)
- Kafka (producer [tópicos/partições] / consumer)
  - conceitos: consumer groups, retry, dead-letter

## Escopo
1 webservice CRUD (REST: GET/POST/PUT/DELETE) para enviar a mensagem.

Envio de mensagem: 1 producer, 2 consumers.

## Passos
1. Criar o CRUD (usar records e Bean Validation) e salvar no BD NoSQL.
2. Criar o CI/CD e publicar em servidor serverless (AWS ou Azure).
3. Rodar o Kafka em Docker ou usar um da nuvem (se gratuito).
4. Alterar o CRUD para o POST criar uma mensagem na fila.
5. Criar dois consumers que leem a fila e salvam no BD (registrar se foi salvo pelo Consumer A ou pelo Consumer B).
6. (Opcional) Criar um Circuit Breaker (Resilience4j) para consultar https://www.visualcrossing.com/weather-api/
7. (Avançado) Implementar arquitetura hexagonal com Spring Boot e Kafka.

## Payload de cadastro
```json
{
  "nome": "",        // @NotNull @NotEmpty @Size(min = 5)
  "cidade": "",
  "observacao": ""
}
```
