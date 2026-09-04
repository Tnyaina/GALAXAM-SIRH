# Documentation Sécurité - Authentification & Autorisation (JWT + Refresh Token)

Ce document décrit l'architecture de sécurité mise en place pour l'application web SIRH (Logistique).

---

## 1. Choix Stratégiques d'Architecture

L'authentification est entièrement **stateless** (sans état côté serveur) pour garantir la scalabilité de l'API REST. Elle combine deux types de tokens :

| Élément | Stockage côté Client | Durée de vie | Rôle & Sécurité |
| :--- | :--- | :--- | :--- |
| **Access Token (JWT)** | Mémoire vive (React) | **15 minutes** | Transmis dans l'en-tête `Authorization: Bearer <token>`. Non persistant dans LocalStorage pour bloquer les attaques **XSS**. |
| **Refresh Token (JWT)** | Cookie `HTTP-Only` | **7 jours** | Cookie avec `SameSite=Strict`, `Secure` et chemin `/api/auth`. Utilisé uniquement pour renouveler l'Access Token sans intervention de l'utilisateur. Protégé contre le **CSRF** et les scripts malicieux. |

---

## 2. Intégration de la Base de Données (Active Record)

La sécurité s'appuie sur le modèle Active Record existant (classes étendant `ClassMere` sans magie ORM) :
- **Authentification & Journalisation** : Lors de la tentative de login, `Utilisateur.rechercherParIdentifiant` est appelé pour valider le mot de passe (haché en BCrypt). Un journal d'accès est inséré dans `journal_connexion` via une transaction JDBC gérée manuellement par le contrôleur (`c.commit()` / `c.rollback()`).
- **Chargement des Rôles** : Le `CustomUserDetailsService` charge les rôles de l'utilisateur en récupérant l'entité `RefRole` correspondante via `findById(idRole)`.

---

## 3. Configuration de Spring Security

### Cycle de Validation d'une Requête

```mermaid
sequenceDiagram
    participant React as Frontend (React)
    participant Filter as JwtAuthenticationFilter
    participant Security as Spring Security Context
    participant Controller as Contrôleurs API

    React->>Filter: Requête HTTP (Authorization: Bearer <Access Token>)
    alt Token présent & valide
        Filter->>Security: Injecte Authentication (UserDetails, Rôles)
        Security->>Controller: Autorise l'accès à la route
        Controller->>React: Réponse HTTP 200 OK
    else Token absent / invalide / expiré
        Filter->>Security: Ne fait rien (Contexte vide)
        Security->>React: Bloque avec HTTP 401 Unauthorized / 403 Forbidden
    end
Sequence Diagram
```

### Règles d'Autorisation de Base

