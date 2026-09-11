# ARBITRAGE FINAL UI/UX & INTÉGRITÉ MÉTIER — SIRH GALAXAM

**Date** : 2026-09-01  
**Auteur** : Auditeur Arbitre Final (Expert Architecture SIRH, Product Design & Sécurité des Systèmes d'Information)  
**Périmètre** : Arbitrage contradictoire entre l'Audit Claude Opus (`docs/audit-ui-ux-claude-opus.md`) et le Contre-Audit Codex (`docs/contre-audit-ui-ux-codex.md`), validé par inspection statique exhaustive et vérification croisée du code source (`backend` Spring Boot & `frontend` React).  
**Règle d'or** : *Aucune modification de code effectuée pendant cette phase d'arbitrage. Les faits techniques constatés dans le code constituent la seule et unique vérité terrain.*

---

## 1. Verdict Global

Le SIRH GALAXAM possède une conception métier de haut niveau, articulée autour d'un moteur de workflow d'événements RH (`WorkflowRhService` ~1580 lignes), d'un moteur de décompte de pointage (`PointageCalculateurEngine`, `CalendrierOuvrableEngine`) et d'un ledger de congés par compensation (`MouvementSoldeConge`).

Cependant, **l'audit contradictoire révèle que Claude Opus s'est focalisé sur des aspects superficiels ou a formulé des faux positifs par méconnaissance du contrat d'API réel**, tandis que **Codex a mis en lumière des failles critiques d'intégrité métier (P0) et d'étanchéité inter-modules** qui rendraient toute tentative de calcul de paie erronée et toute refonte purement cosmétique prématurée.

### Tableau d'évaluation multidimensionnelle (Notes sur 10)

| Dimension | Score | Analyse de l'arbitre |
|---|:---:|---|
| **Intégrité métier backend** | **5.5/10** | Moteurs puissants mais gardes d'entrée trouées : création de congés sans `controler()`, pointage sans jointure de contrat actif, absence de verrou serveur sur période fermée. |
| **Robustesse frontend / API** | **6.0/10** | DTO bien typés, mais `canManage = ... || true` en dur, `size: 1000` sur la liste des employés, et dérogations silencieuses côté client. |
| **Préparation Paie** | **4.0/10** | Risques critiques de propagation d'heures indues, absence de snapshot immuable sur les taux/salaires historiques lors des recalculs, et risque d'écrasement concurrentiel. |
| **Workflows & Métier** | **7.5/10** | Workflow d'embauche, transitions et ruptures bien orchestrés côté backend avec circuit DAF ; dégradé par des endpoints d'activation directe bypassant le workflow. |
| **UX & Guidage Utilisateur** | **6.0/10** | Bons steppers et bannières d'alerte, mais incohérences de fil d'Ariane (renvoi vers Login), navigation cross-module perfectible et formulaires sans validation debounced. |
| **Architecture Informationnelle**| **6.5/10** | Séparation claire des domaines, mais quelques pages et onglets monolithiques (`EmployeList.jsx` 520L, `EmployeFicheDetailPage.jsx` 532L). |
| **Design System & Cohérence UI** | **7.0/10** | Tokens CSS riches dans `index.css`, mais pollution résiduelle de classes Tailwind hardcodées (`slate-*`, `emerald-*`, `amber-*`) au lieu des tokens sémantiques. |
| **Accessibilité (a11y)** | **4.5/10** | Bon contraste et focus visible, mais `Modal.jsx` et `Dialog.jsx` sans focus trap, sans `role="dialog"`, sans `aria-modal="true"`, et boutons d'action icônes sans labels. |
| **Responsive Design** | **5.5/10** | Wrapper horizontal présent, mais grille de pointage dense et modales longues difficilement exploitables sur petit écran. |

---

## 2. Tableau d'Arbitrage Exhaustif

| ID Issue | Audit Claude Opus | Contre-Audit Codex | Vérification Code Source Réel (Faits Techniques) | Verdict Final Arbitre | Gravité / Priorité |
|---|---|---|---|---|:---:|
| **UX-EMP-001 / P0-EMP-001** | P0 : Désactivation manuelle découplée du workflow contrats. | CONFIRMÉ : `PUT /employes/{id}/desactiver` modifie directement l'employé sans clôture de contrat. | `EmployeController.java` (L393-423) et `Employe.java` (L242-252) exécutent un simple `UPDATE employe SET est_actif = FALSE...`. Le contrat actif reste `ACTIF` et l'affectation reste `ACTIVE`. Aucun workflow ni approbation DAF. | **CONFIRMÉ (Bug Métier Majeur)** | 🔴 **P0** |
| **P1-EMP-001** | Non détecté. | P1 : Réactivation directe sans contrat ni affectation. | `EmployeController.java` (L425-445) et `Employe.java` (L254-263) réactivent un employé (`est_actif = TRUE`) sans créer ni vérifier de contrat ni d'affectation active. | **CONFIRMÉ (Bug Métier)** | 🟠 **P1** |
| **UX-EMP-002** | P1 : `size: 1000` en dur dans la liste des employés. | CONFIRMÉ : `EmployeList.jsx` charge 1000 items puis filtre/page en mémoire. | `EmployeList.jsx` (L69) fait `employeService.getEmployes({ size: 1000 })`. Risque de saturation mémoire et lenteurs sur volumétrie réelle. | **CONFIRMÉ (Dette Technique / Perf)** | 🟠 **P1** |
| **UX-EMP-003** | P1 : Absence de guidage post-création d'employé. | FAUX : Redirection vers `?tab=carriere&action=new_contract` et toast implémentés. | `EmployeList.jsx` (L303) redirige explicitement vers `/employes/${newEmpId}?tab=carriere&action=new_contract`. `useCarriereModals.js` (L96) intercepte le paramètre et ouvre immédiatement `EmbaucheModal`. | **FAUX POSITIF CLAUDE** | 🟢 **Rejeté (Conforme)** |
| **UX-EMP-004** | P2 : Formulaire multi-step sans sauvegarde de brouillon. | PARTIELLEMENT CONFIRMÉ : Pas de brouillon persistant, mais ce n'est pas bloquant. | `EmployeFormModal.jsx` conserve l'état en mémoire React locale pendant la saisie multi-onglets. La perte n'intervient qu'en cas de rechargement complet de page. | **CONFIRMÉ (Amélioration UX)** | 🟡 **P2** |
| **UX-CON-001** | P0 : Pas de blocage frontend pour embauche si contrat actif. | PARTIELLEMENT CONFIRMÉ : Pas de garde frontend, mais le backend bloque strictement. | `ContratActifSection.jsx` (L24-60) **masque** le bouton "Initier l'embauche" quand `contratActif` existe. Côté backend, `WorkflowRhService.java` (L30-42) lève une `IllegalArgumentException` si un contrat `ACTIF` ou un workflow `BROUILLON` existe. | **FAUX POSITIF CLAUDE (Doublement protégé)** | 🟢 **Rejeté (Sécurisé)** |
| **UX-CON-002 / BUG-001** | P1 : `initierRupture` utilise le mauvais endpoint `/transition` au lieu de `/rupture`. | FAUX : `/transition` est le endpoint générique conçu pour tous les événements y compris ruptures. | `WorkflowRhController.java` (L99) expose `POST /employes/{id}/workflows/transition`. Le backend traite tous les types d'événements (démission, licenciement, mutation, promotion). Il n'existe aucun endpoint `/rupture` dans l'API et il ne faut pas en créer un. `contratService.initierRupture` est une fonction doublon morte. | **FAUX POSITIF CLAUDE (Nettoyage code mort)** | ⚪ **P3 (Cleanup)** |
| **UX-CON-003 / 004** | P2 : Modales contrat très longues et récapitulatif monolithique (28KB). | CONFIRMÉ : Composant lourd mais fonctionnel. | `RecapitulatifBrouillonModal.jsx` fait 28KB et contient les switch-cases de tous les types d'événements RH. | **CONFIRMÉ (Refactoring UX/Composant)** | 🟡 **P2** |
| **P0-CNG-001** | Non détecté par Claude. | P0 : Création de congé sans appel à `DemandeConge.controler()`. | `DemandeCongeController.java` (L122) appelle `congeService.creerDemande()` qui appelle directement `d.save(c)` SANS appeler `d.controler(c)`. Ni chevauchements, ni préavis, ni ancienneté, ni plafond maternité, ni pool 10j ne sont vérifiés à la création. À la validation, `valider()` ne rappelle pas non plus `controler()`. | **CONFIRMÉ (Bug Métier Critique P0)** | 🔴 **P0** |
| **UX-CNG-001** | P1 : Module congés déconnecté des fiches employés. | FAUX : `CongesTab` relie demandes, solde et ledger au salarié. | `EmployeFicheDetailPage.jsx` (L408) intègre `<CongesTab idEmploye={id} employe={emp} />` qui affiche les demandes, le solde en temps réel et l'historique du ledger. | **FAUX POSITIF CLAUDE** | 🟢 **Rejeté (Conforme)** |
| **UX-CNG-002 / 003** | P2 : `DemandeCongeModal` hors Modal standard et code d'extraction dupliqué. | CONFIRMÉ : Composants et hooks à factoriser. | `DemandeCongeModal.jsx` réimplémente sa propre structure de dialogue au lieu d'utiliser `components/ui/overlays/Modal.jsx`. | **CONFIRMÉ (Dette UX/Design System)** | 🟡 **P2** |
| **CNG-PREAVIS-SILENT** | Non détecté. | Identifié par Codex : Dérogation de préavis auto-activée si date passée. | `useDemandeCongeForm.js` (L127-139) force silencieusement `derogationDelaiPreavis = true` dès que `dateDebut < today`, sans demander de motif ni d'accord utilisateur. | **CONFIRMÉ (Anomalie UX/Métier)** | 🟠 **P1** |
| **P0-PTG-001** | P1 (Claude : UX-PTG-002) : Pointage sans lien avec statut contrat. | CONFIRMÉ P0 : La grille joint `affectation + est_actif`, sans vérifier le contrat ACTIF. | `PointageSaisieService.java` (L398-405) requête `FROM employe e JOIN employe_affectation_carriere a` avec `e.est_actif = TRUE`. Aucun `JOIN contrat_travail` ni vérification `statut = 'ACTIF'`. Des pointages peuvent être saisis pour un salarié sans contrat valide. | **CONFIRMÉ (Bug Métier Critique P0)** | 🔴 **P0** |
| **P0-PTG-002** | Non détecté par Claude. | P0 : Enregistrement de batch de pointage sans verrou sur période fermée. | `PointageSaisieService.java` (L897-906) associe la période sans vérifier si son statut est `VALIDEE` ou `CLOTUREE`. Un batch peut modifier rétroactivement les pointages d'une période déjà gelée pour la paie. | **CONFIRMÉ (Bug Métier Critique P0)** | 🔴 **P0** |
| **P0-PTG-003** | Non détecté. | Concurrence : Écrasement possible entre batches simultanés. | `PointageSaisieService.java` (L743) utilise `ON CONFLICT (id_employe, date_pointage) DO UPDATE` sans numéro de version ni optimistic locking (`updated_at` check). Un utilisateur écrase silencieusement les saisies d'un autre. | **CONFIRMÉ (Concurrence / Race Condition)** | 🔴 **P0** |
| **UX-PTG-001** | P1 : `size: 1000` en dur dans synthèse pointage. | FAUX : Constat inexact sur la synthèse pointage. | La synthèse pointage utilise des filtres par période (`idPeriodePaie`) et site (`idSite`), calculés côté serveur par `PointageSyntheseService.java`. | **FAUX POSITIF CLAUDE** | 🟢 **Rejeté** |
| **UX-PTG-003** | P1 : Workflow de saisie de pointage sans stepper. | FAUX : `PointageGuideStepper` est bien présent et actif. | `SaisieQuotidiennePage.jsx` (L12 & L377) importe et affiche `<PointageGuideStepper>`. | **FAUX POSITIF CLAUDE** | 🟢 **Rejeté** |
| **P1-PTG-003 / PERM-001**| Non détecté par Claude. | P1 : `canManage = ... || true` affiche la gestion de période à tous. | `PeriodePaiePage.jsx` (L72) contient en dur : `const canManage = hasRole?.('ADMIN') || hasAuthority?.('ROLE_ADMIN') || true;`. Tous les boutons d'action critique s'affichent à tous les utilisateurs (même si le backend rejette en 403). | **CONFIRMÉ (Défaut Sécurité Frontend)** | 🟠 **P1** |
| **UX-NAV-001 / BUG-003** | P2 (Claude) / P1 (Arbitre) : Breadcrumb href "/" pointe vers Login. | CONFIRMÉ : Le lien "Tableau de bord" dans le breadcrumb renvoie vers `/` (route Login). | `Router.jsx` (L58) associe `/` à `<Login />`. `EmployeFicheDetailPage.jsx` (L299) configure `{ label: 'Tableau de bord', href: '/' }`. Cliquer sur le breadcrumb perturbe la session. | **CONFIRMÉ (Bug Navigation)** | 🟠 **P1** |
| **UX-DSN-001** | P2 : Couleurs hardcodées Tailwind (`slate-*`, `emerald-*`) vs tokens. | CONFIRMÉ : Mauvaise utilisation des tokens existants. | Plusieurs composants utilisent des classes utilitaires directes (`bg-emerald-500/15`, `bg-amber-500/15`, `text-slate-700`) au lieu des variables `--color-success-*`, `--color-warning-*`, `--color-ink-*`. | **CONFIRMÉ (Qualité UI / Tokens)** | 🟡 **P2** |
| **UX-DSN-002 / BUG-002** | P2 : Badge `variant="brand"` non défini provoquant une anomalie. | FAUX : `Badge.jsx` replie proprement sur `default`. Dette de nommage. | `Badge.jsx` (L45) fait `variants[variant] ? variant : 'default'`. Pas de crash, mais un variant non stylé. Renommage `brand` → `primary` nécessaire. | **FAUX POSITIF CLAUDE (Dette mineure)** | ⚪ **P3** |
| **UX-FRM-001** | P2 : Validation inline quasi inexistante. | PARTIELLEMENT CONFIRMÉ : Présente sur congés, absente ou incomplète ailleurs. | Les formulaires de contrats et d'employés valident principalement au submit via alertes toast ou bannières, au lieu d'une validation synchrone debounced par champ. | **CONFIRMÉ (Amélioration UX)** | 🟡 **P2** |
| **UX-ACC-001** | P3 : Lacunes d'accessibilité (Modales sans focus trap, ARIA, etc.). | CONFIRMÉ : Absence de focus trap, `role="dialog"`, `aria-modal="true"`. | `Modal.jsx` (L43-85) n'a pas de piège de focus clavier, pas d'attributs ARIA pour lecteurs d'écran, et les boutons icônes manquent de labels explicites. | **CONFIRMÉ (Accessibilité a11y)** | ⚪ **P3** |
| **UX-RSP-001** | P3 : Tables non optimisées sur mobile. | PARTIELLEMENT CONFIRMÉ : Wrapper présent mais grille dense. | Le tableau de pointage quotidien et la matrice mensuelle débordent et nécessitent un mode de saisie adapté pour écrans tactiles étroits. | **CONFIRMÉ (Responsive)** | ⚪ **P3** |

---

## 3. P0 — À Corriger Impérativement Avant Toute Modification UX

Ces 5 problèmes mettent en péril direct l'intégrité de la base de données, la cohérence des droits contractuels et la validité de la paie future. **Aucun composant d'interface ne doit être retouché avant leur résolution backend.**

---

### [P0-CNG-001] Création et validation de congé sans contrôle métier d'intégrité

* **Cause racine** : Dans `DemandeCongeController.java` (L122), la méthode `creerDemande` délègue à `congeService.creerDemande(d, ...)` qui invoque directement `d.save(c)` sans appeler `d.controler(c)`. De plus, `DemandeConge.valider()` (L280) vérifie le solde mais ne rappelle pas `controler(c)`.
* **Impact métier & paie** :
  1. Possibilité d'enregistrer des demandes de congés qui se **chevauchent** pour un même collaborateur.
  2. Non-respect du **délai de préavis** légal/conventionnel.
  3. Dépassement non contrôlé du **plafond de congé maternité** (98j / 119j) ou du **pool annuel des permissions exceptionnelles** (10j / an - Art. 88).
  4. Absence de vérification de l'existence d'un contrat actif couvrant les dates de congé.
* **Fichiers impactés** :
  * [DemandeCongeController.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/api/controller/conge/DemandeCongeController.java)
  * [CongeService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java)
  * [DemandeConge.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/conge/DemandeConge.java)
* **Workflow cible & Correction** :
  1. Dans `DemandeConge.controler(Connection c)` : ajouter le contrôle formel que l'employé possède un contrat `ACTIF` dont la plage `[date_debut, date_fin]` englobe la totalité des dates demandées.
  2. Dans `CongeService.creerDemande(...)` : insérer `d.controler(c);` avant `d.save(c);`.
  3. Dans `DemandeConge.valider(...)` : ré-exécuter systématiquement `this.controler(c);` avant d'appliquer la validation et d'émettre le mouvement `PRISE` dans `mouvement_solde_conge`.
* **Tests nécessaires** :
  * Test d'intégration tentant d'insérer deux demandes de congé sur les mêmes dates (doit échouer).
  * Test de création d'un congé de 150 jours maternité sans dérogation (doit échouer).
  * Test de création d'un congé pour un employé dont le contrat se termine avant la date de début du congé (doit échouer).

---

### [P0-PTG-001] Éligibilité de pointage sans vérification de contrat actif

* **Cause racine** : La requête `sqlEmp` dans `PointageSaisieService.java` (L398-405) extrait les employés uniquement sur la base de la table `employe_affectation_carriere` et du booléen `employe.est_actif = TRUE`. Elle ne vérifie ni l'existence d'un contrat dans `contrat_travail`, ni le statut `ACTIF` de ce contrat, ni le statut `ACTIF` de l'affectation.
* **Impact métier & paie** :
  1. Un collaborateur ayant une affectation historique ou résiduelle sans contrat actif en cours peut être pointé chaque jour.
  2. Calcul indu d'heures normales, heures supplémentaires, indemnités de transport et de repas transmises à la paie pour un non-salarié.
* **Fichiers impactés** :
  * [PointageSaisieService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java)
  * [PointageSyntheseService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java)
* **Workflow cible & Correction** :
  * Modifier `sqlEmp` pour joindre impérativement `contrat_travail ct` et `ref_statut_rh s_ct` ainsi que `ref_statut_rh s_aff` :
    ```sql
    SELECT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url,
           COALESCE(f.dispense_pointage, FALSE) AS est_autonome
    FROM employe e
    JOIN contrat_travail ct ON e.id_employe = ct.id_employe
    JOIN ref_statut_rh s_ct ON ct.id_statut_rh = s_ct.id_statut_rh AND s_ct.code = 'ACTIF'
    JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe AND a.id_contrat = ct.id_contrat
    JOIN ref_statut_rh s_aff ON a.id_statut_rh = s_aff.id_statut_rh AND s_aff.code = 'ACTIF'
    LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction
    WHERE a.id_site = ?
      AND ct.date_debut <= ? AND (ct.date_fin IS NULL OR ct.date_fin >= ?)
      AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?)
      AND e.est_actif = TRUE
    ORDER BY e.nom, e.prenom
    ```
* **Tests nécessaires** :
  * Test unitaire avec un employé ayant `est_actif = true` mais son contrat `CLOTURE` : il ne doit pas apparaître dans la grille de pointage.

---

### [P0-PTG-002] Écriture de pointage sans verrou serveur sur période fermée / transmise

* **Cause racine** : `PointageSaisieService.enregistrerPointagesBatch` (L897-906) affecte un `id_periode_paie` sans vérifier son statut. Si la période est `VALIDEE`, `CLOTUREE` ou `TRANSMIS_DAF`, les écritures SQL dans `pointage_jour` sont acceptées.
* **Impact métier & paie** :
  1. Rupture d'étanchéité entre le pointage quotidien et le cumul mensuel gelé (`pointage_cumul_periode`).
  2. Falsification ou divergence des heures après clôture comptable et émission des bulletins de paie.
* **Fichiers impactés** :
  * [PointageSaisieService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java)
  * [PointageAnomalieService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageAnomalieService.java)
* **Workflow cible & Correction** :
  * En tête de `enregistrerPointagesBatch` et de `resoudreAnomalie`, vérifier le statut de la période de chaque pointage :
    ```java
    String checkSql = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?";
    // Si code != 'OUVERTE' && code != 'EN_COURS' && code != 'BROUILLON' -> throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est fermée/validée.");
    ```
* **Tests nécessaires** :
  * Test d'enregistrement d'un batch de pointage sur une date appartenant à une période au statut `VALIDEE` (doit lever une exception HTTP 400/409).

---

### [P0-EMP-001 / UX-EMP-001] Désactivation et réactivation manuelles directes hors workflow RH

* **Cause racine** : `EmployeController.desactiverEmploye` (L393) et `EmployeController.reactiverEmploye` (L425) mettent à jour directement la colonne `employe.est_actif` sans passer par la machine d'état `WorkflowRhService`.
* **Impact métier & paie** :
  1. Employé marqué inactif mais conservant un contrat `ACTIF` et une affectation `ACTIVE` dans les tables RH.
  2. Contournement du rôle DAF et des motifs légaux de rupture (préavis, certificat de travail, solde de tout compte).
  3. Réactivation directe réactivant un employé sans relation juridique contractuelle.
* **Fichiers impactés** :
  * [EmployeController.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/api/controller/employe/EmployeController.java)
  * [EmployeService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/employe/service/EmployeService.java)
  * [EmployeList.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/pages/employe/EmployeList.jsx)
  * [EmployeFicheDetailPage.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/pages/employe/EmployeFicheDetailPage.jsx)
* **Workflow cible & Correction** :
  1. Déprécier et sécuriser strictement les endpoints `PUT /employes/{id}/desactiver` et `PUT /employes/{id}/activer` (réservés à l'administrateur système pour correction d'erreur de saisie civile uniquement, avec interdiction si un contrat actif existe).
  2. Tout changement d'état d'emploi doit obligatoirement passer par :
     * Sortie : `POST /employes/{id}/workflows/transition` (avec type d'événement de rupture) validé par le DAF.
     * Réintégration : `POST /employes/{id}/workflows/embauche-initiale` (réembauche / réintégration) validé par le DAF.
  3. Supprimer les boutons de désactivation rapide des tableaux et fiches, et les remplacer par un lien vers le workflow de rupture dans l'onglet Carrière.
* **Tests nécessaires** :
  * Tentative de désactiver directement un employé avec contrat actif via l'API (doit être refusée).

---

### [P0-PTG-003] Concurrence et écrasement silencieux des saisies de pointage

* **Cause racine** : La clause `ON CONFLICT (id_employe, date_pointage) DO UPDATE` dans `PointageSaisieService.java` (L743) écrase sans discernement toutes les colonnes sans vérifier si la ligne a été modifiée par un autre superviseur depuis son chargement à l'écran.
* **Impact métier & paie** : Perte silencieuse de données de présence ou d'indemnités saisies par un collègue lors de sauvegardes simultanées de la grille de pointage.
* **Fichiers impactés** :
  * [PointageSaisieService.java](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java)
  * [pointage_jour (Table BDD)](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/docs/pointage.md)
* **Workflow cible & Correction** :
  * Ajouter une colonne `version INT DEFAULT 1` ou vérifier `updated_at` dans la clause `WHERE` de mise à jour (Optimistic Locking).
  * Si la version a changé, notifier l'utilisateur avec un message de conflit de saisie proposant de recharger les données à jour.
* **Tests nécessaires** :
  * Test de concurrence simulant deux requêtes batch simultanées sur le même employé et la même date avec des versions déphasées (la seconde doit échouer avec HTTP 409 Conflict).

---

## 4. P1 — À Corriger Avant Refonte UX

Ces défauts créent des comportements anormaux dans l'application, des risques de sécurité dans l'interface ou des dégradations de performance.

1. **[P1-PTG-003 / PERM-001] Permission forcée `canManage = ... || true` dans `PeriodePaiePage.jsx`**
   * *Fichier* : `frontend/src/pages/pointage/PeriodePaiePage.jsx` (L72)
   * *Correction* : Remplacer par :
     ```javascript
     const canManage = hasRole?.('ADMIN') || hasAuthority?.('ROLE_ADMIN') || hasAuthority?.('ROLE_AGENT_POINTAGE');
     ```
2. **[P1-EMP-002] Pagination serveur pour la liste des employés**
   * *Fichier* : `frontend/src/pages/employe/EmployeList.jsx` (L69)
   * *Correction* : Exploiter les paramètres de pagination backend `getEmployes({ page: currentPage, size: pageSize, search: searchQuery, siteId, departementId, statut })` déjà supportés par `EmployeController.java` (`PageDTO`).
3. **[P1-NAV-001 / BUG-003] Fil d'Ariane pointant `/` vers Login au lieu de `/dashboard`**
   * *Fichiers* : `frontend/src/pages/employe/EmployeFicheDetailPage.jsx` (L299), `PeriodePaiePage.jsx` (L242), `PageHeader.jsx`.
   * *Correction* : Remplacer `href: '/'` par `href: '/dashboard'` dans tous les objets de fil d'Ariane.
4. **[P1-CNG-002] Simulation serveur des congés et suppression de l'auto-dérogation silencieuse**
   * *Fichier* : `frontend/src/hooks/conges/useDemandeCongeForm.js` (L127-139, L161-177)
   * *Correction* : Remplacer le calcul local des jours ouvrés par l'appel à l'API `congeService.simulerConge()`. Supprimer l'activation automatique de `derogationDelaiPreavis` et exiger une case explicite avec motif lors de saisies rétroactives.
5. **[P1-CON-002] Nettoyage de la méthode API morte `initierRupture`**
   * *Fichier* : `frontend/src/api/employe/contratService.js` (L96-99)
   * *Correction* : Supprimer `initierRupture` pour consolider tout mouvement vers `initierTransition`.

---

## 5. P2 — Améliorations UX

1. **Navigation Contextuelle Cross-Module Fluide** :
   * Permettre de naviguer en 1 clic entre : Fiche Employé ↔ Onglet Carrière ↔ Historique Congés ↔ Grille Pointage filtrée sur l'employé.
2. **Persistance de l'État dans l'URL (URL State Sync)** :
   * Conserver les filtres, la recherche, la pagination et les onglets actifs dans les query params (`?tab=...&page=...&search=...`) sur toutes les pages principales.
3. **Validation Inline Debounced des Formulaires** :
   * Intégrer un hook de validation synchrone avec debounce (300ms) sur les champs sensibles (CIN, matricule, dates de contrat, salaires, compte bancaire).
4. **Découpage des Composants Monolithiques** :
   * Découper `EmployeList.jsx` (520L) et `EmployeFicheDetailPage.jsx` (532L) en sous-composants autonomes et hooks dédiés (`useEmployeList`, `useEmployeDetail`).
5. **Standardisation de `DemandeCongeModal`** :
   * Aligner `DemandeCongeModal.jsx` sur le composant générique `components/ui/overlays/Modal.jsx`.

---

## 6. P3 — Qualité, Accessibilité & Confort

1. **Accessibilité Modales & Dialogs** :
   * Ajouter le focus trap, la restauration de focus, `role="dialog"`, `aria-modal="true"`, `aria-labelledby` dans `Modal.jsx` et `Dialog.jsx`.
2. **Harmonisation des Tokens CSS** :
   * Remplacer toutes les occurrences de classes Tailwind brutes (`slate-*`, `emerald-*`, `amber-*`) par les variables sémantiques CSS `--color-brand-*`, `--color-ink-*`, `--color-success-*`, `--color-warning-*`.
3. **Nommage des Variantes de Badges** :
   * Corriger les usages de `variant="brand"` vers `variant="primary"` dans les composants d'historique de carrière.
4. **Optimisation Responsive des Grandes Tables** :
   * Proposer un affichage en cartes condensées sur smartphone pour la grille de pointage et les listes d'historique de contrats.

---

## 7. Faux Positifs — À Ne Pas Modifier

Les éléments suivants relevés dans les audits précédents sont des **fausses alertes** ou des mécompréhensions du fonctionnement du système ; ils **ne doivent pas être modifiés** :

1. ❌ **UX-CON-001 (Audit Claude : Embauche possible si contrat actif)** : Le bouton est déjà masqué dans l'UI (`ContratActifSection.jsx`) et le backend lève une exception stricte (`WorkflowRhService.java`).
2. ❌ **UX-CON-002 / BUG-001 (Audit Claude : Endpoint `/rupture` manquant)** : Le backend utilise délibérément l'architecture unifiée `POST /employes/{id}/workflows/transition` pour tous les événements RH (démission, licenciement, départ retraite). Ne pas créer d'endpoint `/rupture`.
3. ❌ **UX-EMP-003 (Audit Claude : Manque de guidage post-création)** : La redirection vers `?tab=carriere&action=new_contract` avec toast est déjà fonctionnelle.
4. ❌ **UX-CNG-001 (Audit Claude : Congé déconnecté de la fiche employé)** : L'onglet `CongesTab.jsx` est présent dans la fiche détail avec solde et ledger.
5. ❌ **UX-PTG-001 (Audit Claude : `size: 1000` sur la synthèse de pointage)** : La synthèse de pointage est filtrée par période et par site, calculée serveur.
6. ❌ **UX-PTG-003 (Audit Claude : Pointage sans stepper)** : `PointageGuideStepper.jsx` est déjà présent dans la page de saisie quotidienne.

---

## 8. Contradictions Résolues (Claude Opus vs Codex)

```
┌─────────────────────────────┬─────────────────────────────┬────────────────────────────────────────────────────────┐
│ Sujet de Contradiction      │ Claude Opus                 │ Codex / Verdict Arbitral Final                         │
├─────────────────────────────┼─────────────────────────────┼────────────────────────────────────────────────────────┤
│ Endpoint de Rupture         │ Réclame un endpoint         │ Codex a raison : L'API est unifiée sur                 │
│                             │ POST .../rupture            │ POST .../workflows/transition. Pas de /rupture requis. │
├─────────────────────────────┼─────────────────────────────┼────────────────────────────────────────────────────────┤
│ Garde Contrat Actif         │ Défaut P0 frontend          │ Codex a raison : Protégé par l'UI et verrouillé par le │
│                             │                             │ backend (WorkflowRhService.creerBrouillonEmbauche).    │
├─────────────────────────────┼─────────────────────────────┼────────────────────────────────────────────────────────┤
│ Contrôle Métier Congés      │ Non identifié               │ Codex a raison (P0 critique confirmé) : création sans  │
│                             │                             │ appel à d.controler(c).                                │
├─────────────────────────────┼─────────────────────────────┼────────────────────────────────────────────────────────┤
│ Éligibilité Pointage        │ Simple problème UX (P1)     │ Codex a raison (P0 critique confirmé) : Pointage sans  │
│                             │                             │ contrat actif dans la requête SQL.                     │
├─────────────────────────────┼─────────────────────────────┼────────────────────────────────────────────────────────┤
│ Verrou Période Paie         │ Non identifié               │ Codex a raison (P0 critique confirmé) : batch sans     │
│                             │                             │ contrôle de statut de période de paie.                 │
└─────────────────────────────┴─────────────────────────────┴────────────────────────────────────────────────────────┘
```

---

## 9. Workflow Cible — Cycle de Vie Employé

```text
               ┌───────────────────────────────┐
               │    1. Création Fiche Civile   │ (Statut civil créé : est_actif = FALSE)
               └──────────────┬────────────────┘
                              │ Redirection auto (?tab=carriere&action=new_contract)
                              ▼
               ┌───────────────────────────────┐
               │    2. Initialisation Contrat  │ (Brouillon d'embauche + Affectation planifiée)
               └──────────────┬────────────────┘
                              │ Soumission au DAF
                              ▼
               ┌───────────────────────────────┐
               │    3. Validation DAF          │ (WorkflowRhService.validerWorkflow)
               └──────────────┬────────────────┘
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
┌───────────────────────┐           ┌───────────────────────┐
│ Contrat → ACTIF       │           │ Employé → est_actif   │
│ Affectation → ACTIVE  │           │           = TRUE      │
└───────────────────────┘           └───────────────────────┘
            │                                   │
            │           4. Vie Salarié          │
            ├───────────────────────────────────┤
            │ • Congés autorisés (si contrat OK)│
            │ • Pointage autorisé (si contrat OK│
            │ • Évolutions via TRANSITION       │
            │   (Mutation, Promo, Renouvellement│
            └─────────────────┬─────────────────┘
                              │
                              │ 5. Sortie Salarié
                              ▼
               ┌───────────────────────────────┐
               │  Brouillon Rupture TRANSITION │ (Démission, Licenciement, Fin CDD)
               └──────────────┬────────────────┘
                              │ Validation DAF
                              ▼
               ┌───────────────────────────────┐
               │     Clôture Automatisée       │
               │ • Contrat → ROMPU             │
               │ • Affectation → FERMEE        │
               │ • Employé → est_actif = FALSE │
               │ • Certificat & STC générés    │
               └───────────────────────────────┘
```

---

## 10. Workflow Cible — Contrats & Carrière

```text
[Salarié sans contrat actif] ──► [Bouton "Initier l'embauche" visible]
                                        │
                                        ▼
                               [Saisie Contrat + Affectation + Essai]
                                        │
                                        ▼
                               [Workflow BROUILLON créé]
                                        │
                                        ▼
                               [Examen & Validation DAF]
                                        │
        ┌───────────────────────────────┴───────────────────────────────┐
        ▼                                                               ▼
[Date Effet <= Aujourd'hui]                                     [Date Effet > Aujourd'hui]
        │                                                               │
        ▼                                                               ▼
• Contrat ACTIF                                                 • Workflow PLANIFIÉ
• Affectation ACTIVE                                            • Application automatique
• Employé ACTIF                                                   par Cron à date d'effet
```

---

## 11. Workflow Cible — Congés & Absences

```text
               ┌───────────────────────────────┐
               │ 1. Demande de Congé Utilisateur│
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 2. Simulation Serveur         │ (/api/conges/simulate-jours)
               │ • Jours ouvrables / fériés    │
               │ • Vérification Solde          │
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 3. Contrôle Atomique Serveur  │ (DemandeConge.controler())
               │ • Plage couverte par contrat  │
               │ • Non-chevauchement dates     │
               │ • Préavis légal               │
               │ • Plafonds Maternité / Pool   │
               └──────────────┬────────────────┘
                              │ Succès
                              ▼
               ┌───────────────────────────────┐
               │ 4. Persistance (EN_ATTENTE)   │
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 5. Décision Valideur RH / DAF │
               └──────┬─────────────────┬──────┘
                      │                 │
             Valider  ▼                 ▼  Refuser / Annuler
    ┌─────────────────────────┐  ┌─────────────────────────┐
    │ Re-contrôle controler() │  │ Demande → REFUSEE       │
    │ Débit MouvementSolde    │  │ Solde intact            │
    │ Demande → VALIDE        │  └─────────────────────────┘
    └─────────────────────────┘
```

---

## 12. Workflow Cible — Pointage & Présence

```text
               ┌───────────────────────────────┐
               │ 1. Sélection Date & Site      │
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 2. Chargement Grille Éligible │
               │ • Jointure Contrat ACTIF      │
               │ • Affectation ACTIVE au Site  │
               │ • Congés validés pré-remplis  │
               │ • Fériés pré-remplis          │
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 3. Saisie & Validation Inline │ (Heures, pauses, indemnités repas/déplacement)
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 4. Batch Save avec Verrou     │
               │ • Vérif: Période Paie OUVERTE │ (Rejet si VALIDEE/CLOTUREE)
               │ • Optimistic Locking (version)│ (Rejet si conflit simultané)
               │ • Calcul serveur des montants │
               └──────────────┬────────────────┘
                              │
                              ▼
               ┌───────────────────────────────┐
               │ 5. Synthèse & Gel de Période  │
               │ • Clôture période par DAF     │
               │ • Gel immuable pointage_cumul │
               │ • Transmission Paie           │
               └───────────────────────────────┘
```

---

## 13. Architecture Cross-Module Cible

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                                 EMPLOYÉ                                     │
│  (Identité civile, matricule, contacts, documents administratifs)           │
└───────┬───────────────────────────────┬─────────────────────────────┬───────┘
        │ 1:N                           │ 1:N                         │ 1:N
        ▼                               ▼                             ▼
┌──────────────────┐            ┌──────────────────┐          ┌───────────────┐
│ CONTRAT TRAVAIL  │            │  DEMANDE CONGÉ   │          │ POINTAGE JOUR │
│ Statut: ACTIF    │◄──Garde───┤ Statut: VALIDE   │          │ Statut:PRESENT│
│ [date_deb, fin]  │   Couvert  │ Ledger: PRISE    │          │ [heures, ind] │
└───────┬──────────┘            └────────┬─────────┘          └───────┬───────┘
        │                             │                            │
        ▼                                │                            │
┌──────────────────┐                     │                            │
│   AFFECTATION    │                     │                            │
│ Site, Fonction,  │                     │                            │
│ Salaire base     │                     │                            │
└───────┬──────────┘                     │                            │
        │                                │                            │
        └───────────────────┬────────────┴────────────────────────────┘
                            │
                            ▼ Agrégation mensuelle
                ┌──────────────────────────────┐
                │       PÉRIODE DE PAIE        │
                │ Statut: EN_COURS / VALIDEE   │
                └──────────────┬───────────────┘
                               │
                               ▼ Snapshot Immuable
                ┌──────────────────────────────┐
                │         FUTURE PAIE          │
                │ • Salaire de base historique │
                │ • Cumul Heures Normales & HS │
                │ • Cumul Nuit & Retards       │
                │ • Indemnités Déplacement/Rep │
                │ • Déductions Congés / Abs    │
                │ • Cotisations & Net à payer  │
                └──────────────────────────────┘
```

---

## 14. Architecture UX Cible

* **Navigation Globale** :
  * Topbar épurée avec breadcrumb normalisé (`PageHeader`), bouton de changement de thème, sélecteur de rôle/site et cloche de notification interactive.
  * Sidebar structurée par domaines : *Tableau de bord*, *Collaborateurs*, *Carrières & Contrats*, *Congés & Absences*, *Pointage & Présence*, *Paramètres*.
* **Gestion du Contexte Salarié** :
  * Bannière de contexte salarié (`EmployeHeaderCard`) affichant l'avatar, le matricule, le statut civil, le contrat actif, le site d'affectation et les actions directes autorisées selon le rôle de l'utilisateur.
* **Formulaires & Dialogues** :
  * Modales unifiées via `Modal.jsx` (max-width adaptatif, header sticky, footer d'actions standardisé avec bouton primaire à droite, focus trap et gestion clavier `Escape` / `Tab`).
  * Validation inline avec messages d'erreurs sous les champs en rouge sémantique dès la saisie (debounced).

---

## 15. Wireframes Cibles (ASCII)

### 15.1 Tableau de Bord (Dashboard)

```text
+-----------------------------------------------------------------------------------------------+
| GALAXAM SIRH  |  Tableau de bord                                           [🔔 3] [Admin ▼]  |
+-----------------------------------------------------------------------------------------------+
| [Collaborateurs]  [Carrières]  [Congés]  [Pointage]  [Paramètres]                             |
+-----------------------------------------------------------------------------------------------+
| VUE D'ENSEMBLE                                                                                |
| +-------------------+ +-------------------+ +-------------------+ +-------------------------+ |
| | Collaborateurs    | | Contrats Actifs   | | Demandes Congés   | | Pointage Aujourd'hui    | |
| | 148 Actifs (2 In) | | 142 CDI / 6 CDD   | | 4 En attente      | | 138 Présents / 4 Retards| |
| +-------------------+ +-------------------+ +-------------------+ +-------------------------+ |
|                                                                                               |
| ACTIONS REQUISES                                          ALERTES & ÉCHÉANCES                 |
| +-------------------------------------------------------+ +---------------------------------+ |
| | [Congé] Jean Dupont · Annuel (3j)    [Refuser] [Valider] | | ⚠️ 2 Périodes d'essai à échéance| |
| | [Contrat] Mutation Site Tamatave     [Voir Brouillon] | | ⚠️ 1 Fin de CDD dans 15 jours   | |
| | [Pointage] 3 anomalies de pointage   [Régulariser]    | | 🔒 Période Paie Août : À valider| |
| +-------------------------------------------------------+ +---------------------------------+ |
+-----------------------------------------------------------------------------------------------+
```

### 15.2 Liste des Employés (`EmployeList.jsx`)

```text
+-----------------------------------------------------------------------------------------------+
| Collaborateurs > Liste du Personnel                                   [+ Nouvel Employé]     |
+-----------------------------------------------------------------------------------------------+
| [🔍 Rechercher nom, matricule...] [Site: Tous ▼] [Département: Tous ▼] [Statut: Actifs ▼]     |
+-----------------------------------------------------------------------------------------------+
| Matricule | Nom & Prénoms       | Site / Dépt        | Contrat En Cours | Statut   | Actions   |
|-----------|---------------------|--------------------|------------------|----------|-----------|
| EMP-00142 | RABE Jean Michel    | Siège / Logistique | CDI (Actif)      | [ACTIF]  | [Voir 👁️] |
| EMP-00143 | RASOA Marie         | Tamatave / Admin   | CDD (Fin 30/09)  | [ACTIF]  | [Voir 👁️] |
| EMP-00110 | ANDRIA Hery         | Majunga / Transit  | Aucun (Rompu)    | [INACTIF]| [Voir 👁️] |
+-----------------------------------------------------------------------------------------------+
| Affichage 1-10 sur 148 collaborateurs                  [Précédent] Page [ 1 ] sur 15 [Suivant]|
+-----------------------------------------------------------------------------------------------+
```

### 15.3 Fiche Salarié — Vue Carrière (`CarriereTab.jsx`)

```text
+-----------------------------------------------------------------------------------------------+
| Collaborateurs > RABE Jean Michel (EMP-00142) > Carrière & Contrats                          |
+-----------------------------------------------------------------------------------------------+
| [👤 RABE Jean Michel]  CDI Actif depuis 01/02/2024 · Site Siège · Salaire : 1 200 000 Ar/m    |
| Onglets : [Fiche Personnelle] [Carrière & Contrats (Actif)] [Congés] [Pointage] [Documents]   |
+-----------------------------------------------------------------------------------------------+
| SITUATION CONTRACTUELLE ACTUELLE                                                              |
| +-------------------------------------------------------------------------------------------+ |
| | Contrat N° CT-2024-0089 · Type : CDI · Statut : [ACTIF]                                  | |
| | Affectation : Responsable Logistique · Département : Logistique · Site : Siège           | |
| | Actions Carrière : [📈 Mutation / Promotion] [🔄 Renouveler] [🛑 Clôturer / Rompre Contrat] | |
| +-------------------------------------------------------------------------------------------+ |
|                                                                                               |
| HISTORIQUE DES MOUVEMENTS RH & TIMELINE                                                       |
| • 01/02/2024 : Embauche Initiale (CDI) · Validé par DAF                                       |
| • 01/08/2024 : Confirmation Fin de Période d'Essai · Validé par DAF                           |
+-----------------------------------------------------------------------------------------------+
```

### 15.4 Saisie Quotidienne du Pointage (`SaisieQuotidiennePage.jsx`)

```text
+-----------------------------------------------------------------------------------------------+
| Pointage > Saisie Quotidienne                                                                 |
+-----------------------------------------------------------------------------------------------+
| Date : [ 2026-09-01 📅 ]  Site : [ Siège Antananarivo ▼ ]  Période : [ Août 2026 (OUVERTE) 🟢]|
+-----------------------------------------------------------------------------------------------+
| Stepper : (1) Sélection Date/Site ──► (2) Saisie Présences ──► (3) Contrôle ──► (4) Sauvegarde|
+-----------------------------------------------------------------------------------------------+
| Matr.   | Collaborateur  | Matin In-Out | AM In-Out   | Total | Retard | Repas | Dépl | Statut|
|---------|----------------|--------------|-------------|-------|--------|-------|------|-------|
| EMP-142 | RABE Jean      | 08:00 - 12:00| 13:00 - 17:00| 8.0h  | 0 min  | [x]   | [x]  | [OK]  |
| EMP-143 | RASOA Marie    | 08:25 - 12:00| 13:00 - 17:00| 7.6h  | 25 min | [x]   | [x]  | [RET] |
| EMP-144 | RAKOTO Paul    | [ CONGÉ ANNUEL VALIDÉ DU 01/09 AU 05/09 ]           | [CONGÉ]|
+-----------------------------------------------------------------------------------------------+
| Total Présents : 42 | Retards : 2 | Congés : 1 | Anomalies : 0     [💾 Enregistrer le Lot]     |
+-----------------------------------------------------------------------------------------------+
```

### 15.5 Synthèse Mensuelle & Préparation Paie (`SynthesePointagePage.jsx`)

```text
+-----------------------------------------------------------------------------------------------+
| Pointage > Synthèse Mensuelle & Validation Paie                                               |
+-----------------------------------------------------------------------------------------------+
| Période : [ Août 2026 (01/08 - 31/08) ▼ ]  Site : [ Tous ▼ ]  Statut : [ À TRANSMETTRE DAF 🟡]|
+-----------------------------------------------------------------------------------------------+
| Matricule | Nom & Prénoms  | J.Ouv | J.Prés | HS 30% | HS 50% | Nuit  | Ind.Repas | Ind.Dépl. |
|-----------|----------------|-------|--------|--------|--------|-------|-----------|-----------|
| EMP-00142 | RABE Jean      | 22 j  | 22 j   | 4.0 h  | 0.0 h  | 0.0 h | 44 000 Ar | 31 200 Ar |
| EMP-00143 | RASOA Marie    | 22 j  | 21 j   | 0.0 h  | 2.0 h  | 3.5 h | 42 000 Ar | 31 200 Ar |
+-----------------------------------------------------------------------------------------------+
| Totaux Période : 148 Salariés | HS30: 124h | HS50: 38h | Indemnités: 11 450 000 Ar            |
| Actions DAF : [📄 Exporter CSV Paie] [🔒 Valider & Geler la Période (Snapshot Immuable)]     |
+-----------------------------------------------------------------------------------------------+
```

---

## 16. Design System Cible

### Palette Sémantique & Tokens CSS (`index.css`)
```css
/* Surface & Typographie */
--color-surface: #ffffff;
--color-surface-subtle: #f8fafc;
--color-border: #e2e8f0;
--color-border-subtle: #f1f5f9;
--color-fg: #0f172a;
--color-fg-muted: #64748b;

/* Marque & Accents */
--color-brand-500: #1e3a8a; /* Deep Blue Galaxam */
--color-brand-600: #1e293b;
--color-brand-50: #eff6ff;

/* Statuts Fonctionnels */
--color-success-500: #16a34a; /* Validé, Actif, Présent */
--color-warning-500: #d97706; /* En attente, Retard, Brouillon */
--color-danger-500:  #dc2626; /* Rompu, Refusé, Anomalie, Inactif */
--color-info-500:    #2563eb; /* Planifié, Information */
```

### Règles des Composants UI
* **Boutons (`Button.jsx`)** : Toujours associer un `variant` sémantique (`primary`, `secondary`, `danger`, `ghost`, `outline`) et une taille stricte (`xs`, `sm`, `md`, `lg`).
* **Badges (`Badge.jsx`)** : Tolérer `primary`, `secondary`, `success`, `warning`, `danger`, `info`. Pas de variant inconnu. Toujours du texte uppercase contrasté.
* **Champs de formulaire (`Input.jsx`, `Select.jsx`)** : Intégrer l'affichage du label, du message d'aide et du message d'erreur avec `aria-invalid` et `aria-describedby`.
* **Modales (`Modal.jsx`)** : Structure fixe `Header / Body (scrollable) / Footer`, piège à focus automatique et accessibilité clavier garantie.

---

## 17. Plan Directeur de Modification Séquentielle

Toutes les modifications futures devront suivre scrupuleusement cet ordre chronologique :

```
1. INTÉGRITÉ BACKEND (P0)
   ├── 1.1 Ajouter d.controler(c) dans CongeService.creerDemande et DemandeConge.valider
   ├── 1.2 Ajouter le contrôle de contrat ACTIF dans DemandeConge.controler
   ├── 1.3 Sécuriser PointageSaisieService.getGrilleSaisie avec JOIN contrat_travail ACTIF
   ├── 1.4 Verrouiller PointageSaisieService.enregistrerPointagesBatch contre périodes fermées
   ├── 1.5 Implémenter le contrôle de concurrence / optimistic locking sur pointage_jour
   └── 1.6 Sécuriser / désactiver les routes PUT /employes/{id}/desactiver & activer directes

2. TESTS D'INTÉGRATION BACKEND
   ├── 2.1 Tests de rejet des congés sans contrat couvrant ou chevauchants
   ├── 2.2 Tests de non-éligibilité au pointage sans contrat actif
   └── 2.3 Tests de rejet de modification sur période paie VALIDEE

3. SÉCURITÉ & LOGIQUE FRONTEND CRITIQUE (P1)
   ├── 3.1 Corriger canManage = ... || true dans PeriodePaiePage.jsx
   ├── 3.2 Corriger le lien breadcrumb '/' vers '/dashboard'
   ├── 3.3 Remplacer la simulation de congé locale par l'appel API simulateur
   └── 3.4 Supprimer la méthode morte contratService.initierRupture

4. NAVIGATION & ÉTAT D'URL (P2)
   ├── 4.1 Synchronisation des filtres et pagination dans l'URL (EmployeList, Pointage, Congés)
   └── 4.2 Liens contextuels fluides entre fiche employé, contrats, congés et pointage

5. FORMULAIRES & EXPÉRIENCE UTILISATEUR (P2)
   ├── 5.1 Validation debounced synchrone dans les formulaires
   ├── 5.2 Découpage des composants monolithiques (EmployeList, EmployeFicheDetail)
   └── 5.3 Standardisation de DemandeCongeModal sur Modal.jsx

6. ACCESSIBILITÉ & DESIGN SYSTEM (P3)
   ├── 6.1 Focus trap, ARIA et touches clavier sur Modal.jsx et Dialog.jsx
   ├── 6.2 Nettoyage des couleurs Tailwind hardcodées au profit des tokens CSS
   └── 6.3 Optimisation responsive des tableaux de pointage

7. CONSOLIDATION PRÉPARATION PAIE
   ├── 7.1 Immuabilité stricte de pointage_cumul_periode
   └── 7.2 Moteur de snapshot salarial pour bulletin de paie
```

---

## 18. Liste Exhaustive des Fichiers Impactés

### Backend (Spring Boot / Java & SQL)
* **Contrôleurs REST** :
  * `backend/src/main/java/mg/galaxam/sirh/api/controller/conge/DemandeCongeController.java`
  * `backend/src/main/java/mg/galaxam/sirh/api/controller/pointage/PointageSaisieController.java`
  * `backend/src/main/java/mg/galaxam/sirh/api/controller/pointage/PointageAnomalieController.java`
  * `backend/src/main/java/mg/galaxam/sirh/api/controller/pointage/PeriodePaieController.java`
  * `backend/src/main/java/mg/galaxam/sirh/api/controller/employe/EmployeController.java`
* **Services Métier & Moteurs** :
  * `backend/src/main/java/mg/galaxam/sirh/model/conge/service/CongeService.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSaisieService.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageSyntheseService.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/pointage/service/PointageAnomalieService.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/contrat/WorkflowRhService.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/employe/service/EmployeService.java`
* **Entités & Modèles Métier** :
  * `backend/src/main/java/mg/galaxam/sirh/model/conge/DemandeConge.java`
  * `backend/src/main/java/mg/galaxam/sirh/model/employe/Employe.java`
* **Tests d'Intégration** :
  * `backend/src/test/java/mg/galaxam/sirh/model/conge/DemandeCongeIntegrationTest.java` (à enrichir)
  * `backend/src/test/java/mg/galaxam/sirh/model/pointage/PointageWorkflowDbIntegrationTest.java`

### Frontend (React 18 / Vite / Tailwind)
* **Pages** :
  * `frontend/src/pages/pointage/PeriodePaiePage.jsx`
  * `frontend/src/pages/pointage/SaisieQuotidiennePage.jsx`
  * `frontend/src/pages/pointage/SynthesePointagePage.jsx`
  * `frontend/src/pages/employe/EmployeList.jsx`
  * `frontend/src/pages/employe/EmployeFicheDetailPage.jsx`
  * `frontend/src/pages/conges/GestionCongesPage.jsx`
* **Composants & Modales** :
  * `frontend/src/components/conges/DemandeCongeModal.jsx`
  * `frontend/src/components/ui/overlays/Modal.jsx`
  * `frontend/src/components/ui/overlays/Dialog.jsx`
  * `frontend/src/components/ui/data-display/Badge.jsx`
  * `frontend/src/components/ui/navigation/Breadcrumb.jsx`
  * `frontend/src/components/ui/navigation/PageHeader.jsx`
  * `frontend/src/components/carriere/timeline/ContratActifSection.jsx`
* **Hooks & Services API** :
  * `frontend/src/hooks/conges/useDemandeCongeForm.js`
  * `frontend/src/hooks/carriere/useCarriereModals.js`
  * `frontend/src/hooks/pointage/usePointageSaisie.js`
  * `frontend/src/api/employe/contratService.js`
  * `frontend/src/api/employe/employeService.js`
  * `frontend/src/api/congeService.js`
* **Styles & Thème** :
  * `frontend/src/index.css`

---

## 19. Verdict Arbitral Définitif

### **B — Prêt pour correction backend, puis UX**

### Justification Détaillée :
1. **Les fondations fonctionnelles globales sont remarquablement saines** : le modèle de données, les référentiels conventionnels malgaches, le circuit d'approbation DAF et les calculs de majoration de nuit et d'heures supplémentaires sont fidèlement implantés dans le backend.
2. **Cependant, le système ne peut pas être modifié sur le plan UI/UX sans corriger au préalable les 5 vulnérabilités P0 identifiées** (création de congés sans `controler()`, pointage sans jointure de contrat actif, absence de verrou serveur sur période fermée, réactivation/désactivation directe hors workflow, et concurrence sur batch save).
3. Procéder à une refonte d'interface avant de consolider ces contrôles backend exposerait le SIRH à des fuites de données de paie et à des anomalies d'intégrité masquées par une interface rafraîchie.
4. Dès que la Phase 1 (Intégrité Backend) et la Phase 2 (Tests d'Intégration) seront achevées, le système sera immédiatement prêt pour le déploiement complet des améliorations UX et du Design System.
