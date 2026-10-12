# Guide d'installation

Machine locale, Windows, Docker Desktop. Le dépôt n'est pas reconstruit par le démarrage ordinaire.

## Prérequis

- Docker répond à `docker info`.
- Le cluster kind `dcp` existe déjà. Son nœud s'appelle `dcp-control-plane`.
- Java 21 et Maven ne sont nécessaires que pour `-Build`.
- Node sert l'interface si le port 4200 est libre. La version du frontend est Angular 21.

## Démarrer

Depuis la racine du projet :

```powershell
.\scripts\start.ps1
```

Le script démarre le nœud kind s'il est arrêté, puis `docker compose` avec `docker-compose.yml` et `docker-compose.k8s.yml`. Il ne supprime pas de volume. Il attend que `http://127.0.0.1:18080/actuator/health` réponde 200. Si rien n'écoute sur le port 4200, il lance `npx ng serve`. Il affiche ensuite l'adresse `http://127.0.0.1:4200` et l'état de `kubectl --context kind-dcp get --raw=/readyz`.

Si Kubernetes ne répond pas, le script le dit. Les pages qui ne parlent pas au cluster restent utilisables. Les pages Kubernetes affichent l'indisponibilité.

Pour reconstruire les images des services :

```powershell
.\scripts\start.ps1 -Build
```

## Arrêter

```powershell
.\scripts\stop.ps1
```

La commande est `docker compose stop`, puis `docker stop` du nœud kind s'il tourne. Elle ne fait pas `docker rm`, `docker volume rm`, ni `compose down -v`. Les bases MySQL et le cluster kind restent sur le disque. La relance est `.\scripts\start.ps1`.

## Ports

L'interface est sur 4200. La passerelle est sur 18080. Les autres services écoutent de 18181 à 18087. MySQL est sur 13306, le registre sur 15000, Prometheus sur 19090, Grafana sur 13001. Ils sont liés à la machine locale.

## Santé

- Passerelle : `http://127.0.0.1:18080/actuator/health`
- Kubernetes : `kubectl --context kind-dcp get --raw=/readyz`
- Docker : `docker info`

Si le socket Docker est absent, la page Docker affiche l'indisponibilité et conserve les images déjà enregistrées en base.