Définies dans `SecurityConfig.java` :
- **Accès Public** : Les endpoints `/auth/login`, `/auth/register`, `/auth/refresh`, `/auth/logout`, `/uploads/photos/**` (photos d'avatar) et `/error` sont ouverts.
- **Protection des Documents Confidentiels** : Les fichiers déposés sous `/uploads/documents/**` requièrent obligatoirement une authentification valide (`JWT`).
- **Rôle Administrateur** : Les endpoints sous `/admin/**` requièrent l'autorité `ROLE_ADMIN`.
- **Rôle Pointage** : Les endpoints sous `/pointage/**` requièrent soit `ROLE_ADMIN`, soit `ROLE_AGENT_POINTAGE`.
- **Annotation Method Security** : `@EnableMethodSecurity` est activé pour permettre des restrictions plus fines au niveau des méthodes des contrôleurs via l'annotation `@PreAuthorize("hasAuthority('...')")`.

---

## 4. Endpoints d'Authentification

Toutes les routes d'authentification ont pour préfixe de base `/api` (défini par `server.servlet.context-path` dans les propriétés globales).

### Login (`POST /api/auth/login`)
- **Corps de la requête (JSON)** :
  ```json
  {
    "identifiant": "admin",
    "motDePasse": "MotDePasse123"
  }
  ```
- **Persistance en Base** : Le refresh token généré et sa date d'expiration exacte sont enregistrés dans les colonnes `refresh_token` et `refresh_token_expiration` de la table `utilisateur`.
- **Réponse HTTP 200 (JSON)** :
  ```json
  {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "expiresIn": 900,
    "identifiant": "admin",
    "role": "ROLE_ADMIN"
  }
  ```
- **En-tête de réponse** : Positionne le cookie `refreshToken=...; Path=/api/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict`.

### Rafraîchissement avec Rotation (`POST /api/auth/refresh`)
- **Action** : Reçoit automatiquement le cookie contenant le refresh token.
- **Contrôle BDD & Rotation** : Vérifie l'existence active du token en base (`rechercherParRefreshToken`), invalide l'ancien et génère un nouveau couple Access Token + Refresh Token (rotation de sécurité).
- **Réponse HTTP 200 (JSON)** :
  ```json
  {
    "accessToken": "nouveau_jwt_token..."
  }
  ```
- **Réponse HTTP 401** : Si le refresh token est absent, expiré ou révoqué en base.

### Déconnexion (`POST /api/auth/logout`)
- **Action** : Révoque immédiatement le refresh token en base (`revoquerRefreshToken`) et supprime le cookie HTTP-Only (`Max-Age=0`).

---

## 5. Propriétés Configurables

Les variables de configuration de sécurité dans `application.properties` sont protégées par des valeurs par défaut robustes mais surchargeables via des variables d'environnement en production :

```properties
# Clé secrète de signature (HMAC-SHA-512) encodée en Base64 (à surcharger obligatoirement en production)
app.security.jwt.secret=${JWT_SECRET:dGhpcy1pcy1hLXNlY3VyZS1hbmQtc3VmZmljaWVudGx5LWxvbmctc2VjcmV0LWtleS1mb3Itand0LXNpZ25pbmctYnktYW50aWdyYXZpdHk=}

# Durée de vie de l'Access Token (15 minutes en millisecondes)
app.security.jwt.access-token-expiration-ms=${JWT_ACCESS_EXPIRATION_MS:900000}

# Durée de vie du Refresh Token (7 jours en millisecondes)
app.security.jwt.refresh-token-expiration-ms=${JWT_REFRESH_EXPIRATION_MS:604800000}
```

---

## 6. Guide d'Intégration Frontend (React JS)

Pour consommer cette API de façon transparente et sécurisée côté React, il est fortement recommandé d'utiliser une instance d'**Axios** avec des **intercepteurs**.

### Structure suggérée pour le client Axios (`api.js`)

```javascript
import axios from 'axios';

// Variables mémoires pour garder le token hors de portée XSS
let _accessToken = null;

export const setAccessToken = (token) => {
  _accessToken = token;
};

const api = axios.create({
  baseURL: 'http://localhost:8080/api',
  withCredentials: true, // Crucial pour inclure le Cookie HTTP-Only dans les requêtes
});

// Intercepteur pour attacher l'Access Token s'il est présent en mémoire
api.interceptors.request.use(
  (config) => {
    if (_accessToken) {
      config.headers['Authorization'] = `Bearer ${_accessToken}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Intercepteur pour gérer l'expiration de l'Access Token (Erreur 401)
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    
    // Si l'erreur est une 401 et qu'on n'a pas déjà tenté de rafraîchir
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      
      try {
        // Appel de la route silencieuse de refresh
        const res = await axios.post('http://localhost:8080/api/auth/refresh', {}, { withCredentials: true });
        const { accessToken } = res.data;
        
        setAccessToken(accessToken);
        originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;
        
        // Re-tente la requête initiale avec le nouveau token
        return api(originalRequest);
      } catch (refreshError) {
        // Si le refresh échoue (ex: refresh token expiré après 7 jours), l'utilisateur doit se reconnecter
        _accessToken = null;
        window.location.href = '/login';
        return Promise.reject(refreshError);
      }
    }
    return Promise.reject(error);
  }
);

export default api;
```
