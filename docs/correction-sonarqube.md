# Correction des problèmes SonarQube

Date des mesures : 11 octobre 2026. Projet SonarQube `devops-control-plane`. Serveur Community Build `26.9.0.129388`, `UP` sur `127.0.0.1:9000`. Les seuils du Quality Gate `DevOps Control Plane` n’ont pas été modifiés. Aucune règle n’a été désactivée. `DCP_SONAR_MODE` reste `off`. Aucun secret n’est recopié ici : le jeton est `DCP_SONAR_TOKEN`, le mot de passe administrateur est `SONAR_ADMIN_PASSWORD`.

## Problèmes détectés

Première analyse, tâche CE `bc530381-5729-49aa-a328-c72e0f284eb1`, `SUCCESS` à `2026-10-11T20:54:52+0000`. Quality Gate `ERROR`.

| Mesure | Valeur |
| --- | --- |
| Bugs | 3 |
| Vulnérabilités | 10 |
| Code smells | 71 |
| Couverture | 24,7 % |
| Duplications | 2,8 % |
| Lignes de code | 16 008 |

Les conditions en échec étaient les bugs (supérieur à 0), les vulnérabilités (supérieur à 0) et la couverture (inférieur à 30). Les code smells (71, seuil 100) et les duplications (2,8 %, seuil 3) passaient. L’API des issues ouvertes, relue après correction, confirme que ces 13 issues sont fermées. Les lignes ci-dessous sont celles relevées sur cette première analyse.

### Bugs

| Priorité | Règle | Fichier | Cause | Risque | Correction |
| --- | --- | --- | --- | --- | --- |
| BLOCKER | `java:S2095` | `KubernetesClusterService.java`, ligne 743 | `within()` créait un `ExecutorService` et n’appelait que `shutdownNow()`. `close()` aurait pu attendre jusqu’à un jour. | Fuite de threads à chaque appel Kubernetes borné. | `CompletableFuture.supplyAsync`, attente de 4 secondes, `cancel(true)` en cas d’expiration, d’erreur ou d’interruption. Plus d’exécuteur possédé par la méthode. |
| MAJOR | `java:S2259` | `PipelineStore.java`, ligne 105 | `finishFromDeployment` lisait l’image et le statut d’un déploiement qui peut être nul. | `NullPointerException` quand le déploiement n’est pas revenu. | Le succès n’est pris que si le déploiement est non nul et que son statut vaut `SUCCEEDED`. Le chemin d’échec lit le statut après ce contrôle. |
| MAJOR | `typescript:S1848` | `line-chart.ts`, ligne 21 | `new Chart(...)` n’était pas conservé. | Graphique créé puis inaccessible, et possible graphique orphelin sur le canvas. | Le graphique est conservé et détruit s’il n’est pas celui déjà attaché au canvas. |

### Vulnérabilités

| Priorité | Règle | Fichier | Cause | Risque | Correction |
| --- | --- | --- | --- | --- | --- |
| BLOCKER | `typescript:S6268` | `monitoring.page.ts`, ligne 208 | `bypassSecurityTrustResourceUrl` marquait l’URL Grafana comme sûre. | Une URL non prévue pouvait être chargée dans l’iframe. | Le sanitizer a été retiré. `grafanaEmbedUrl` n’accepte que `http`, l’hôte `127.0.0.1` ou `localhost`, le port `13001`, et un chemin de tableau de bord qui commence par `/` sans `//`, `\` ni `://`. L’iframe reçoit cette URL, ou n’a pas de `src`. |
| CRITICAL | `java:S5443` | `BuildJob.java`, ligne 39 | `Files.createTempDirectory` créait le clone dans le répertoire temporaire public. | Un autre compte de la machine pouvait lire le dépôt cloné. | Le répertoire est `user.home/.dcp/builds/src-<uuid>`, créé avec `createDirectory`. Sur un système POSIX, les droits sont limités au propriétaire. |
| MINOR, huit fois | `docker:S6471` | Dockerfile des huit services | L’image `eclipse-temurin:21-jre-alpine` exécutait Java en root. | Un processus compromis avait les droits root du conteneur. | Groupe et utilisateur `dcp`, `COPY --chown=dcp:dcp`, `USER dcp`. |

Le socket Docker de l’hôte est `srw-rw---- root:root`. `docker-service` ajoute le groupe `0` pour que l’utilisateur `dcp` puisse l’utiliser sans redevenir root. Le kubeconfig monté dans `kubernetes-service` reste lisible par cet utilisateur.

## Corrections effectuées

