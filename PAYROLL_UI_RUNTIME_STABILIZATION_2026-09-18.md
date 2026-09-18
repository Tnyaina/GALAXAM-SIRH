# PAYROLL UI RUNTIME STABILIZATION — RAPPORT D'EXÉCUTION & VERDICT FINAL
**Date :** 18 Septembre 2026  
**Environnement :** PostgreSQL `sirh_galaxam` • Spring Boot (`localhost:8080`, context-path `/api`) • Vite/React (`localhost:5173`)  
**Profil de test :** Directeur Administratif & Financier (`daf@galaxam.com`)  
**Verdict Global :** `PAYROLL_UI_RUNTIME_STABILIZATION_PASS`

---

## 1. SCOPE RÉEL DÉCOUVERT
Le périmètre d'investigation runtime et d'audit de stabilisation a couvert l'ensemble des modules Paie ainsi que les écrans RH directement ou indirectement connectés :

- **Campagne de Paie Stepper (7 étapes) :**
  - `/paie/periodes/:id/preparation` : Préparation de la période, verrous prérequis pointage scellé, dates d'arrêté.
  - `/paie/periodes/:id/population` : Liste des collaborateurs éligibles, détection entrées/sorties de période, types de contrat.
  - `/paie/periodes/:id/variables` : Variables de paie (primes, acomptes, avances, retenues diverses).
  - `/paie/periodes/:id/calcul` : Moteur de calcul batch/individuel, mode simulation vs réel.
  - `/paie/periodes/:id/controle` : Grille d'audit des bulletins, détection anomalies, écarts M-1, drawer latéral et impression A4.
  - `/paie/periodes/:id/validation` : Validation DAF, clôture comptable, réouverture avec traçabilité.
  - `/paie/periodes/:id/sorties` : Documents finaux, états de paie, export SAFI, DNTS CNaPS, virement bancaire.
- **Vues Paie Globales :**
  - `/paie` : Tableau de bord de campagne et sélection de période active.
  - `/paie?tab=campagne` : Vue stepper contextuelle.
  - `/paie?tab=variables` : Gestion globale des éléments variables de paie du mois.
  - `/paie?tab=periodes` : Liste des périodes et création/reprise.
  - `/paie?tab=cockpit` : Cockpit centralisé DAF et KPI financiers.
  - `/paie?tab=exports` : Restitutions légales et fichiers d'échange.
- **Interfaces RH Connectées :**
  - Employé → Paie (`/employes/:id/paie` ou `/employes/:id?tab=paie`) : Synthèse financière, historique bulletins, mouvements du mois, gestion des prêts, solde de tout compte.
  - Employé → Carrière & Contrats (`/employes/:id/carriere`) : Statut contractuel, salaire de base, rupture de contrat, déclencheur STC.
  - Employé → Congés (`/employes/:id/conges`) : Solde de congés, congés pris dans le mois (incidence TCONGE et déduction Base/30).
  - Employé → Pointage (`/employes/:id/pointage`) : Heures travaillées, déficit horaire, heures supp 30%/50%, majorations nuit.
  - Contrat → Rupture & STC (`StcPreparationModal.jsx`, `StcRecuModal.jsx`) : Calcul légal Art. 53, ICCP, préavis, licenciement, apurements prêts/avances, quittance libératoire imprimable.

---

## 2. SITEMAP FINAL

```text
/paie
├── ?tab=campagne                 (Stepper dynamique de campagne active)
│   ├── /periodes/:id/preparation (Étape 1 : Préparation & dates de paie)
│   ├── /periodes/:id/population  (Étape 2 : Population éligible & statuts contrats)
│   ├── /periodes/:id/variables   (Étape 3 : Saisie des primes, acomptes & déductions)
│   ├── /periodes/:id/calcul      (Étape 4 : Calcul de paie simulation / réel)
│   ├── /periodes/:id/controle    (Étape 5 : Audit des bulletins, tiroir latéral & export CSV)
│   ├── /periodes/:id/validation  (Étape 6 : Validation DAF, clôture & réouverture)
│   └── /periodes/:id/sorties     (Étape 7 : Bulletins massifs, SAFI DGI, DNTS, Virement)
├── ?tab=variables                (Gestion centralisée des ajustements DAF)
├── ?tab=periodes                 (Gestionnaire de périodes de paie)
├── ?tab=cockpit                  (Ancien cockpit financier DAF stabilisé)
└── ?tab=exports                  (Restitutions fiscales, sociales et bancaires)

/employes/:id
├── personnel                     (Fiche Personnelle & coordonnées bancaires)
├── famille                       (Famille, enfants & contacts urgence)
├── documents                     (Dossier documentaire RH)
├── carriere                      (Contrats, salaires, avenants, rupture conventionnelle)
├── conges                        (Soldes congés & demandes)
├── pointage                      (Feuilles d'heures & régularisations)
└── paie                          (EmployePaieTab : Synthèse, Bulletins, Prêts, STC)
```

