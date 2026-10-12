# Rapport de démonstration — demo-box

Date : 11 octobre 2026, relevé entre 21:12 et 21:20 (heure locale, UTC+2). Les horodatages ci-dessous sont ceux renvoyés par l'interface, en UTC.

Application isolée : `demo-box`, identifiant `b66c5b1d-ff40-41cf-b318-0245f0bea92f`. Dépôt public `https://github.com/crccheck/docker-hello-world`, branche `master`, Dockerfile à la racine, contexte `.`, port 8000, environnement DEV. Les applications `vote`, `web` et `webcanary` n'ont pas été modifiées. Aucun push GitHub. Aucun conteneur ni volume supprimé.

Les captures sont dans `docs/captures/demonstration/`.

## 1. Création

L'application a été créée depuis Angular. La fiche affiche le dépôt, la branche et le statut CREATED. L'historique contient `CREATED — Application created — 2026-10-11T19:12:16.280500Z`.

Captures : `demo-01-creation.png`, `demo-02-fiche.png`.

## 2. Construction de l'image

Formulaire Docker : application `b66c5b1d-ff40-41cf-b318-0245f0bea92f`, tag `v1`. Confirmation « Lancer la construction ».

Build `6d785a3a-02de-4129-815d-1c947535a819` : SUCCEEDED, image `localhost:15000/dcp/demo-box:v1`.

Le registre local répond `{"name":"dcp/demo-box","tags":["v1"]}` juste après ce build. `docker image inspect` donne l'identifiant `sha256:c77571d79f8daf3078a6b10597416bf5dd74e9774432135759d245e9879204c6` et la taille 2 229 113 octets.

Captures : `demo-03-build-form.png`, `demo-04-confirm-build.png`, `demo-05-build-pending.png`, `demo-06-build-ok.png`.

## 3. Pipeline

Premier pipeline, lancé depuis la fiche : `e6054e71-3259-4959-a008-0f97859b75e5`.

- Statut SUCCEEDED, conclusion `success`
- Début `2026-10-11T19:13:53.046541Z`, fin `2026-10-11T19:13:57.257110Z`
- prepare : dépôt et branche `master`
- build-and-test : `registry:5000/dcp/demo-box:20261011191353`
- deploy et verify : Deployment `dcp-demo-box`, révision 1

Second pipeline, pour obtenir une révision distincte : `c1e98adb-d12d-4a2e-83a3-5705ffcbf523`, SUCCEEDED. Image `registry:5000/dcp/demo-box:20261011191646`, Deployment `dcp-demo-box` révision 2, 1/1 prêt.

Tags présents ensuite dans le registre : `v1`, `20261011191353`, `20261011191646`.

Capture : `demo-07-confirm-pipeline.png`, `demo-08-pipeline.png`.

## 4. Kubernetes — premier déploiement

Observé avant le second pipeline :

- Pod `dcp-demo-box-844855bdc9-572qh`, namespace `dcp`, phase Running, Ready true, 0 redémarrage, nœud `dcp-control-plane`
- Image `registry:5000/dcp/demo-box:20261011191353`
- Journal : `httpd started`
- Événements : assigné à `dcp-control-plane`, image tirée en 204 ms (2 229 113 octets), conteneur créé puis démarré
- Service `dcp-demo-box`, ClusterIP `10.96.95.73`, port 80 vers le port conteneur 8000, sélecteur `app=dcp-demo-box`
- Deployment `dcp-demo-box` affiché 1/1

Une requête `wget` dans le Pod vers `http://127.0.0.1:8000` a renvoyé du contenu. Après les opérations suivantes, les Services `demo-box-stable`, `demo-box-canary` et `demo-box` répondent HTTP 200 et le corps contient `Hello`. Les deux tags viennent du même Dockerfile : le corps ne permet pas de distinguer les pistes.

Captures : `demo-09-pod.png`, `demo-10-pod-logs.png`, `demo-11-service.png`, `demo-12-deployment.png`.

## 5. Rollback Kubernetes

Historique de `dcp-demo-box` avant le retour :

- révision 2, image `registry:5000/dcp/demo-box:20261011191646`, courante
- révision 1, image `registry:5000/dcp/demo-box:20261011191353`, précédente

Confirmation : « Revenir à la révision 1 de dcp-demo-box ? »

Résultat affiché : nom `dcp-demo-box`, image `registry:5000/dcp/demo-box:20261011191353`, 1 replica, état RUNNING, message `Ready`. `kubectl` confirme ready 1/1, image de la révision 1, annotation de révision 3. Le Pod courant est `dcp-demo-box-844855bdc9-8bpt8`, Ready, Running.

Captures : `demo-13-rollback-history.png`, `demo-14-confirm-rollback.png`, `demo-15-rollback-result.png`.

## 6. Blue/green

Nom `demo-box`, port 8000, une replica. Aucun Deployment `web-*` n'a été visé.

