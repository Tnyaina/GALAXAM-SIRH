# CARTE DES WORKFLOWS RUNTIME DU DOMAINE CONGÉS & TCONGE
**Date :** 17 Septembre 2026  
**Référence :** Phase 2A-2 Audit Contradictoire Complet  
**Système :** GALAXAM SIRH  

---

## 1. VUE D'ENSEMBLE DU RUNTIME ACTUEL

Le schéma ci-dessous retrace l'intégralité des flux réels traversant le système, du frontend aux tables de base de données :

```text
               +-------------------------------------------+
               |            FRONTEND REACT / VITE          |
               |                                           |
               | [GestionCongesPage]      [CongesTab]      |
               |        |                      |           |
               +--------|----------------------|-----------+
                        |                      |
                        v                      v
         +---------------------------------------------------+
         |             API CLIENT (congeService.js)          |
         +---------------------------------------------------+
                                   |
                  +----------------+----------------+
                  |                                 |
                  v                                 v
   +------------------------------+  +------------------------------+
   |    DemandeCongeController    |  |     CongeSoldeController     |
   |                              |  |                              |
   | - POST /simuler              |  | - GET /planning              |
   | - POST /verifier-conflits    |  | - GET /solde/employe/{id}    |
   | - POST / (créer)             |  | - GET /mouvements/employe/{id|
   | - POST /{id}/valider         |  | - POST /solde/ajustement     |
   | - POST /{id}/annuler         |  | - POST /purge-expiration     |
   +------------------------------+  +------------------------------+
                  |                                 |
                  +----------------+----------------+
                                   |
                                   v
             +--------------------------------------------+
             |                 BACKEND                    |
             |                                            |
             |  CongeService                              |
             |  ├── CongeCalculateurEngine                |
             |  ├── CalendrierOuvrableEngine              |
             |  └── DemandeConge                          |
             +--------------------------------------------+
                                   |
           +-----------------------+-----------------------+
           |                                               |
           v                                               v
+-----------------------+                       +-----------------------+
|     demande_conge     |                       | mouvement_solde_conge |
|                       |                       |                       |
| - id_statut: VALIDE   |                       | - type: PRISE (-)     |
| - nb_jours_decomptes  |                       | - type: ANNULATION (+)|
+-----------------------+                       | - type: ACQUISITION(+)|
           |                                    +-----------------------+
           |                                               |
           v                                               v
+------------------------------------+          +-----------------------+
|       POINTAGE QUOTIDIEN           |          |  v_solde_conge_employe|
|                                    |          |                       |
| PointageSaisieService              |          | - solde_actuel        |
| - Lit demande_conge (VALIDE)       |          | - total_acquis        |
| - Assigne pointage_jour: CONGE     |          | - total_pris          |
+------------------------------------+          +-----------------------+
           |
           v
+------------------------------------+
|       SYNTHÈSE POINTAGE            |
|                                    |
| PointageSyntheseService            |
| - Compte jours ouvrés non fériés   |
| - Exclut week-ends & fériés        |
| - Génère pointage_synthese_employe |
+------------------------------------+
           |
           v
+------------------------------------+
|    GEL DU POINTAGE (SNAPSHOT)      |
|                                    |
| pointage_snapshot_mensuel          |
| - nb_jours_conge scellé            |
+------------------------------------+
           |
           +---------------------------------------+
           |                                       |
           v                                       v
+------------------------------------+  +------------------------------------+
|              PAIE                  |  |                STC                 |
|                                    |  |                                    |
| PayrollService.preparerInput       |  | StcService.preparerStc             |
| - Lit pointageSnapshot.conges      |  | - Lit v_solde_conge_employe        |
| - Lit TCONGE depuis Grand-Livre    |  | - Lit TCONGE depuis Grand-Livre    |
|                                    |  |                                    |
| PayrollExecutionEngine             |  | Moteur STC                         |
| - RETENUE_CONGE   = Base / 30      |  | - ICCP = SoldeRestant * TCONGE     |
| - INDEMNITE_CONGE = Jours * TCONGE |  |                                    |
|                                    |  +------------------------------------+
| Clôture Paie                       |
| - PayrollService.mettreAJourJournal|
| - UPSERT conge_journal_droit       |
+------------------------------------+
```

