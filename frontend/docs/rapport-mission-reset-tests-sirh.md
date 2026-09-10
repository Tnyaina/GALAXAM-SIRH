# Rapport de Mission : Reset BDD, Dataset Réaliste 5 Salariés (2006-2026) & Convergence Bulletin de Paie

**Date** : 01 Septembre 2026  
**Auteur** : Antigravity — Pair Programming SIRH Galaxam  
**Statut** : **MISSION ACCOMPLIE AVEC SUCCÈS (100% DES TESTS PASSÉS)**  

---

## 1. Contexte et Objectifs Stratégiques

L'objectif de cette mission était de réaliser une validation fonctionnelle et technique complète du SIRH Galaxam en utilisant la base de données réelle PostgreSQL, avec des règles de gestion rigoureuses et une finalité concrète : **fournir la fiche de paie (bulletin de paie) conforme à la législation malgache** en s'appuyant sur l'ensemble de nos structures, dossiers RH, congés et pointages.

### Directives Utilisateur Appliquées à la Lettre :
1. **Données structurelles réelles conservées sans ajout artificiel** :
   - Filiales réelles : `GLX` (Galaxam Logistics) et `VA` (Vina sy Asa ).
   - Sites réels : `TNR` (Siège Antananarivo) et `TMM` (Port de Toamasina / Tamatave).
   - Départements (`LOG`, `LMD`), Services (`ADM`, `OPE`) et référentiels officiels de `26-08-05-sp0-ref.sql`.
2. **Dataset de 5 Salariés sur 20 ans d'histoire RH (2006 à 2026)** :
   - Historique complet de mouvements : embauche, CDD, CDI, promotions, revalorisations de salaire, mutation géographique, période d'essai.
3. **1 Seule période de pointage active : Août 2026 (`2026-08`)** :
   - Férié légal du 15 Août (Assomption) chômé.
   - 5 jours ouvrables de congés posés et décomptés.
   - Pointages complets avec heures sup (+30%, +50%), travail de nuit (>22h), déduction repas Tamatave (-1 000 Ar/j).
4. **Convergence Finale : Le Bulletin de Paie** :
   - Calcul exact pour les 5 profils types (Cadre autonome, Opérateur Tamatave, Salariée en congé, Chauffeur de nuit, Nouvel embauché au prorata).
5. **Nettoyage et refonte complète des tests** :
   - Remplacement de tous les anciens tests fragmentés par 5 suites modulaires et 1 suite API E2E sur base réelle.

---

## 2. Synthèse des Résultats des Tests (27/27 Réussis)

Commande exécutée : `mvn clean test`  
Résultat : **`BUILD SUCCESS`** en 6.89 secondes  

