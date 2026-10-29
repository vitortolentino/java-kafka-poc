# Tooling for the infra/ folder: Azure CLI + Terraform, so nothing is installed in WSL.
# Usage: docker compose run --rm infra
FROM hashicorp/terraform:1.16.5 AS terraform

FROM mcr.microsoft.com/azure-cli:2.90.0
COPY --from=terraform /bin/terraform /usr/local/bin/terraform
WORKDIR /app/infra
ENTRYPOINT ["bash"]
