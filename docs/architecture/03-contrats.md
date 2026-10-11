# Contrats entre services

Préfixe public via la passerelle : `http://127.0.0.1:18080`.

Routes publiques : `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/refresh`, `POST /api/cicd/webhooks/github`, santé de la passerelle.

Toutes les autres routes `/api/**` exigent `Authorization: Bearer`.

Les routes `/internal/**` ne sont pas publiées par la passerelle. Elles exigent `X-Internal-Token`.

## Auth

`POST /api/auth/login` répond `{ accessToken, refreshToken, expiresIn, tokenType, user }`.
`user` contient `{ id, email, fullName, roles, createdAt }`.

## Application

`ApplicationView` : `id`, `ownerId`, `name`, `description`, `gitRepositoryUrl`, `branch`, `environment`, `dockerfilePath`, `contextPath`, `containerPort`, `status`, `createdAt`, `updatedAt`.

`GET /internal/applications/{id}/env` renvoie les valeurs déchiffrées. La route utilisateur masque les secrets avec `********`.

## Docker

`POST /internal/docker/builds` répond `202` avec `{ id, status, imageName, imageTag, fullImage }`.
`GET /internal/docker/builds/{id}` ajoute `logText`, `errorMessage`, `startedAt`, `finishedAt` et `steps`.

## Kubernetes

`POST /internal/kubernetes/deployments` reçoit `{ name, image, replicas, containerPort, actorId, applicationId, env }`.
La réponse est `{ name, namespace, image, replicas, readyReplicas, revision, status }`.

## Déploiement

`DeploymentView` : `id`, `applicationId`, `requestedBy`, `imageRepository`, `imageTag`, `fullImage`, `replicas`, `environment`, `trigger`, `status`, `k8sDeploymentName`, `k8sRevision`, `errorMessage`, `createdAt`, `startedAt`, `finishedAt`.

## Pipeline

`POST /api/pipelines` avec `{ applicationId }` crée un pipeline et appelle le service de déploiement.

## Monitoring

`GET /api/monitoring/overview` interroge Prometheus. Une erreur de connexion est un échec. Une réponse Prometheus sans série laisse les mesures à vide, sans nombre inventé.

`GET /api/monitoring/series?metric=cpu|memory&range=15m|1h|6h|24h` utilise uniquement ces valeurs.
