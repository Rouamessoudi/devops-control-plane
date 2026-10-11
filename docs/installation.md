# Installation

1. Installer Java 21, Node.js 24.13 et Docker Desktop. Ne pas changer les versions du projet (Spring Boot 3.5.15, Angular 21).
2. Placer Apache Maven 3.9.11 dans `.tools/apache-maven-3.9.11` si le dossier n'est pas déjà là.
3. Depuis la racine : `.\scripts\start-local.ps1`.
4. Attendre que `docker compose ps` montre les huit services Java et MySQL `healthy`.
5. Lancer Angular : `npx ng serve --host 127.0.0.1 --port 4200` dans `frontend/`.
6. Ouvrir `http://127.0.0.1:4200` et se connecter avec le compte créé au premier démarrage d'auth-service.

Les secrets de développement sont dans `.env`, ignoré par Git. Ne pas les réutiliser hors de la machine locale.

Kubernetes n'est pas installé par ce guide. Le 11 octobre 2026, l'hôte n'avait qu'environ 4,7 Go de RAM libre et 44 conteneurs déjà lancés. Activer Docker Desktop Kubernetes dans ces conditions n'a pas été fait.
