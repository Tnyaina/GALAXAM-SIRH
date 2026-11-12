# Config — infrastructure Spring

Ce dossier assemble les services techniques. Il ne possède pas de règle RH.

```mermaid
flowchart LR
    REQ[Requête] --> SEC[SecurityConfig]
    SEC --> JWT[JwtAuthenticationFilter]
    JWT --> API[Contrôleur métier]
    API --> DB[DatabaseConfig / DataSource]
    API --> ERR[GlobalExceptionHandler]
    NATIVE[Profil natif] --> TOMCAT[NativeTomcatConfig]
```

| Fichier | Méthodes | Usage et importance |
| --- | --- | --- |
| [`DatabaseConfig`](DatabaseConfig.java) | `dataSource`, `normalizeJdbcUrl` | **Critique** — construit le pool Hikari ; lit la configuration Spring puis `SPRING_DATASOURCE_URL` ou `DATABASE_URL`, normalise l'URL PostgreSQL et les identifiants éventuels. |
| [`SecurityConfig`](SecurityConfig.java) | `validateCorsConfiguration`, `securityFilterChain`, `roleHierarchy`, `corsConfigurationSource`, `passwordEncoder`, `authenticationManager` | **Critique** — CORS, routes publiques et admin, JWT, session sans état, rôles, BCrypt et authentification. |
| [`GlobalExceptionHandler`](GlobalExceptionHandler.java) | `handleSirhBusiness`, `handleValidationException`, `handleSqlException`, `handleAccessDenied`, autres `handle*`, `problem` | **Critique** — transforme les exceptions en `ApiErrorResponse` ; `problem` centralise la forme HTTP. Les annotations `@ExceptionHandler` donnent la liste complète des erreurs couvertes. |
| [`NativeTomcatConfig`](NativeTomcatConfig.java) | `nativeTomcatServletWebServerFactory` | **Support** — configure Tomcat NIO2 quand `galaxam.native.nio2=true`. |

`GlobalExceptionHandler` expose aussi `handleIllegalArgument`, `handleIllegalState`, `handleDataIntegrity`, `handleMaxUploadSize`, `handleTypeMismatch`, `handleMessageNotReadable`, `handleMethodNotSupported` et `handleGenericException` : chaque méthode adapte le type d'échec correspondant au contrat d'erreur HTTP. `handleSqlException` délègue au traitement privé `pointagePeriodConstraint` pour une contrainte spécifique de période.

Une nouvelle erreur métier commence dans `SirhErrorCode`. Un changement de route publique ou admin touche `SecurityConfig` et les contrôleurs concernés. Les propriétés de profils sont dans `backend/src/main/resources/application*.properties`.