---

## 3. PAGE → API → DTO MAP

| Interface | Service Frontend | Endpoint API | Controller Backend | DTO / Entité Consommée | Propriétés Clés Validées |
|---|---|---|---|---|---|
| `BulletinPaieDrawer` | `paieService.getBulletinComplet` | `GET /paie/bulletin/{id}/complet` | `PaieController` | `BulletinPaieCompletDTO` | `bulletin.nomPrenom`, `bulletin.joursTravailles`, `bulletin.salaireNetImposable`, `bulletin.cinNumero`, `lignes[].montantSalarial` |
| `BulletinPaiePrintView` | `paieService.getBulletinComplet` | (Composant d'impression lié) | `PaieController` | `BulletinPaieCompletDTO` | `nomPrenom`, `intitulePoste`, `joursTravailles`, `cinNumero`, `lignes[].montantSalarial` selon `sens` |
| `VariablesPaieTab` | `paieService.listerAjustements` | `GET /paie/ajustements` | `PaieController` | `AjustementPaieDTO` / `AvanceSalaire` | `idAvance ?? id_avance`, `nomComplet`, `dateDemande`, `typeLibelle`, `sens`, `montant` |
| `StcPreparationModal` | `stcService.preparerStc` | `GET /stc/preparer` | `StcController` | `StcPreparationDTO` | `dateDepart` (ISO nettoyé pour input HTML5), `soldeCongesEstime`, `moisPreavisSuggere` |
| `StcPreparationModal` | `stcService.calculerStc` | `POST /stc/calculer` | `StcController` | `StcCalculResultDTO` | `netAPayerStc ?? netAPayer`, `totalBrutStc ?? totalBrut`, `soldePretsDeduit` |
| `StcRecuModal` | `stcService.obtenirRecuStc` | `GET /stc/{id}/recu` | `StcController` | `StcRecuDTO` | `numeroRecu`, `nomComplet`, `netAPayer ?? netAPayerStc`, `totalBrut ?? totalBrutStc`, `netEnLettres` |
| `EmployePaieTab` | `paieService.getSynthesePaieEmploye` | `GET /paie/employe/{id}/synthese` | `PaieController` | `EmployePaieSyntheseDTO` | `salaireBaseActuel`, `dernierNetPaye`, `totalPretsEnCours`, `dernierBulletin` |
| `ControleStepPage` | `paieService.getCockpitPeriode` | `GET /paie/periode/{id}/cockpit` | `PaieController` | `CockpitPaieDTO` | `bulletins[].nomPrenom`, `salaireBase`, `totalGainsBruts`, `netAPayer`, `ecartM1Pct` |
| `ValidationStepPage` | `periodePaieService.executeCommand` | `POST /pointage/periodes/{id}/commands` | `PeriodePaieWorkflowController` | `PeriodePaieDTO` | `state: PAIE_VALIDEE / CLOTUREE / VALIDEE`, `reopenError` |
| `CockpitPaieTab` | `paieService.getCockpitPeriode` | `GET /paie/periode/{id}/cockpit` | `PaieController` | `CockpitPaieDTO` | `kpis.totalBrut`, `kpis.totalNetAPayer`, `kpis.totalCotisationsPatronales` |
| `ExportsPaieTab` | `paieService.obtenirDonneesSafi` | `GET /paie/periode/{id}/export/safi` | `PaieController` | `List<ExportSafiDTO>` | Lignes fiscales et montants DGI |

---

## 4. BUGS DÉTECTÉS & ROOT CAUSES

### Bug 1 : `BULLETIN DE PAIE — undefined undefined` dans le Drawer et la Vue Impression
- **Symptôme :** Le titre du tiroir affichait `BULLETIN DE PAIE — undefined undefined`. Dans la vue impression A4, le champ nom affichait également `undefined undefined`.
- **Cause racine :** Le frontend lisait `bulletin.nomEmploye` et `bulletin.prenomEmploye`, alors que l'entité/DTO backend retourne la propriété canonique `bulletin.nomPrenom` (ou `nom` et `prenom` sur l'employé).
- **Correction :** Résolution robuste : `const nomAffiche = bulletin.nomPrenom || [bulletin.nom, bulletin.prenom].filter(Boolean).join(' ') || [bulletin.nomEmploye, bulletin.prenomEmploye].filter(Boolean).join(' ') || 'Employé'`.

