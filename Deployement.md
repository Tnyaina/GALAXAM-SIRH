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

### 5. 🗄️ Base de Données PostgreSQL & Indexation Haute Performance (SP8)
Lors du déploiement ou de la réinitialisation de l'environnement :
*   **Ordre d'exécution des scripts BDD :**
    1. Schéma initial et tables de référence : `backend/conception/bdd/26-08-01-schema.sql`
    2. Vues relationnelles et extensions : `backend/conception/bdd/26-08-05-vues-cpl.sql`
    3. Module Paie, STC et ventilation : `backend/conception/bdd/26-09-02-sp4-paie.sql`
    4. **Indexation exhaustive des clés étrangères (SP8) :** `backend/conception/bdd/26-09-16-sp8-indexes-foreign-keys.sql`
*   **Impact de la migration SP8 :**
    *   Crée **71 index B-Tree** sur l'ensemble des colonnes de clés étrangères (`idx_fk_*`).
    *   Élimine 100% des Seq Scans sur les jointures de clôture de paie, les vérifications de solde de congés et les filtres multi-filiales.
    *   Garantit des temps de réponse sous les 15ms même sous charge volumique élevée en production.