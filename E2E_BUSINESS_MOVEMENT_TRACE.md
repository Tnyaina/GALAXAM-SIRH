# Traçabilité des Mouvements Métier et Transitions d'État E2E

Ce document retrace l'intégralité des flux et mouvements métier exécutés au cours des 6 scénarios de certification E2E, les transitions de statut dans la base de données PostgreSQL, les snapshots figés et l'intégrité comptable.

---

## 1. Vue Synthétique des Scénarios E2E

| N° | Fichier de Test Playwright | Description Fonctionnelle | Statut | Mouvements & Effets Métier |
|---|---|---|:---:|---|
| **1** | `01-auth-and-rbac.spec.js` | Authentification JWT, Ségrégation des Rôles & Contrôle d'Accès |  **SUCCÈS** | Émission de tokens signés HS512, validation des claims, redirection conditionnelle selon profil. |
| **2** | `02-pointage-workflow.spec.js` | Saisie Quotidienne, Détection d'Anomalies & Persistance Batch |  **SUCCÈS** | Insertion de pointages en base (`pointage_jour`), détection automatique d'anomalie de dépassement. |
| **3** | `03-pointage-validation-cycle.spec.js` | Régularisation, Transmission (`SUBMIT`), Rejet DAF (`REJECT`) et Scellement (`APPROVE`) |  **SUCCÈS** | Transition de cycle de pointage, archivage du motif de rejet, scellement irréversible dans `pointage_cumul_periode` (statut `GELE`). |
| **4** | `04-conges-workflow.spec.js` | Demande de Congé, Audit Financier Interactif, Validation DAF et Imputation Ledger |  **SUCCÈS** | Débit de solde de congé (`15.00` → `13.00 j`), calcul ventilation Déduction Base/30 vs Indemnité TCONGE, écriture dans le grand livre `conge_journal_droit`. |
| **5** | `05-paie-workflow.spec.js` | Cycle Complet Paie en 7 Étapes : Préparation, Simulation, Calcul Réel, Contrôle, Validation et Clôture |  **SUCCÈS** | Calcul officiel de 4 bulletins, masse salariale brute 1 084 598.75 Ar, net à payer 1 068 770.00 Ar, passage `CALCULE` → `VALIDE` → `CLOTUREE`. |
| **6** | `06-movement-and-resilience.spec.js` | Robustesse Système : Étanchéité RBAC, URL Tampering, Résilience F5 et Navigation Transversale |  **SUCCÈS** | Blocage des accès non autorisés, tolérance aux identifiants invalides, intégrité du cache après F5. |

---

## 2. Déroulé Chronologique des Mouvements Métier (Période PER-2026-09-E2E)

### Phase 1 : Cycle du Pointage et Gel du Snapshot (Scénarios 2 & 3)
1. **Saisie quotidienne** : L'Agent Pointage saisit les heures des salariés pour la période du 01/09/2026 au 15/09/2026.
2. **Détection d'anomalie** : Une anomalie de saisie est levée sur `EMP002`. La machine d'état bloque toute transmission (`SUBMIT`) tant que l'anomalie est active.
3. **Régularisation** : L'anomalie est régularisée avec motif explicite.
4. **Transmission (`SUBMIT`)** : La période passe de `EN_COURS` à `TRANSMIS_DAF`. La saisie quotidienne est immédiatement verrouillée pour l'Agent.
5. **Rejet DAF (`REJECT`)** : La DAF constate un écart et renvoie la période avec motif. La période repasse en `EN_COURS`, déverrouillant la saisie pour correction.
6. **Re-transmission & Approbation DAF (`APPROVE`)** : La période passe au statut `VALIDEE`.
7. **Scellement du snapshot** : Le cumul des heures est scellé dans `pointage_cumul_periode` avec `statut_snapshot = 'GELE'`. Dès cet instant, toute modification physique des pointages est interdite.

