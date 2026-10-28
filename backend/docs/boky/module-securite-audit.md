# GALAXAM SIRH — Module Sécurité, IAM, RBAC & Piste d'Audit
## Document Canonique — Mis à jour Octobre 2026

> **Références d'implémentation** : `securite/api/AuthController.java`, `config/SecurityConfig.java`, `securite/IdleSessionService.java`, `frontend/src/context/AuthContext.jsx` et `backend/conception/bdd/01_schema_core_securite.sql`.
> **Cadre Juridique** : Loi n° 2014-038 (Protection des données personnelles, Madagascar) — NIST SP 800-107 (HS512) — OWASP API Security Top 10.

---

## Corrections par rapport à l'ancienne documentation

| Élément | Ancienne doc (ERRONÉE) | Réalité du code |
|---|---|---|
| Rôles seeedés | 5 rôles (ADMIN, DAF, RH, MANAGER, SALARIE) | **2 rôles** : `ROLE_ADMIN` et `ROLE_AGENT_POINTAGE` |
| Table `utilisateur` | Pas de champ `email` mentionné | `email VARCHAR(254)` présent + index `LOWER(email)` |
| Modules seeedés | Module `ADMIN` | Module `ORGANISATION` (code=5, id=5) |
| `soumis_isi_5` intervenant | ISI 5% actif pour CONSULTANT | `CHECK (soumis_isi_5 = FALSE)` — contrainte SQL bloquant toute valeur TRUE |
| CONSULTANT (type intervenant) | soumis_sante=OUI, +60 ans | `soumis_sante=FALSE`, `soumis_cnaps=FALSE`, `soumis_irsa=FALSE` |
| Matrice permissions DAF/RH/MANAGER | Décrite en détail | **Aucune ligne** — seuls ROLE_ADMIN (tout TRUE) et ROLE_AGENT_POINTAGE seedés |
| `scheduler_execution` | Non documentée | Table avec verrou distribué `(job_code, execution_key)` PK |
| Menus seeedés | Liste partielle | **25 menus total** (6 niv.1 + 19 niv.2) seedés dans `ref_menu` et rattachés à `ROLE_ADMIN` |

**Rôles présents dans le seed :** `ROLE_ADMIN` et `ROLE_AGENT_POINTAGE`. Des rôles supplémentaires peuvent être créés par l'administration ; aucun rôle technique DAF ou RH n'est seedé. Les tests repérés ne vérifient pas toutes les combinaisons de rôles et de permissions configurables.

**Contrôle ciblé des deux rôles seedés :** `ROLE_AGENT_POINTAGE` conserve lecture et écriture du module Pointage sans validation et sans accès PAIE. La liste des dérogations d'indemnités par salarié (`GET /pointages/parametres/derogations`) est réservée à `PAIE_READ` ou `ROLE_ADMIN`, car elle expose des exceptions de rémunération nominatives. Les tests backend de `PointageDerogationAuthorizationTest` vérifient le refus de l'agent et l'accès des autorités PAIE et ADMIN.

**Paramètres globaux :** leur consultation, création, modification, activation/désactivation et annulation programmée sont réservées à `ROLE_ADMIN`, y compris par appel API direct. Les autorisations `PAIE_READ`, `PAIE_WRITE`, `ORGANISATION_WRITE` et `POINTAGE_WRITE` ne suffisent pas. Cette règle ne bloque pas les API de lecture des référentiels Organisation nécessaires aux parcours métier (par exemple le chargement des sites de la grille Pointage) ; leurs mutations restent protégées par `ORGANISATION_WRITE` ou `ROLE_ADMIN`. `ParametreGlobalAuthorizationTest` vérifie les expressions d'accès de toutes les routes du contrôleur de paramètres.

---

## 1. Principes Directeurs de Sécurité

La gestion RH manipule des données hautement sensibles : identités civiles (CIN), données bancaires (RIB), salaires, motifs de rupture contractuelle. Dans le contexte multi-sites de GALAXAM, le système applique trois principes fondamentaux :

