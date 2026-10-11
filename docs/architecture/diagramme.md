# Architecture

Huit processus Spring Boot. La passerelle est le seul point d'entrée du navigateur. Chaque service métier a sa base MySQL. Prometheus interroge `/actuator/prometheus`. Kubernetes n'est appelé que par `kubernetes-service`, et seulement si un cluster répond.

```mermaid
flowchart LR
  angular[Angular 4200] --> gateway[API Gateway 8080]
  gateway --> auth[Auth]
  gateway --> apps[Applications]
  gateway --> docker[Docker]
  gateway --> k8s[Kubernetes]
  gateway --> deploy[Deployments]
  gateway --> cicd[CI/CD]
  gateway --> mon[Monitoring]
  auth --> mysql[(MySQL)]
  apps --> mysql
  docker --> mysql
  k8s --> mysql
  deploy --> mysql
  cicd --> mysql
  mon --> mysql
  mon --> prom[Prometheus]
  prom --> grafana[Grafana]
  docker --> daemon[Docker]
  k8s --> cluster[API Kubernetes]
```
