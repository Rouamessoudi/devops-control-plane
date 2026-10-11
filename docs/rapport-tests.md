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

## Non exécutés

Playwright n'est pas installé. Aucun test de déploiement, de rollback ou de HPA n'a tourné contre un cluster. SonarQube n'a pas été lancé. Ces absences ne sont pas des succès.
