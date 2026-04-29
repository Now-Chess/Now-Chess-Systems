FROM mcr.microsoft.com/devcontainers/java:21-bookworm

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        postgresql-client \
        redis-tools \
        stockfish \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /home/vscode/.gradle/wrapper/dists \
    && chown -R vscode:vscode /home/vscode/.gradle