---

## 2. DÉTAIL DES CHEMINS D'EXÉCUTION RUNTIME

### Flux 1 : Simulation et Contrôle de Conflits
- **Déclencheur :** Utilisateur ouvrant le formulaire de congé dans `DemandeCongeModal.jsx`.
- **Chemin :**
  1. `POST /conges/demandes/simuler` $\to$ `DemandeCongeController.simulerDemande`
  2. `CongeService.simulerNombreJours` $\to$ `CongeCalculateurEngine.calculerNombreJours`
  3. Retour : `nbJoursDecomptes` (calendaires bruts).
  4. `POST /conges/demandes/verifier-conflits` $\to$ `DemandeCongeController.verifierConflits`
  5. `CongeService.verifierConflitsDemande` :
     - Vérifie chevauchement personnel sur `demande_conge`.
     - Vérifie collègues du même poste et même site sur `employe_affectation_carriere`.
     - Calcule `nbJoursOuvres`, `nbJoursFeries`, `nbJoursWeekEnd` via `CalendrierOuvrableEngine`.
     - Récupère solde actuel via `VSoldeCongeEmploye`.
  6. Retour : Objet complet d'aide à la décision.

### Flux 2 : Création de Demande de Congé
- **Déclencheur :** Validation du formulaire de demande.
- **Chemin :**
  1. `POST /conges/demandes` $\to$ `DemandeCongeController.creerDemande`
  2. `CongeService.creerDemande` :
     - Assigne statut `EN_ATTENTE`.
     - Vérifie si arrêt maladie $>30$ jours (`CongeCalculateurEngine.doitScinderArretMaladie`).
     - Si oui : scinde en 2 demandes (30j maintien + reliquat CNaPS sans solde).
     - Si non : `DemandeConge.save(c)` $\to$ `INSERT INTO demande_conge`.
     - Crée une notification RH (`NotificationRhService.creer`).
  3. **Impact DB :** 1 ligne insérée dans `demande_conge`. Aucun mouvement de solde n'est généré.

### Flux 3 : Approbation / Validation RH
- **Déclencheur :** Clic "Valider" sur `DemandesValidationTab.jsx`.
- **Chemin :**
  1. `POST /conges/demandes/{id}/valider` $\to$ `DemandeCongeController.validerDemande`
  2. `CongeService.validerDemande` $\to$ `DemandeConge.valider(currentUserId, c)`
  3. `DemandeConge` :
     - `UPDATE demande_conge SET id_statut_demande_conge = (SELECT id FROM ref_statut_demande_conge WHERE code = 'VALIDE')`
     - Contrôle solde négatif autorisé (`ParametreGlobal.PLAFOND_SOLDE_CONGE_NEGATIF`).
     - **Imputation immédiate :**
       ```sql
       INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, id_demande_conge, ...)
       VALUES (?, 'PRISE', -nb_jours_decomptes, ?, ...);
       ```
  4. **Impact DB :** Le solde dans `v_solde_conge_employe` est immédiatement débité.

### Flux 4 : Annulation de Congé
- **Déclencheur :** Clic "Annuler" sur une demande validée ou en attente.
- **Chemin :**
  1. `POST /conges/demandes/{id}/annuler` $\to$ `DemandeCongeController.annulerDemande`
  2. `CongeService.annulerDemande` $\to$ `DemandeConge.annuler(currentUserId, motif, c)`
  3. `DemandeConge` :
     - Vérifie que la demande n'est pas déjà `ANNULE` ou `REFUSE`.
     - Si la demande était `VALIDE` :
       ```sql
       INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, id_demande_conge, ...)
       VALUES (?, 'ANNULATION', +nb_jours_decomptes, ?, ...);
       ```
     - `UPDATE demande_conge SET id_statut_demande_conge = (SELECT id FROM ref_statut_demande_conge WHERE code = 'ANNULE')`
  4. **Anomalie Runtime :** Aucun contrôle n'interdit l'annulation d'un congé rattaché à une période de paie déjà clôturée.

