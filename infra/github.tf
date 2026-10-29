# Lets GitHub Actions deploy without any stored password (OIDC / workload identity federation):
# GitHub signs a short-lived token saying "this is job X of repo Y in environment Z", and
# Entra ID trusts it because of the federated credential below.

resource "azuread_application_registration" "github" {
  display_name = "github-${var.project}"
}

resource "azuread_service_principal" "github" {
  client_id = azuread_application_registration.github.client_id
}

# Only jobs that run in the "production" environment of this repository get a token
resource "azuread_application_federated_identity_credential" "github" {
  application_id = azuread_application_registration.github.id
  display_name   = "github-production"
  audiences      = ["api://AzureADTokenExchange"]
  issuer         = "https://token.actions.githubusercontent.com"
  subject        = "repo:${var.github_repository}:environment:production"
}

# Least privilege: manage web apps in this resource group, nothing else
resource "azurerm_role_assignment" "github" {
  scope                = azurerm_resource_group.main.id
  role_definition_name = "Website Contributor"
  principal_id         = azuread_service_principal.github.object_id
}
