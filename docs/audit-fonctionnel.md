# Audit fonctionnel

Relevé du 11 octobre 2026, après ouverture de l'application sur `http://127.0.0.1:4200`. Une ligne n'est marquée vérifiée que si l'appel ou l'écran a été observé. Les essais Kubernetes de cette session portent sur `demo-box` et sur le nom d'hôte de la route `split`. Les Deployments `web-blue`, `web-green`, `webcanary-*`, `dcp-vote` et `split-*` n'ont pas été modifiés.

## Correspondance

| Page | Fonction attendue | Endpoint | État | Permissions | Résultat observé | Problème |
| --- | --- | --- | --- | --- | --- | --- |
| `/login` | Connexion JWT | `POST /api/auth/login` | Vérifié | Public | Playwright : mauvais mot de passe affiche l'erreur API ; la connexion ouvre `/dashboard` | Jeton d'accès court : une session inactive renvoie vers la connexion |
| `/register` | Compte DEVELOPER | `POST /api/auth/register` | Présent | Public, invité | Le lien est visible. Pas de nouveau compte créé pendant cet audit | Le rôle initial est toujours DEVELOPER |
| `/dashboard` | Indicateurs réels | applications, deployments, kubernetes, monitoring | Vérifié | Authentifié | Écran : 12 applications, 2 actives, 16 Pods, 10 Services, CPU 0,0136, mémoire 1,5 Gio | Le nombre de Pods vient de l'API Kubernetes, pas de Prometheus |
| `/applications` | Liste, filtre, suppression | `GET/DELETE /api/applications` | Vérifié | Propriétaire ou ADMIN | Playwright crée une application et la retrouve dans la liste | La suppression n'a pas été rejouée sur `vote` |
| `/applications/new` et `/:id/edit` | Création et modification | `POST/PUT /api/applications` | Vérifié plus tôt | Propriétaire ou ADMIN | `demo-box` a été créée depuis ce formulaire | Le dépôt doit être un `https://github.com/org/repo` |
| `/applications/:id` | Fiche, variables, déploiement, pipeline | applications, env, deployments, pipelines | Vérifié | Propriétaire ou ADMIN | Déployer et Pipeline demandent une confirmation. Le pipeline ouvre ensuite `/pipelines/:id` | Les secrets d'environnement restent masqués, ce qui est voulu |
| `/docker` | Build, images, conteneurs, logs | `/api/docker/builds`, `/images`, `/containers` | Vérifié | Build : propriétaire ou ADMIN. Liste d'images : tout authentifié | Build `demo-box:v1` SUCCEEDED, image au registre | La liste des conteneurs `dcp.managed` est vide : le pipeline ne laisse pas de conteneur hôte. Tout utilisateur authentifié voit les images gérées |
| `/kubernetes` et sous-pages | Pods, Services, Ingress, Deployments, namespaces, logs | `/api/kubernetes/*` | Vérifié | Authentifié | Pod, Service, logs et Deployment de `dcp-demo-box` lus dans l'interface | Scale et redémarrage existent, avec confirmation. Ils n'ont pas été rejoués |
| `/kubernetes/scaling` | Blue/green, bascule, canary, rollback, HPA | releases, rollback, autoscaler | Vérifié | Bascule et rollback : selon l'endpoint. Rollback Kubernetes : ADMIN | Voir `docs/validation-client.md` | Les images ne sont plus préremplies avec `web`. Le bouton de bascule appelle l'API testée ; le clic navigateur de ce bouton n'a pas été refait |
| `/pipelines` | Liste | `GET /api/pipelines` | Vérifié | Les siens, ou tous si ADMIN | L'écran liste les pipelines SUCCEEDED de `demo-box` et `vote`, et les FAILED anciens | Il n'y a pas de bouton pour lancer un pipeline ici : le lancement reste sur la fiche |
| `/pipelines/:id` | Étapes et journal | `GET /api/pipelines/{id}` | Vérifié plus tôt | Demandeur ou ADMIN | Pipeline `e6054e71-3259-4959-a008-0f97859b75e5`, quatre étapes SUCCEEDED | — |
| `/monitoring` | Séries Actuator | `/api/monitoring/overview`, `/series` | Vérifié | Authentifié | Cinq séries, 61 points sur 1 h. Pods Prometheus vides | Pas de métriques cAdvisor ni kube-state-metrics |
| `/logs` | Journal d'un Pod | `GET /api/monitoring/logs` | Présent | Authentifié | Le bouton sans nom de Pod affiche « Indiquez le nom du Pod. » | La page des Pods reste le chemin le plus direct pour un journal |
| `/admin` | Utilisateurs, rôles, audit, santé | `/api/users`, audit | Vérifié | ADMIN | Playwright : un DEVELOPER n'a pas le lien et `/admin` revient au tableau de bord | Changer un rôle ou supprimer un utilisateur n'a pas été rejoué |
| `/settings` | Profil et mot de passe | `PUT /api/auth/me`, `/password` | Présent | Authentifié | Le formulaire est en place. Le mot de passe n'a pas été changé pendant l'audit | — |
| — | Notifications | `GET /api/notifications` | Absent de l'interface | Authentifié | L'endpoint existe. Aucune page ne l'appelle | Fonction non accessible |
| — | Webhook GitHub | `POST /api/cicd/webhooks/github` | API seule | Signature | Test unitaire de signature. Pas d'essai de webhook réel | Pas d'écran |

## Écarts corrigés dans cette session

- Les HTTPRoute canary portent le nom d'hôte `{nom}.dcp.local`. La route `split` a reçu `split.dcp.local`. Une requête sans nom d'hôte répond 404.
- Le formulaire Releases n'envoie plus les images `registry:5000/dcp/web:*` par défaut.
- Le libellé « Trafic confirmé » est devenu « Route acceptée ».
- La liste des pipelines est dans le menu.
- `scripts/start.ps1` et `scripts/stop.ps1` démarrent et arrêtent la plateforme sans supprimer de volume.

## Écarts encore ouverts

- Les notifications n'ont pas d'écran.
- L'inventaire Docker n'est pas filtré par propriétaire.
- Le client Kubernetes est l'administrateur du cluster kind, pas le Role de `infrastructure/kubernetes/rbac.yaml`.
- `DCP_ENFORCE_GITHUB_ALLOWLIST` vaut `false` : un dépôt GitHub public peut être cloné.
- Prometheus ne voit pas les Pods. Le tableau de bord et la page Monitoring le disent.
