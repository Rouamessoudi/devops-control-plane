# Guide utilisateur

1. Ouvrir `http://127.0.0.1:4200/login`.
2. Se connecter. Un mot de passe incorrect renvoie une erreur de l'API, sans donnée de remplacement.
3. Le tableau de bord lit les applications, les déploiements et l'aperçu monitoring. Le CPU affiché vient de Prometheus. Si aucun cluster n'est joignable, le nombre de Pods reste indisponible et un message l'indique.
4. La page Applications liste les applications réellement enregistrées, par exemple `hello-dcp`.
5. La page Releases propose blue/green, canary, le rollback d'une révision Kubernetes et le HPA. Blue/green déplace le sélecteur du Service après des replicas prêts. Le canary affiche le port du conteneur (80 pour l'application web de démonstration) et n'annonce un trafic appliqué que si les Pods sont prêts et que le Gateway accepte l'HTTPRoute. Le panneau Rollback Kubernetes annule une révision du Deployment ; ce n'est pas le rollback d'image d'une fiche application, et il est réservé à un administrateur.
6. Grafana est sur `http://127.0.0.1:13001`, tableau `DevOps Control Plane` (uid `dcp-overview`).
7. La déconnexion est dans le menu du nom affiché en haut à droite. Une page protégée ouverte sans session renvoie à la connexion.

Les tests Playwright de `frontend/e2e` rejouent ce parcours sur les services locaux.

Les journaux Kubernetes et la page Pods passent par l'API Kubernetes. Le 11 octobre 2026, cette page liste les Pods du namespace `dcp` et indique que l'API est joignable.
