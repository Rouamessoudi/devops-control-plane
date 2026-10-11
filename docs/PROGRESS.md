# Avancement

## Phase 1 — Audit du 11 octobre 2026

Les huit modules existent dans `backend/` : api-gateway, auth-service, application-service, docker-service, kubernetes-service, deployment-service, cicd-service, monitoring-service.

`mvn -B test` avec Maven 3.9.11 s'est terminé par BUILD SUCCESS à 13:17. Les tests Surefire exécutés : dcp-common 6, api-gateway 1, auth-service 6, application-service 5, docker-service 6, kubernetes-service 3, deployment-service 5, cicd-service 4, monitoring-service 10. Aucun échec.

Le dépôt n'est pas un dépôt Git. Aucun commit et aucun push n'ont été faits.

Docker Compose n'est pas démarré. Les constructions d'images ont échoué : d'abord parce que le parent Maven exige tous les modules, puis à cause d'un téléchargement Maven Central interrompu, puis d'un timeout TLS vers Docker Hub, puis du mode hors ligne qui n'a pas reconnu `bcprov-jdk18on:1.80.2` dans le cache de build. `docker compose config --quiet` réussit. La RAM libre de l'hôte était d'environ 6,6 Go sur 32 Go au moment de l'audit : le stack complet n'a pas été relancé.

Aucun contexte kubectl n'est configuré. Minikube n'est pas installé. Les appels Kubernetes réels ne sont donc pas validés.

Les fonctionnalités avancées demandées (blue/green, canary, HPA, alertes, équipes, GitOps, assistant) n'existaient pas dans le code audité. Prometheus, Grafana, Actuator et Micrometer sont déjà branchés pour le monitoring de base.

## Phase 2 — Déploiements avancés et HPA, code ajouté

Le service Kubernetes applique maintenant :

- deux Deployments blue/green et un Service dont le sélecteur `dcp.track` ne bascule qu'après des replicas prêts ;
- un HTTPRoute Gateway API avec des poids 10, 25, 50 ou 100 ;
- un HorizontalPodAutoscaler v2, avec un état explicite si `metrics.k8s.io` est absent.

Les tests `ReleasePoliciesTest` ont été exécutés le 11 octobre 2026 à 13:43 avec `mvn -B -pl backend/kubernetes-service -am test` : 6 tests nouveaux, 9 au total pour kubernetes-service, 0 échec. Ils vérifient les poids, les causes de rollback, les sélecteurs et le manifeste HPA. Ils ne remplacent pas un essai sur un cluster.

Pages Angular ajoutées : `/kubernetes/scaling`. Elle appelle ces endpoints. `npx ng test --watch=false` : 5 tests, 0 échec. `ng serve` compile et `http://127.0.0.1:4200/kubernetes/scaling` redirige vers la page de connexion tant que l'utilisateur n'est pas authentifié. Le formulaire n'avait pas été soumis tant que la passerelle était arrêtée. La section suivante décrit l'essai réel.

## Exécution locale — 11 octobre 2026, 14:12

Les images Java sont construites à partir des JAR produits par `mvn -B -DskipTests package`, puis copiées dans `eclipse-temurin:21-jre-alpine`. TLS vers Docker Hub est resté actif : l'image JRE a été tirée normalement. `scripts/start-local.ps1` enchaîne le packaging Maven et `docker compose up`.

Corrections appliquées pendant le démarrage :

- script d'init MySQL en fins de ligne Unix, sinon `/bin/bash` refuse le fichier ;
- colonnes de logs mappées en `MEDIUMTEXT` (`SqlTypes.LONGVARCHAR`) pour coller à Flyway ;
- `payload_sha256` mappé en `CHAR(64)` ;
- CORS de la passerelle autorise `http://127.0.0.1:4200` en plus de `localhost` ;
- l'état Kubernetes répond en moins de 4 secondes quand aucun cluster n'existe.

`docker compose ps` : les huit services Java, MySQL, Prometheus, Grafana et le registre sont démarrés et healthy. Ports hôte : passerelle 18080, auth 18181, applications 18082, Docker 18083, Kubernetes 18084, déploiements 18085, CI/CD 18086, monitoring 18087, MySQL 13306, Prometheus 19090, Grafana 13001, registre 15000.

