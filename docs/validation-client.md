# Validation client

Date : 11 octobre 2026, 21:34. Aucun push Git. Aucun volume supprimé. Les applications `vote`, `web` et `webcanary` n'ont pas été redéployées.

## Tests automatiques

| Commande | Résultat |
| --- | --- |
| `mvn -B test` | BUILD SUCCESS à 21:34:39, 34,4 s, 0 échec |
| Surefire | dcp-common 6, dcp-common-web 7, api-gateway 4, auth-service 11, application-service 5, docker-service 6, kubernetes-service 13, deployment-service 8, cicd-service 9, monitoring-service 14. Total 83 |
| `npx ng test --watch=false` | 10 tests, 4 fichiers, 0 échec |
| `npx playwright test` | 7 tests, 0 échec, 16,1 s |

Playwright a vérifié : refus d'un visiteur anonyme, erreur de mot de passe, connexion et déconnexion, création d'une application, données réelles du tableau de bord, Docker, Kubernetes, Releases et Monitoring, absence du lien Administration pour un DEVELOPER, et le menu sur un écran étroit.

## Canary isolé

Route `demo-box`, hôte `demo-box.dcp.local`, Gateway `dcp`, Accepted. Images `registry:5000/dcp/demo-box:v1` (stable) et `registry:5000/dcp/demo-box:20261011191646` (canary). Les deux pages sont identiques (`Content-Length: 439`, corps « Hello World »). Le comptage utilise les octets émis par chaque Pod sur 200 requêtes, après 2 secondes, avec l'en-tête `Host: demo-box.dcp.local`.

| Poids demandé | Octets stable | Octets canary | Part canary des octets |
| --- | --- | --- | --- |
| 10 | 190 708 | 22 200 | 10,4 % |
| 25 | 158 940 | 53 606 | 25,2 % |
| 50 | 106 314 | 106 380 | 50,0 % |
| 100 | 1 120 | 211 986 | 99,5 % |

L'API répond `APPLIED` et rappelle que l'acceptation de la route n'est pas ce comptage. Le dernier poids laissé sur `demo-box` est 100.

La route `split` a été limitée à `split.dcp.local`, poids inchangés 50/50, Accepted. Huit requêtes sur cet hôte ont renvoyé `stable` et `canary` en alternance (4 et 4). Quatre requêtes sans nom d'hôte ont reçu HTTP 404. `demo-box.dcp.local` répond toujours HTTP 200 « Hello World ».

## Autres essais de cette session

- Bascule blue/green de `demo-box` : le sélecteur est passé à `dcp.track=green`, puis revenu à `dcp.track=blue`. Les deux pistes étaient déjà prêtes. Message : `Service selector moved back to track blue`.
- HPA `dcp-demo-box` : min 1, max 2. Après 49 secondes, `cpu: 2%/70%`, `memory: 1%/80%`, 1 replica, `ScalingActive=True`. `kubectl top` voyait 1 mCPU. Les HPA `web-blue` et `web-green` n'ont pas été touchés.
- Page Pipelines : les pipelines réels de `demo-box` et de `vote` s'affichent, y compris les FAILED.

## Classement

Validées de bout en bout : connexion, rôles ADMIN et DEVELOPER, création d'application, liste des pipelines, consultation Kubernetes, blue/green avec bascule du Service `demo-box`, canary sur `demo-box.dcp.local` pour les poids 10, 25, 50 et 100, rollback Kubernetes de `dcp-demo-box` (session précédente, révision 3), HPA de `dcp-demo-box`, monitoring Actuator.

Partiellement fonctionnelles : construction Docker (réussie plus tôt pour `demo-box:v1`, pas relancée ce soir), logs (la page Pods les affiche ; la page Logs exige un nom), administration (l'accès est contrôlé, les boutons de rôle n'ont pas été rejoués), allowlist GitHub (le code existe, le drapeau est désactivé).

Non fonctionnelles dans l'interface : notifications.

Bloquées par l'environnement : métriques de Pods dans Prometheus (`kube_pod_info` et `container_cpu_usage_seconds_total` vides). Le cluster et Metrics Server répondent.

## Verdicts

Ces trois niveaux ne se valent pas.

1. Présentation client locale : oui. Ouvrir `http://127.0.0.1:4200`, se connecter, parcourir les pages. Pour un canary, utiliser le nom d'hôte `{nom}.dcp.local`. Dire que « Route acceptée » n'est pas le comptage HTTP, et que Prometheus ne compte pas les Pods.
2. Utilisation interne : oui, sur cette machine, par des personnes de confiance. L'isolation des applications existe pour le propriétaire et l'administrateur, mais le client Kubernetes est administrateur du cluster et l'inventaire d'images Docker est commun.
3. Production : non. Pas d'identité Kubernetes limitée, allowlist GitHub désactivée, secrets locaux, registre HTTP, Metrics Server en TLS non vérifié, et une seule machine.
