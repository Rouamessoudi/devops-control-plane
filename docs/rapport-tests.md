# Rapport de tests — 11 octobre 2026, 17:10

## Maven

`mvn -B test` (Maven 3.9.11) : BUILD SUCCESS, 0 échec, 29,5 s. Surefire :

| Module | Tests |
| --- | ---: |
| dcp-common | 6 |
| api-gateway | 1 |
| auth-service | 6 |
| application-service | 5 |
| docker-service | 6 |
| kubernetes-service | 9 |
| deployment-service | 5 |
| cicd-service | 4 |
| monitoring-service | 10 |

## JaCoCo 0.8.13

Instructions couvertes / manquées, lues dans `target/site/jacoco/jacoco.csv` après ce `mvn test` :

| Module | Couvertes | Manquées | % |
| --- | ---: | ---: | ---: |
| dcp-common | 473 | 261 | 64,4 |
| api-gateway | 32 | 156 | 17,0 |
| auth-service | 575 | 1200 | 32,4 |
| application-service | 404 | 1036 | 28,1 |
| docker-service | 301 | 2987 | 9,2 |
| kubernetes-service | 643 | 3815 | 14,4 |
| deployment-service | 1022 | 947 | 51,9 |
| cicd-service | 178 | 2607 | 6,4 |
| monitoring-service | 257 | 927 | 21,7 |

`dcp-common-web` n'a pas de tests, donc pas de rapport. Total instructions : 3885 couvertes et 13936 manquées, soit 21,8 %.

## Angular

`npx ng test --watch=false` : 5 tests, 3 fichiers, 0 échec.

## API

`POST /api/auth/login` avec le compte admin renvoie un JWT. `GET /api/monitoring/overview` avec ce jeton renvoie des séries Prometheus réelles. `GET /api/kubernetes/status` renvoie `reachable=false`.

## Non exécutés le 11 octobre à 17:10

Playwright n'était pas encore installé. Aucun test de déploiement, de rollback ou de HPA n'a tourné contre un cluster. SonarQube n'a pas été lancé.

## Relance du 11 octobre 2026, 17:25

`mvn -B test` : BUILD SUCCESS. JaCoCo 0.8.13, toutes les instructions des CSV : 5 202 couvertes, 13 189 manquées, **28,3 %**.

Couverture d'instructions des classes de décision relues après cette exécution :

| Classe | % |
| --- | ---: |
| PasswordPolicy, GithubAllowlist, InternalTokenFilter | 100 |
| BearerTokenFilter | 95,9 |
| ImagePolicy | 93,3 |
| GithubSignature | 92,7 |
| JwtGatewayFilter | 92,4 |
| ReleasePolicies | 87,7 |
| JwtService | 75,7 |
| AuthService | 74,0 |
| DeploymentService | 65,9 |
| PipelineService | 55,6 |
| UserAdminService | 54,8 |
| WebhookService | 48,9 |
| MonitoringService | 38,9 |
| ApplicationService | 30,6 |

L'objectif de 70 % est atteint sur l'authentification JWT, les filtres, les politiques d'image et de release. Il n'est pas atteint sur l'orchestration Docker, le client Kubernetes, ni l'ensemble du dépôt. Les appels au démon Docker et à l'API Kubernetes ne sont pas simulés.

`npx ng test --watch=false` : 9 tests, 4 fichiers, 0 échec.

`npx playwright test` à 17:23, contre Angular sur le port 4200 et la passerelle réelle : 4 tests, 0 échec. Scénarios : accès anonyme redirigé vers la connexion, mot de passe incorrect affiché, tableau de bord, liste des applications, déconnexion, création d'une application.

À 17:25, ces opérations n'avaient pas encore tourné sur un cluster : 4 039 Mo de RAM libre, et la VM Docker hébergeait déjà les autres projets.

Le même jour, après l'arrêt des conteneurs coinrush, supcom et smartrecruit, le cluster kind `dcp` a servi aux vérifications suivantes. Depuis la page Angular Releases, connecté en `admin@dcp.local`, le formulaire blue/green `web` a renvoyé `status=RUNNING`, `activeTrack=green`, `trafficApplied=true`. `kubectl` confirme le sélecteur `app=web,dcp.track=green` et l'image `registry:5000/dcp/web:2` sur `web-green`. Le formulaire canary `webcanary` a renvoyé HTTP 503 : `Gateway API HTTPRoute was not applied. Weighted traffic is unchanged. Resource not found`. `kubectl get httproute` confirme l'absence du type. Le formulaire HPA sur `web-green` (min 1, max 3, CPU 1 %) a renvoyé `status=APPLIED`. Environ une minute plus tard, `kubectl get hpa` montrait `cpu: 2%/1%` et 3 replicas pour `web-blue` et `web-green`.

Le rollback Kubernetes n'a pas de bouton dans Angular. `POST /api/kubernetes/deployments/web-blue/rollback` a restauré l'image `registry:5000/dcp/web:1` (révision 3). La révision 2 portait `registry:5000/dcp/web:2`. Le bouton Rollback d'une application appelle le service de déploiements, pas cet undo. Il n'a pas été utilisé.

Le bouton Déployer d'une application, qui clone le dépôt Git, n'a pas été soumis. Les Pods observés viennent des images déjà poussées `registry:5000/dcp/web:1` et `:2`.

## Finalisation — 11 octobre 2026, 19:50

`mvn test` : BUILD SUCCESS à 19:48:58, 38,0 s, 0 échec. Surefire : dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 6, kubernetes-service 12, deployment-service 8, cicd-service 9, monitoring-service 14. Total 82. JaCoCo, somme des CSV : 5 353 instructions couvertes, 13 750 manquées, 28,0 %.

`npx ng test --watch=false` : 9 tests, 4 fichiers, 0 échec. `npx playwright test` : 4 tests, 0 échec, 12,8 s.

Canary `split`, images `registry:5000/dcp/web:stable` et `:canary`, port 80. Après propagation, 200 requêtes vers Traefik : poids 10 → stable 180 / canary 20 ; 25 → 150 / 50 ; 50 → 100 / 100 ; 100 → 0 / 200. L'API et la page Angular renvoient `trafficApplied=true` et `HTTPRoute split accepted by Gateway dcp`.

Rollback Kubernetes depuis Angular sur `web-blue`, révision 4 : image `registry:5000/dcp/web:2`, nouvelle révision 6, 3 replicas prêts. Retour à la révision 5 : image `registry:5000/dcp/web:1`, révision 7. Sans jeton : HTTP 401. Compte `DEVELOPER` : HTTP 403.

Pipeline `038e368b-335d-409d-baee-c09aabf8a326` : clone de `https://github.com/dockersamples/example-voting-app` branche `main`, image `registry:5000/dcp/vote:20261011174622` aussi présente sur `localhost:15000`, Deployment `dcp-vote` révision 1, Pod `1/1`, gunicorn sur le port 80. La page du pipeline affiche les quatre étapes `SUCCEEDED`. Deux essais précédents ont échoué : branche `master` absente, puis client Docker 3.4.2 incapable d'ouvrir le socket Unix (corrigé par docker-java 3.7.1).

HPA inchangé : `web-blue` et `web-green`, `cpu: 2%/1%`, `memory: 19%/80%`, 3 replicas. Blue/green inchangé : sélecteur `app=web,dcp.track=green`.