1. **API sans session servlet** : les requêtes protégées utilisent un access token JWT signé HMAC-SHA512. L'état des familles de refresh tokens et de leur inactivité est néanmoins conservé en base dans `utilisateur_session`.
2. **Contrôles d'autorisation** : `SecurityConfig` ouvre explicitement quelques routes, réserve `/auth/register`, `/admin/**` et `/securite/**` à `ROLE_ADMIN`, puis exige une authentification pour le reste. Les contrôleurs concernés ajoutent des contrôles `@PreAuthorize` ou des vérifications métier selon l'opération ; les routes publiques d'authentification utilisent leurs propres validations.
3. **Piste d'audit conservée par le cycle applicatif** : `audit_log` est liée par `ON DELETE SET NULL`. `AuditLog` refuse modification et suppression via son modèle, et `Utilisateur.delete` refuse la suppression physique d'un compte. Ces règles ne rendent pas la table immuable face à des écritures SQL directes ; une suppression SQL de l'utilisateur supprimerait les lignes de `journal_connexion` (`ON DELETE CASCADE`) et détacherait ses lignes d'audit.

Les écritures métier qui nécessitent un auteur exigent une identité authentifiée dans le contexte Spring Security. En l'absence de cette identité, la requête est refusée ; aucun identifiant d'administrateur par défaut n'est attribué. L'octroi automatique de toutes les permissions est réservé au code de rôle exact `ROLE_ADMIN`.

### 1.1 Session et expiration pour inactivité

Chaque connexion crée une famille de refresh tokens identifiée par `session_id`
dans `utilisateur_session`. Cette table est distincte du compte afin que les
connexions concurrentes d'un même utilisateur aient chacune leur propre état
d'inactivité. La durée est configurable par
`app.security.session.idle-timeout-seconds` (valeur par défaut : 900 secondes).
Le backend initialise/complète la table de façon idempotente au démarrage et
importe les anciens refresh tokens encore valides; le paquet desktop conserve
ses six scripts SQL canoniques (`01` à `06`).

Le frontend ne signale que des interactions explicites (clic, saisie,
interaction tactile, formulaire), avec limitation de fréquence. Mouvement
continu de souris, appels API, renouvellement JWT, timers et tâches automatiques
ne prolongent pas le délai. L'échéance est recalculée après retour de focus ou
reprise de page. Les onglets d'un même navigateur partagent la date d'échéance
et coordonnent les renouvellements.

Le backend conserve `last_activity_at`; l'endpoint authentifié `/auth/activity`
ne l'avance que si la famille est encore active et que le compte est actif.
Chaque requête protégée vérifie la session liée au claim `sid`, et le refresh
effectue une rotation sans renouveler l'activité. À l'échéance, la session est
révoquée et ses refresh tokens sont invalidés; un access token signé encore
valide ne suffit donc pas à rétablir l'accès. La déconnexion manuelle révoque
la famille courante, et la désactivation du compte révoque les familles de cet
utilisateur.

Le refresh token courant est conservé dans `utilisateur_session`, avec le token
précédent et la date de rotation. Un renouvellement normal remplace le token
courant. Pendant les dix secondes suivant cette rotation, une requête
concurrente portant l'ancien token peut récupérer le nouveau token de la même
famille ; elle ne prolonge pas l'activité. Les colonnes `refresh_token` du compte
`utilisateur` servent à l'import des anciens tokens au démarrage.

Dans React, l'access token reste en mémoire dans `apiClient`. `AuthContext`
conserve dans `localStorage` les informations utilisateur décodées du JWT
(`galaxam-user`) et l'échéance d'inactivité partagée entre onglets ; le refresh
token demeure dans le cookie HttpOnly. Le client tente un renouvellement avant
l'expiration de l'access token, au besoin à la reprise, et coordonne les
renouvellements concurrents. Ces contrôles d'interface n'accordent aucune
permission côté serveur.

