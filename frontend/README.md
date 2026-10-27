# Frontend GALAXAM SIRH

Application React construite avec Vite. La configuration Docker principale se trouve à la racine du dépôt dans `docker-compose.yml` ; consulter le README racine pour la préparation de `.env`, les volumes et le démarrage.

En mode intégré, le Dockerfile construit les fichiers statiques puis Nginx les sert sur `http://localhost:8088`. Nginx transmet les requêtes `/api/` au service backend sur le réseau Docker. Le navigateur n'accède pas directement au port du backend.

Le mode de développement reste indépendant : depuis `frontend/`, exécuter `npm install` puis `npm run dev`. Vite utilise son port de développement habituel (`5173` si disponible). Le backend local et sa base doivent être configurés séparément ; les variables du `.env` racine sont destinées à Docker Compose.

Commandes de contrôle : `npm run lint`, `npm run test:api` et `npm run build`. Elles ne sont pas exécutées par ce document.