1. Piste `green`, image candidate `registry:5000/dcp/demo-box:20261011191646`. Réponse : BLUE_GREEN, piste `green`, trafic confirmé oui, RUNNING. Message : `Service selector points at track green; previous track was left in place`. Deployment `demo-box-green` 1/1. Service `demo-box` : port 80 vers 8000, sélecteur `app=demo-box, dcp.track=green`.
2. Piste `blue`, image candidate `registry:5000/dcp/demo-box:v1`. Réponse : piste `blue`, trafic confirmé oui. Sélecteur du Service déplacé vers `dcp.track=blue`. `demo-box-green` est resté 1/1.

État final des deux pistes : `demo-box-blue` image `v1`, `demo-box-green` image `20261011191646`, les deux Ready. Le Service `demo-box` pointe vers blue et répond HTTP 200.

Captures : `demo-16-bluegreen-form.png`, `demo-17-bluegreen-green.png`.

## 7. Canary

Formulaire : nom `demo-box`, image stable `registry:5000/dcp/demo-box:v1`, image canary `registry:5000/dcp/demo-box:20261011191646`, poids 25, port 8000, une replica.

Réponse Angular : stratégie CANARY, poids 25, trafic confirmé oui, état APPLIED. Message : `HTTPRoute demo-box accepted by Gateway dcp. stable=75 canary=25`.

`kubectl` :

- HTTPRoute `demo-box` : Accepted True, ResolvedRefs True, poids 75 vers `demo-box-stable` et 25 vers `demo-box-canary`
- `demo-box-stable` 1/1, image `v1`, Pod `demo-box-stable-746477b48d-zh4pv`
- `demo-box-canary` 1/1, image `20261011191646`, Pod `demo-box-canary-6db7c56584-tsgxg`
- les deux Services répondent HTTP 200 avec un corps qui contient `Hello`

La répartition HTTP n'a pas été mesurée par le corps des réponses : les deux images servent la même page. Douze requêtes vers `http://traefik.traefik.svc.cluster.local`, envoyées depuis le Pod `dcp-demo-box`, ont renvoyé alternativement `canary` et `stable` (6 et 6). Ces corps sont ceux de la route déjà présente `split`, pas ceux de `demo-box`. La route `split` est restée Accepted True, poids 50/50 vers `split-stable` et `split-canary`. Les deux HTTPRoute n'ont pas de nom d'hôte et partagent le Gateway `dcp`. La route `demo-box` est acceptée, mais ces douze requêtes n'ont pas été servies par elle. Les journaux Traefik étaient vides, donc aucun comptage de backend n'en a été tiré.

La route `demo-box` a été laissée en place. Le HPA n'a pas été créé.

Captures : `demo-18-canary-form.png`, `demo-19-canary-result.png`.

## 8. Métriques réellement présentes

Relevé API `2026-10-11T19:20:55.408514642Z` :

- Prometheus joignable
- `cpuCores` = 0.0035641929119358323
- `memoryBytes` = 1 636 233 672
- `controlPlaneHeapBytes` = 403 594 600
- `podCount` = null
- `restartCount` = null
- message : `Kubernetes pod metrics are absent. CPU and memory come from Prometheus actuator series.`

L'écran affichait, sur un scrape légèrement antérieur (`19:20:48Z`) : CPU 0.00210, mémoire 1.5 Gio, heap 399 Mio, Pods et redémarrages en tiret.

Les cinq séries `cpu`, `memory`, `http`, `errors` et `latency` sur 1 h ont chacune 61 points et un message vide. Cinq graphiques sont dessinés. `container_cpu_usage_seconds_total` et `kube_pod_info` renvoient un résultat vide sur Prometheus. Aucune courbe de Pod n'a été ajoutée.

Captures : `demo-20-monitoring.png`, `demo-21-monitoring-charts.png`.

## 9. Applications laissées intactes

Relevé après la démonstration, namespace `dcp` :

| Deployment | Prêts | Image |
| --- | --- | --- |
| web-blue | 3 | registry:5000/dcp/web:1 |
| web-green | 3 | registry:5000/dcp/web:2 |
| webcanary-stable | 1 | registry:5000/dcp/web:1 |
| webcanary-canary | 1 | registry:5000/dcp/web:2 |
| dcp-vote | 1 | registry:5000/dcp/vote:20261011174622 |
| split-stable | 1 | registry:5000/dcp/web:stable |
| split-canary | 1 | registry:5000/dcp/web:canary |

## 10. Ressources créées pour demo-box

Deployments : `dcp-demo-box`, `demo-box-blue`, `demo-box-green`, `demo-box-stable`, `demo-box-canary`. Services : `dcp-demo-box`, `demo-box`, `demo-box-stable`, `demo-box-canary`. HTTPRoute : `demo-box`. Images : `localhost:15000/dcp/demo-box` tags `v1`, `20261011191353`, `20261011191646`.
