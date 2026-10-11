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

## Audit mémoire et tests — 11 octobre 2026, 17:25

RAM libre : 4 039 Mo sur 32 125 Mo. CPU à 20 %. Docker : 44 conteneurs en cours, VM de 16 Go.

Mémoire observée par projet, sans arrêter aucun conteneur étranger :

| Projet | Conteneurs | Mémoire |
| --- | ---: | ---: |
| coinrush | 24 | 5 697 Mio |
| devops-control-plane | 12 | 2 924 Mio |
| supcom | 4 | 2 027 Mio |
| smartrecruit | 1 | 410 Mio |

Le plus gros conteneur est `supcom-sonarqube` (1,9 Gio). Les services Java de ce projet restent sous leur limite de 384 Mio.

Stratégie pour un cluster, sans toucher aux autres projets : arrêter Grafana, Prometheus et le registre de ce projet libérerait environ 225 Mio, ce qui ne suffit pas. Un cluster kind, k3d ou Docker Desktop Kubernetes a besoin d'environ 1,5 à 2 Gio dans la même VM, déjà occupée à environ 11 Gio. L'activer risquerait de faire tuer des conteneurs des autres projets. Aucun cluster n'a donc été démarré. Blue/green, canary, rollback et HPA restent non exécutés sur Kubernetes.