Vérifications HTTP :

- `GET /actuator/health` renvoie UP pour les huit services ;
- `POST /api/auth/login` avec `admin@dcp.local` renvoie un JWT ; un mot de passe incorrect et un appel sans jeton renvoient 401 ;
- `POST /api/applications` a créé `hello-dcp` ; la liste Angular l'affiche ;
- `GET /api/kubernetes/status` renvoie `reachable=false` et `Kubernetes API timed out` ;
- `GET /api/kubernetes/metrics-server` renvoie `available=false` ;
- `GET /api/docker/images` répond avec l'inventaire réel du démon ;
- le tableau de bord affiche `No samples in Prometheus for this query`, sans nombre inventé.

Tests relancés à 14:12, BUILD SUCCESS : dcp-common 6, api-gateway 1, docker-service 6, kubernetes-service 9, cicd-service 4. Les tests Angular du matin restent 5 réussis. Aucun test E2E automatisé de blue/green, canary, HPA ou rollback n'a été exécuté.

RAM libre après le stack : environ 4,3 Go. Minikube, Metrics Server et un contrôleur Gateway API n'ont pas été installés.

## Plan des phases suivantes

3. Événements HPA et courbe de replicas, seulement si Metrics Server répond.
4. Alertmanager, règles et acquittement. Loki et OpenTelemetry seulement si la RAM le permet.
5. Namespaces par environnement, promotion staging vers production, rate limit de la passerelle, RBAC d'équipe.
6. Relance de pipeline, SonarQube et Trivy branchés sur des résultats réels.
7. Équipes, invitations et permissions par application.
8. Argo CD et assistant IA seulement après mesure de la mémoire, sans commande destructive.
9. Tests de bout en bout une fois Compose et un cluster disponibles.

Une phase n'est marquée validée que lorsque la commande correspondante a réussi.

## Finalisation — 11 octobre 2026, 17:10

Ressources mesurées avant toute nouvelle infrastructure : 4 675 Mo libres sur 32 125 Mo, CPU à 19 %, disque C: 464,6 Go libres. Docker Desktop utilise déjà une VM de 16 Go avec 44 conteneurs, dont d'autres projets. `kubectl config get-contexts` est vide. Minikube, kind, k3d et Helm ne sont pas installés. Docker Desktop Kubernetes n'a pas été activé : le faire dans cette VM risquerait les conteneurs déjà en cours. Aucun namespace, Metrics Server, RBAC ni Pod de démonstration n'a donc été créé.

Conséquence pour la phase 2 : déploiement standard, blue/green, canary, rollback et HPA n'ont pas été exécutés sur un cluster. Les tests `ReleasePoliciesTest` restent des tests de manifestes. L'API Kubernetes répond toujours `reachable=false`.

Prometheus scrape les huit cibles Actuator (`job=devops-control-plane`, état `up`). Les requêtes cAdvisor et kube-state-metrics restaient vides. Le catalogue PromQL utilise maintenant `process_cpu_usage` et `jvm_memory_used_bytes`, avec repli sur les métriques de conteneur si elles existent. `GET /api/monitoring/overview` à 17:09 a renvoyé `prometheusUp=true`, un CPU d'environ 0,001 cœur, `memoryBytes=1516741032`, `podCount=8` (cibles `up`, pas des Pods) et `restartCount=null`. Le tableau de bord Angular affiche ce CPU. Grafana 11.5.2 affiche CPU, heap, requêtes HTTP, latence, erreurs 5xx et le nombre de services `up`. Alertmanager n'a pas été ajouté : la VM Docker est déjà chargée par d'autres projets.

`mvn -B test` : BUILD SUCCESS à 17:10:19, 0 échec. `npx ng test --watch=false` : 5 tests, 3 fichiers, 0 échec. JaCoCo 0.8.13 a produit un CSV par module ; les pourcentages d'instructions sont dans `docs/rapport-tests.md`. SonarQube n'a pas été démarré. Aucune suite Playwright, aucun test de déploiement Kubernetes en cluster, aucun rollback réel.

`.gitignore` ignore `.env`, `.tools/`, `target/` et `node_modules/`. Le dépôt Git local est initialisé après ces vérifications. Aucun push.
