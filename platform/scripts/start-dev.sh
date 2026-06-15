#!/usr/bin/env bash
set -euo pipefail

# Start local development infrastructure (PostgreSQL + Redis)
echo "Starting dev infrastructure..."
docker compose -f infrastructure/docker-compose.dev.yml up -d

echo ""
echo "Infrastructure running:"
echo "  PostgreSQL : localhost:5432 (db: mesha, user: mesha, password: mesha)"
echo "  Redis      : localhost:6379"
echo ""
echo "Start backend-api:"
echo "  cd backend-api && mvn spring-boot:run"
echo ""
echo "Start frontend:"
echo "  cd frontend && npm run dev"
