# Architecture

Huit processus Spring Boot, une interface Angular 21, MySQL, un registre d'images local, Prometheus et Grafana. Kubernetes est un cluster kind nommé `dcp`. Aucun neuvième service n'a été ajouté.

```text
Navigateur :4200
    → api-gateway :18080
         → auth-service
         → application-service
         → docker-service  → démon Docker et registre :15000
         → cicd-service    → docker-service, puis deployment-service
         → deployment-service → kubernetes-service
         → kubernetes-service → API du nœud kind
         → monitoring-service → Prometheus :19090
```

Chaque service métier a sa base. La passerelle est le seul entrée HTTP du navigateur. Elle vérifie le JWT, sauf sur la connexion, l'inscription et le rafraîchissement. CORS n'autorise que `http://localhost:4200` et `http://127.0.0.1:4200`.

## Rôles

ADMIN voit les utilisateurs, l'audit global et le rollback Kubernetes. DEVELOPER gère ses applications, ses builds et ses pipelines. Les routes internes `/internal/**` exigent le rôle INTERNAL et ne sont pas appelées par Angular.

## Livraison

Un pipeline clone un dépôt `https://github.com/org/repo`, construit l'image, la pousse vers le registre public `localhost:15000`, puis le déploiement réécrit le nom en `registry:5000` pour le cluster. Les commandes de test dans le conteneur sont une liste fixe. Un chemin de Dockerfile qui contient `..` est refusé.

Le déploiement Kubernetes crée un Deployment, un Service et un Ingress `{nom}.dcp.local`.

## Releases

Blue/green crée `{nom}-blue` et `{nom}-green`. Le Service `{nom}` sélectionne `app` et `dcp.track`. La bascule ne change que ce sélecteur, et seulement si la piste cible est prête.

Canary crée `{nom}-stable` et `{nom}-canary`, deux Services, et une HTTPRoute du Gateway `dcp`. La route a le nom d'hôte `{nom}.dcp.local` et des poids 10, 25, 50 ou 100. Sans ce nom d'hôte, une autre route ne doit pas recevoir le trafic. L'acceptation par le Gateway est un état de configuration. Le comptage HTTP se fait en interrogeant cet hôte.

Le rollback d'une révision recopie le modèle de Pod de cette révision. Ce n'est pas le retour d'image de la fiche application.

Le HPA lit Metrics Server. Le pourcentage n'existe que si le conteneur a des requests. Prometheus, lui, ne scrape que les actuators des huit services.

## Ce qui reste local

Le kubeconfig monté dans `kubernetes-service` est celui de l'administrateur kind. Le Role du fichier `infrastructure/kubernetes/rbac.yaml` n'est pas l'identité utilisée. Le registre est en HTTP. L'allowlist d'organisations GitHub est désactivée par défaut.
