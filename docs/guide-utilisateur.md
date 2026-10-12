# Guide utilisateur

L'application se consulte dans le navigateur, à l'adresse `http://127.0.0.1:4200`.

## Entrer

La page de connexion demande un email et un mot de passe. Le compte administrateur local est celui de la variable `DCP_ADMIN_PASSWORD` dans `.env`. Un compte créé par « Créer un compte » reçoit le rôle DEVELOPER. Le menu Administration n'apparaît que pour un ADMIN. Une session expirée ramène à la connexion.

## Parcours

1. Le tableau de bord résume les applications, les déploiements, les Pods, les Services et les séries Prometheus. Un tiret signifie que la mesure n'existe pas.
2. Applications : créer, ouvrir, modifier, supprimer. La suppression demande une confirmation.
3. Sur la fiche : ajouter une variable d'environnement, lancer un déploiement ou un pipeline. Les deux demandent une confirmation. Le pipeline ouvre sa page de suivi.
4. Docker : choisir une application, un tag, puis Construire. La confirmation précise que l'image est poussée et que rien n'est supprimé. Seuls les conteneurs marqués `dcp.managed=true` apparaissent.
5. Kubernetes : Pods, Services, Ingress, Deployments, namespaces. Le détail d'un Pod montre son journal. Scale et redémarrage demandent une confirmation.
6. Releases : blue/green, bascule du Service vers une piste déjà déployée, canary, rollback d'une révision, HPA. Les images et le port doivent être saisis. Le canary n'accepte que 10, 25, 50 ou 100.
7. Pipelines : liste des exécutions et lien vers les étapes.
8. Monitoring : graphiques Actuator. Grafana s'ouvre par le lien de la page.
9. Logs : indiquer le nom du Pod.
10. Paramètres : nom affiché et mot de passe.
11. Administration : utilisateurs, rôles, journal. Réservé à l'ADMIN.

## Canary

Chaque release canary est servie sur `http://{nom}.dcp.local/` à travers le Gateway. Une requête sans ce nom d'hôte ne doit pas atteindre l'application. Le message « Route acceptée » indique que le Gateway a pris la route. Il ne remplace pas un comptage des réponses.

## Blue/green

« Déployer la piste » crée ou met à jour `nom-blue` ou `nom-green`, attend les Pods prêts, puis pointe le Service `nom` vers cette piste. « Basculer le Service » change seulement le sélecteur, vers une piste déjà prête. La piste précédente reste déployée.

## Ce que l'interface ne fait pas

Elle ne lance pas un pipeline depuis la page Pipelines. Elle ne montre pas les notifications. Elle ne dessine pas le CPU des Pods.