### Bug 2 : Lignes de rubriques avec montants vides `—` dans le Drawer et la Vue Impression
- **Symptôme :** Toutes les lignes de la table détaillée des rubriques (salaire base, heures sup, cotisations, etc.) affichaient `—` au lieu des montants réels.
- **Cause racine :** Le frontend lisait `ligne.montantGain` et `ligne.montantRetenue`. Or `BulletinPaieLigne` backend stocke `montantSalarial` et `sens` (`GAIN`, `RETENUE`, `PATRONAL`). `montantGain` et `montantRetenue` étaient `undefined`.
- **Correction :** Ventilation dynamique selon `ligne.sens` :
  - Si `ligne.sens === 'GAIN' || 'INFO'` : `ligne.montantSalarial ?? ligne.montantGain`.
  - Si `ligne.sens === 'RETENUE'` : `ligne.montantSalarial ?? ligne.montantRetenue`.

### Bug 3 : Intitulé de poste, CIN et jours travaillés manquants dans le Bulletin
- **Symptôme :** Le poste affichait `-`, le CIN affichait `Non renseigné`, et les jours travaillés affichaient `Détail de pointage indisponible` (ou fallback statique `22j` dans le print).
- **Cause racine :** 
  - Backend retourne `intitulePoste`, frontend lisait `fonction`.
  - Backend retourne `cinNumero`, frontend lisait `cin`.
  - Backend retourne `joursTravailles` (Double), frontend lisait `nbJoursTravailles`.
- **Correction :** Prise en charge des propriétés canoniques backend avec fallbacks rétrocompatibles.

### Bug 4 : CNaPS & OSTIE absents de l'entête du bulletin (`Détail indisponible`)
- **Symptôme :** Les lignes sociales de synthèse affichaient `Détail indisponible`.
- **Cause racine :** `bulletin.cnapsSalariale` et `bulletin.ostieSalariale` ne sont pas toujours persistés au niveau de l'entête du bulletin, mais sont toujours présents dans `data.lignes` (`COTISATION_CNAPS_SAL`, `COTISATION_OSTIE_SAL`).
- **Correction :** Extraction de secours automatique depuis `lignes.find(l => l.codeRubrique === 'COTISATION_CNAPS_SAL').montantSalarial`.

### Bug 5 : Net Imposable `undefined Ar` ou vide dans le Bulletin
- **Symptôme :** La ligne Net Imposable affichait `—` ou était nulle.
- **Cause racine :** Backend renvoie `bulletin.salaireNetImposable` (norme juridique CGI), le frontend lisait `bulletin.netImposable`.
- **Correction :** `bulletin.salaireNetImposable ?? bulletin.netImposable`.

### Bug 6 : DTO Drift & suppression impossible dans `VariablesPaieTab`
- **Symptôme :** Éléments variables avec collaborateur `Collaborateur` par défaut, date `—`, et bouton de suppression inopérant (`idAvance` undefined).
- **Cause racine :** Le frontend lisait des clés snake_case (`v.id_avance`, `v.date_demande`, `v.nom_complet`, `v.type_libelle`), alors que l'API renvoie des clés camelCase (`idAvance`, `dateDemande`, `nomComplet`, `typeLibelle`).
- **Correction :** Support unifié `v.idAvance ?? v.id_avance`, `v.dateDemande || v.date_demande`, `v.nomComplet || v.nom_complet`.

### Bug 7 : Date de cessation vide dans `StcPreparationModal`
- **Symptôme :** Le champ `<input type="date">` de date de départ restait vide à l'ouverture de la modale STC.
- **Cause racine :** L'API `preparerStc` retourne un timestamp ISO complet avec fuseau horaire (`2026-09-18T12:31:18.408+03:00`). Les navigateurs rejettent les chaînes avec heure pour un `type="date"` HTML5.
- **Correction :** Implémentation du helper de normalisation `fmtDateInput(val)` extrayant strictement `YYYY-MM-DD`.