Les huit microservices, l’authentification JWT, la passerelle, Docker, Kubernetes, le pipeline, le monitoring et SonarQube sont conservés. Aucun seuil n’a été baissé. Aucune application n’a été déployée. Aucun push n’a été fait.

Deux ajustements ont suivi la disparition des bugs et des vulnérabilités, parce que le Quality Gate restait `ERROR` pour d’autres conditions :

- Les fichiers `**/src/test/**` sont déclarés dans `sonar.tests` et `sonar.test.inclusions`. Ils restent analysés comme tests. Ils ne comptent plus comme code de production sans couverture. Ce n’est pas un changement de seuil. Une analyse intermédiaire, encore avec les tests dans les sources, mesurait déjà 32,8 % de couverture, 0 bug et 0 vulnérabilité, mais 11,1 % de couverture du nouveau code : JaCoCo ne couvre pas les fichiers de test, et ces fichiers étaient presque tous nouveaux.
- `PipelineDtos.DeploymentView` était une classe de getters identique au contrat déjà exposé par le service de déploiement. Elle est devenue un record. La densité de duplication, montée à 3,2 % une fois les tests sortis du dénominateur, est redescendue à 2,4 %.

Trois code smells du nouveau code ont aussi été retirés avant la dernière analyse : la constante `SUCCEEDED` déjà définie dans `PipelineStore`, le bloc de texte de `SonarProbeTest` sorti de la lambda, et le paramètre par défaut de `grafanaEmbedUrl` à la place d’une réaffectation.

## Tests ajoutés

Ils couvrent des succès, des refus, des erreurs et un sondage de déploiement. Ils ne remplacent pas un test par une assertion vide.

| Test | Scénarios |
| --- | --- |
| `BuildJobTest` | Le répertoire de build est sous le dossier personnel, pas dans le temporaire public. Un jeton présent dans une erreur de préparation est masqué et le build Docker n’est pas lancé. |
| `BuildServiceTest` | Refus si l’appelant n’est pas propriétaire, si la liste GitHub imposée est vide, ou si l’exécuteur est saturé. Acceptation du propriétaire. Build interne qui reprend les valeurs de l’application. |
| `DockerQueryServiceTest` | Build absent ou appartenant à un autre compte : 404. Le propriétaire lit les étapes. L’administrateur liste et consulte l’audit. Conteneurs et images, y compris un démon Docker injoignable qui conserve la ligne en base. |
| `KubernetesClusterServiceTest` | Statut joignable, statut injoignable, et expiration de l’appel borné à 4 secondes. |
| `PipelineStoreTest` | Gate en échec sans déploiement. Déploiement réussi avec image. Déploiement réussi sans image. Déploiement en échec avec message et heure de début. Déploiement nul sans exception. Pipeline inconnu. Webhook succès, échec et ignoré. Journal refusé à un autre utilisateur. Liste limitée au demandeur. |
| `PipelineRunnerTest` | Gate en échec : pas d’appel de déploiement. Gate réussi et déploiement déjà terminé. Réponse vide ou sans identifiant. Déploiement encore `RUNNING`, puis `SUCCEEDED` au sondage. |
| `PipelineDtosTest` | Le JSON de déploiement conserve chaque champ lu ensuite par le pipeline, y compris un champ inconnu ignoré. |
| `SonarProbeTest` | Lecture d’un serveur local : gate, couverture, bugs, vulnérabilités, smells, duplications, date et en-tête d’autorisation. URL vide ou injoignable : mesures absentes. |
| `grafana-url.spec.ts` | Hôtes locaux acceptés. HTTPS, autre hôte, chemin absolu, chemin `//`, URL invalide et URL nulle refusés. |
| `monitoring.page.spec.ts` | L’iframe reçoit l’URL Grafana locale, puis disparaît si l’URL n’est plus autorisée. |
| `line-chart.spec.ts` | Création puis effacement d’un graphique sur un canvas. |

`AuthService`, `DeploymentService`, `PipelineService` et `MonitoringService` avaient déjà des tests. Leur couverture SonarQube après cette passe : `AuthService` 67,6 %, `DeploymentService` 57,8 %, `PipelineService` 58,5 %, `MonitoringService` 40,2 %. `ApplicationService` reste à 29,1 % (134 lignes non couvertes sur 188). `KubernetesClusterService` reste à 2,9 % (698 lignes non couvertes sur 726) : le test ajouté ne couvre que l’attente bornée, pas les opérations du cluster. `BuildService` est à 85,6 %, `DockerQueryService` à 64,2 %, `PipelineStore` à 64,7 %.

## Couverture avant et après

