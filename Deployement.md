Utilisation de render

structure Git : main production, develop code local -> feature sprint etc -> PR vers develop , staging (apartir de develop)

---

## 🛡️ Guide des Optimisations Techniques, Sécurité & Audits Lighthouse

Ce document récapitule les configurations et optimisations appliquées sur le projet pour garantir des performances optimales, la sécurité des données RH et une parfaite conformité avec les audits Web (Lighthouse / DevTools).

---

### 1. 🔒 Sécurité des Cookies (Authentification & Refresh Token)
*   **Gestion Cross-Site / HTTPS Proxy (Render) :** 
    Le backend Spring Boot (`AuthController.java`) s'adapte dynamiquement au header `X-Forwarded-Proto` de Render.
    *   **En Production (HTTPS) :** Les cookies de rafraîchissement (`refreshToken`) sont émis avec `Secure; HttpOnly; SameSite=None`.
    *   **En Développement Local (HTTP) :** Les cookies basculent automatiquement sur `SameSite=Lax` pour garantir la compatibilité sans HTTPS.
*   **Note sur la "Privacy Sandbox" de Chrome (DevTools) :**
    Chrome affiche un avertissement concernant l'abandon futur des cookies tiers sur les domaines gratuits `*.onrender.com` (considérés comme inter-sites). 
    *   *Solution définitive pour la Production finale :* L'utilisation d'un **Nom de Domaine Personnalisé partagé** (ex: `sirh.galaxam.mg` pour le frontend et `api.sirh.galaxam.mg` pour l'API) transformera le cookie en *First-Party*, ce qui supprimera définitivement cet avertissement.

---

### 2. 🛡️ En-têtes de Sécurité Web (CSP, Clickjacking & HSTS)
Pour maximiser le score de **Bonnes Pratiques** dans Lighthouse et sécuriser le DOM contre les attaques :
*   **Protection Clickjacking (XFO) :**
    *   `<meta http-equiv="X-Frame-Options" content="DENY" />` dans `frontend/index.html`.
    *   Configuration `headers.frameOptions(frame -> frame.deny())` dans `SecurityConfig.java`.
*   **Politique de Sécurité des Contenus (CSP) :**
    *   `<meta http-equiv="Content-Security-Policy" content="..." />` configurée dans `frontend/index.html` pour restreindre l'exécution de scripts/styles non autorisés (prévention XSS).
*   **Transport Sécurisé Forcé (HSTS) :**
    *   `httpStrictTransportSecurity` configuré sur 1 an (`31536000s`) avec inclusion des sous-domaines dans `SecurityConfig.java`.

---

### 3. 🔍 Indexation & SEO (Contexte Intranet Privé)
*   **Masquage Moteurs de Recherche :** 
    L'application gérant des données RH confidentielles, l'indexation est explicitement désactivée via :
    *   `<meta name="robots" content="noindex, nofollow" />` dans `index.html`.
    *   Le composant React dynamique `SEO.jsx` avec `react-helmet-async`.
*   **Impact sur l'audit Lighthouse SEO :** 
    Le rapport Lighthouse affichera l'avertissement *"Document is blocked from indexing"*. Il s'agit d'un **faux positif attendu et volontaire** pour préserver la confidentialité des données de l'entreprise. Les balises sémantiques (`<title>`, `<meta description>`, `viewport`) restent toutefois optimisées pour l'accessibilité.

---

### 4. ⚡ Performances & Tree-Shaking des Icônes
*   **Optimisation de la Sidebar :** 
    Les icônes RH de la barre latérale sont chargées via un registre centralisé (`iconRegistry.js`). 
    Seules les icônes nécessaires à l'application sont importées individuellement depuis `lucide-react`, permettant au bundler Vite d'exécuter un **Tree-Shaking** efficace (exclusion automatique des ~1300 autres icônes de la bibliothèque pour alléger le bundle JavaScript).

---

### 5. 🗄️ Base de données PostgreSQL et migrations actives
Lors du déploiement ou de la réinitialisation de l'environnement :
*   **Ordre d'exécution des scripts BDD :**
    1. `01_schema_core_securite.sql`
    2. `02_schema_referentiels_rh.sql`
    3. `03_schema_employes_contrats.sql`
    4. `04_schema_conges.sql`
    5. `05_schema_pointage.sql`
    6. `06_schema_paie.sql`
    7. `2026-09-13_snapshot_pointage_versionne.sql`
    8. `2026-09-14_workflow_periode_paie.sql`
    9. `2026-09-14_remove_business_sql.sql`

Les scripts sous `archive_migrations_historiques/` sont conservés pour traçabilité et ne doivent pas être rejoués comme migrations actives. Ne pas revendiquer de couverture d'index ou de temps de réponse sans mesure `EXPLAIN ANALYZE` sur la base cible.

### 6. Variables obligatoires en production

Le profil `prod` doit recevoir explicitement :

- `SPRING_DATASOURCE_URL` (ou `DATABASE_URL` au format JDBC), ainsi que les identifiants si l'URL ne les contient pas ;
- `JWT_SECRET`, suffisamment long et aléatoire ;
- `CORS_ORIGIN`, liste d'origines HTTPS explicites ;
- `UPLOAD_DIR`, répertoire persistant non servi publiquement.

Le bootstrap administrateur est désactivé par défaut. Pour une initialisation ponctuelle et auditée seulement, fournir `BOOTSTRAP_ADMIN_ENABLED=true`, `GALAXAM_DEFAULT_ADMIN_USERNAME` et `GALAXAM_DEFAULT_ADMIN_PASSWORD`, puis désactiver le bootstrap après le premier démarrage.

Avant déployer :

```bash
cd backend
./mvnw test
cd ../frontend
npm ci
npm run lint
npm run build
```
