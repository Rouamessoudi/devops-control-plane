# Audit de l'environnement — 11 octobre 2026

Machine : Windows 11, Intel Core Ultra 9 285H, 16 cœurs logiques, 32 Go de RAM, 481 Go libres sur C:.

| Outil | Résultat |
| --- | --- |
| Java | OpenJDK 21.0.12.1 (Microsoft), JAVA_HOME défini |
| javac | 21.0.12.1 |
| Maven global | absent. Maven 3.9.11 portable est dans `.tools/`, ignoré par Git. Le build du projet passera par ce binaire, puis par le wrapper. |
| Node.js | 24.13.1 |
| npm | 11.8.0 |
| Angular CLI global | absente. Le frontend est généré avec `npx @angular/cli@21.2.26`. |
| Docker | Client et moteur 29.0.1, Docker Desktop 4.53.0, contexte `desktop-linux` |
| Docker Compose | v2.40.3 |
| kubectl | client v1.34.1, aucun contexte configuré |
| Minikube | absent |
| Helm | absent |
| Git | 2.45.1 |
| GitHub CLI | 2.87.3 |
| WSL2 | Ubuntu en cours d'exécution, docker-desktop en cours d'exécution |

Docker Desktop a 16 Go alloués, 16 CPU, 29 conteneurs déjà démarrés. La RAM Windows libre au moment de l'audit était d'environ 3,4 Go. Minikube n'est pas installé et ne sera pas démarré tant que cette marge n'est pas meilleure : un cluster supplémentaire entrerait en conflit avec les conteneurs déjà présents.

`kubectl cluster-info` n'atteint aucun cluster. Le client reçoit une page d'authentification, sans contexte dans la configuration.

## Ports déjà pris

`wslrelay` écoute sur l'interface locale pour 3000, 3306, 8080-8088, 9000, 9090 et 9091. Le port 18081 est également occupé. Ces processus ne sont pas modifiés.

Ports retenus pour cette plateforme :

| Service | Port hôte |
| --- | --- |
| Interface Angular | 4200 |
| API Gateway | 18080 |
| Auth | 18181 |
| Applications | 18082 |
| Docker | 18083 |
| Kubernetes | 18084 |
| Déploiements | 18085 |
| CI/CD | 18086 |
| Monitoring | 18087 |
| MySQL | 13306 |
| Registre | 15000 |
| Prometheus | 19090 |
| Grafana | 13001 |

À l'intérieur du réseau Compose, les services gardent les ports 8080 à 8087 et MySQL 3306.

## Versions retenues

- Java 21, Spring Boot 3.5.15, Spring Cloud 2025.0.3
- Angular 21.2, TypeScript 5.9, RxJS 7.8
- MySQL 8.4, Prometheus 3.2, Grafana 11.5
- JaCoCo 0.8.13, Testcontainers 1.21.3, Fabric8 7.3.1, docker-java 3.4.2

Angular 22.2 est la dernière ligne stable, mais elle exige Node.js 24.15.0. Le poste a 24.13.1, donc Angular 21.2 est la version stable compatible, sans mise à jour de Node.