| Mesure | Avant (22:54) | Après (23:37) |
| --- | --- | --- |
| Couverture SonarQube | 24,7 % | 42,1 % |
| Couverture du nouveau code | non applicable, première version | 92,9 % |
| Lignes Java JaCoCo | non retenue comme résultat SonarQube | 2 019 couvertes, 2 534 manquées, 44,3 % |

L’analyse intermédiaire de 23:29, avec la même configuration de sources que celle de 22:54, mesurait déjà 32,8 % de couverture globale. Le passage à 42,1 % vient ensuite du classement des tests hors du code de production, pas d’un abaissement du seuil de 30 %.

Le rapport v8 des fichiers Angular réellement chargés par les tests unitaires passe de 58 lignes couvertes sur 97 à 247 sur 347 (71,18 %). Ce pourcentage n’est pas la couverture du projet.

## Résultats SonarQube avant et après

| Mesure | 22:54 | 23:37 |
| --- | --- | --- |
| Bugs | 3 | 0 |
| Vulnérabilités | 10 | 0 |
| Code smells | 71 | 66 |
| Couverture | 24,7 % | 42,1 % |
| Duplications | 2,8 % | 2,4 % |
| Nouvelles violations | — | 0 |
| Couverture du nouveau code | — | 92,9 % |
| Duplications du nouveau code | — | 0,0 % |
| Lignes de code | 16 008 | 14 240 |

La recherche des issues ouvertes de type `BUG` et `VULNERABILITY` renvoie un total de 0. Les issues du nouveau code ouvertes renvoient aussi 0.

Entre ces deux analyses, une passe à 23:29 avait encore un gate `ERROR` : couverture du nouveau code 11,1 % et 3 nouvelles violations, alors que les bugs et les vulnérabilités étaient déjà à 0. Une passe à 23:35 avait les duplications à 2,4 % mais la couverture du nouveau code à 75,9 %, sous le seuil de 80 % imposé par Clean as You Code. Ces deux conditions ont été levées par les tests de branches du pipeline, sans changer les seuils.

## Quality Gate final

`OK`. Tâche CE `b6424e96-ef1e-46b8-9127-f41df0b1071e`, `SUCCESS`. Date renvoyée par l’API de qualité : `2026-10-11T21:37:38+0000`.

| Condition | Comparateur | Seuil | Valeur | Résultat |
| --- | --- | --- | --- | --- |
| bugs | supérieur à | 0 | 0 | OK |
| vulnerabilities | supérieur à | 0 | 0 | OK |
| code_smells | supérieur à | 100 | 66 | OK |
| coverage | inférieur à | 30 | 42,1 | OK |
| duplicated_lines_density | supérieur à | 3 | 2,4 | OK |
| new_coverage | inférieur à | 80 | 92,9 | OK |
| new_violations | supérieur à | 0 | 0 | OK |
| new_duplicated_lines_density | supérieur à | 3 | 0,0 | OK |

`DCP_SONAR_MODE` n’est pas passé à `required`. Le gate porte sur le projet de la plateforme. L’activer bloquerait chaque déploiement selon cette analyse, et non selon le dépôt de l’application déployée. Le chemin « gate réussi, le déploiement continue » est couvert par `PipelineRunnerTest`. Il n’a pas été rejoué sur `vote`, `web` ou `demo-box`.

## Validation locale

| Commande | Résultat |
| --- | --- |
| `mvn -B test` | BUILD SUCCESS à 23:40:08, 1 min 17 s, 120 tests, 0 échec. |
| `npx ng test --watch=false --coverage` | 13 tests, 7 fichiers, 0 échec, 4,56 s. |
| `npx ng build --configuration=development` | Succès à 23:28, `frontend/dist/frontend`. |
| `npx playwright test` | 7 tests, 0 échec, 14,3 s. |
| Analyse SonarQube | `SUCCESS`, Quality Gate `OK`. |

Les huit images ont été reconstruites avec `docker compose -f docker-compose.yml -f docker-compose.k8s.yml up -d --no-deps --build`. Le conteneur SonarQube, signalé orphelin parce que ce fichier Compose n’est pas inclus, n’a pas été supprimé. Chaque processus Java tourne avec l’utilisateur `dcp` (uid 100). `docker-service` appartient aussi au groupe `0`. Les huit sondes `actuator/health` répondent `UP`. La connexion administrateur répond HTTP 200. `GET /api/kubernetes/status` répond HTTP 200 et `reachable=true`. `GET /api/docker/images` répond HTTP 200. `GET /api/pipelines/quality` renvoie le gate `OK` et `mode=off`.
