# Déploiement

## Local

`scripts/start-local.ps1` exécute `mvn -B -DskipTests package`, puis `docker compose up -d --build`. Chaque image Java est `eclipse-temurin:21-jre-alpine` avec le JAR `service.jar` déjà construit. Le socket Docker n'est monté que sur `docker-service`.

## Kubernetes

Les manifestes et l'API blue/green, canary (HTTPRoute) et HPA existent dans `kubernetes-service`. Ils ne s'appliquent que si un cluster est joignable et, pour le canary, si les CRD Gateway API et un contrôleur sont installés. Sans cluster, l'API renvoie une indisponibilité et ne prétend pas avoir déplacé le trafic.

Le 11 octobre 2026 à 17:06, aucun contexte kubectl n'existait. Les déploiements réels, le rollback et le HPA ne sont donc pas démontrés.
