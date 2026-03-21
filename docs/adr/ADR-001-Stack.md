# ADR-001: Technology Stack Selection

## Status
Accepted

## Context
The "NowChessSystems" project requires a modern, scalable,
and maintainable technology stack to support web-based interfaces.
The system is designed as a microservice architecture to allow for independent scaling and development of various components (e.g., engine, matchmaking, user management).

## Decision
We have decided to use the following technologies for the core system:

### Backend
- **Language:** [Scala 3](https://scala-lang.org/) for its powerful type system, functional programming capabilities, and seamless JVM integration.
- **Framework:** [Quarkus](https://quarkus.io/) with the `io.quarkiverse.scala:quarkus-scala3` extension to leverage GraalVM native compilation and fast startup times.
- **Persistence:** [Hibernate](https://hibernate.org/) and [Jakarta Persistence](https://jakarta.ee/specifications/persistence/) for standard-based ORM.

### Frontend
- **Build Tool:** [Vite](https://vitejs.dev/) for a fast development experience.
- **Framework:** TBD (Evaluation between React, Angular, and Vue).
- **Terminal UI:** [Lanterna](https://github.com/mabe02/lanterna) for a text-based user interface (TUI).

### DevOps & Infrastructure
- **Orchestration:** [Kubernetes](https://kubernetes.io/) for container orchestration.
- **GitOps & Delivery:** [ArgoCD](https://argoproj.github.io/cd/) for continuous delivery and [Kargo](https://kargo.io/) for multi-stage lifecycle management.

### AI-Assisted Development
- [Claude Code Pro](https://claude.ai/) and [Claude Agent Teams](https://claude.ai/team) for coding and reviews.
- [Google Stitch](https://stitch.google.com/) (Free) for UI design and prototyping.

## Consequences

### Positive
- **High Performance:** Quarkus and GraalVM enable low memory footprint and fast startup.
- **Developer Productivity:** Scala 3 and AI tools provide a high-level, expressive environment.
- **Robustness:** Kubernetes and ArgoCD ensure reliable deployment and scaling.
- **Accessibility:** Offering both a TUI and a web interface caters to different user preferences.

### Negative / Risks
- **Complexity:** Managing a microservices architecture with Kubernetes adds operational overhead.
- **Learning Curve:** Scala 3 and the specific Quarkus-Scala integration may require training for new developers.
- **Consistency:** Maintaining parity between the TUI and Web frontend functionality.