### Phase 2 : Workflow des Congés Payés (Scénario 4)
1. **Consultation du solde** : Vérification du solde initial de `EMP003` (15.00 jours acquis).
2. **Dépôt de la demande** : Demande de 2 jours ouvrables de congé payé du 07/09/2026 au 08/09/2026.
3. **Audit comptable DAF** : La modale d'audit affiche la ventilation financière :
   - Déduction congé (1/30ème) : `30 000.00 Ar` (2 j × 15 000 Ar/j)
   - Indemnité de maintien (taux TCONGE) : `30 000.00 Ar` (2 j × 15 000 Ar/j)
   - Impact net : `0 Ar` (maintien intégral de salaire).
4. **Validation et Imputation** : La DAF approuve la demande. Le solde restant passe immédiatement à `13.00 j` dans `mouvement_solde_conge` et `conge_journal_droit`.

### Phase 3 : Campagne de Paie Officielle en 7 Étapes (Scénario 5)
1. **Étape 1 — Préparation (`/paie/periodes/101/preparation`)** :
   - Prereq 1 : Snapshot pointage scellé & figé (`pointage_cumul_periode.statut_snapshot = 'GELE'`) → **CONFORME**.
   - Prereq 2 : Exercice fiscal & Barème progressif LF 2026 actif → **CONFORME**.
   - Prereq 3 : Moteur de calcul prêt → **CONFORME**.
   - Prereq 4 : Saisie des variables autorisée → **CONFORME**.
2. **Étape 2 — Population (`/paie/periodes/101/population`)** :
   - Résolution déterministe de 4 collaborateurs inclus : EMP001 (RAZAFY Jean), EMP002 (RABE Paul), EMP003 (RASOA Marie), EMP006 (RAKOTO Herizo).
3. **Étape 3 — Variables (`/paie/periodes/101/variables`)** :
   - Vérification de la disponibilité du formulaire de saisie d'ajustements / primes / acomptes.
4. **Étape 4 — Simulation & Calcul Réel (`/paie/periodes/101/calcul`)** :
   - **Simulation collective** : Exécution in-memory sans persistance. Agrégats vérifiés :
     - Masse Salariale Brute : `1 084 598.75 Ar`
     - Cotisations Salariales : `23 168.87 Ar`
     - Retenue IRSA (DGI) : `12 000.00 Ar`
     - Net à Payer Global : `1 068 770.00 Ar`
     - Charges Patronales : `207 550.66 Ar`
     - Coût Global Employeur : `1 311 494.85 Ar`
   - **Calcul réel officiel** : Persistance en base PostgreSQL des 4 bulletins au statut `CALCULE`.
5. **Étape 5 — Contrôle (`/paie/periodes/101/controle`)** :
   - Inspection de la grille collective des bulletins.
   - Ouverture du `BulletinPaieDrawer` pour EMP001 :
     - Salaire de base contractuel : `400 000.00 Ar`
     - Heures d'absence retenues : `88.00 h` (montant absence : `203 083.56 Ar`)
     - Total gains bruts : `233 842.96 Ar`
     - Cotisation CNaPS Salariale (1%) : `2 338.43 Ar`
     - Cotisation Santé OSTIE (1%) : `2 338.43 Ar`
     - Salaire Net Imposable : `229 166.10 Ar`
     - IRSA Net (minimum de perception) : `3 000.00 Ar`
     - **Net à Payer final vérifié** : `231 000.00 Ar`.
6. **Étape 6 — Validation DAF (`/paie/periodes/101/validation`)** :
   - Clic sur "Approuver les bulletins" : statut des 4 bulletins mis à jour de `CALCULE` à `VALIDE`.
   - La période passe au statut officiel `PAIE_VALIDEE`.
7. **Étape 7 — Clôture & Exports (`/paie/periodes/101/sorties`)** :
   - Clôture de la période (`CLOSE`) : passage au statut `CLOTUREE`.
   - Déclenchement du trigger d'immutabilité PostgreSQL `trg_bulletin_paie_immutabilite` : tout recalcul, modification ou suppression est désormais strictement bloqué au niveau SGBD.
   - Déverrouillage des exports réglementaires SAFI (ODS/CSV), EDI-ISI (XML), DNTS CNaPS (CSV) et ordre de virement bancaire collectif.