### Bug 8 : Fallbacks trompeurs `0 Ar` au lieu de `—`
- **Symptôme :** Présence de fonctions locales `formatAr` renvoyant `0 Ar` quand la donnée était `null` ou `undefined`.
- **Cause racine :** Duplication de code dans `CalculStepPage.jsx`, `GestionPaiePage.jsx`, `VariablesPaieTab.jsx`, `CockpitPaieTab.jsx`.
- **Correction :** Importation et utilisation systématique de `formatMoneyAr(val)` depuis `@/utils/moneyUtils` (retourne `—` pour `null`/`undefined`, et `0 Ar` uniquement pour `0` réel).

### Bug 9 : Appels à `window.alert()` natif bloquant
- **Symptôme :** Popups `alert()` du navigateur lors des erreurs de saisie ou d'API.
- **Cause racine :** 11 occurrences de `alert()` dans `VariablesPaieTab`, `CockpitPaieTab`, `ValidationStepPage`, `ExportsPaieTab`, `EmployePaieTab`, `ReglementationFiscaleTab`.
- **Correction :** Remplacement par des états d'alerte UI contextuels (`setFeedbackMsg`, `setFeedback`, `reopenError`).

---

## 5. DESIGN SYSTEM & POLISH VISUEL
- Respect strict des tokens CSS de l'application (`var(--color-brand-...)`, `var(--color-surface)`, `var(--color-border-subtle)`, `var(--color-danger-...)`, `var(--color-success-...)`).
- Ajout du raccourci direct **« Paie & Rémunération »** dans `EmployeSideSummaryPanel` pour fluidifier le passage entre le dossier collaborateur et le suivi financier.
- Nettoyage complet des espaces de fin de ligne (`git diff --check` validé à 100%).

---

## 6. TESTS AJOUTÉS & VALIDATION AUTOMATISÉE

### A. Nouveau Fichier de Tests Contrat & Non-Régression
- `frontend/src/tests/PayrollRuntimeStabilization.test.js` (10 tests unitaires) :
  1. Résolution du nom complet du salarié sans jamais afficher `undefined undefined`.
  2. Résolution des jours travaillés sans faux fallback `22j`.
  3. Résolution du net imposable via `salaireNetImposable`.
  4. Récupération dynamique CNaPS & OSTIE depuis `lignes`.
  5. Calcul des montants selon le sens `GAIN` / `RETENUE`.
  6. Compatibilité DTO `VariablesPaieTab` (camelCase et snake_case).
  7. Conversion timestamp ISO vers `YYYY-MM-DD` pour champ date STC.
  8. Résolution `netAPayerStc` / `netAPayer` sans `NaN`.
  9. Règle anti-fallback mensonger (`formatMoneyAr(null) === '—'`).
  10. Règle montant nul légitime (`formatMoneyAr(0) === '0 Ar'`).

### B. Résultats des Suites de Tests Globales

#### Frontend (Vitest)
```text
Test Files  8 passed (8)
Tests       52 passed (52)
Duration    316ms
```
- `src/tests/soldeInitialAndConge.test.js` : 6/6 PASS
- `src/tests/PayrollWorkflowStepper.test.js` : 4/4 PASS
- `src/tests/purgeExpiration.test.js` : 6/6 PASS
- `src/tests/sidebarUtils.test.js` : 8/8 PASS
- `src/tests/moneyUtils.test.js` : 4/4 PASS
- `src/tests/EmployePaieAndStc.test.js` : 10/10 PASS
- `src/tests/PayrollRuntimeStabilization.test.js` : 10/10 PASS
- `src/tests/apiErrorMessage.test.js` : 4/4 PASS

#### Frontend Build (Vite)
```text
npm run build
✓ built in 663ms with 0 errors
```

#### Contrôle Git Whitespace
```text
git diff --check
Sortie : 0 warning / 0 error
```

#### Backend Integration Tests (Maven / PostgreSQL Réel)
```text
mvn test -Dtest=PaieControllerIntegrationTest,StcControllerIntegrationTest,GalaxamRealPayrollDatasetIntegrationTest,PayrollGoldenMasterFinalizationIntegrationTest,GrandWorkflowOhatraE2ETest

Results :
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS (Total time: 28.534 s)
```
- `GalaxamRealPayrollDatasetIntegrationTest` : 5/5 PASS
- `StcControllerIntegrationTest` : 3/3 PASS
- `PayrollGoldenMasterFinalizationIntegrationTest` : 2/2 PASS
- `PaieControllerIntegrationTest` : 10/10 PASS
- `GrandWorkflowOhatraE2ETest` : 1/1 PASS

