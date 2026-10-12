# Rapport des interfaces — 11 octobre 2026, 21:04

## Conteneurs Docker

Le démon Docker 29.0.1 répond. `docker-service` monte `/var/run/docker.sock` et son health est HTTP 200. La liste est vide parce que `listContainersCmd` filtre `dcp.managed=true` et qu’aucun conteneur ne porte ce label (`docker ps -aq --filter label=dcp.managed=true` = 0). Une image le porte. Le pipeline construit cette image puis déploie un Pod ; il ne laisse pas de conteneur géré sur l’hôte. Sans jeton, `GET /api/docker/containers` répond 401. Administrateur et DEVELOPER reçoivent tous les deux `[]` : il n’y a pas de conteneur autorisé à montrer, et les conteneurs des autres projets restent exclus. Le frontend le dit explicitement. L’API ne contient toujours ni port ni CPU/RAM de conteneur.

## Séries Prometheus

Prometheus ne scrape que les Actuator du job `devops-control-plane`. Les familles `container_*` et `kube_pod_*` sont absentes, donc aucune courbe Kubernetes n’a été ajoutée. `cpu` et `memory` sont devenues des sommes JVM seules, sans repli conteneur. Le compte de Pods Prometheus n’utilise plus `count(up == 1)`, qui valait 8 et mélangeait les microservices avec les Pods. L’overview affiche ce compte vide et le message d’absence. Sur 1 h, les cinq séries ont 61 échantillons : `cpu`, `memory`, `http`, `errors`, `latency`.

## Tests du 11 octobre 2026, 21:01

| Commande | Résultat |
| --- | --- |
| `mvn -B test` | BUILD SUCCESS, 40,0 s, 82 tests, 0 échec |
| `npx ng test --watch=false` | 10 tests, 0 échec |
| `npx ng build --configuration=development` | succès |
| `npx playwright test` | 7 tests, 0 échec, 9,5 s |

Surefire : dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 6, kubernetes-service 12, deployment-service 8, cicd-service 9, monitoring-service 14.

## Pages revues

Tableau de bord, applications, Docker, Kubernetes, Releases, pipeline `038e368b-335d-409d-baee-c09aabf8a326` (SUCCEEDED, quatre étapes), monitoring, logs du Pod `dcp-vote` (gunicorn sur le port 80), administration, paramètres. États vus : chargement du tableau de bord, liste de conteneurs vide expliquée, mot de passe incorrect, 401 sans jeton, 403 du DEVELOPER sur `/api/users`. Aucune confirmation de déploiement, scale, rollback ou suppression n’a été acceptée.

# Relevé de 20:55

Le chiffre « Prometheus voit 8 Pods » de ce relevé était `count(up == 1)`, les huit microservices. Il a été retiré à 21:04. Le compte de Pods Kubernetes reste celui de l’API Kubernetes.

Les pages Angular n’affichent plus le JSON brut des API Kubernetes, Docker et administration. Les chiffres et les courbes viennent des réponses HTTP. Aucun déploiement, scale, redémarrage, rollback ou construction d’image n’a été confirmé. Aucun push.

`monitoring-service` a été recréé pour exposer les séries Prometheus `http`, `errors` et `latency`, déjà calculées dans le catalogue PromQL. Les autres conteneurs n’ont pas été reconstruits. coinrush, supcom et smartrecruit n’ont pas été touchés.

URL : `http://127.0.0.1:4200`

Connexion administrateur : `admin@dcp.local`. Le mot de passe reste `DCP_ADMIN_PASSWORD` dans `.env`.

## Ce qui est affiché

| Écran | Données lues |
| --- | --- |
| Tableau de bord | 8 applications, 1 active, 1 déploiement réussi, 2 échoués, 11 Pods Kubernetes, 6 Services. CPU Prometheus environ `0.00218` cœur. Mémoire JVM `1.5 Gio`. Message API : les métriques de Pods Kubernetes sont absentes, CPU et mémoire viennent des séries Actuator. Prometheus voit 8 Pods. Heap JVM du control plane : `392 Mio` au moment de la capture. Courbe CPU sur 1 h. |
| Docker | Builds `SUCCEEDED` et `FAILED` de `localhost:15000/dcp/vote`. Images `56 Mio` (base) et `230 Mio` (démon). L’API conteneurs a renvoyé une liste vide : la page le dit. Ports et CPU/RAM des conteneurs ne sont pas dans `ContainerView`, donc ils ne sont pas inventés. |
| Pods | 11 lignes, dont `dcp-vote-65b9996844-hqxbz`, phase `Running`, namespace `dcp`. Recherche, âge et bouton Détails. |
| Services, Ingress, Deployments | Tableaux. Playwright a vu `ClusterIP`, l’hôte `dcp-vote.dcp.local` et le bouton Actions. Scale et redémarrage demandent une confirmation ; ils n’ont pas été exécutés. |
| Namespaces | Une seule ligne, `dcp`. L’API ne liste pas les autres namespaces du cluster. |
| Releases | Formulaires blue/green, canary (port 80, poids 10), rollback et HPA. Metrics Server disponible. Chaque action de déploiement ouvre une confirmation. Aucune n’a été acceptée. |
| Monitoring | Cinq graphiques, 61 échantillons chacun sur 1 h pour `cpu`, `memory`, `http`, `errors` et `latency`. Message vide côté API. Les cartes séparent la série JVM et le compte de Pods Prometheus. |

## Tests

- `npx ng test --watch=false` : 10 tests, 4 fichiers, 0 échec (20:55).
- `npx ng build --configuration=development` : compilation réussie après ajout de `@angular/animations` 21.2.25, requis par les dialogues déjà utilisés.
- `mvn -B -pl backend/monitoring-service -am test` : BUILD SUCCESS, monitoring-service 14 tests, 0 échec. La suite Maven complète des huit services n’a pas été relancée.
- `npx playwright test` : 7 tests, 0 échec, 9,6 s. Navigation, données réelles (`vote`, Pods, Ingress), droits du compte `reader@dcp.local`, formulaire de création, fenêtre 390 px, aucune `pageerror`.

## Captures

- `docs/captures/interfaces/ui-dashboard-k8s-absent.png` (21:04, compte de Pods Prometheus vide)
- `docs/captures/interfaces/ui-dashboard.png`
- `docs/captures/interfaces/ui-dashboard-charts.png`
- `docs/captures/interfaces/ui-docker.png`
- `docs/captures/interfaces/ui-pods.png`
- `docs/captures/interfaces/ui-releases.png`
- `docs/captures/interfaces/ui-monitoring.png`
- `docs/captures/interfaces/ui-monitoring-http.png`
- `docs/captures/interfaces/ui-sidebar.png`
