# Devcontainer

Dieses Setup startet den NowChess-Workspace zusammen mit Redis und PostgreSQL.

## Enthaltene Services
- `workspace` – Scala/Gradle-Entwicklungscontainer
- `redis` – Redis 7.4
- `postgres` – PostgreSQL 16

## Wichtige Ports
- App-Services: `8080`, `8081`, `8082`, `8083`, `8084`, `8085`, `8086`, `9086`
- Redis auf dem Host: `16379`
- PostgreSQL auf dem Host: `15432`

## Einstieg
- VS Code: Ordner in einem Dev Container öffnen
- IntelliJ: Dev Container / Docker-Compose-Workspace öffnen und den `workspace`-Dienst nutzen

