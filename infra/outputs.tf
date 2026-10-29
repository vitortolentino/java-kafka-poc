output "app_url" {
  value = "https://${azurerm_linux_web_app.main.default_hostname}"
}

# The values below go into GitHub: Settings > Secrets and variables > Actions > Variables

output "AZURE_WEBAPP_NAME" {
  value = azurerm_linux_web_app.main.name
}

output "AZURE_CLIENT_ID" {
  value = azuread_application_registration.github.client_id
}

output "AZURE_TENANT_ID" {
  value = azuread_service_principal.github.application_tenant_id
}

output "AZURE_SUBSCRIPTION_ID" {
  value = var.subscription_id
}
