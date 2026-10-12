# DevOps Control Plane

Plan de contrôle local pour construire une image, la publier et la déployer sur Kubernetes. Le dépôt contient huit microservices Spring Boot, une interface Angular, Docker Compose et les manifestes Kubernetes.

## Architecture

Le navigateur parle à Angular. Angular appelle la passerelle. La passerelle vérifie le JWT et route vers le service concerné. MySQL est la base partagée. Prometheus scrape les métriques Actuator. Grafana les affiche.

```text
Angular
  └─ api-gateway
       ├─ auth-service            comptes, JWT, rôles
       ├─ application-service     fiches application
       ├─ docker-service          clone Git, build, registre
       ├─ kubernetes-service      Pods, releases, HPA
       ├─ deployment-service      orchestration build puis déploiement
       ├─ cicd-service            pipelines
       └─ monitoring-service      Prometheus et alertes
```

Un cluster kind nommé `dcp` se raccorde à `kubernetes-service` par `docker-compose.k8s.yml`. Le kubeconfig de ce raccordement reste sur la machine : il n'est pas versionné.

## Fonctionnalités

- Connexion, rôles et administration des comptes.
- Fiche application liée à un dépôt GitHub HTTPS.
- Pipeline local : `prepare`, `sonar-analysis`, `quality-gate`, `build-and-test`, `deploy`, `verify`. En mode `off`, les deux étapes Sonar sont ignorées et le déploiement continue. En mode `required`, un Quality Gate différent de `OK` bloque le déploiement.
- Page Qualité branchée sur SonarQube. Le projet `devops-control-plane` a été réanalysé le 11 octobre 2026 à 23:37. Le Quality Gate observé est `OK` (bugs 0, vulnérabilités 0, code smells 66, couverture 42,1 %, duplications 2,4 %). `DCP_SONAR_MODE` reste `off`, parce que la clé de projet est celle de la plateforme.
- Blue/green : le Service ne change de sélecteur qu'une fois les Pods prêts.
- Canary par HTTPRoute Gateway API, poids 10, 25, 50 ou 100. Le trafic n'est marqué appliqué que si les Pods sont prêts et que le Gateway accepte la route.
- Rollback d'une révision de Deployment, réservé à un administrateur. Ce n'est pas le retour à l'image précédente d'une fiche application.
- Horizontal Pod Autoscaler lorsque Metrics Server répond.
- Tableaux de bord Angular : cartes, tableaux et graphiques Prometheus. Les courbes CPU, mémoire, HTTP, erreurs et latence viennent des séries Actuator. Prometheus ne scrape pas les Pods.
- Inventaire Docker limité aux images et conteneurs marqués `dcp.managed=true`.

## Technologies

| Élément | Version |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.15 |
| Spring Cloud | 2025.0.3 |
| Angular | 21 |
| MySQL | 8 |
| Docker Compose | fichiers à la racine |
| Kubernetes | manifestes dans `infrastructure/kubernetes`, cluster kind 0.27 |
| Gateway API | CRD v1.6.3, contrôleur Traefik 3.3 |
| Prometheus, Grafana | inclus dans Compose |

## Installation

Prérequis : Java 21, Maven 3.9, Node.js 24, Docker Desktop.

```powershell
Copy-Item .env.example .env
.\scripts\start-local.ps1
cd frontend
npm install
npx ng serve --host 127.0.0.1 --port 4200
```

L'interface est sur `http://127.0.0.1:4200`. Le script copie `.env.example` vers `.env` s'il manque, construit les JAR, puis lance Compose. S'il trouve Maven dans `.tools/apache-maven-3.9.11`, il l'utilise. Sinon il appelle `mvn` du PATH. Le dossier `.tools` n'est pas versionné.

Le compte administrateur local est créé au premier démarrage avec les valeurs de `.env` (`DCP_ADMIN_EMAIL`, `DCP_ADMIN_PASSWORD`). Les valeurs du modèle commencent par `dev-only-` ou sont un mot de passe local à changer avant tout usage partagé. Le profil `prod` refuse un secret `dev-only-`.

| Service | Port hôte |
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

Le raccordement du cluster kind est décrit dans `docs/deploiement.md`. Les autres guides sont dans `docs/`.

## Captures

Prises sur l'instance locale du 11 octobre 2026.

Connexion.

![Connexion](docs/captures/login.png)

Tableau de bord. Les compteurs viennent des API.

![Tableau de bord](docs/captures/tableau-de-bord.png)

Applications enregistrées, dont le dépôt de démonstration `dockersamples/example-voting-app`.

![Applications](docs/captures/applications.png)

Releases : blue/green, canary, rollback Kubernetes et HPA. Metrics Server répond.

![Releases](docs/captures/releases.png)

Pipeline réussi : clone, image, déploiement Kubernetes.

![Pipeline](docs/captures/pipeline.png)

Grafana, CPU et requêtes HTTP des microservices.

![Grafana](docs/captures/grafana.png)

## Tests

```powershell
.\.tools\apache-maven-3.9.11\bin\mvn.cmd -B test
cd frontend
npx ng test --watch=false
npx playwright test
```

Le 11 octobre 2026, 23:40 : `mvn -B test` BUILD SUCCESS en 1 min 17 s, 120 tests, 0 échec (dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 17, kubernetes-service 16, deployment-service 8, cicd-service 32, monitoring-service 14). `npx ng test --watch=false --coverage` : 13 tests, 0 échec. `npx ng build --configuration=development` : succès. `npx playwright test` : 7 tests, 0 échec, 14,3 s. Playwright a besoin de Compose et de `ng serve`. Le détail SonarQube est dans `docs/correction-sonarqube.md`.

Le jeton SonarQube et le mot de passe administrateur SonarQube sont dans `.env` (`DCP_SONAR_TOKEN`, `SONAR_ADMIN_PASSWORD`). Ils ne sont pas versionnés.

## Ce qui n'est pas dans le dépôt

`.env`, `.tools/`, les clés, les certificats et `infrastructure/kubernetes/kubeconfig.container` sont ignorés. `infrastructure/kubernetes/secret.example.yaml` ne contient que des `REPLACE_ME`. Aucun jeton GitHub n'est écrit dans le code : `GITHUB_TOKEN` reste vide dans `.env.example`.
