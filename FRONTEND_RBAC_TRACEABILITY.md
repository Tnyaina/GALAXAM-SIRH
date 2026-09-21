# Matrice de Traçabilité RBAC Frontend & Autorisations

Ce document détaille la politique de contrôle d'accès basé sur les rôles (Role-Based Access Control - RBAC) mise en œuvre dans le frontend de GALAXAM SIRH, les permissions associées et les mécanismes d'application à triple niveau (Route Guards, UI Conditional Rendering, et Backend API Security).

---

## 1. Rôles et Profils Utilisateurs Référencés

| Rôle Système | Profil Métier | Description & Périmètre de Responsabilité |
|---|---|---|
| `ROLE_ADMIN` | Direction Administrative & Financière (DAF / DG) | Accès absolu à l'ensemble du système. Arbitrage du pointage, simulation, calcul, validation officielle et clôture de la paie. Génération des exports légaux. |
| `ROLE_AGENT_POINTAGE` | Agent de Saisie du Temps / Gestionnaire Site | Saisie des horaires quotidiens, gestion et régularisation des anomalies locales de badgeage, transmission de la synthèse de pointage pour validation. Aucun accès aux modules financiers (Paie, Salaires). |
| `ROLE_RH` | Gestionnaire Ressources Humaines | Gestion du personnel, des contrats, carrières et validation initiale des congés et absences. |
| `ROLE_EMPLOYE` | Collaborateur Salarié | Consultation de son propre dossier, de son solde de congés et dépôt de demandes d'absence. |

---

## 2. Matrice d'Accès par Module et par Route

| Module / Vue | Route Frontend | Permission Requise | `ROLE_ADMIN` (DAF) | `ROLE_AGENT_POINTAGE` | `ROLE_RH` | `ROLE_EMPLOYE` | Mécanisme de Verrouillage |
|---|---|---|:---:|:---:|:---:|:---:|---|
| **Tableau de Bord** | `/dashboard` | `EMPLOYE_READ` |  Autorisé | ❌ Redirigé |  Autorisé | ❌ Redirigé | Route Guard (`ProtectedRoute`) |
| **Fiches Collaborateurs** | `/employes/*` | `EMPLOYE_READ` |  Autorisé | ❌ Non autorisé |  Autorisé | ❌ Non autorisé | Route Guard + Menu Sidebar |
| **Contrats & Carrières** | `/carrieres/*` | `CARRIERE_READ` |  Autorisé | ❌ Non autorisé |  Autorisé | ❌ Non autorisé | Route Guard + Menu Sidebar |
| **Congés — Soldes** | `/conges?tab=soldes` | `CONGE_READ` |  Autorisé | ❌ Non autorisé |  Autorisé |  Autorisé (Self) | Route Guard |
| **Congés — Demandes** | `/conges?tab=demandes` | `CONGE_READ` |  Autorisé | ❌ Non autorisé |  Autorisé |  Autorisé (Self) | Route Guard |
| **Congés — Validation** | Bouton `Approuver` | `CONGE_WRITE` |  Autorisé | ❌ Masqué |  Autorisé | ❌ Masqué | UI Conditionnel (`hasPermission`) |
| **Pointage — Saisie** | `/pointages?tab=saisie` | `POINTAGE_READ` |  Autorisé |  Autorisé |  Autorisé | ❌ Non autorisé | Route Guard (`allowedRoles`) |
| **Pointage — Anomalies** | `/pointages?tab=suivi` | `POINTAGE_READ` |  Autorisé |  Autorisé |  Autorisé | ❌ Non autorisé | Route Guard (`allowedRoles`) |
| **Pointage — Transmission** | Bouton `Transmettre` | `POINTAGE_WRITE` |  Autorisé |  Autorisé | ❌ Masqué | ❌ Masqué | UI Conditionnel + API Guard |
| **Pointage — Arbitrage/Gel** | `/pointages?tab=validation`| `POINTAGE_WRITE` |  Autorisé | ❌ Consultation | ❌ Non autorisé | ❌ Non autorisé | Actions réservées DAF |
| **Paie — Sélection Période**| `/paie` | `PAIE_READ` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard (`allowedRoles=["ROLE_ADMIN"]`) |
| **Paie — Préparation** | `/paie/periodes/:id/preparation` | `PAIE_READ` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard |
| **Paie — Population** | `/paie/periodes/:id/population` | `PAIE_READ` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard |
| **Paie — Variables** | `/paie/periodes/:id/variables` | `PAIE_WRITE` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard + Formulaire bridé |
| **Paie — Simulation & Calcul**| `/paie/periodes/:id/calcul` | `PAIE_WRITE` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard + Boutons d'exécution |
| **Paie — Contrôle & Drawer** | `/paie/periodes/:id/controle` | `PAIE_READ` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard |
| **Paie — Validation DAF** | `/paie/periodes/:id/validation` | `PAIE_WRITE` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Route Guard + Confirmation modale |
| **Paie — Clôture** | Bouton `Clôturer` | `PAIE_WRITE` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | State Machine + Confirmation modale |
| **Paie — Exports & Virements**| `/paie/periodes/:id/sorties` | `PAIE_READ` |  Autorisé | ❌ Redirigé | ❌ Non autorisé | ❌ Non autorisé | Conditionnel à `PAIE_VALIDEE`/`CLOTUREE` |

---

## 3. Mécanismes de Défense en Profondeur

### Niveau 1 : Filtrage d'Affichage Dynamique (Sidebar & Menus)
Le composant `Sidebar` inspecte les permissions de l'utilisateur actif via le hook `useAuth()`. Les modules dont les permissions ne sont pas possédées (ex: `PAIE_READ` pour l'Agent de Pointage) ne sont tout simplement pas injectés dans le DOM, éliminant toute tentation visuelle de clic.

### Niveau 2 : Route Guard React Router (`ProtectedRoute.jsx`)
Si un utilisateur tente de forcer l'accès par saisie manuelle de l'URL dans la barre d'adresse (URL tampering) :
1. Si non authentifié : redirection immédiate vers `/` (Login).
2. Si authentifié sans le rôle requis :
   - Si l'utilisateur possède `POINTAGE_READ` (cas de l'Agent de Pointage), il est automatiquement renvoyé vers son cockpit de travail autorisé `/pointages/saisie`.
   - Sinon, il est redirigé vers `/non-autorise`.
Ce comportement a été formellement validé par le test E2E `6.1`.

### Niveau 3 : Verrouillage Machine d'État Backend (`PayrollPeriodWorkflowService`)
Toute requête transmise avec un jeton JWT valide mais non habilité à une transition donnée (ex: un Agent qui tenterait un `POST /api/periodes/101/actions/APPROVE` ou un `POST /api/paie/calculer-periode`) se heurte à la vérification stricte `requireAuthority(command, user)` qui lève une exception `AccessDeniedException` (HTTP 403 Forbidden).
