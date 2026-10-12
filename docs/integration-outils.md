# Intégration des outils DevOps

Relevé du 11 octobre 2026, après les vérifications de cette session. Aucun chiffre ci-dessous n’est estimé.

## Audit de départ

| Outil | Constat |
| --- | --- |
| Prometheus | Déjà démarré sur `127.0.0.1:19090`. Il ne scrapait que les 8 actuators. |
| Grafana | Déjà démarré sur `127.0.0.1:13001`, image `grafana/grafana:11.5.2`. Le Viewer anonyme existait déjà. L’inscription reste fermée. |
| Kubernetes | Cluster kind `dcp` déjà joignable. Metrics Server était déjà installé. |
| Docker | `docker-service` sain. Les conteneurs listés restent filtrés par `dcp.managed=true`. |
| CI/CD | Pipeline local en quatre étapes : `prepare`, `build-and-test`, `deploy`, `verify`. |
| SonarQube | Absent du produit. Le conteneur `supcom-sonarqube` appartenait à un autre projet, état `Exited (137)`. Il n’a pas été démarré. Le port 9000 était libre. |

Mémoire libre au début de l’audit : 5 339 Mo sur 32 125 Mo. Les services Java étaient proches de leur limite de 384 Mo.

## Prometheus et Grafana

`kube-state-metrics` v2.15.0 tourne dans `kube-system`. Prometheus, rattaché au réseau kind, scrape trois jobs :

- `devops-control-plane` : actuators des 8 microservices ;
- `kubelet-cadvisor` : CPU et RAM des conteneurs du namespace `dcp` ;
- `kube-state-metrics` : pods, redémarrages et replicas du namespace `dcp`.

Requêtes lues sur Prometheus après le scrape :

- `count(up{job="devops-control-plane"} == 1)` = 8
- `count(kube_pod_info{namespace="dcp"})` = 16
- `sum(kube_pod_container_status_restarts_total{namespace="dcp"})` = 0
- `sum(kube_deployment_status_replicas_available{namespace="dcp"})` = 16
- `count(container_memory_working_set_bytes{namespace="dcp",container!="",container!="POD"})` = 16

L’API Kubernetes a renvoyé un CPU et une RAM pour les 16 Pods. Exemple lu : `dcp-demo-box-844855bdc9-8bpt8`, CPU `222920n`, RAM `664Ki`. Les séries JVM (`process_cpu_usage`, `jvm_memory_used_bytes`) restent séparées des séries de Pods.

Trois règles d’alerte sont chargées (`MicroserviceDown`, `MicroserviceHttpErrors`, `PodRestarting`). Au moment de la lecture, `/api/v1/alerts` contenait 0 alerte en cours.

Grafana répond sur `/api/health` (base `ok`, version 11.5.2). Le tableau `dcp-overview` contient 12 panneaux : JVM, HTTP 4xx/5xx, latence, disponibilité, CPU/RAM des Pods, redémarrages et replicas. L’écran Monitoring embarque ce tableau seulement si l’adresse est `localhost:13001` ou `127.0.0.1:13001`. Le compte d’édition n’est pas devenu anonyme.

## Kubernetes

Les pages Pods affichent le CPU et la RAM renvoyés par `metrics.k8s.io`. Un tiret reste affiché si la valeur est absente. La page Namespaces liste les namespaces réellement lus : `dcp`, `default`, `kube-node-lease`, `kube-public`, `kube-system`, `local-path-storage`, `traefik`. Le détail d’un Deployment charge l’historique des révisions. Blue/green, canary, HPA et rollback n’ont pas été modifiés.

## SonarQube

SonarQube Community Build `26.9.0.129388` répond `UP` sur `127.0.0.1:9000`. Le jeton d’analyse est dans `DCP_SONAR_TOKEN` (fichier `.env`, non versionné). Le mot de passe administrateur initial a été remplacé, parce que l’interface exigeait ce changement avant d’afficher le projet. La nouvelle valeur est seulement dans `SONAR_ADMIN_PASSWORD`. Aucun des deux secrets n’est recopié ici.

L’analyse a été envoyée le 11 octobre 2026 à 22:54, tâche CE `bc530381-5729-49aa-a328-c72e0f284eb1`, statut `SUCCESS`. Elle couvre les sources `backend`, `libs` et `frontend/src`, les dix rapports JaCoCo XML déjà produits par `mvn test`, et le lcov Angular des fichiers réellement exécutés par les tests unitaires. Les chemins du lcov ont été préfixés par `frontend/` pour correspondre à la racine du projet. Les pages Angular non importées par les tests n’ont pas de couverture dans ce lcov. Le rapport unitaire v8 de ces fichiers donne 58 lignes couvertes sur 97 (59,79 %). Ce n’est pas la couverture du projet.

