#!/usr/bin/env bash

set -e

echo "Loading ForgeAI environment..."
set -a
source .env
set +a

echo "Starting PostgreSQL..."
docker start forgeai-postgres >/dev/null 2>&1 || true

echo "Starting ForgeAI Project Service..."
cd services/project-service

./mvnw spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Duser.timezone=UTC"