---

## 7. MATRICE DE TEST RUNTIME VÉRIFIÉE

| Interface | URL / Contexte | Dataset Réel | API Testée | Console Runtime | Undefined / NaN | Actions Vérifiées | État Final |
|---|---|---|---|---|---|---|---|
| **Campagne Active** | `/paie` | Période active ID 7163 | `GET /paie/periodes` | 0 error | 0 | Navigation stepper, sélecteur période | PASS |
| **Étape 1 : Préparation** | `/paie/periodes/7163/preparation` | Période 7163 (Août-Sept 2026) | `GET /paie/periodes/7163/preparation-state` | 0 error | 0 | Vérification prérequis, dates | PASS |
| **Étape 2 : Population** | `/paie/periodes/7163/population` | 13 salariés éligibles | `GET /paie/periodes/7163/population` | 0 error | 0 | Filtres contrat, recherche collaborateur | PASS |
| **Étape 3 : Variables** | `/paie/periodes/7163/variables` | Variables mensuelles | `GET /paie/ajustements` | 0 error | 0 | Modale ajout, filtre Gain/Retenue | PASS |
| **Étape 4 : Calcul** | `/paie/periodes/7163/calcul` | Période active | `POST /paie/calculer-simulation` | 0 error | 0 | Simulation, KPIs temps réel | PASS |
| **Étape 5 : Contrôle** | `/paie/periodes/1/controle` | Période calculée réelle | `GET /paie/periode/1/cockpit` | 0 error | 0 | Filtrage statut, anomalies | PASS |
| **Tiroir Bulletin** | Drawer latéral sur bulletin 1 | Bulletin RAKETAKA Raketaka | `GET /paie/bulletin/1/complet` | 0 error | 0 | Nom complet, pointage 18j, ventilation rubriques | PASS |
| **Impression A4** | Modal Impression A4 / PDF | Bulletin complet RAKETAKA | Aperçu dynamique | 0 error | 0 | Lignes gains et retenues visibles, net chiffré | PASS |
| **Étape 6 : Validation** | `/paie/periodes/1/validation` | Période validée/scellée | `POST /pointage/periodes/1/commands` | 0 error | 0 | Modale réouverture sans `alert()` | PASS |
| **Étape 7 : Sorties** | `/paie/periodes/1/sorties` | Période validée | `GET /paie/periode/1/export/...` | 0 error | 0 | Téléchargements documents | PASS |
| **Employé → Paie** | `/employes/530964/paie` | Employé avec bulletins & prêts | `GET /paie/employe/530964/synthese` | 0 error | 0 | Synthèse, bulletins, mouvements sans native alert | PASS |
| **Employé → Carrière** | `/employes/530964/carriere` | Contrat actif / rupture | `GET /carrieres/employe/530964/detail` | 0 error | 0 | Boutons STC contextuels | PASS |
| **Solde de Tout Compte** | `StcPreparationModal` | Salarié en départ | `GET /stc/preparer`, `POST /stc/calculer` | 0 error | 0 | Date HTML5 YYYY-MM-DD, simulation temps réel | PASS |
| **Quittance STC** | `StcRecuModal` | Quittance officielle | `GET /stc/1/recu` | 0 error | 0 | Mentions libératoires Art. 53, net à payer | PASS |

---

## 8. DETTE TECHNIQUE RESTANTE
- **Aucune dette bloquante P0 ou P1.**
- Suggestions d'optimisations futures (P2) :
  - Génération PDF vectorielle côté backend (iText ou OpenHTMLtoPDF) en alternative au `window.print()` du navigateur pour les exports massifs zippés.

---

## 9. VERDICT FINAL

```text
========================================================================
             PAYROLL_UI_RUNTIME_STABILIZATION_PASS
========================================================================
 Toutes les interfaces Paie et RH connectées ont été inspectées au runtime.
 0 undefined visible
 0 NaN visible
 0 [object Object]
 0 Invalid Date
 0 alert() natif
 0 erreur console React runtime
 52/52 tests unitaires frontend PASS
 21/21 tests d'intégration backend & Golden Master PASS
 Vite build PASS en 663ms
 git diff --check PASS (0 violation d'espace)
========================================================================
```
