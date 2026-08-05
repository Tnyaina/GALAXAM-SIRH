# Documentation Sécurité - Frontend React JS (JWT & Cookies)

Ce document décrit l'implémentation et la gestion de la sécurité côté client (React) pour l'application web SIRH.

---

## 1. Menaces Sécurité & Choix Techniques

Pour sécuriser une application Web moderne contre les failles courantes, nous avons combiné deux protections complémentaires :

### Protection contre le vol de Token (XSS)
- **Le problème** : Si l'Access Token est stocké dans le `LocalStorage` ou le `SessionStorage`, n'importe quel script malveillant (via une dépendance compromise ou une injection) peut y accéder.
- **La solution** : L'Access Token (JWT) est stocké **uniquement en mémoire vive** (in-memory) dans une variable JavaScript privée du module `apiClient.js`. Dès que la page est actualisée, le token disparaît.

### Protection contre l'usurpation d'identité (CSRF)
- **Le problème** : Si la session s'appuie uniquement sur des cookies standard, des requêtes contrefaites peuvent être envoyées à notre insu.
- **La solution** : Le Refresh Token est stocké dans un cookie configuré avec :
  - `HTTP-Only` : Interdit l'accès au cookie via JavaScript (neutralise XSS).
  - `Secure` : Transmis uniquement via HTTPS.
  - `SameSite=Strict` : Empêche le navigateur d'envoyer le cookie lors de requêtes initiées par des sites tiers (neutralise CSRF).
  - `Path=/api/auth` : Restreint l'envoi du cookie aux seuls endpoints d'authentification (login, refresh, logout), réduisant sa surface d'exposition.

---

## 2. Le Client API (`apiClient.js`)

Le client API utilise **Axios** et orchestre l'injection et le renouvellement automatique des tokens.

```mermaid
sequenceDiagram
    participant React as Composants React
    participant Client as apiClient (Axios)
    participant API as API Backend (Spring Boot)

    React->>Client: Appelle api.get('/mon-endpoint')
    Note over Client: Injecte "Bearer <Access Token>" (si présent en mémoire)
    Client->>API: Envoi Requête HTTP
    alt Réponse 200 OK
        API->>Client: Retourne les données
        Client->>React: Fournit les données
    else Réponse 401 Unauthorized (Token Expiré)
        API->>Client: Retourne 401
        Note over Client: Intercepte le 401, suspend la requête initiale
        Client->>API: Lance POST /auth/refresh (avec Cookie)
        alt Rafraîchissement réussi
            API->>Client: Retourne nouvel Access Token
            Note over Client: Enregistre le token en mémoire vive
            Note over Client: Rejoue la requête suspendue avec le nouveau token
            Client->>API: Envoi Requête HTTP (Nouveau Token)
            API->>Client: Retourne 200 OK
            Client->>React: Fournit les données (transparent pour l'utilisateur)
        else Rafraîchissement échoué (Session finie / Cookie expiré)
            API->>Client: Retourne 401
            Note over Client: Supprime le token de la mémoire vive
            Note over Client: Émet l'événement "auth-expired"
            Client->>React: Échec de la requête (Redirection vers Login)
        end
    end
React sequence diagram
```

### Configuration Clé :
- **`withCredentials: true`** : Indique à Axios d'inclure automatiquement le cookie HTTP-Only lors des requêtes vers le backend.

---

## 3. Contexte d'Authentification (`AuthContext.jsx`)

Le composant `AuthProvider` diffuse l'état d'authentification dans toute l'application React :
- **`user`** : Contient les données utilisateur de session (identifiant et rôle).
- **`isAuthenticated`** : Indicateur booléen dérivé de la présence de `user`.
- **`loading`** : Indicateur de chargement initial. Empêche le clignotement de l'interface au démarrage.

### Cycle de vie applicatif :

1. **Vérification au démarrage (Silent Refresh)** : 
   Au montage de l'application, un `useEffect` tente d'appeler silencieusement `/auth/refresh`. Si l'utilisateur possède un cookie valide, il récupère un nouvel Access Token et sa session est restaurée instantanément sans qu'il ait besoin de se reconnecter.
2. **Décodage du Token (JWT parser)** : 
   Une fonction utilitaire interne parse le payload base64 du JWT pour extraire le sujet (`sub`) et l'autorité (`role`), évitant ainsi d'ajouter des dépendances tierces lourdes comme `jwt-decode`.
3. **Gestion des Expirations globales** :
   Le contexte écoute l'événement global `auth-expired` émis par `apiClient.js` pour vider l'état `user` et forcer le retour à l'écran de connexion si la session est expirée (après 7 jours d'inactivité).

---

## 4. Protection des Routes (`ProtectedRoute.jsx`)

Le composant `ProtectedRoute` sert de garde-barrière pour l'accès aux pages :

- **Attente de session (`loading === true`)** : Affiche un Spinner de chargement neutre et sobre pour éviter les redirections brutales pendant que l'application interroge le serveur.
- **Vérification de Connexion** : Redirige vers `/` (Login) si l'utilisateur n'est pas connecté.
- **Contrôle d'accès par Rôles (`allowedRoles`)** : Permet de restreindre des zones spécifiques. Par exemple, si l'utilisateur n'a pas le rôle `ROLE_ADMIN`, il est redirigé vers l'écran d'accès refusé (`/non-autorise`).

### Configuration du routeur (`Router.jsx`) :

```javascript
<AuthProvider>
    <Routes>
        {/* Pages Publiques */}
        <Route path="/" element={<Login />} />
        <Route path="/non-autorise" element={<Unauthorized />} />

        {/* Pages Protégées Basiques (Connecté) */}
        <Route element={<ProtectedRoute />}>
            <Route path="/dashboard" element={<Dashboard />} />
        </Route>

        {/* Pages Protégées Admin (Connecté + Rôle ADMIN) */}
        <Route element={<ProtectedRoute allowedRoles={["ROLE_ADMIN"]} />}>
            <Route path="/admin" element={<AdminPanel />} />
        </Route>
        
        {/* wildcard 404 */}
        <Route path="*" element={<NotFound />} />
    </Routes>
</AuthProvider>
```

---

## 5. Bonnes Pratiques de Développement

Pour ajouter un nouvel appel API nécessitant une authentification, importez simplement le client configuré :

```javascript
import apiClient from '@/api/apiClient';

export const getDossiersEmployes = async () => {
    // Le token sera automatiquement injecté dans l'en-tête Authorization
    const response = await apiClient.get('/employe');
    return response.data;
};
```