Quality Gate `DevOps Control Plane`, défaut du serveur. Les seuils n’ont pas été modifiés. Première analyse, à 22:54 :

| Condition | Seuil | Valeur | Résultat |
| --- | --- | --- | --- |
| bugs | supérieur à 0 | 3 | ERROR |
| vulnerabilities | supérieur à 0 | 10 | ERROR |
| code_smells | supérieur à 100 | 71 | OK |
| coverage | inférieur à 30 | 24,7 | ERROR |
| duplicated_lines_density | supérieur à 3 | 2,8 | OK |

Cette première analyse laissait le gate en `ERROR`. `ncloc` valait 16 008. Date d’analyse : `2026-10-11T20:54:52+0000`.

Après correction des 3 bugs et des 10 vulnérabilités, classement des fichiers `src/test` comme tests, et réduction d’un bloc de getters dupliqué, l’analyse de 23:37 (tâche CE `b6424e96-ef1e-46b8-9127-f41df0b1071e`, `SUCCESS`) donne un Quality Gate `OK` :

| Condition | Seuil | Valeur | Résultat |
| --- | --- | --- | --- |
| bugs | supérieur à 0 | 0 | OK |
| vulnerabilities | supérieur à 0 | 0 | OK |
| code_smells | supérieur à 100 | 66 | OK |
| coverage | inférieur à 30 | 42,1 | OK |
| duplicated_lines_density | supérieur à 3 | 2,4 | OK |
| new_coverage | inférieur à 80 | 92,9 | OK |
| new_violations | supérieur à 0 | 0 | OK |
| new_duplicated_lines_density | supérieur à 3 | 0,0 | OK |

`ncloc` vaut 14 240. `GET /api/pipelines/quality` après reconstruction de `cicd-service` : `available=true`, `mode=off`, `projectKey=devops-control-plane`, `qualityGate=OK`, `coverage=42.1`, `bugs=0`, `vulnerabilities=0`, `codeSmells=66`, `duplications=2.4`, `analyzedAt=2026-10-11T21:37:38+0000`.

`DCP_SONAR_MODE` reste `off`. Le gate est maintenant `OK`, mais la clé de projet est celle de la plateforme, pas celle de l’application déployée. Le mode `required` appliquerait ce gate à chaque déploiement. Il n’est pas activé. Le détail des corrections est dans `docs/correction-sonarqube.md`.

Essais du pipeline, sans déploiement de `vote`, `web` ou `demo-box` :

- Mode `required` et gate `ERROR` : pipeline `7d8d19f7-8940-488a-93da-1edf3cab2817` sur `hello-dcp`. `prepare` est `SUCCEEDED`. `sonar-analysis` et `quality-gate` sont `FAILED` avec le journal `Quality gate ERROR. Deployment is blocked.` Les étapes suivantes portent `Not started because the quality gate blocked the deployment`. Le pipeline est `FAILED`. `GET /api/deployments` pour cette application compte 0 déploiement.
- Serveur injoignable (`DCP_SONAR_URL=http://127.0.0.1:9`, mode encore `required`) : l’API a répondu `available=false` et `SonarQube is unreachable. No analysis result is available.` Les mesures sont restées nulles. L’URL et le mode ont ensuite été restaurés. L’appel suivant a de nouveau renvoyé les mesures du tableau.
- Un gate `OK` qui laisse le déploiement continuer est couvert par `PipelineRunnerTest` (le déploiement interne est appelé). Il n’a pas été rejoué en direct sur `vote`, `web` ou `demo-box`.

Captures : `docs/captures/sonar/qualite-mesures.png`, `docs/captures/sonar/qualite-details.png`, `docs/captures/sonar/sonar-dashboard.png`.

## Validation exécutée

| Commande | Résultat |
| --- | --- |
| `mvn -B test` | BUILD SUCCESS à 23:40:08, 1 min 17 s, 0 échec. Surefire : dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 17, kubernetes-service 16, deployment-service 8, cicd-service 32, monitoring-service 14. Total 120. |
| `npx ng test --watch=false --coverage` | 13 tests, 7 fichiers, 0 échec. Lignes des fichiers chargés par ces tests : 247/347 (71,18 %). |
| `npx ng build --configuration=development` | Succès à 23:28, sortie `frontend/dist/frontend`. |
| `npx playwright test` | 7 tests, 0 échec, 14,3 s, contre l’interface déjà servie sur le port 4200. |

La couverture du projet lue par SonarQube après la correction est 42,1 %. La somme JaCoCo des lignes des dix CSV, après `mvn -B test` à 23:40, est 2 019 lignes couvertes et 2 534 manquées, soit 44,3 % du code Java. Ce n’est pas le pourcentage SonarQube.
