# DevOps Control Plane

Plan de contrôle local : GitHub, CI/CD, Docker, Kubernetes et monitoring. Huit applications Spring Boot 3.5.15 (Java 21) derrière une passerelle, plus un frontend Angular 21.

## Démarrage

Prérequis : Java 21, Node 24.13, Docker Desktop. Maven 3.9.11 est fourni dans `.tools/` (non versionné).

```powershell
.\scripts\start-local.ps1
cd frontend
npx ng serve --host 127.0.0.1 --port 4200
```

Le script copie `.env.example` vers `.env` s'il manque, construit les JAR, puis lance Compose. L'interface est sur `http://127.0.0.1:4200`. Compte initial : `admin@dcp.local` / `ChangeMe!2026`.

## Ports hôte

| Service | Port |
| --- | --- |
| Angular | 4200 |
| API Gateway | 18080 |
| Auth | 18181 |
| Applications | 18082 |
| Docker | 18083 |
| Kubernetes | 18084 |
| Déploiements | 18085 |
| CI/CD | 18086 |
| Monitoring | 18087 |
| MySQL | 13306 |
| Prometheus | 19090 |
| Grafana | 13001 |
| Registre | 15000 |

## État vérifié le 11 octobre 2026

Compose, MySQL, Flyway, JWT et les métriques Actuator dans Prometheus et Grafana fonctionnent. Aucun cluster Kubernetes n'est joignable : les opérations de déploiement, blue/green, canary, rollback et HPA ne sont pas validées en cluster. Le détail est dans `docs/PROGRESS.md`.

Aucun push GitHub n'est fait par ce dépôt tant qu'il n'est pas demandé.
