# Disposable tooling shell for provisioning Azure (infra/): Azure CLI + Terraform at pinned versions,
# so nothing is installed in WSL. Not the app image and not used by CI; see infra/README.md.
# Usage: docker compose run --rm --build infra

# Stage only used as a source for the Terraform binary (static Go binary, runs on any Linux)
FROM hashicorp/terraform:1.16.5 AS terraform

FROM mcr.microsoft.com/azure-cli:2.91.0
COPY --from=terraform /bin/terraform /usr/local/bin/terraform

# Non-root user (uid 1000, the usual WSL user, so files written to the bind mount keep your ownership).
# HOME holds the "az login" session, persisted by the azure-cli volume.
ENV HOME=/home/infra
RUN mkdir -p /home/infra/.azure && chown -R 1000:1000 /home/infra
USER 1000:1000

WORKDIR /app/infra
ENTRYPOINT ["bash"]