Les classes concernées sont `securite/IdleSessionService.java`,
`securite/JwtAuthenticationFilter.java`, `securite/JwtService.java` et
`securite/api/AuthController.java`; côté React,
`context/idleSession.js`, `context/AuthContext.jsx` et `api/apiClient.js`.
Les tests temporels du service nécessitent PostgreSQL de test isolé via
`IDLE_TEST_JDBC_URL`; ils ne prouvent pas à eux seuls une recette complète en
navigateur ou en application Tauri.

---

## 2. Schéma Physique de la Base de Données (Code réel)

### 2.1 Table `ref_role`
```sql
CREATE TABLE ref_role (
    id_role            SERIAL PRIMARY KEY,
    code_role          VARCHAR(50) UNIQUE NOT NULL,
    libelle            VARCHAR(100) NOT NULL,
    est_actif          BOOLEAN DEFAULT TRUE,
    date_desactivation TIMESTAMP,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Seed réel (2 rôles uniquement) :**

| id_role | code_role | libelle |
|---|---|---|
| 1 | `ROLE_ADMIN` | Administrateur |
| 2 | `ROLE_AGENT_POINTAGE` | Agent de Pointage |

> Les rôles DAF, RH, MANAGER, SALARIE sont absents du seed. Ils doivent être créés par l'administrateur via `/admin/securite?tab=roles`. Le schéma associe un seul `id_role` à un utilisateur ; les droits de lecture, écriture et validation restent portés par `role_permission`.

### 2.2 Table `ref_module` — 5 modules seeedés

| id_module | code_module | Libellé |
|---|---|---|
| 1 | `EMPLOYE` | Gestion des dossiers employés et contrats |
| 2 | `CONGE` | Gestion des absences et congés |
| 3 | `POINTAGE` | Suivi des présences et horaires |
| 4 | `PAIE` | Moteur de paie et bulletins |
| 5 | `ORGANISATION` | Structure d'entreprise, filiales, sites et fonctions |

### 2.3 Matrice `role_permission` — Seed réel

| Rôle | EMPLOYE | CONGE | POINTAGE | PAIE | ORGANISATION |
|---|:---:|:---:|:---:|:---:|:---:|
| `ROLE_ADMIN` (id=1) | R/W/V | R/W/V | R/W/V | R/W/V | R/W/V |
| `ROLE_AGENT_POINTAGE` (id=2) | - | - | R+W (pas V) | - | - |

### 2.4 Table `utilisateur`
```sql
CREATE TABLE utilisateur (
    id_utilisateur           SERIAL PRIMARY KEY,
    identifiant              VARCHAR(100) NOT NULL,
    email                    VARCHAR(254) NOT NULL,
    mot_de_passe_hache       VARCHAR(255) NOT NULL,   -- BCrypt
    id_role                  INTEGER NOT NULL REFERENCES ref_role(id_role),
    est_actif                BOOLEAN DEFAULT TRUE,
    date_desactivation       TIMESTAMP,
    motif_desactivation      VARCHAR(100),
    refresh_token            VARCHAR(500),
    refresh_token_expiration TIMESTAMP,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index réels
CREATE UNIQUE INDEX idx_utilisateur_email_lower       ON utilisateur (LOWER(email));
CREATE INDEX        idx_utilisateur_identifiant_lower ON utilisateur (LOWER(identifiant));
CREATE INDEX        idx_utilisateur_refresh_token     ON utilisateur (refresh_token) WHERE refresh_token IS NOT NULL;
```

`id_role` est une colonne unique sur le compte : le modèle actuel associe un seul
rôle à chaque utilisateur. L'email est unique sans distinction de casse ;
`identifiant` dispose d'un index `LOWER` non unique et ne doit donc pas être
présenté comme unique par la base.

La désactivation, la réactivation et le changement de mot de passe par un
administrateur effacent `refresh_token` et
`refresh_token_expiration`. L'ancien cookie ne peut pas être rafraîchi après
ces opérations ; l'utilisateur doit se reconnecter. Le filtre JWT vérifie que le
compte chargé est actif et que la session `sid` du jeton n'est pas révoquée à
chaque requête. La désactivation et le changement de mot de passe révoquent ces
sessions en base ; un ancien access token reste refusé. Une nouvelle connexion
est nécessaire.

Les appels `/auth/refresh` et `/auth/logout`, qui utilisent le cookie de session,
exigent une origine explicitement autorisée par `app.cors.allowed-origins`.
L'en-tête `Origin` est prioritaire ; `Referer` sert de repli. Une origine absente
ou non autorisée est refusée. L'écriture d'un événement d'audit remonte une
erreur en cas d'échec ; les opérations métier transactionnelles sont annulées
par leur gestionnaire de transaction.

`journal_connexion.id_utilisateur` référence actuellement `utilisateur` avec
`ON DELETE CASCADE`, contrairement à `audit_log`. L'API applicative interdit
la suppression physique pour préserver ces traces ; un `DELETE` SQL direct
reste dangereux et n'est pas une procédure d'administration supportée.

### 2.5 Table `audit_log`
```sql
CREATE TABLE audit_log (
    id_audit          SERIAL PRIMARY KEY,
    id_utilisateur    INT REFERENCES utilisateur(id_utilisateur) ON DELETE SET NULL,
    action            VARCHAR(50) NOT NULL,
    nom_table         VARCHAR(50) NOT NULL,
    id_enregistrement VARCHAR(100) NOT NULL,
    anciennes_valeurs JSONB,
    nouvelles_valeurs JSONB,
    adresse_ip        VARCHAR(45),
    date_action       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

> `ON DELETE SET NULL` conserve la ligne d'audit mais perd son lien direct à l'utilisateur. La suppression physique est refusée par `Utilisateur.delete` ; la politique normale est la désactivation. Attention : `journal_connexion.id_utilisateur` est déclaré `ON DELETE CASCADE`, donc une suppression SQL directe détruirait les journaux de connexion.

Le login consigne les statuts `SUCCES`, `ECHEC` et `COMPTE_DESACTIVE` lorsque
le compte est connu. Une tentative avec email inconnu n'a pas de `id_utilisateur`
à rattacher et ne peut pas être auditée par le modèle actuel de
`journal_connexion`.

La route de connexion limite dix échecs par email normalisé sur quinze minutes.
Le compteur en mémoire est borné et se réinitialise après connexion réussie ;
il convient à l'instance unique de `docker-compose.web.yml`. Une architecture
multi-instance doit ajouter une limite partagée. `/auth/ping` exécute `SELECT 1`
et renvoie `503` si la base est inaccessible, afin de servir de contrôle de
disponibilité.

### 2.6 Table `scheduler_execution` (registre de tentatives et bail)
```sql
CREATE TABLE scheduler_execution (
    job_code      VARCHAR(80) NOT NULL,
    execution_key DATE NOT NULL,
    status        VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    started_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at  TIMESTAMP NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    error_message TEXT NULL,
    attempt_token UUID,
    lease_until   TIMESTAMP NULL,
    CHECK ((status = 'RUNNING' AND attempt_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_until IS NULL)),
    PRIMARY KEY (job_code, execution_key)
);
```

L'acquisition insère atomiquement la clé `(job_code, execution_key)`. Une
tentative `RUNNING` possède un UUID et un bail renouvelé périodiquement ; après
expiration, une autre instance peut reprendre la clé. Seul le jeton courant
peut renouveler le bail ou terminer l'enregistrement. Cela protège le registre,
mais n'arrête pas un worker qui continue déjà son traitement métier : les effets
non répétables doivent aussi être idempotents ou protégés dans leur transaction.
Le bail par défaut est de 30 minutes, avec heartbeat toutes les 10 minutes et
une limite de renouvellement à 23 heures.

---

## 3. Arborescence des Menus Seedés (25 menus réels)

**Niveau 1 (6 racines) :**

| id_menu | Libellé | Route | Rang |
|---|---|---|:---:|
| `DASHBOARD` | Tableau de bord | `/dashboard` | 1 |
| `GESTION_PERSONNEL` | Gestion du Personnel | *(parent)* | 2 |
| `CONGES_MODULE` | Congés & Absences | `/conges` | 3 |
| `POINTAGE` | Pointage & Présences | `/pointages` | 4 |
| `PAIE_MODULE` | Paie & Rémunération | *(parent)* | 5 |
| `ADMIN_MODULE` | Administration & Paramétrage | *(parent)* | 6 |

**Niveau 2 (19 enfants) :**

| id_menu | Libellé | Route | Parent | Rang |
|---|---|---|---|:---:|
| `PERS_EMPLOYES` | Fiches Employés | `/employes` | `GESTION_PERSONNEL` | 1 |
| `PERS_CARRIERES` | Contrats & Carrières | `/carrieres` | `GESTION_PERSONNEL` | 2 |
| `CONGES_GENERAL` | Vue Générale | `/conges?tab=planning` | `CONGES_MODULE` | 1 |
| `CONGES_DEMANDES` | Demandes & Validation | `/conges?tab=demandes` | `CONGES_MODULE` | 2 |
| `CONGES_PERIODES` | Contrôle des droits | `/conges?tab=periodes` | `CONGES_MODULE` | 3 |
| `POINTAGE_JOURNALIER` | Saisie Quotidienne | `/pointages?tab=saisie` | `POINTAGE` | 1 |
| `POINTAGE_SUIVI` | Suivi & Anomalies | `/pointages?tab=suivi` | `POINTAGE` | 2 |
| `POINTAGE_VALIDATION` | Contrôle & Validation | `/pointages?tab=validation` | `POINTAGE` | 3 |
| `PAIE_CENTRE` | Centre de paie | `/paie` | `PAIE_MODULE` | 1 |
| `PAIE_CAMPAGNE` | Cycles de paie | `/paie?tab=periodes` | `PAIE_MODULE` | 2 |
| `PAIE_ACOMPTES` | Acomptes & avances | `/paie/acomptes-avances` | `PAIE_MODULE` | 3 |
| `PAIE_PRESTATAIRES` | Prestataires indépendants | `/paie/prestataires` | `PAIE_MODULE` | 4 |
| `PAIE_DOSSIERS_SORTIE` | Dossiers de sortie & STC | `/paie/dossiers-sortie` | `PAIE_MODULE` | 5 |
| `PAIE_DECLARATIONS` | Déclarations & sorties | `/paie/declarations` | `PAIE_MODULE` | 6 |
| `PAIE_CONFIGURATION` | Configuration de paie | `/paie/configuration` | `PAIE_MODULE` | 7 |
| `ADMIN_SECURITE` | Sécurité & Accès | `/admin/securite` | `ADMIN_MODULE` | 1 |
| `ADMIN_ORGANISATION` | Organisation & Structure | `/admin/organisation` | `ADMIN_MODULE` | 2 |
| `ADMIN_REFERENTIELS` | Référentiels RH | `/admin/referentiels-rh` | `ADMIN_MODULE` | 3 |
| `ADMIN_PARAMETRES` | Paramétrage des Modules | `/admin/parametres` | `ADMIN_MODULE` | 4 |

**Habilitations seed :**
- `ROLE_ADMIN` (id=1) : Accès complet aux 25 menus (6 racines + 19 sous-menus).
- `ROLE_AGENT_POINTAGE` (id=2) : Accès strict à l'exploitation pointage (`POINTAGE`, `POINTAGE_JOURNALIER`, `POINTAGE_SUIVI`, `POINTAGE_VALIDATION`). La page de validation reste accessible pour consultation et préparation ; les commandes sensibles restent filtrées par `droit_validation` côté API et interface.

---

## 4. Interface Frontend — Panel Sécurité

**Page** : [`SecuriteAdminPanel.jsx`](../../../frontend/src/features/administration/pages/securite/SecuriteAdminPanel.jsx)
**Route** : `/admin/securite` — Navigation par `?tab=` query param.

| Onglet (`tab=`) | Composant (lazy) | Fonctionnalités |
|---|---|---|
| `users` (défaut) | `UserManagementTab.jsx` | CRUD comptes utilisateurs. Formulaire : `identifiant`, `email`, `motDePasse`, `confirmPassword`, `idRole`. Désactivation avec motif obligatoire. |
| `roles` | `RolesAndPermissionsTab.jsx` | Gestion rôles + matrice R/W/V par module + attribution menus par rôle. |
| `menus` | `MenuManagementTab.jsx` | CRUD arborescence de navigation niv.1 et niv.2. |

Le formulaire peut transmettre un nouveau `motDePasse` lors de la modification
d'un compte. `UtilisateurController.updateUser` le vérifie puis le hache via
`Utilisateur.controler` avant enregistrement. Si un nouveau mot de passe non
vide est fourni, la même transaction efface les colonnes de refresh héritées du
compte et révoque toutes ses familles via `IdleSessionService.revokeAllForUser`.
Les access tokens déjà émis sont alors refusés par le filtre JWT, qui vérifie
la session `sid` à chaque requête protégée. Une modification du compte sans
nouveau mot de passe ne déclenche pas cette révocation. Le test ciblé
`UtilisateurPasswordChangeTest` vérifie ces deux cas et l'annulation de la
transaction si la révocation échoue ; le test PostgreSQL de révocation des
familles nécessite une base de test isolée.

---

## 5. Catalogue des Endpoints API (Code réel)

| Action | Méthode | Endpoint | Habilitation |
|---|:---:|---|---|
| **Authentification & Session (`/auth`)** | | | |
| Disponibilité API + base | GET | `/auth/ping` | Public |
| Connexion (login) | POST | `/auth/login` ← `{email, motDePasse}` | Public |
| Rafraîchissement JWT | POST | `/auth/refresh` ← Cookie HttpOnly `refreshToken` | Public |
| Déconnexion (logout) | POST | `/auth/logout` ← Cookie HttpOnly + origine autorisée | Public (session par cookie) |
| Prolongation d'activité | POST | `/auth/activity` ← Header Bearer | Authentifié |
| Arbre des menus autorisés | GET | `/auth/menu` | Authentifié |
| Création compte direct | POST | `/auth/register` ← `{identifiant, email, motDePasse, idRole}` | `ROLE_ADMIN` |
| **Utilisateurs (`/securite/utilisateurs`)** | | | |
| Liste des utilisateurs | GET | `/securite/utilisateurs` | `ROLE_ADMIN` |
| Utilisateur par ID | GET | `/securite/utilisateurs/{id}` | `ROLE_ADMIN` |
| Créer utilisateur | POST | `/securite/utilisateurs` | `ROLE_ADMIN` |
| Modifier utilisateur (dont mot de passe si fourni) | PUT | `/securite/utilisateurs/{id}` | `ROLE_ADMIN` |
| Désactiver utilisateur | PUT | `/securite/utilisateurs/{id}/desactiver` ← body `{motifDesactivation}` | `ROLE_ADMIN` |
| Réactiver utilisateur | PUT | `/securite/utilisateurs/{id}/activer` | `ROLE_ADMIN` |
| **Rôles (`/referentiel/roles`)** | | | |
| Liste des rôles | GET | `/referentiel/roles` | Authentifié |
| Rôle par ID | GET | `/referentiel/roles/{id}` | Authentifié |
| Créer rôle | POST | `/referentiel/roles` | `ROLE_ADMIN` |
| Modifier rôle | PUT | `/referentiel/roles/{id}` | `ROLE_ADMIN` |
| Désactiver rôle | PUT | `/referentiel/roles/{id}/desactiver` | `ROLE_ADMIN` |
| Activer rôle | PUT | `/referentiel/roles/{id}/activer` | `ROLE_ADMIN` |
| **Permissions (`/referentiel/roles/{id}/permissions`)** | | | |
| Lire permissions rôle | GET | `/referentiel/roles/{id}/permissions` | Authentifié |
| Mettre à jour permissions | POST | `/referentiel/roles/{id}/permissions` ← body `{permissions}` | `ROLE_ADMIN` |
| **Menus (`/referentiel/menus`)** | | | |
| Lire menus rôle | GET | `/referentiel/roles/{id}/menus` | Authentifié |
| Mettre à jour menus rôle | POST | `/referentiel/roles/{id}/menus` ← body `{idMenus}` | `ROLE_ADMIN` |
| Menus (arbre hiérarchique) | GET | `/referentiel/menus` | Authentifié |
| Menus (liste plate) | GET | `/referentiel/menus/flat` | Authentifié |
| Créer menu | POST | `/referentiel/menus` | `ROLE_ADMIN` |
| Modifier menu | PUT | `/referentiel/menus/{id}` | `ROLE_ADMIN` |
| Supprimer menu | DELETE | `/referentiel/menus/{id}` | `ROLE_ADMIN` |
| **Modules (`/referentiel/modules`)** | | | |
| Liste des modules SIRH | GET | `/referentiel/modules` | Authentifié |
| **Support Desktop & Cycle de Vie (`/desktop` & `/admin`)** | | | |
| Amorçage admin initial | POST | `/desktop/setup/admin` (Profile `desktop`, boucle locale, capability) | Capability Bearer |
| Arrêt gracieux service | POST | `/desktop/internal/shutdown` (Profile `desktop`, token SCM supervisor) | Token interne |
| Autorisation backup natif | GET | `/admin/desktop-backup/authorize` (Confirme session administrateur vivante) | `ROLE_ADMIN` via `/admin/**` |

**Cache frontend** : `referentielService.js` cache `modules`, `roles`, `menusFlat`, `menusTree`. Toute mutation appelle `invalidateReferentielCache()` + `notifyMenuChanged()` (bus d'événements pour rafraîchir la navigation immédiatement).

---

## 6. Cycle d'Authentification JWT

```mermaid
sequenceDiagram
    autonumber
    actor Client as Navigateur (React)
    participant Auth as AuthController (/auth/login)
    participant DB as PostgreSQL
    participant JWT as JwtService (HS512)

    Client->>Auth: POST /auth/login {email, motDePasse}
    Auth->>DB: SELECT utilisateur par LOWER(email)
    alt Email inconnu
        Auth-->>Client: Erreur d'authentification (aucun journal lié à un utilisateur)
    else Compte désactivé
        Auth->>DB: INSERT journal_connexion (COMPTE_DESACTIVE)
        Auth-->>Client: Accès refusé
    else Mot de passe incorrect
        Auth->>Auth: Vérifier le mot de passe avec BCrypt
        Auth->>DB: INSERT journal_connexion (ECHEC)
        Auth-->>Client: Erreur d'authentification
    else Compte actif et mot de passe valide
        Auth->>Auth: Vérifier le mot de passe avec BCrypt
        Auth->>DB: INSERT journal_connexion (SUCCES)
        Auth->>JWT: Générer refresh token HS512
        Auth->>DB: INSERT utilisateur_session (session_id, refresh_token)
        Auth->>JWT: Générer access token HS512 avec sid
        Auth-->>Client: 200 {accessToken} + cookie HttpOnly refreshToken
    end
```

---

## 7. Contrôles techniques observés

| Domaine | Implémentation observable et limite |
|---|---|
| **Conservation applicative des traces** | `AuditLog` refuse modification et suppression via le modèle ; `audit_log.id_utilisateur` utilise `ON DELETE SET NULL`. Ces faits ne constituent pas à eux seuls une certification légale ou une protection contre les écritures SQL directes. |
| **Signature des JWT** | HMAC-SHA512 ; `JwtService.validateJwtSecret` refuse au démarrage une clé décodée de moins de 64 octets. |
| **Contrôles d'accès aux données** | Permissions et contrôles métier dépendent des endpoints ; leur couverture complète n'est pas démontrée par ce document. |
| **Requêtes SQL du module** | Les requêtes JDBC examinées dans les parcours d'authentification et de comptes emploient des `PreparedStatement` pour les valeurs variables. Ce constat ne couvre pas toutes les requêtes du projet. |
| **BCrypt** | Hachage adaptatif des mots de passe |
| **SessionSecurityScheduler** | Nettoyage nocturne des refresh tokens expirés ; son exécution est inscrite dans `scheduler_execution` |

---

## 8. Catalogue des Règles de Gestion de Sécurité & IAM (RG)

| Code Règle | Énoncé Formel de la Règle | Fondement & Standard | Contrôle & Comportement Système |
|:---|:---|:---|:---|
| **RG-SEC-01** | **Authentification Stateless JWT (HS512)** | NIST SP 800-107 & OWASP Top 10 | Signature cryptographique HMAC-SHA512 avec clé secrète $\ge 64$ octets vérifiée au démarrage (`JwtService`). Les requêtes HTTP ne créent aucune session servlet côté serveur. |
| **RG-SEC-02** | **Expiration d'Inactivité Glissante (900s)** | Norme de sécurité des sessions bancaires/RH | Session invalidée après 900 secondes (paramètre `idle-timeout-seconds`) sans interaction explicite (clics, frappes). Les requêtes API en arrière-plan et les timers ne prolongent pas le délai. |
| **RG-SEC-03** | **Rotation des Refresh Tokens** | Implémentation de `IdleSessionService` | Un renouvellement remplace le token courant dans `utilisateur_session`. Le token précédent peut servir pendant dix secondes à récupérer le token déjà émis lors d'une requête concurrente, sans prolonger l'activité. |
| **RG-SEC-04** | **Révocation des Sessions** | `IdleSessionService` et `UtilisateurController` | La déconnexion manuelle (`POST /auth/logout`) révoque la famille courante ; la désactivation ou le changement de mot de passe par un administrateur révoque toutes les familles du compte dans la même transaction que la mise à jour. |
| **RG-SEC-05** | **Politique et Hachage des Mots de Passe** | `Utilisateur.controler` | Hachage BCrypt. Le mot de passe fourni doit contenir au moins 8 caractères, une majuscule, une minuscule et un chiffre. |
| **RG-SEC-06** | **Contrôles d'autorisation (RBAC)** | `SecurityConfig` et contrôleurs | Filtrage des URI publiques, administrateur et authentifiées par Spring Security ; contrôles `@PreAuthorize` ou vérifications métier selon les endpoints. Une permission affichée dans React ne remplace pas le contrôle serveur. |
| **RG-SEC-07** | **Arborescence Dynamique Filtrée par Rôle** | Moindre privilège d'interface | L'API `GET /auth/menu` ne renvoie au client React que les menus et sous-menus (parmi les 25 du référentiel `ref_menu`) explicitement habilités pour le rôle de l'utilisateur dans `role_menu`. |
| **RG-SEC-08** | **Conservation applicative de la Piste d'Audit** | `AuditLog`, `Utilisateur` et schéma SQL | Le modèle `AuditLog` refuse modification et suppression ; `Utilisateur.delete` refuse la suppression physique. `ON DELETE SET NULL` conserve les lignes d'audit si un compte est supprimé directement en SQL, sans garantir l'immutabilité de la table. |
| **RG-SEC-09** | **Bootstrap Sécurisé de l'Administrateur** | Sécurité d'initialisation en environnement Desktop | L'endpoint `/desktop/setup/admin` exige un jeton de capacité éphémère à usage unique généré par le superviseur natif, et refuse toute création si un compte administrateur actif existe déjà. |
| **RG-SEC-10** | **Paramétrage des requêtes du module** | Requêtes examinées dans `Utilisateur` et `CustomUserDetailsService` | Les recherches et mutations de comptes examinées lient leurs paramètres avec `PreparedStatement`. Cette règle ne garantit pas à elle seule l'absence d'injection SQL ou XSS dans tout le projet. |

---

*GALAXAM SIRH — Module Sécurité IAM — Document mis à jour depuis le code source réel — Octobre 2026*
