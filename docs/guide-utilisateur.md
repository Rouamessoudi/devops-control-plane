# Guide utilisateur

1. Ouvrir `http://127.0.0.1:4200/login`.
2. Se connecter. Un mot de passe incorrect renvoie une erreur de l'API, sans donnée de remplacement.
3. Le tableau de bord lit les applications, les déploiements et l'aperçu monitoring. Le CPU affiché vient de Prometheus. Si aucun cluster n'est joignable, le nombre de Pods reste indisponible et un message l'indique.
4. La page Applications liste les applications réellement enregistrées, par exemple `hello-dcp`.
5. La page Releases propose blue/green, canary et HPA. Sans cluster, l'appel échoue et le trafic ne change pas.
6. Grafana est sur `http://127.0.0.1:13001`, tableau `DevOps Control Plane` (uid `dcp-overview`).

Les journaux Kubernetes et les événements de Pods passent par l'API Kubernetes. Ils sont vides ou en erreur tant que le cluster est absent.
