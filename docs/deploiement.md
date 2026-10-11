# Déploiement

## Local

`scripts/start-local.ps1` exécute `mvn -B -DskipTests package`, puis `docker compose up -d --build`. Chaque image Java est `eclipse-temurin:21-jre-alpine` avec le JAR `service.jar` déjà construit. Le socket Docker n'est monté que sur `docker-service`.

## Kubernetes

Les manifestes et l'API blue/green, canary (HTTPRoute) et HPA existent dans `kubernetes-service`. Ils ne s'appliquent que si un cluster est joignable et, pour le canary, si les CRD Gateway API et un contrôleur sont installés. Sans cluster, l'API renvoie une indisponibilité et ne prétend pas avoir déplacé le trafic.

Le 11 octobre 2026 à 17:14, aucun contexte kubectl n'existait et la RAM libre était d'environ 4 Go. Docker Desktop Kubernetes n'a pas été activé.

Après `docker stop` des conteneurs coinrush, supcom et smartrecruit (liste dans `docs/conteneurs-arretes.txt`), un cluster kind mono-nœud `dcp` (kind 0.27.0, image `kindest/node:v1.32.2`) tourne dans la VM Docker. `kubernetes-service` utilise `infrastructure/kubernetes/kubeconfig.container`, qui pointe vers `https://dcp-control-plane:6443`. Le kubeconfig hôte reste sur `127.0.0.1`. Le namespace est `dcp`. Metrics Server répond après le correctif `--kubelet-insecure-tls`. Le client utilise le kubeconfig administrateur de kind.

Le registre Compose est attaché au réseau `kind` sous l'alias `registry`. Les images `registry:5000/dcp/web:1` et `:2` sont tirées par le nœud. `docker-compose.k8s.yml` monte ce kubeconfig sur `kubernetes-service` seulement.

Blue/green, HPA et rollback ont été observés. Les CRD Gateway API v1.6.3 et Traefik 3.3.5 programment le Gateway `dcp`. Le canary `split` répartit réellement le trafic HTTP : sur 200 requêtes, 10 % donne 20 réponses canary, 25 % en donne 50, 50 % en donne 100, 100 % en donne 200. Le détail est dans `docs/PROGRESS.md`.