`mvn -B test` à 17:25 : BUILD SUCCESS. Couverture JaCoCo globale : 5 202 instructions couvertes et 13 189 manquées, soit 28,3 %. Plusieurs classes de décision dépassent 70 % (JWT, filtres, politiques d'image et de release, `AuthService` à 74 %). `ApplicationService` reste à 30,6 %, `DeploymentService` à 65,9 %, `MonitoringService` à 38,9 %. Le détail est dans `docs/rapport-tests.md`.

Angular : `npx ng test --watch=false`, 9 tests, 0 échec. Playwright : 4 tests contre `http://127.0.0.1:4200`, 0 échec (connexion, erreur d'API, navigation, protection des routes, déconnexion, création d'application).

## Cluster kind — 11 octobre 2026

`docker stop` a arrêté 29 conteneurs de coinrush, supcom et smartrecruit. Aucun `docker rm`, aucun volume supprimé, aucun fichier de ces projets modifié. Les 12 conteneurs DevOps Control Plane sont restés en cours. Les noms exacts sont dans `docs/conteneurs-arretes.txt`. RAM libre mesurée ensuite : 5 537 Mo sur 32 125 Mo. La mémoire reste surtout dans la VM Docker de 16 Go ; kind a été créé dans cette VM.

Cluster : kind 0.27.0, nom `dcp`, contexte `kind-dcp`, nœud `dcp-control-plane`, namespace `dcp`. `kubernetes-service` a été recréé avec `docker-compose.k8s.yml` pour monter un kubeconfig vers `https://dcp-control-plane:6443`. `GET /api/kubernetes/status` : joignable. Metrics Server : disponible (`kubectl top nodes` a répondu). Le correctif kubelet est `--kubelet-insecure-tls`. L'identité du client est l'administrateur kind, pas le Role limité de `infrastructure/kubernetes/rbac.yaml`.

Images publiées : `localhost:15000/dcp/web:1` et `:2` (nginx 1.27). Le nœud les tire via `registry:5000` sur le réseau kind.

Depuis Angular (`http://127.0.0.1:4200/kubernetes/scaling`, compte administrateur) :

- Blue/green `web`, piste green, port 80 : `RUNNING`, `trafficApplied=true`, sélecteur `app=web,dcp.track=green`. `web-green` exécute `registry:5000/dcp/web:2`. `web-blue` reste en place.
- Canary `webcanary`, poids 10 : HTTP 503, détail `Gateway API HTTPRoute was not applied. Weighted traffic is unchanged. Resource not found`. Aucun type `httproute` sur le cluster. Les Deployments `webcanary-stable` et `webcanary-canary` existent, sans répartition de trafic. Le formulaire envoie le port 8080 (champ absent de la page) : les nouveaux Pods échouaient alors au probe. Un second appel avec le port 80 les a rendus `1/1`.
- HPA `web-green`, min 1, max 3, CPU 1 % : `APPLIED`. Cible observée `cpu: 2%/1%`, 3 replicas. `web-blue` a le même palier et 3 replicas.

Rollback : `web-blue` révision 3, image `registry:5000/dcp/web:1`. La révision 2 était `registry:5000/dcp/web:2`. Angular n'affiche pas ce rollback Kubernetes. Le bouton Rollback d'une fiche application n'a pas été cliqué. Le bouton Déployer, qui clone GitHub, n'a pas été soumis.

Aucun push GitHub.

## Finalisation Kubernetes — 11 octobre 2026, 19:50

RAM libre en fin de passe : 3 940 Mo sur 32 125 Mo. Pendant le build de l'application vote : 3 513 Mo. `docker-service` a été recréé pour le client Docker. Les autres conteneurs DevOps Control Plane sont restés en cours. Aucun volume supprimé. coinrush, supcom et smartrecruit n'ont pas été redémarrés. Aucun push GitHub.

### Canary

CRD Gateway API standard v1.6.3 installées (`gatewayclasses`, `gateways`, `grpcroutes`, `httproutes`, `referencegrants`). Le contrôleur est Traefik 3.3.5, image `traefik:v3.3.5-amd64`, GatewayClass `traefik`, Gateway `dcp` dans le namespace `dcp`, écoute HTTP 80. `Accepted=True`, `Programmed=True`. NGINX Gateway Fabric 2.1.2 a été tenté puis retiré : l'image `ghcr.io` ne se téléchargeait pas. Le manifeste `infrastructure/kubernetes/nginx-gateway-fabric.yaml` n'est pas appliqué.

Le formulaire Angular affiche le port du conteneur. Les valeurs par défaut du canary sont `registry:5000/dcp/web:stable`, `registry:5000/dcp/web:canary` et le port 80. Les poids ne sont écrits qu'après des Pods prêts. `trafficApplied` reste faux tant que le Gateway n'a pas `Accepted=True` et `ResolvedRefs=True`.

Depuis la page Releases, le formulaire `split`, poids 50, port 80, a affiché `status=APPLIED`, `trafficApplied=true`, message `HTTPRoute split accepted by Gateway dcp. stable=50 canary=50`.

Répartition mesurée par 200 requêtes `wget` vers `http://traefik.traefik.svc.cluster.local`, après deux secondes de propagation. Les corps répondent `stable` ou `canary` :

| Poids canary | stable | canary |
| --- | --- | --- |
| 10 % | 180 | 20 |
| 25 % | 150 | 50 |
| 50 % | 100 | 100 |
| 100 % | 0 | 200 |

Un enchaînement sans attente relisait encore le poids précédent. Les chiffres du tableau sont ceux relevés après propagation. Le poids laissé sur le cluster est 50 %, recompté `stable=100` `canary=100`.

### Rollback Kubernetes

La page Releases a un panneau « Rollback Kubernetes », distinct du rollback d'image d'une fiche application. Pour `web-blue`, l'historique affiché était la révision 5 (`registry:5000/dcp/web:1`, courante) et la révision 4 (`registry:5000/dcp/web:2`). Le bouton « Revenir à cette révision » a produit, dans la page, `image=registry:5000/dcp/web:2`, `revision=6`, `readyReplicas=3`, `status=RUNNING`. Un second appel, révision 5, a restauré `registry:5000/dcp/web:1`, révision 7. `kubectl` confirme ensuite `web-blue` sur `:1` et `web-green` sur `:2`. Le Service `web` reste sur le sélecteur `app=web,dcp.track=green`.

`POST /api/kubernetes/deployments/web-blue/rollback` sans jeton : HTTP 401. Le même appel avec un compte `DEVELOPER` : HTTP 403.

### Déploiement GitHub

Application `vote` (`7b5208bb-8670-414a-abfb-b38f2f824c70`), dépôt public `https://github.com/dockersamples/example-voting-app`, branche `main`, Dockerfile `vote/Dockerfile`, contexte `vote`, port 80. Aucun jeton GitHub dans le code. La branche `master` n'existe pas : le pipeline `2afdb486-d509-46f8-a4c2-ea8658c4a2de` a échoué au clone (`Remote branch 'refs/heads/master' not found`).

Le pipeline `5595246c-e3a4-41e2-841b-9a213376dd22` a cloné `main` puis échoué : docker-java 3.4.2, avec httpclient5 5.5, ouvrait `unix://localhost:2375` au lieu de `/var/run/docker.sock`. docker-java est passé en 3.7.1. `docker-service` a été reconstruit.

Le pipeline `038e368b-335d-409d-baee-c09aabf8a326` est `SUCCEEDED`. La page Angular affiche les quatre étapes en succès : clone de `main`, image `registry:5000/dcp/vote:20261011174622`, déploiement `dcp-vote` révision 1. Le tag est présent sur `localhost:15000`. Le Pod `dcp-vote` est `1/1` et gunicorn écoute sur le port 80.

### Tests de cette passe

`mvn test` (Maven 3.9.11) : BUILD SUCCESS à 19:48:58, 38,0 s, 0 échec. Surefire : dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 6, kubernetes-service 12, deployment-service 8, cicd-service 9, monitoring-service 14. Total 82.

JaCoCo 0.8.13, somme des CSV de cette exécution : 5 353 instructions couvertes, 13 750 manquées, 28,0 %.

`npx ng test --watch=false` : 9 tests, 4 fichiers, 0 échec.

`npx playwright test` : 4 tests, 0 échec, 12,8 s.

Blue/green inchangé : sélecteur `app=web,dcp.track=green`, `web-green` sur `registry:5000/dcp/web:2`, `web-blue` sur `registry:5000/dcp/web:1`. HPA `web-blue` et `web-green` : `cpu: 2%/1%`, `memory: 19%/80%`, 3 replicas, min 1, max 3.
