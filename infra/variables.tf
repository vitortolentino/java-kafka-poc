variable "subscription_id" {
  description = "Azure subscription to deploy into (az account show --query id -o tsv)."
  type        = string
}

variable "location" {
  description = "Azure region for all resources."
  type        = string
  default     = "brazilsouth"
}

variable "project" {
  description = "Prefix used in resource names."
  type        = string
  default     = "poc-kafka"
}

variable "app_service_sku" {
  description = "App Service plan SKU. F1 is free (60 CPU minutes/day, no Always On); B1 is the cheapest paid one."
  type        = string
  default     = "F1"
}

variable "cosmos_free_tier" {
  description = "Cosmos DB free tier (1000 RU/s and 25 GB free). Only one account per subscription can use it."
  type        = bool
  default     = true
}

variable "github_repository" {
  description = "GitHub repository allowed to deploy, as owner/name (e.g. vitor/poc-kafka)."
  type        = string
}

variable "kafka_bootstrap_servers" {
  description = "Bootstrap servers of the managed Kafka the app connects to in the cloud."
  type        = string
}

variable "kafka_security_protocol" {
  description = "Kafka security protocol; managed brokers use SASL_SSL."
  type        = string
  default     = "SASL_SSL"
}

variable "kafka_sasl_mechanism" {
  description = "SASL mechanism: PLAIN (Confluent Cloud, Event Hubs) or SCRAM-SHA-256/512."
  type        = string
  default     = "PLAIN"
}

variable "kafka_sasl_jaas_config" {
  description = "Full sasl.jaas.config value with the broker credentials."
  type        = string
  sensitive   = true
}
