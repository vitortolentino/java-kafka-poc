# App Service and Cosmos DB names are part of public URLs, so they must be globally unique
resource "random_string" "suffix" {
  length  = 6
  special = false
  upper   = false
}

locals {
  name = "${var.project}-${random_string.suffix.result}"
}

resource "azurerm_resource_group" "main" {
  name     = "rg-${var.project}"
  location = var.location
}

# ---------------------------------------------------------------------------
# Cosmos DB with the MongoDB API: the app keeps using the MongoDB driver,
# only MONGODB_URI changes.
# ---------------------------------------------------------------------------

resource "azurerm_cosmosdb_account" "main" {
  name                 = "cosmos-${local.name}"
  location             = azurerm_resource_group.main.location
  resource_group_name  = azurerm_resource_group.main.name
  offer_type           = "Standard"
  kind                 = "MongoDB"
  mongo_server_version = "6.0"
  free_tier_enabled    = var.cosmos_free_tier

  capabilities {
    name = "EnableMongo"
  }

  # Session: a client always reads its own writes; cheaper than Strong
  consistency_policy {
    consistency_level = "Session"
  }

  geo_location {
    location          = azurerm_resource_group.main.location
    failover_priority = 0
  }
}

# Throughput (RU/s) is provisioned at database level and shared by its collections.
# 400 RU/s is the minimum and fits in the free tier.
resource "azurerm_cosmosdb_mongo_database" "main" {
  name                = "poc"
  resource_group_name = azurerm_resource_group.main.name
  account_name        = azurerm_cosmosdb_account.main.name
  throughput          = 400
}

# Indexes declared here instead of relying on the app: Cosmos DB charges RUs for every
# query, and filtering by an unindexed field scans the whole collection.
# No shard key: the data is tiny. With real volume, a key with high cardinality that
# appears in most queries (e.g. city) would spread the data across partitions.
resource "azurerm_cosmosdb_mongo_collection" "messages" {
  name                = "messages"
  resource_group_name = azurerm_resource_group.main.name
  account_name        = azurerm_cosmosdb_account.main.name
  database_name       = azurerm_cosmosdb_mongo_database.main.name

  index {
    keys   = ["_id"]
    unique = true
  }

  index {
    keys = ["city"]
  }
}

# ---------------------------------------------------------------------------
# App Service running the Spring Boot jar on the Java SE runtime
# ---------------------------------------------------------------------------

resource "azurerm_service_plan" "main" {
  name                = "asp-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  os_type             = "Linux"
  sku_name            = var.app_service_sku
}

resource "azurerm_linux_web_app" "main" {
  name                = "app-${local.name}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  service_plan_id     = azurerm_service_plan.main.id
  https_only          = true

  # Deploys use OIDC (see github.tf), so password-based publishing is turned off
  ftp_publish_basic_authentication_enabled       = false
  webdeploy_publish_basic_authentication_enabled = false

  site_config {
    # The free plan does not support Always On: the app sleeps after ~20 idle minutes,
    # and so do the Kafka consumers inside it
    always_on = var.app_service_sku != "F1"

    application_stack {
      java_server         = "JAVA"
      java_server_version = "25"
      java_version        = "25"
    }
  }

  app_settings = {
    # App Service routes traffic to port 80 of the Java SE container
    SERVER_PORT             = "80"
    MONGODB_URI             = azurerm_cosmosdb_account.main.primary_mongodb_connection_string
    MONGODB_DATABASE        = azurerm_cosmosdb_mongo_database.main.name
    KAFKA_BOOTSTRAP_SERVERS = var.kafka_bootstrap_servers
    KAFKA_SECURITY_PROTOCOL = var.kafka_security_protocol
    KAFKA_SASL_MECHANISM    = var.kafka_sasl_mechanism
    KAFKA_SASL_JAAS_CONFIG  = var.kafka_sasl_jaas_config
  }
}