### Flux 5 : Passerelle Congé $\to$ Pointage Quotidien
- **Déclencheur :** Génération ou consultation de la grille journalière de pointage.
- **Chemin :**
  1. `PointageSaisieService.getSaisieJournaliere(idSite, date)`
  2. Exécute :
     ```sql
     SELECT tc.libelle, tc.code, tc.est_paye, tc.decompte_solde_acquis, dc.est_demi_journee
     FROM demande_conge dc
     JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge
     JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge
     WHERE dc.id_employe = ? AND ? BETWEEN dc.date_debut AND dc.date_fin
       AND sc.code IN ('VALIDE', 'APPROUVEE');
     ```
  3. Si trouvé :
     - Si `decompte_solde_acquis = true` $\to$ Statut = `CONGE` (0h).
     - Si `decompte_solde_acquis = false` et payé $\to$ Statut = `DISPENSE_POINTAGE` (8h).
     - Si non payé $\to$ Statut = `ABSENCE_JUSTIFIEE` (0h).
  4. Enregistré dans `pointage_jour`.

### Flux 6 : Synthèse Mensuelle & Gel Pointage
- **Déclencheur :** Clôture de la période de pointage par le gestionnaire RH / DAF.
- **Chemin :**
  1. `PointageSyntheseService.calculerSyntheseMensuelle(idEmploye, idPeriode)`
  2. Parcourt les `DailyAttendanceResult` :
     - Ne compte comme congé que si `!isWeekend && !isFerie`.
     - `joursCongeTroncon += 1.0` (ou `0.5`).
  3. Déduit ce nombre du seuil d'heures attendu de la semaine (`expectedMinutes`).
  4. `PointageSyntheseService.figerPointage` :
     - Insère dans `pointage_synthese_employe` (`nb_jours_conge = conges`).
     - Insère dans `pointage_snapshot_mensuel`.

### Flux 7 : Consommation en Paie & Clôture
- **Déclencheur :** Calcul et clôture de la campagne de paie.
- **Chemin :**
  1. `PayrollService.preparerInput` :
     - Récupère le snapshot gelé du pointage (`nb_jours_conge`).
  2. `PayrollService.calculerEmploye` :
     - Résout TCONGE via `StcService.getTauxJournalierApplicable(idEmploye, dateFinPeriode, salaireBase, c)`.
     - Exécute `SELECT taux_journalier_conge FROM conge_journal_droit WHERE id_employe = ? AND (annee < ? OR (annee = ? AND mois < ?)) ORDER BY annee DESC, mois DESC LIMIT 1`.
  3. `PayrollExecutionEngine.calculerConges` :
     - `RETENUE_CONGE` = `nbJoursConges * (salaireBase / 30.0)`
     - `INDEMNITE_CONGE` = `nbJoursConges * TCONGE`
  4. `PayrollService.cloturerDonneesPaie` :
     - Appelle `PayrollService.mettreAJourJournalConge(idPeriodePaie, c)`.
     - Calcule le droit acquis du mois : `2.5 * (30 - absInjustifiees) / 30`.
     - Calcule la valorisation du mois : `baseTotale * droitMois / 30`.
     - Calcule le nouveau TCONGE : `cumulValorisation / cumulJoursDroit`.
     - `UPSERT INTO conge_journal_droit`.

### Flux 8 : Liquidation STC (Solde de Tout Compte)
- **Déclencheur :** Préparation du reçu pour solde de tout compte d'un salarié sortant.
- **Chemin :**
  1. `StcService.preparerStc` $\to$ Récupère `VSoldeCongeEmploye.rechercherParEmploye(idEmploye, c)`.
  2. Récupère le TCONGE applicable : `StcService.getTauxJournalierApplicable(idEmploye, dateDepart, base, c)`.
  3. Calcule l'Indemnité Compensatrice de Congés Payés :
     `ICCP = soldeJoursRestant * TCONGE`
  4. Sauvegarde dans `paie_stc`.