```text
[INFO] Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

### Détail par Suite de Tests :

| Module | Classe de Test | Tests | Statut | Points Clés Validés |
| :--- | :--- | :---: | :---: | :--- |
| **Structure & Administration** | `StructureEtAdministrationIntegrationTest` | 5 | ✅ PASS | Filiales GLX/VA, Sites TNR/TMM, Dispense DAF, Paramètres légaux (40h, seuils HS/Nuit/Repas), Férié 15 Août, Auth BCrypt/JWT |
| **Contrats & Carrière 20 ans** | `ContratCarriereWorkflowIntegrationTest` | 4 | ✅ PASS | 20 ans carrière EMP 1 (3 contrats successifs), Mutation TNR $\rightarrow$ TMM de l'EMP 2, Essai 3 mois EMP 5 chez VA, Unicité contrat actif |
| **Grand Livre des Congés** | `CongeLedgerWorkflowIntegrationTest` | 3 | ✅ PASS | Bonifications ancienneté (+4j, +2j, +1j), Demande 5j ouvrables en Août (EMP 3), Solde net officiel `v_solde_conge_employe` |
| **Pointage & Synthèse Mensuelle** | `PointageSyntheseWorkflowIntegrationTest` | 5 | ✅ PASS | Dispense de pointage DAF (31j statut 10), Retard compensé & déduction Tamatave, Semaine CONGE (5), Heures de nuit > 22h & 2e panier repas, `PointageSyntheseService` |
| **Livrable Final : Bulletin de Paie** | `BulletinPaiePreparationIntegrationTest` | 4 | ✅ PASS | **Calcul complet du bulletin de paie des 5 profils** (Plafond CNaPS 8x SMIG, OSTIE 1%, IRSA progressif avec déduction enfants, Prorata temporis) |
| **API REST & Sécurité RBAC** | `SirhApiE2EIntegrationTest` | 5 | ✅ PASS | Login admin Pass1234, Rejet 403 sans token, GET `/employes` paginé (5 employés), GET `/conges/solde/employe/{id}`, GET `/pointages/synthese` |
| **Context Spring Boot** | `SirhApplicationTests` | 1 | ✅ PASS | Démarrage complet du conteneur Spring Boot |

---

## 3. Les 5 Profils Types & Leurs Bulletins de Paie d'Août 2026

### Profil 1 : DAF Cadre Autonome — RAKOTONDRAZAKA Andry Nirina (`EMP-2006-0001`)
* **Historique RH (20 ans)** : Embauché le 01/02/2006 comme Aide-Comptable (450 000 Ar) $\rightarrow$ Promotion Chef Comptable en 2011 (1 200 000 Ar) $\rightarrow$ Promotion DAF en 2018 (3 800 000 Ar).
* **Ancienneté** : 20 ans $\rightarrow$ **+4 jours de bonification d'ancienneté légale** au grand livre des congés (Solde net : 37.5j).
* **Pointage** : Dispense légale de pointage (`dispense_pointage = true`, statut 10 sur tous les jours du mois).
* **Bulletin de Paie** :
  - Salaire Brut de Base : `3 800 000.00 Ar`
  - CNaPS Salariale (1%) : Plafonnée à 8 x SMIG (`2 101 440.00 Ar`) = `21 014.40 Ar`
  - OSTIE Salariale (1%) : 1% non plafonné = `38 000.00 Ar`
  - Base Imposable IRSA : `3 740 985.60 Ar`
  - IRSA Net Déduit : `651 696.62 Ar` (après déduction légale pour **2 enfants à charge** : -4 000 Ar)
  - **👉 NET À VIRER BNI** : **`3 089 288.98 Ar`** (Compte `00004 00123 00000000001 45`)

### Profil 2 : OPE Manager Muté — RANDRIAMAMPIONONA Jean Jacques (`EMP-2014-0012`)
* **Historique RH (12 ans)** : CDD 2014 $\rightarrow$ CDI 2015 à TNR $\rightarrow$ Mutation géographique au Port de Tamatave (`TMM`) en 2022 avec revalorisation à `1 850 000.00 Ar`.
* **Ancienneté** : 12 ans $\rightarrow$ **+2 jours de bonification d'ancienneté** (palier 10 ans).
* **Pointage Août 2026** :
  - 15 Août férié chômé (Assomption).
  - Semaine 33 : 3h supplémentaires effectuées le 12 Août (payées à +30% : +41 625.79 Ar).
  - Semaine 34 : Retard de 10 min compensé le vendredi 21 Août (retard net déduit = 0).
  - Indemnité de déplacement Tamatave nette : 53 200 Ar forfaitaire - 21 000 Ar (déduction de 1 000 Ar/jour pour repas pris sur site) = `32 200.00 Ar`.
* **Bulletin de Paie** :
  - Salaire Brut : `1 891 625.79 Ar` + Indemnité déplacement non imposable `32 200.00 Ar`.
  - CNaPS Salariale (1%) : `18 916.26 Ar`
  - OSTIE Salariale (1%) : `18 916.26 Ar`
  - Déduction IRSA : 1 enfant à charge (-2 000 Ar).

### Profil 3 : Admin RH en Congé — RASOANIRINA Voahirana Mialy (`EMP-2019-0028`)
* **Historique RH (7 ans)** : CDI depuis 2019 à TNR Siège (`1 350 000.00 Ar`).
* **Ancienneté** : 7 ans $\rightarrow$ **+1 jour de bonification d'ancienneté** (palier 5 ans).
* **Pointage Août 2026** : Congé annuel validé posé du 03 au 07 Août 2026 (**5 jours ouvrables décomptés du grand livre** et pointage en statut `CONGE`).
* **Bulletin de Paie** : **Maintien intégral du salaire de base** (1 350 000.00 Ar, aucune déduction pour absence), déduction IRSA 1 enfant (-2 000 Ar).

### Profil 4 : Chauffeur / Transit Travail Nocturne — ANDRIANARIVELO Patrick (`EMP-2023-0055`)
* **Historique RH (3 ans)** : CDD 2023 $\rightarrow$ CDI 2024 à Tamatave (`950 000.00 Ar`).
* **Pointage Août 2026** : Semaine 35 intensive au port :
  - Lundi 24 Août : 14.5h travaillées, dont 1.5h de nuit (> 22h00) et double panier repas (sortie après 21h00).
  - Mardi 25 Août : 14h travaillées, dont 1h de nuit et double panier repas.
  - Total : 8h sup à +30%, 6.5h sup à +50%, 2.5h de travail de nuit à +30%, paniers repas supplémentaires.
* **Bulletin de Paie** : Salaire brut augmenté des majorations légales pour un total brut de `1 064 550.18 Ar`.

### Profil 5 : Nouvel Embauché avec Prorata — RAKOTONDRASOA Faly Christian (`EMP-2026-0089`)
* **Historique RH** : Embauché le 17 Août 2026 chez Vina sy Asa  (`VA`) au Siège TNR, CDI en période d'essai (3 mois jusqu'au 16/11/2026, `1 050 000.00 Ar` mensuel plein).
* **Pointage Août 2026** : 11 jours ouvrés travaillés sur les 21 jours ouvrés du mois.
* **Bulletin de Paie** :
  - **Proratisation temporis exacte** : 1 050 000 Ar x (11 / 21) = **`550 000.00 Ar`**.
  - Cotisation CNaPS salariale (1%) calculée sur la base proratisée = `5 500.00 Ar`.

---

## 4. Scripts et Fichiers Clés Livrés

1. **Script SQL du Dataset Métier Réaliste** :
   [`backend/conception/bdd/26-09-01-dataset-20ans-5employes.sql`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/conception/bdd/26-09-01-dataset-20ans-5employes.sql)
2. **Suites de Tests d'Intégration Modulaires** :
   - [`backend/src/test/java/mg/galaxam/sirh/structure/StructureEtAdministrationIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/structure/StructureEtAdministrationIntegrationTest.java)
   - [`backend/src/test/java/mg/galaxam/sirh/contrat/ContratCarriereWorkflowIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/contrat/ContratCarriereWorkflowIntegrationTest.java)
   - [`backend/src/test/java/mg/galaxam/sirh/conge/CongeLedgerWorkflowIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/conge/CongeLedgerWorkflowIntegrationTest.java)
   - [`backend/src/test/java/mg/galaxam/sirh/pointage/PointageSyntheseWorkflowIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/pointage/PointageSyntheseWorkflowIntegrationTest.java)
   - [`backend/src/test/java/mg/galaxam/sirh/paie/BulletinPaiePreparationIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/paie/BulletinPaiePreparationIntegrationTest.java)
   - [`backend/src/test/java/mg/galaxam/sirh/api/SirhApiE2EIntegrationTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/api/SirhApiE2EIntegrationTest.java)
