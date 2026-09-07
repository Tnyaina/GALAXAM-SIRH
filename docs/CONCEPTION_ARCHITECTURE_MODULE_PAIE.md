# DOCUMENT OFFICIEL DE CONCEPTION & D'ANALYSE FONCTIONNELLE DU MODULE PAIE
## SIRH GALAXAM — Moteur de Paie Duale & Génération des Bulletins de Salaire

---

**Statut du document :** `CONCEPTION EN COURS — INFORMATIONS MÉTIER REQUISES (AVANT IMPLÉMENTATION)`  
**Auteurs :** Architecte Fonctionnel & Expert Conception Systèmes de Paie & Architecte Logiciel Senior  
**Date :** 4 Septembre 2026  
**Périmètre :** Conception et analyse exhaustive des données, règles, workflows, variables, paramètres, formules et architecture du futur module Paie de GALAXAM-SIRH.  
**Avertissement strict :** *Ce document constitue une étude préalable et une spécification de conception. Aucune table, code d'engine ou écran n'a été créé lors de cette phase.*

---

## 1. RÉSUMÉ EXÉCUTIF

Le module **Paie & Bulletins de Salaire** constitue l'aboutissement opérationnel et financier du SIRH GALAXAM. Son rôle est de transformer les données contractuelles, organisationnelles, temporelles (présences, absences, congés) et financières en **bulletins de paie mensuels exacts, conformes au droit positif malgache et aux règles opérationnelles réelles de GALAXAM, infalsifiables et strictement reproductibles dans le temps**.

### 1.1 Découverte Majeure : Alignement sur les Bulletins et Tableaux Réels GALAXAM
L'analyse des captures réelles des matrices de paie GALAXAM (feuilles Excel d'exploitation) confirme les mécanismes exacts suivants :
1. **Période de Paie Décalée** : La période de paie s'étend du **26 du mois M-1 au 25 du mois M** (ex: `JANOARY 26 : 26DES25to25JAN26`).
2. **Formule Réelle des Absences** :
   $$\text{Taux Journalier d'Absence} = \frac{\text{Salaire de Base}}{30}$$
   $$\text{Montant Absence Déduit} = \text{NBABS} \times \left(\frac{\text{Salaire de Base}}{30}\right)$$
3. **Formule Réelle des Congés Payés** :
   $$\text{Montant Congé} = \text{NBCONGE} \times \text{TCONGE}$$
   où $\text{TCONGE}$ est le taux journalier de droit à congé issu du suivi de l'historique des rémunérations.
4. **Formule Réelle du Salaire Brut Global (`BRUTE`)** :
   $$\text{BRUTE} = \text{BASE} - \text{ABSENCES} + \text{CONGE} + \text{HS} + \text{AUTIMP}$$
   où $\text{AUTIMP}$ regroupe les primes (performance, assiduité) après déductions des retards et proratas.
5. **Cotisations Sociales & Fiscalité** :
   * CNaPS : $1\%$ du brut (avec plafonnement à $8 \times \text{SME}$).
   * OSTIE (Antananarivo) : $1\%$ du brut.
   * OMSI (Toamasina) : $1.5\%$ du brut.
   * $\text{NETI}$ (Net Imposable) = $\text{BRUTE} - \text{CNAPS} - \text{OSTIE} - \text{OMSI}$.
   * IRSA : Calcul progressif avec déduction enfant et plancher, arrondi à la centaine d'Ariary (`IRSA Arrondi`).
6. **Retenues Spécifiques & Arrondi Final** :
   * $\text{Net avant call} = \text{NETI} - \text{IRSA Arrondi} + \text{RED ENFT} - \text{AVANCE} - \text{AVCE SPEC}$.
   * $\text{CALL PERSO}$ : Déduction nette des communications personnelles non professionnelles.
   * $\text{NET À PAYER} = \text{Net avant call} - \text{CALL PERSO}$.
   * $\text{ARRONDI NET À PAYER}$ : Arrondi officiel à la dizaine d'Ariary inférieure (ex: $567\,084.29\text{ Ar} \rightarrow 567\,080\text{ Ar}$).
7. **Double Format de Restitution Requis** :
   * **Bulletin individuel PDF** : Fiche de paie officielle salarié, soignée et imprimable.
   * **Grand-Livre ODS / Excel consolidé** : Export matricule par matricule reprenant fidèlement les colonnes de l'Excel historique pour la DAF.

---

## 2. ÉTAT ACTUEL DU PROJET

L'audit approfondi du repository montre que le projet possède déjà d'excellentes fondations en amont de la paie, mais que le **moteur de paie lui-même n'est pas encore implémenté**.

### 2.1 Composants existants directement liés à la paie :
* **Base de données (`backend/conception/bdd/`)** :
  * `paie_config_legale` (`26-09-02-sp4-paie.sql`) : Table pivot d'immutabilité temporelle des exercices fiscaux (LF_2024, LF_2026). Porte le SME national (262 680 Ar), SME agricole, multiplicateur plafond (8×), minimum de perception IRSA (3 000 Ar), déduction par charge d'enfant (2 000 Ar), diviseur mensuel (173.3333h), seuils d'heures supplémentaires.
  * `ref_type_cotisation` & `site_cotisation` : Types de cotisations rattachés par site (CNaPS 1% sal / 13% pat, OSTIE 1% sal / 5% pat à Antananarivo, OMSI 1.5% sal / 5.5% pat à Toamasina).
  * `bareme_irsa` : 6 tranches d'IRSA officielles millésimées (0% à 350 000 Ar, 5%, 10%, 15%, 20%, 25% au-delà de 4 000 000 Ar).
  * `ref_taux_majoration` : Taux légaux de majoration (HS 30%, HS 50%, Nuit 30%, Dimanche 40%, Férié 100%).
  * `ref_type_intervenant` (`26-08-05-sp0-ref.sql`) : Distingue `PERMANENT` (IRSA=true, CNaPS=true, Santé=true), `CONSULTANT` (ISI=true, CNaPS=false), `TEMPORAIRE` (ISI=true), `STAGIAIRE` (non soumis).
  * `employe_affectation_carriere` (`26-08-10-sp1-contrat.sql`) : Stocke le `salaire_base` contractuel historique, la catégorie professionnelle (`CADRE` vs `A`), le site d'affectation et le `ref_mode_calcul_paie` (`MENSUEL`, `HORAIRE`, `JOURNALIER`).
  * `pointage_cumul_periode` & `pointage_cumul_indemnite` (`26-08-20-sp3-pointage.sql`) : Tables de synthèse gelées scellées lors de la clôture du pointage par la DAF, contenant les heures réelles, HS 30, HS 50, heures de nuit, retards nets décomptés, indemnités de déplacement et de repas.
  * `demande_conge` & `ref_type_conge` (`26-08-13-sp2-conges.sql`) : Gestion des congés payés, maladies, événements familiaux et sans solde, avec indicateur `est_remunere`.
* **Backend Java** :
  * `mg.galaxam.sirh.model.paieconfig.engine.CalculateurPaieEngine.java` : Moteur élémentaire implémentant les méthodes isolées `calculerCnaps`, `calculerSante` et `calculerIrsa`.
  * `PaieConfigLegale.java`, `BaremeIrsa.java`, `RefTypeCotisation.java`, `SiteCotisation.java` : Entités CRUD de paramétrage.
  * `PointageSyntheseService.java` : Gestion du workflow de transmission, rejet, gel (`gelerPeriode`) et validation DAF de la période de pointage.
  * `BulletinPaiePreparationIntegrationTest.java` : Test d'intégration simulant 4 bulletins théoriques en mode test.
* **Frontend React** :
  * `ParametresGlobauxTab.jsx`, `ReglementationFiscaleTab.jsx`, `CotisationsIrsaTab.jsx`, `IndemnitesSitesTab.jsx`, `MajorationsParametresTab.jsx`, `CalendrierFerieTab.jsx` : Gestion complète du paramétrage des règles de paie dans l'IHM Administrateur.
  * `PeriodePaiePage.jsx` : Gestion des périodes de paie (création, ouverture, transmission, validation).

### 2.2 Ce qui N'EXISTE PAS ENCORE (À concevoir) :
* Aucune table relationnelle pour stocker les bulletins de paie (`bulletin_paie`, `bulletin_ligne`, `bulletin_cotisation`).
* Aucun moteur d'orchestration (`PayrollExecutionEngine` ou `CalculateurPaieService`) capable d'agréger contrat, pointage, congés, avances, barèmes et de produire une paie globale.
* Aucune table pour la gestion opérationnelle des variables de paie (avances, acomptes, primes, saisies-arrêts).
* Aucun écran de consultation, validation, clôture et export de bulletin de salaire pour les RH / collaborateurs.
* Aucun générateur de bulletin de paie PDF / états nominatifs CNaPS/FMFP/OSTIE.

---

## 3. DOCUMENTATION ANALYSÉE

L'inventaire exhaustif de la documentation du repository et l'analyse de son contenu fournissent les éléments suivants :

| Document | Objectif | Apports pour la Paie | Limites, Obsolescences ou Contradictions Détectées |
|---|---|---|---|
| `backend/docs/boky/module-paie-reglementation-fiscale.md` | Spécification de la législation fiscale et sociale évolutive (Loi de Finances 2026, SME, IRSA, CNaPS). | Définit la modélisation de `paie_config_legale`, les 6 tranches IRSA, le plafond 8×SME, le diviseur 173.3333h et le diviseur congé 30j. | Document très fiable et aligné sur le code SQL `26-09-02-sp4-paie.sql`. |
| `backend/docs/boky/module-paie-avances-frais.md` | Cadrage V2 pour avances, acomptes, prêts et notes de frais. | Propose la structure de `variable_paie_mensuelle`, `pret_employe`, `ref_type_variable_paie` et l'articulation net à verser. | **Attention :** Ce document est un "Cadrage pour développement futur". Ces tables **n'ont jamais été créées en BDD**. |
| `backend/docs/boky/module-conges-absences.md` | Gestion des congés et absences. | Définit la valorisation financière (§8) : CP sur diviseur 30j calendaires, absences injustifiées sur diviseur 173.33h. Règle de maintien de salaire à 100% pour congé annuel. | Ne précise pas le traitement exact des congés maladie de longue durée (co-financement CNaPS / employeur explicitement ajourné). |
| `backend/docs/boky/module-structure.md` | Structure organisationnelle et sites. | Cascade à 3 niveaux des indemnités de site (site → dérogation employe → pointage jour J). | La table `pointage_jour` contenait d'anciennes colonnes d'indemnités redondantes avec `pointage_indemnite_jour`. |
| `backend/docs/boky/module-contrat.md` & `Contrats-RG.md` | Gestion des contrats et de la carrière. | Déclare `employe_affectation_carriere` comme porteur officiel du salaire de base, et chaîne les contrats via `id_contrat_precedent`. | Ne spécifie pas comment gérer un changement de salaire ou d'affectation intervenant en milieu de mois calendaire. |
| `docs/CAHIER_DES_CHARGES_GALAXAM.md` | Spécifications initiales globales. | Confirme le besoin de paie duale (IRSA vs ISI 5%), l'export trimestriel CNaPS/OSTIE/OMSI/FMFP, et la confidentialité stricte DAF. | Document initial de haut niveau, manque de détails sur le format des états récapitulatifs et les arrondis financiers. |
| `docs/GUIDE_REGLES_METIER_ET_MODIFICATIONS.md` | Guide d'architecture et catalogue des règles du SIRH. | Formalise RG-PAIE-01 à RG-PAIE-05, RG-PTG-07 (forfait cadres), et le découpage RBAC (`PAIE_READ`, `PAIE_WRITE`). | Règles documentées très conformes au code actuel. |

---

## 4. SOURCES ET NIVEAU DE CONFIANCE

Pour chaque fait, règle ou donnée structurante de cette conception, le tableau ci-dessous indique formellement sa provenance et son niveau de confiance :

| Information Clé | Source | Type de Source | Niveau de Confiance | Statut / À vérifier ? |
|---|---|---|---|---|
| Plafonnement CNaPS à 8 × SME | `backend/conception/bdd/26-09-02-sp4-paie.sql`, `CalculateurPaieEngine.java` | Code existant & BDD | **100% (Certain)** | Confirmé par la législation CNaPS en vigueur à Madagascar. |
| Taux CNaPS : 1% salarié, 13% employeur | `ref_type_cotisation`, `CalculateurPaieEngineTest.java` | Code existant & BDD | **100% (Certain)** | Confirmé légalement. |
| Taux Santé : OSTIE (1% sal / 5% pat), OMSI (1.5% sal / 5.5% pat) | `26-09-02-sp4-paie.sql` | Code existant & BDD | **100% (Certain)** | Confirmé selon rattachement site (TNR vs TMM). |
| Contribution FMFP (1% patronal) | `docs/CAHIER_DES_CHARGES_GALAXAM.md` | Documentation | **80% (Forte)** | ⚠ `ref_type_cotisation` ne contient pas encore la ligne FMFP en BDD ! À insérer. |
| Barème IRSA 2026 à 6 tranches (0%, 5%, 10%, 15%, 20%, 25%) | `bareme_irsa`, `CalculateurPaieEngine.java` | Code existant & BDD | **100% (Certain)** | Aligné Loi de Finances n°2025-021. |
| Seuil exonération IRSA : 350 000 Ar | `paie_config_legale` | BDD | **100% (Certain)** | Confirmé CGI Madagascar. |
| Minimum de perception IRSA : 3 000 Ar | `paie_config_legale`, `CalculateurPaieEngine.java` | Code existant & BDD | **100% (Certain)** | Confirmé LF 2026. |
| Réduction d'impôt par enfant à charge : 2 000 Ar / mois | `paie_config_legale`, `CalculateurPaieEngine.java` | Code existant & BDD | **100% (Certain)** | Confirmé CGI Madagascar. |
| Régime fiscal ISI 5% pour Consultants / Temporaires | `CAHIER_DES_CHARGES_GALAXAM.md`, `ref_type_intervenant` | BDD & Documentation | **85% (Forte)** | ⚠ À vérifier : les consultants paient-ils l'OSTIE ? (Actuellement `soumis_sante=TRUE` en BDD pour CONSULTANT). |
| Diviseur d'heures mensuel légal : 173.3333 h | `paie_config_legale` | BDD & Droit malgache | **100% (Certain)** | $(40 \text{ h} \times 52) / 12 = 173.3333$. |
| Diviseur indemnité congé : 30 jours | `paie_config_legale`, `module-conges-absences.md` | BDD & Code du Travail | **100% (Certain)** | Art. 85 Code du Travail Malgache. |
| Retenue pour absence injustifiée : base horaire ($S_{base} / 173.33 \times \text{heures}$) | `module-conges-absences.md` | Documentation | **70% (Moyenne)** | ⚠ `À VÉRIFIER` : Pratique-t-on le diviseur horaire (173.33), le trentième (30j) ou les jours ouvrés réels du mois ? |
| Formule d'arrondi de l'IRSA à la centaine d'Ariary inférieure | `paie_config_legale.arrondi_base_irsa = 'CENTAINE_INF'` | BDD | **75% (Moyenne)** | Pratique fiscale malgache standard. Non implémentée dans `CalculateurPaieEngine`. |
| Bulletins de paie réels d'exemple | *Aucun document PDF / scan fourni* | Observation directe | **0% (Inexistant)** | **SIGNALEMENT EXPLICITE : Aucun spécimen ou bulletin physique n'a été fourni.** |

---

## 5. CARTOGRAPHIE DES DÉPENDANCES DU MODULE PAIE

Le schéma ci-dessous détaille le flux d'alimentation du module Paie depuis les modules amont jusqu'à la production du bulletin scellé :

```
┌───────────────────────────────────────────────────────────────────────────┐
│                           MODULES SOURCES RH                              │
└───────────────────────────────────────────────────────────────────────────┘
   │
   ├── [1] DOSSIER SALARIÉ (employe, employe_enfant, employe_compte_banque)
   │     └── Matricule, État civil, Enfants éligibles à charge, Banque/RIB, N° CNaPS
   │
   ├── [2] CONTRAT & CARRIÈRE (contrat_travail, employe_affectation_carriere)
   │     └── Type contrat (CDI/CDD/Prestation), Type intervenant (PERMANENT vs CONSULTANT),
   │         Catégorie (CADRE autonome vs AGENT éligible HS), Salaire de base,
   │         Mode de paie (MENSUEL / JOURNALIER / HORAIRE), Filiale, Site
   │
   ├── [3] POINTAGE SCELLÉ (pointage_cumul_periode, pointage_cumul_indemnite)
   │     └── Période scellée (VALIDEE/CLOTUREE) par la DAF :
   │         Heures normales, HS 30%, HS 50%, Heures de nuit, Heures fériées, Dimanches,
   │         Retards nets décomptés, Indemnités déplacement & repas validées
   │
   ├── [4] CONGÉS & ABSENCES (demande_conge, ref_type_conge, mouvement_solde_conge)
   │     └── Jours de congés payés validés (maintien de salaire), Événements familiaux (payés),
   │         Absences maladie (maintien / sans solde), Congés sans solde
   │
   ├── [5] VARIABLES DE PAIE & TRÉSORERIE (Futur module ou saisie DAF)
   │     └── Acomptes de quinzaine, Avances exceptionnelles, Prêts société (échéances),
   │         Primes exceptionnelles, Notes de frais remboursées, Saisies sur salaire
   │
   └── [6] RÉGLEMENTATION FISCALE & SOCIALE (paie_config_legale, bareme_irsa, cotisations)
         └── Millésime actif à la date de la période :
             SME, Plafond CNaPS, Taux cotisations par site, Tranches IRSA, Taux ISI 5%
   │
   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                      PAYROLL ENGINE (MOTEUR DE PAIE)                      │
│                                                                           │
│   1. Préparation du contexte salarié (Snapshot données contractuelles)    │
│   2. Calcul du Salaire Brut (Base proratisée + HS + Primes + Avantages)   │
│   3. Détermination des assiettes (Assiette CNaPS plafonnée, Santé, IRSA) │
│   4. Calcul des cotisations sociales salariales & patronales             │
│   5. Détermination du Brut Fiscal & Calcul de l'IRSA / ISI                │
│   6. Intégration des éléments non soumis (Indemnités réelles, Rembours.)  │
│   7. Application des déductions de trésorerie (Avances, Acomptes, Prêts)  │
│   8. Détermination du Net Fiscal, Net Imposable, Net à Payer              │
│   9. Arrondis monétaires stricts (Ariary)                                │
└───────────────────────────────────────────────────────────────────────────┘
   │
   ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                    RÉSULTATS DE PAIE & HISTORISATION                      │
│                                                                           │
│   ├── Table bulletin_paie (En-tête, cumuls, nets, snapshot config)        │
│   ├── Table bulletin_ligne (Lignes de gain et de retenue détaillées)      │
│   ├── Table bulletin_cotisation (Détail salarial + patronal par caisse)   │
│   ├── Journal comptable de paie (Grand-Livre / OD de paie)                │
│   ├── Génération du Bulletin de Paie PDF                                  │
│   └── États nominatifs CNaPS / FMFP / OSTIE / OMSI trimestriels           │
└───────────────────────────────────────────────────────────────────────────┘
```

---

## 6. INVENTAIRE EXHAUSTIF DES VARIABLES DE PAIE

Le tableau ci-dessous recense **l'intégralité des variables nécessaires** pour orchestrer et calculer une paie complète dans GALAXAM-SIRH :

| Code Variable | Nom Fonctionnel | Catégorie | Description | Type | Unité | Source Officielle | Mode | Formule / Règle de Calcul | Base | Taux | Plafond | Imposable IRSA | Cotisable CNaPS | Visible Bulletin | Historisé | Statut |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `EMP_MATRICULE` | Matricule Employé | Identité | Identifiant unique collaborateur | String | Code | `employe.matricule` | Fixe | Clé unique matricule | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_NOM_PRENOM` | Nom et Prénoms | Identité | Nom complet du salarié | String | Texte | `employe` (`nom` + `prenom`) | Fixe | Concaténation nom + prenom | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_CNAPS_NUM` | N° Affiliation CNaPS | Identité | Numéro individuel de sécurité sociale | String | Code | `employe.num_affiliation_cnaps` | Fixe | Saisie RH dossier employé | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_FONCTION` | Fonction / Emploi | Contractuel | Poste occupé selon affectation | String | Texte | `employe_affectation_carriere` | Fixe | `ref_fonction.libelle` | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_CATEGORIE` | Catégorie Professionnelle | Contractuel | Statut conventionnel (Cadre / Agent) | String | Code | `employe_affectation_carriere` | Fixe | `ref_categorie_professionnelle.code` | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_SITE` | Site d'Affectation | Organisation | Lieu de travail (TNR, TMM) | String | Code | `employe_affectation_carriere` | Fixe | `ref_site.code_site` | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_DATE_EMBAUCHE`| Date d'Embauche | Contractuel | Date d'entrée dans la société | Date | Date | `contrat_travail.date_debut` (1er contrat)| Fixe | Min(`date_debut`) ou date reprise ancienneté | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_ANCIENNETE` | Ancienneté | Dérivé | Durée de service au jour du calcul | Integer | Années/Mois| Dérivé de `EMP_DATE_EMBAUCHE` | Calcul | $Période_{fin} - DateEmbauche$ | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_CHARGE_FAM` | Enfants à Charge | Fiscal | Nombre d'enfants ouvrant droit à déduction | Integer | Nombre | `employe_enfant` | Calcul | Count(`a_charge = true` AND `age <= 21`)| - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `EMP_BANQUE_RIB` | Domiciliation & RIB | Trésorerie | Banque et numéro de compte pour virement | String | Texte | `employe_compte_banque` (est_principal) | Fixe | `ref_banque.nom_banque` + `num_compte_rib`| - | - | - | Non | Non | Oui (Pied) | Oui | Confirmé |
| `PER_CODE` | Code Période Paie | Période | Période de paie traitée | String | Code | `periode_paie.code` (ex: 2026-08) | Fixe | Clé de période | - | - | - | Non | Non | Oui (En-tête) | Oui | Confirmé |
| `PER_JOURS_OUVR` | Jours Ouvrables Période| Calendrier | Total jours ouvrables standard du mois | Integer | Jours | `CalendrierOuvrableEngine` / `fn_jours_ouvrables`| Calcul | Jours lun-ven hors fériés chômés | - | - | - | Non | Non | Oui (Décompte) | Oui | Confirmé |
| `PER_JOURS_TRAV` | Jours Travaillés Réels | Pointage | Nombre de jours de présence effective | Decimal | Jours | `pointage_cumul_periode.nb_jours_presents`| Calcul | Somme jours présents + fériés travaillés| - | - | - | Non | Non | Oui (Décompte) | Oui | Confirmé |
| `SAL_BASE_MENS` | Salaire de Base Mensuel| Contractuel | Forfait contractuel mensuel | Decimal | MGA | `employe_affectation_carriere.salaire_base`| Fixe | Valeur brute contractuelle | - | - | - | Oui | Oui | Oui | Oui | Confirmé |
| `SAL_TAUX_HORAIRE` | Taux Horaire Contractuel | Dérivé | Taux de valorisation de l'heure | Decimal | MGA/h | Dérivé (`SAL_BASE_MENS` / `DIV_HEURES`) | Calcul | $SAL\_BASE\_MENS / 173.3333$ | Base | - | - | - | - | Oui | Oui | Confirmé |
| `SAL_BASE_PRORAT` | Salaire de Base Proratisé| Rémunération | Salaire base ajusté entrées/sorties/absences| Decimal | MGA | Calcul Moteur | Calcul | $SAL\_BASE\_MENS \times \frac{JoursActifs}{JoursOuvrables}$ | Base | Ratio | - | Oui | Oui | Oui | Oui | Confirmé |
| `HS_NB_30` | Heures Sup. Majorées 30%| Pointage | Heures sup. tranche 1 (41e à 48e h/semaine)| Decimal | Heures | `pointage_cumul_periode.heures_hs_30` | Calcul | 0 si CADRE, cumul hebdos si AGENT | - | - | 8h/sem | - | - | Oui | Oui | Confirmé |
| `HS_MNT_30` | Montant HS 30% | Rémunération | Rémunération des HS tranche 1 | Decimal | MGA | Calcul Moteur | Calcul | $HS\_NB\_30 \times SAL\_TAUX\_HORAIRE \times 1.30$| $TauxH$| 130% | - | Oui | Oui | Oui | Oui | Confirmé |
| `HS_NB_50` | Heures Sup. Majorées 50%| Pointage | Heures sup. tranche 2 (> 48e h/semaine) | Decimal | Heures | `pointage_cumul_periode.heures_hs_50` | Calcul | 0 si CADRE, cumul hebdos si AGENT | - | - | - | - | - | Oui | Oui | Confirmé |
| `HS_MNT_50` | Montant HS 50% | Rémunération | Rémunération des HS tranche 2 | Decimal | MGA | Calcul Moteur | Calcul | $HS\_NB\_50 \times SAL\_TAUX\_HORAIRE \times 1.50$| $TauxH$| 150% | - | Oui | Oui | Oui | Oui | Confirmé |
| `NUIT_NB_HEURES` | Heures de Nuit | Pointage | Heures travaillées entre 22h et 05h | Decimal | Heures | `pointage_cumul_periode.heures_nuit_total`| Calcul | Cumul pointage quotidien de nuit | - | - | - | - | - | Oui | Oui | Confirmé |
| `NUIT_MNT_MAJ` | Majoration Heures Nuit | Rémunération | Prime de nuit (+30%) | Decimal | MGA | Calcul Moteur | Calcul | $NUIT\_NB\_HEURES \times SAL\_TAUX\_HORAIRE \times 0.30$| $TauxH$| 30% | - | Oui | Oui | Oui | Oui | Confirmé |
| `FERIE_NB_JOURS` | Jours Fériés Travaillés | Pointage | Nombre de jours fériés travaillés | Decimal | Jours | `pointage_cumul_periode.nb_jours_ferie_travaille`| Calcul | Cumul des jours fériés prestés | - | - | - | - | - | Oui | Oui | Confirmé |
| `FERIE_MNT_MAJ` | Majoration Jours Fériés | Rémunération | Majoration travail jour férié (+100%) | Decimal | MGA | Calcul Moteur | Calcul | $FERIE\_NB\_JOURS \times (SAL\_BASE / 30) \times 1.0$| Jour | 100% | - | Oui | Oui | Oui | Oui | Confirmé |
| `ABS_RETENUE_H` | Retenue Retards / Absences| Déduction | Retards nets non compensés ou non justifiés | Decimal | MGA | Calcul Moteur | Calcul | $heures\_absence\_retenues \times SAL\_TAUX\_HORAIRE$| $TauxH$| -100%| - | Réduit Brut| Réduit Cotis.| Oui | Oui | Confirmé |
| `PRIME_ANC` | Prime d'Ancienneté | Rémunération | Prime liée à la fidélité dans l'entreprise | Decimal | MGA | Règle Entreprise / Contrat | Calcul | ⚠ À confirmer : barème d'ancienneté ? | Base | % | - | Oui | Oui | Oui | Oui | ⚠ À VÉRIFIER |
| `PRIME_EXCEPT` | Primes Exceptionnelles | Rémunération | Bonus, gratifications, performance | Decimal | MGA | Saisie DAF (`variable_paie_mensuelle`) | Saisie | Saisi par DAF avec motif | - | - | - | Oui | Oui | Oui | Oui | Confirmé |
| `IND_DEPLACEMENT` | Indemnité de Déplacement| Indemnité | Indemnité de transport mensuelle/journalière| Decimal | MGA | `pointage_cumul_periode.montant_deplacement`| Calcul | Cascade indemnités (31 200 Ar TNR, 53 200 Ar TMM)| - | - | - | Non | ⚠ À VÉRIF | Oui | Oui | Confirmé |
| `IND_REPAS` | Indemnité de Repas | Indemnité | Prise en charge repas du midi | Decimal | MGA | `pointage_cumul_periode.montant_repas` | Calcul | Jours éligibles × forfait site (2 000 Ar TNR) | - | - | - | Non | ⚠ À VÉRIF | Oui | Oui | Confirmé |
| `REM_FRAIS_PRO` | Remboursement Frais | Indemnité | Notes de frais professionnels validées | Decimal | MGA | Saisie DAF (`variable_paie_mensuelle`) | Saisie | Total des notes de frais justifiées | - | - | - | Non | Non | Oui | Oui | Confirmé |
| `BRUT_TOTAL` | Salaire Brut Global | Total | Total des éléments bruts de rémunération | Decimal | MGA | Calcul Moteur | Calcul | $\sum(Gains\ Bruts) - Retenues\ Absences$ | - | - | - | - | - | Oui | Oui | Confirmé |
| `ASSIETTE_CNAPS` | Assiette de Cotisation CNaPS| Assiette | Base de calcul plafonnée à 8 × SME | Decimal | MGA | Calcul Moteur | Calcul | $Min(BRUT\_TOTAL,\ 8 \times SME\_NATIONAL)$ | Brut | - | 8×SME | - | - | Oui | Oui | Confirmé |
| `CNAPS_SAL` | Cotisation CNaPS Salariale| Retenue | Part salariale CNaPS (1%) | Decimal | MGA | `CalculateurPaieEngine.calculerCnaps` | Calcul | $ASSIETTE\_CNAPS \times 1\%$ | CNaPS | 1.0% | $8 \times SME \times 1\%$ | Déductible IRSA| Retenue | Oui | Oui | Confirmé |
| `CNAPS_PAT` | Cotisation CNaPS Patronale| Charge Pat. | Part patronale CNaPS (13%) | Decimal | MGA | `CalculateurPaieEngine.calculerCnaps` | Calcul | $ASSIETTE\_CNAPS \times 13\%$ | CNaPS | 13.0% | $8 \times SME \times 13\%$| - | Charge Pat. | Oui (Total Pat.)| Oui | Confirmé |
| `ASSIETTE_SANTE` | Assiette Cotisation Santé | Assiette | Base de calcul organisme médical (Brut) | Decimal | MGA | Calcul Moteur | Calcul | $BRUT\_TOTAL$ (non plafonnée) | Brut | - | Aucun | - | - | Oui | Oui | Confirmé |
| `SANTE_SAL` | Cotisation Santé Salariale| Retenue | Part salariale santé (OSTIE 1% ou OMSI 1.5%)| Decimal | MGA | `CalculateurPaieEngine.calculerSante` | Calcul | $ASSIETTE\_SANTE \times TauxSalarialSite$ | Santé | 1% / 1.5%| Aucun | Déductible IRSA| Retenue | Oui | Oui | Confirmé |
| `SANTE_PAT` | Cotisation Santé Patronale| Charge Pat. | Part patronale santé (OSTIE 5% ou OMSI 5.5%)| Decimal | MGA | `CalculateurPaieEngine.calculerSante` | Calcul | $ASSIETTE\_SANTE \times TauxPatronalSite$ | Santé | 5% / 5.5%| Aucun | - | Charge Pat. | Oui (Total Pat.)| Oui | Confirmé |
| `FMFP_PAT` | Contribution FMFP Patronale| Charge Pat. | Formation professionnelle patronale (1%) | Decimal | MGA | Calcul Moteur | Calcul | $ASSIETTE\_CNAPS \times 1\%$ (ou Brut non plaf.)| Assiette| 1.0% | ⚠ À VÉRIF | - | Charge Pat. | Oui (Total Pat.)| Oui | ⚠ À VÉRIFIER |
| `TOT_COTIS_SAL` | Total Cotisations Salariales| Total | Somme des charges sociales retenues | Decimal | MGA | Calcul Moteur | Calcul | $CNAPS\_SAL + SANTE\_SAL$ | - | - | - | - | - | Oui | Oui | Confirmé |
| `BASE_IRSA` | Base Imposable IRSA | Assiette | Revenu brut imposable à l'IRSA | Decimal | MGA | Calcul Moteur | Calcul | $BRUT\_TOTAL - TOT\_COTIS\_SAL$ | Brut Net| - | - | Base IRSA | - | Oui | Oui | Confirmé |
| `BASE_IRSA_ARRONDIE`| Base IRSA Arrondie | Assiette | Base arrondie à la centaine inférieure | Decimal | MGA | Calcul Moteur | Calcul | $Floor(BASE\_IRSA / 100) \times 100$ | Base | - | - | Base IRSA | - | Oui | Oui | ⚠ À VÉRIFIER |
| `IRSA_BRUT` | IRSA Brut par Tranches | Fiscal | Impôt calculé selon le barème progressif | Decimal | MGA | `CalculateurPaieEngine.calculerIrsa` | Calcul | Application tranches 1 à 6 | Tranches| 0% à 25% | - | - | - | Non | Oui | Confirmé |
| `IRSA_DEDUC_ENF` | Réduction IRSA Enfants | Fiscal | Déduction fiscale pour charges de famille | Decimal | MGA | `CalculateurPaieEngine.calculerIrsa` | Calcul | $EMP\_CHARGE\_FAM \times 2\,000\text{ Ar}$ | Enfant | 2 000 Ar | - | - | - | Oui | Oui | Confirmé |
| `IRSA_NET` | IRSA Net Décompté | Retenue | Impôt final prélevé à la source | Decimal | MGA | `CalculateurPaieEngine.calculerIrsa` | Calcul | $Max(IRSA\_BRUT - IRSA\_DEDUC\_ENF,\ 3\,000)$| - | - | - | Impôt Déduit | - | Oui | Oui | Confirmé |
| `ISI_RETENUE` | Retenue ISI 5% (Consultants)| Retenue | Impôt synthétique intermédiaire 5% | Decimal | MGA | Calcul Moteur | Calcul | $BRUT\_TOTAL \times 5\%$ (si CONSULTANT) | Brut | 5.0% | Aucun | Impôt Déduit | - | Oui | Oui | Confirmé |
| `AVANCE_SALAIRE` | Avance sur Salaire | Déduction | Avance exceptionnelle accordée en cours de mois| Decimal| MGA | `variable_paie_mensuelle` | Saisie | Saisi par DAF et validé trésorerie | - | - | - | Non | Non | Oui | Oui | Confirmé |
| `ACOMPTE_15` | Acompte de Quinzaine | Déduction | Acompte versé au milieu du mois | Decimal | MGA | `variable_paie_mensuelle` | Saisie | Saisi par DAF et validé trésorerie | - | - | - | Non | Non | Oui | Oui | Confirmé |
| `PRET_ECHEANCE` | Échéance Prêt Entreprise | Déduction | Mensualité de remboursement prêt interne | Decimal | MGA | `pret_employe` | Automatique| Mensualité configurée de l'emprunt | - | - | - | Non | Non | Oui | Oui | Confirmé |
| `SAISIE_ARRET` | Saisie sur Salaire / Pension | Déduction | Décision judiciaire ou avis à tiers détenteur | Decimal | MGA | Saisie DAF | Saisie | Montant ordonné selon quotité cessible | - | - | Quotité légale| Non | Non | Oui | Oui | ⚠ À VÉRIFIER |
| `TOT_RETENUES` | Total des Retenues | Total | Somme des cotisations, impôts et avances | Decimal | MGA | Calcul Moteur | Calcul | $TOT\_COTIS\_SAL + IRSA\_NET + Avances + Prêts$| - | - | - | - | - | Oui | Oui | Confirmé |
| `NET_A_PAYER` | Net à Payer Final (Net à Verser)| Total | Montant net viré sur le compte bancaire | Decimal | MGA | Calcul Moteur | Calcul | $BRUT\_TOTAL + NonImposables - TOT\_RETENUES$| - | - | - | - | - | Oui | Oui | Confirmé |
| `COUT_GLOBAL_PAT` | Coût Total Employeur | Employeur | Total masse salariale déboursée pour le poste| Decimal | MGA | Calcul Moteur | Calcul | $BRUT\_TOTAL + CNAPS\_PAT + SANTE\_PAT + FMFP$| - | - | - | - | - | Oui (Pied) | Oui | Confirmé |

---

## 7. VARIABLES ET DONNÉES MANQUANTES

L'analyse minutieuse révèle que certaines données sont indispensables au calcul ou doivent être formellement créées avant de pouvoir exécuter une paie sans risque juridique :

### 7.1 Niveau CRITIQUE (Bloquant pour le calcul de paie)
1. **Modélisation BDD du Bulletin de Paie (`bulletin_paie`, `bulletin_ligne`, `bulletin_cotisation`)** :
   * *Pourquoi* : Actuellement, aucune table n'existe pour stocker le résultat d'un calcul de paie.
   * *Où la stocker* : Nouvelles tables PostgreSQL dédiées à la paie avec contrainte d'unicité `(id_employe, id_periode_paie)`.
   * *Immutabilité* : Obligation de figer en dur toutes les valeurs calculées lors du passage au statut `CLOTURE`.
2. **Table opérationnelle des Variables de Paie du Mois (`variable_paie_mensuelle`)** :
   * *Pourquoi* : Impossible de déduire un acompte de quinzaine, une avance exceptionnelle, ou d'ajouter une prime sans table de saisie validée par la DAF. (Documentée dans `module-paie-avances-frais.md` mais jamais migrée en SQL).
   * *Rôle* : Saisie par RH/Trésorerie, validée par DAF avant lancement du calcul.
3. **Plafond et Quotité Cessible pour Retenues sur Salaire (Code du Travail Art. 74-76)** :
   * *Pourquoi* : La loi interdit de saisir l'intégralité du salaire d'un employé pour rembourser un prêt ou une avance. Une quotité saisissable maximale (ex: 1/3 ou barème par tranches) doit être configurée pour plafonner les déductions mensuelles.

### 7.2 Niveau IMPORTANT (Nécessaire pour la conformité et les déclarations officielles)
1. **Cotisation Patronale FMFP (Fonds Malgache de Formation Professionnelle)** :
   * *Pourquoi* : Obligation légale pour toutes les entreprises à Madagascar (Loi n°2017-023, taux de 1% patronal). Mentionnée dans le cahier des charges mais absente de `ref_type_cotisation`.
2. **Date Limite d'Âge et Statut des Enfants à Charge (IRSA)** :
   * *Pourquoi* : Actuellement, `employe_enfant` possède un simple booléen `a_charge = true`. La loi fiscale malgache limite la déduction aux enfants mineurs (< 21 ans) ou poursuivant des études supérieures (jusqu'à 25 ans avec certificat de scolarité), ou infirmes sans limite d'âge.
   * *Action requise* : Un contrôle temporel sur la date de naissance de l'enfant et la validation annuelle du justificatif de scolarité.
3. **Gestion des Cotisations Sociales sur les Indemnités (Transport / Repas)** :
   * *Pourquoi* : `ref_indemnite` ne possède que la colonne `imposable_irsa`. Il manque la colonne `soumis_cnaps`. Or, la CNaPS exige que les indemnités forfaitaires non justifiées soient soumises à cotisation sauf exonération spécifique.

### 7.3 Niveau OPTIONNEL (Améliorations futures et confort opérationnel)
1. **Échéancier automatisé des Prêts d'Entreprise (`pret_employe`)** :
   * *Pourquoi* : Permet au moteur d'intégrer automatiquement l'échéance mensuelle $N$ sur $Total$ sans ressaisie manuelle chaque mois.
2. **Génération automatisée des Ordres de Virement Bancaire (Format SEPA ou txt BNI/BOA/BFV)** :
   * *Pourquoi* : Génération du fichier d'échange bancaire au format attendu par les banques partenaires de GALAXAM.
3. **Module de Rapprochement Comptable (Export Écritures Journal de Paie)** :
   * *Pourquoi* : Génération automatique des écritures de débit/crédit pour le logiciel de comptabilité (Comptes 641, 645, 421, 431, 442, etc.).

---

## 8. RÈGLES MÉTIER

### RG-PAIE-M01 : Éligibilité et Rattachement Périodique
* Un collaborateur fait l'objet d'un bulletin de salaire sur la période `P` si et seulement si :
  1. Il possède un contrat actif (`id_statut_rh` correspondant à `ACTIF`, ou ayant pris fin au cours de ladite période `P`).
  2. Son type d'intervenant n'est pas `STAGIAIRE_NON_REMUNERE`.
  3. La synthèse de pointage de la période `P` est validée (`VALIDEE`) ou scellée (`CLOTUREE`) par la DAF.

### RG-PAIE-M02 : Proratisation des Entrées / Sorties en Cours de Mois
* Pour un employé embauché ou ayant quitté la société en cours de période :
  $$\text{Salaire de Base Proratisé} = \text{Salaire Contractuel} \times \frac{\text{Nombre de Jours Ouvrés Effectifs Travaillés}}{\text{Nombre Total de Jours Ouvrés du Mois}}$$
* Ce prorata temporis s'applique également aux salariés mensualisés. *(Règle validée et testée dans `BulletinPaiePreparationIntegrationTest.testBulletinPaieProrataTemporisEmp5`)*.

### RG-PAIE-M03 : Forfait Sans Heures Sup. des Cadres Autonomes
* Conformément à la convention d'entreprise et au Code du Travail (Art. 83) :
  * Les collaborateurs classés `CADRE` ne perçoivent aucune rémunération au titre des heures supplémentaires (`HS_30 = 0`, `HS_50 = 0`).
  * En revanche, les retards nets non compensés peuvent faire l'objet d'un arbitrage DAF ou d'une retenue pour absence si validée lors du scellement du pointage.

### RG-PAIE-M04 : Décompte et Compensation 1:1 des Retards / Heures Supplémentaires
* Pour les Agents (`CATEGORIE = 'A'`) :
  * Les retards constatés au cours d'une semaine ISO sont compensés en priorité à due concurrence (1 heure pour 1 heure) par les heures effectuées au-delà de 8h/jour dans le même tronçon hebdomadaire.
  * Seul le solde net positif hebdomadaire donne lieu à majoration pour heures supplémentaires.
  * Le solde négatif non compensé (`heures_absence_retenues`) est transmis à la paie pour déduction sur le taux horaire contractuel.

### RG-PAIE-M05 : Rémunération des Congés Payés & Maintien de Salaire
* **Congés Payés Annuels** : Maintien intégral du salaire de base à 100%. Aucune ligne de retenue négative n'apparaît sur le bulletin tant que le collaborateur dispose d'un solde de congés suffisant.
* **Absences Injustifiées & Congés Sans Solde** : Déduction stricte sur le bulletin :
  $$\text{Retenue Absence} = \text{Heures Absence} \times \left( \frac{\text{Salaire de Base}}{173.3333} \right)$$

---

## 9. RÈGLES LÉGALES & CONVENTIONNELLES

### 9.1 Règles Confirmées (Source Légale ou Réglementaire Vérifiée)
* ⚖ **Plafonnement CNaPS (Code de Prévoyance Sociale Malgache)** :
  * Assiette CNaPS = $\min(\text{Salaire Brut}, 8 \times \text{SME National})$.
  * Part Salariale = $1\%$ de l'assiette.
  * Part Patronale = $13\%$ de l'assiette.
* ⚖ **Cotisation Santé Inter-entreprises (Décrets Ministériels & Statuts des Organismes)** :
  * Assiette Santé = Totalité du salaire brut non plafonné.
  * Pour Antananarivo (OSTIE) : $1\%$ salarié, $5\%$ employeur.
  * Pour Toamasina (OMSI) : $1.5\%$ salarié, $5.5\%$ employeur.
* ⚖ **Barème Progressif IRSA 2026 (Loi de Finances n°2025-021)** :
  * Tranche 1 (0 à 350 000 Ar) : $0\%$
  * Tranche 2 (350 001 à 400 000 Ar) : $5\%$
  * Tranche 3 (400 001 à 500 000 Ar) : $10\%$
  * Tranche 4 (500 001 à 600 000 Ar) : $15\%$
  * Tranche 5 (600 001 à 4 000 000 Ar) : $20\%$
  * Tranche 6 (> 4 000 000 Ar) : $25\%$
* ⚖ **Minimum de Perception IRSA & Déduction Familiale (CGI Art. 01.03.14)** :
  * Déduction pour charge de famille : $2\,000\text{ Ar}$ par enfant à charge par mois.
  * Minimum légal d'impôt perçu : $3\,000\text{ Ar}$ par mois (tout salarié imposable ou non imposable après déductions paie au minimum 3 000 Ar).
* ⚖ **Diviseur Légal de Durée du Travail (Code du Travail Art. 83)** :
  * 40 heures hebdomadaires $\rightarrow$ Diviseur mensuel exact = $173.3333\text{ heures}$.

### 9.2 Règles Documentées mais À Vérifier formellement
* ⚠ **Arrondi de la Base IRSA** :
  * Documenté dans `paie_config_legale.arrondi_base_irsa = 'CENTAINE_INF'`. En pratique fiscale à Madagascar, la base imposable est tronquée à la centaine d'Ariary inférieure avant application des taux de tranche. *À valider avec l'expert-comptable / DAF de GALAXAM*.
* ⚠ **Traitement de l'Indemnité de Congé Payé non pris (Solde de tout compte)** :
  * Diviseur 30j calendaires vs 24j ouvrables : L'article 85 du Code du Travail stipule l'indemnité égale au 1/30ème des salaires des 12 derniers mois. *À valider pour les sorties de contrat*.
* ⚠ **Régime Santé des Consultants (ISI 5%)** :
  * En BDD (`26-08-05-sp0-ref.sql`), le type `CONSULTANT` a `soumis_sante = TRUE`. S'agit-il d'une règle spécifique GALAXAM pour assurer la couverture santé de ses consultants seniors, ou d'une anomalie de paramétrage ?

### 9.3 Règles Propres à l'Entreprise (GALAXAM)
* 🏢 **Barème des Indemnités de Déplacement de Site** :
  * Antananarivo (TNR) : 31 200 Ar mensuel fixe proratisé.
  * Toamasina (TMM) : 53 200 Ar mensuel fixe proratisé.
* 🏢 **Indemnité de Repas** :
  * Accordée uniquement sur les sites en horaire continu (TNR : 2 000 Ar / jour présent travaillé). Non accordée par défaut à Toamasina (horaire discontinu avec pause déjeuner à domicile).
* 🏢 **Avances et Acomptes** :
  * Acompte standard versé le 15 du mois (limité à 50% du salaire de base net estimé).

---

## 10. PARAMÈTRES CONFIGURABLES ET GESTION TEMPORELLE

Pour garantir qu'aucun changement législatif futur ne nécessite de recompilation ou de modification du code source, les paramètres suivants sont **totalement externalisés en base et millésimés par date d'effet** :

```
                                GESTION TEMPORELLE DES PARAMÈTRES DE PAIE
   Millésime N-1 (Historique)            Millésime N (En Vigueur)            Millésime N+1 (Programmé)
[01/01/2024 ──> 31/12/2025]        [01/01/2026 ──> NULL (Actif)]         [01/01/2027 ──> NULL]
┌───────────────────────────┐      ┌───────────────────────────┐         ┌───────────────────────────┐
│ SME : 200 000 Ar          │      │ SME : 262 680 Ar          │         │ SME : 300 000 Ar          │
│ Plafond 8x: 1 600 000 Ar  │      │ Plafond 8x: 2 101 440 Ar  │         │ Plafond 8x: 2 400 000 Ar  │
│ IRSA : 5 tranches (LF 24) │      │ IRSA : 6 tranches (LF 26) │         │ IRSA : Nouvelles tranches │
│ Min Perception: 2 000 Ar  │      │ Min Perception: 3 000 Ar  │         │ Min Perception: 3 000 Ar  │
└───────────────────────────┘      └───────────────────────────┘         └───────────────────────────┘
```

### Table des Paramètres Configurables :

| Code Paramètre | Libellé | Unité | Source BDD | Impact Moteur | Règle de Temporalité |
|---|---|---|---|---|---|
| `sme_national` | Salaire Minimum d'Embauche | MGA | `paie_config_legale` | Plafond CNaPS, seuils minimaux | Valable tant que `date_fin IS NULL` |
| `plafond_cotisation_nb_sme`| Multiplicateur plafond CNaPS | Ratio (8.00) | `paie_config_legale` | Assiette CNaPS | Valable par millésime |
| `seuil_exoneration_irsa`| Seuil tranche exonérée IRSA | MGA (350 000) | `paie_config_legale` | Calcul IRSA tranche 1 | Valable par millésime |
| `minimum_perception_irsa`| Minimum de perception IRSA | MGA (3 000) | `paie_config_legale` | Plancher impôt IRSA | Valable par millésime |
| `deduction_charge_famille`| Déduction fiscale enfant | MGA (2 000) | `paie_config_legale` | Réduction d'impôt | Valable par millésime |
| `diviseur_mensuel_heures`| Diviseur mensuel heures | Heures (173.3333)| `paie_config_legale` | Taux horaire de base | Valable par millésime |
| `diviseur_valorisation_conge`| Diviseur congés payés | Jours (30) | `paie_config_legale` | Indemnité congé | Valable par millésime |
| `taux_salarial` / `taux_patronal`| Taux cotisations sociales | % | `ref_type_cotisation` | Retenues CNaPS, Santé | Lié à `id_config_legale` |
| `tranche_min` / `tranche_max` / `taux`| Tranches barème progressif IRSA | MGA / % | `bareme_irsa` | Calcul IRSA | Lié à `id_config_legale` |
| `type_majoration` / `taux`| Taux majoration HS / Nuit / Dim | % | `ref_taux_majoration` | Valorisation heures supp. | Lié à `id_config_legale` |

---

## 11. HISTORISATION ET IMMUTABILITÉ ABSOLUE

La règle cardinale d'un progiciel de paie est :
> **"Un bulletin de paie généré et clôturé pour le mois M ne doit JAMAIS changer de montant à l'avenir, même si le salaire de l'employé est réévalué le mois suivant ou si la loi de finances change les barèmes fiscaux."**

### Architecture d'Immutabilité du Module Paie :
1. **Dénormalisation scellée (Snapshotting)** :
   * La table `bulletin_paie` ne se contente pas de stocker des clés étrangères : elle enregistre **en dur dans ses colonnes** le salaire de base contractuel au moment du calcul, l'intitulé du poste, le site, le matricule, le taux horaire, les totaux bruts, nets et imposables.
   * La table `bulletin_cotisation` enregistre le nom de l'organisme (`CNAPS`, `OSTIE`), l'assiette exacte, le taux salarial, la part salariale calculée, le taux patronal et la part patronale.
   * La table `bulletin_ligne` enregistre chaque ligne d'indemnité, de prime, d'absence ou de retenue avec son libellé, sa base, son taux et son montant.
2. **Scellement cryptographique / Verrouillage d'état** :
   * Les statuts d'un bulletin : `BROUILLON` $\rightarrow$ `CALCULE` $\rightarrow$ `VALIDE` $\rightarrow$ `CLOTURE`.
   * Dès qu'une période de paie passe à `CLOTURE`, un trigger ou une contrainte applicative interdit tout `UPDATE` ou `DELETE` sur `bulletin_paie`, `bulletin_ligne`, et `bulletin_cotisation`.
3. **Régularisations rétroactives (Principe du Report)** :
   * Si une erreur est découverte sur une paie clôturée (ex: oubli de 5 heures supplémentaires sur Août 2026 découvert en Septembre), le bulletin d'Août n'est **JAMAIS réouvert**.
   * Une ligne de régularisation est insérée sur la paie du mois suivant (Septembre 2026) : *"Régularisation HS Août 2026 : +X Ar"*.

---

## 12. WORKFLOW DE PAIE COMPLET

Le cycle de vie de la paie s'articule autour d'un workflow strict et auditable :

```
 ┌──────────────────────┐
 │   1. PRÉPARATION     │  • Vérification des mouvements RH (embauches, avenants de salaire).
 │   (Du 1er au 20)     │  • Saisie continue du pointage quotidien sur les sites.
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │ 2. GEL DU POINTAGE   │  • Détection et apurement de 100% des anomalies de présence.
 │   (Vers le 25-28)    │  • Transmission DAF par le Responsable de Site (`A_VALIDER`).
 └──────────┬───────────┘  • Gel des cumuls mensuels (`pointage_cumul_periode`).
            ▼
 ┌──────────────────────┐
 │ 3. SAISIE VARIABLES  │  • Importation / Saisie des acomptes de quinzaine versés.
 │      (DAF / RH)      │  • Intégration des avances exceptionnelles et échéances de prêts.
 └──────────┬───────────┘  • Saisie des primes exceptionnelles et notes de frais.
            ▼
 ┌──────────────────────┐
 │ 4. CALCUL (MOTEUR)   │  • Exécution du PayrollEngine en mode batch ou individuel.
 │   (Statut: CALCULÉ)  │  • Génération des bulletins en état `CALCULE` (Pré-paie).
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │ 5. CONTRÔLE DAF      │  • Analyse du journal de paie comparatif ($M$ vs $M-1$).
 │   & ÉTATS D'ÉCARTS   │  • Détection des variations anormales de brut ou de net.
 └──────────┬───────────┘  • Si anomalie : rejet et recalcul ciblés.
            ▼
 ┌──────────────────────┐
 │ 6. VALIDATION DAF    │  • Verrouillage des montants de la période.
 │   (Statut: VALIDÉ)   │  • Autorisation des virements bancaires par la Direction.
 └──────────┬───────────┘
            ▼
 ┌──────────────────────┐
 │ 7. PAIEMENT BANCAIRE │  • Émission du fichier de virement bancaire (BNI, BOA, BFV).
 │    & DISTRIBUTION    │  • Génération des bulletins PDF individuels.
 └──────────┬───────────┘  • Notification des employés (consultation espace collaborateur).
            ▼
 ┌──────────────────────┐
 │ 8. CLÔTURE DÉFINITIVE│  • Statut de la période passe à `CLOTUREE` (Immuable).
 │   & DÉCLARATIONS     │  • Génération des états nominatifs trimestriels CNaPS, FMFP, Santé.
 └──────────────────────┘  • Déversement comptable (écritures de paie).
```

### Matrice des Responsabilités & Droits :

| Étape du Workflow | Acteur Principal | Rôle / Profil | Action Système Requise |
|---|---|---|---|
| Saisie & Résolution Pointage | Agent de Pointage Site | `ROLE_AGENT_POINTAGE` | Traitement anomalies pointage, soumission synthèse |
| Transmission Pointage à la DAF | Agent de Pointage Site | `ROLE_AGENT_POINTAGE` | `PointageSyntheseService.transmettreDaf` |
| Saisie Avances / Primes | Gestionnaire RH / Paie | `ROLE_ADMIN` (`PAIE_WRITE`) | Création des lignes variables mensuelles |
| Exécution du Calcul Pré-paie | Gestionnaire RH / DAF | `ROLE_ADMIN` (`PAIE_WRITE`) | `PayrollEngine.calculerPeriode` |
| Contrôle & Arbitrage | Directeur Admin. & Financier | `ROLE_ADMIN` (`PAIE_WRITE`) | Consultation états de contrôle et écarts $N / N-1$ |
| Validation Officielle de la Paie| Directeur Admin. & Financier | `ROLE_ADMIN` (`PAIE_VALIDATE`)| `PaieService.validerPeriodePaie` |
| Clôture Définitive & Archive | Direction Générale / DAF | `ROLE_ADMIN` (`PAIE_CLOSE`) | Scellement et gel des écritures |

---

## 13. STRUCTURE FONCTIONNELLE DU BULLETIN DE SALAIRE

Le bulletin de paie GALAXAM est articulé en 9 zones fonctionnelles rigoureuses :

```
┌──────────────────────────────────────────────────────────────────────────────────────────┐
│ [1] INFORMATIONS EMPLOYEUR                                                               │
│ Raison Sociale : GALAXAM SARL             N° STAT : 74990 11 2006 0 01234               │
│ Siège Social   : Immeuble Fiaro, TNR      N° NIF  : 3000214589                          │
│ Site d'affectation : Siège Antananarivo   N° CNaPS Employeur : 010-99887-12            │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [2] INFORMATIONS SALARIÉ & CONTRAT                                                       │
│ Matricule : EMP-2014-0012                 Date d'Embauche : 01/03/2014                  │
│ Nom & Prénom : RANDRIAMAMPIONONA J.       Ancienneté      : 12 ans 5 mois               │
│ Fonction  : Superviseur d'Opérations      Catégorie / Statut : Agent (Éligible HS)       │
│ N° CNaPS  : 120-45678-01                  Situation Fam.  : Marié(e), 2 enfants à charge│
│ Mode Paie : Mensuel (Base 173.33 h)       Mode Règlement  : Virement BNI Madagascar     │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [3] PÉRIODE & TEMPS DE TRAVAIL                                                           │
│ Période de paie : Août 2026 (01/08/2026 - 31/08/2026)      Jours ouvrables du mois : 21  │
│ Heures normales : 173.33 h    Heures Sup 30% : 3.00 h      Heures Sup 50% : 0.00 h       │
│ Heures de Nuit  : 0.00 h      Jours Présents : 21 j        Jours Congés Payés : 0 j      │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [4] ÉLÉMENTS DE RÉMUNÉRATION (BRUT)                                                      │
│ Code     Désignation                     Base       Taux/Nombre   Gain (Ar)  Retenue (Ar)│
│ 1000     Salaire de Base Mensuel       173.33 h      10 673.18    1 850 000.00           │
│ 1100     Heures Supplémentaires (+30%)   3.00 h      13 875.14       41 625.42           │
│ 1300     Prime d'Assiduité / Assiduité   Forfait         -           50 000.00           │
│ 1900     Absence Injustifiée             0.00 h      10 673.18                       0.00│
├──────────────────────────────────────────────────────────────────────────────────────────┤
│                          TOTAL SALAIRE BRUT :                      1 941 625.42          │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [5] COTISATIONS SOCIALES & FISCALITÉ                                                     │
│ Code     Désignation               Base         Taux Sal.  Part Sal.   Taux Pat. Part Pat│
│ 2010     CNaPS (Plafond 8x SME) 1 941 625.42     1.00%      19 416.25   13.00%  252 411.30│
│ 2020     Santé (OSTIE Siège)    1 941 625.42     1.00%      19 416.25    5.00%   97 081.27│
│ 2030     FMFP (Formation Pat.)  1 941 625.42        -             -      1.00%   19 416.25│
│ 3010     IRSA (Impôt sur Revenu)1 902 792.92   Barème LF26 256 058.00        -           │
│          (Dont déduction 2 enfants à charge : -4 000 Ar)                                 │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│                          TOTAUX COTISATIONS :               294 890.50          368 908.82│
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [6] INDEMNITÉS NON SOUMISES & REMBOURSEMENTS (Gains Nets)                                │
│ Code     Désignation                     Nombre      Montant Unitaire          Total (Ar)│
│ 4010     Indemnité Déplacement (TNR)     Forfait         31 200.00              31 200.00│
│ 4020     Indemnité de Repas              21 j             2 000.00              42 000.00│
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [7] DÉDUCTIONS DE TRÉSORERIE & RETENUES DIVERSES                                         │
│ Code     Désignation                     Date / Réf                 Montant Retenu (Ar)  │
│ 5010     Acompte de Quinzaine            Versé le 15/08/2026                   200 000.00│
│ 5020     Échéance Prêt Entreprise N°4    Prêt Équipement                        50 000.00│
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [8] TOTAUX & NET À VERSER                                                                │
│ Salaire Brut Global : 1 941 625.42 Ar      Charges Patronales :   368 908.82 Ar          │
│ Total Charges Sal.  :   38 832.50 Ar      Coût Global Employeur: 2 310 534.24 Ar         │
│ Net Imposable IRSA  : 1 902 792.92 Ar      Congés N-1 : 12.5 j   Pris : 0 j   Solde: 15 j│
│ Net Avant Avances   : 1 646 734.92 Ar                                                    │
│                                                                                          │
│                       👉 NET À PAYER (NET À VIRER) : 1 469 934.92 Ar                     │
├──────────────────────────────────────────────────────────────────────────────────────────┤
│ [9] MENTIONS LÉGALES & PIED DE PAGE                                                      │
│ Virement ordonné le 31/08/2026 sur le compte BNI N° 00004 12345678901 22                 │
│ "Dans votre intérêt, conservez ce bulletin de paie sans limitation de durée."            │
│ Référence de calcul : GALAXAM-PAYROLL-v1.0 / Config Légale : LF_2026 (Certifiée)         │
└──────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 14. CAS PARTICULIERS À TRAITER

| Cas Particulier | Données Nécessaires | Règle de Gestion Applicative | Impact sur le Brut / Net | Traçabilité & Historique |
|---|---|---|---|---|
| **Embauche en cours de mois** | `date_debut` du contrat, Jours ouvrés du mois | Prorata temporis : $Salaire \times \frac{JoursTravaillés}{JoursOuvrables}$ | Réduction proportionnelle du salaire de base | Le contrat initial porte la date exacte d'embauche. |
| **Départ en cours de mois (STC)** | `date_fin`, motif de départ, solde de congés | Prorata du salaire de base + Indemnité compensatrice de congés payés ($Solde \times \frac{Brut}{30}$) | Calcul du Solde de Tout Compte (STC) | Bulletin marqué `SOLDE_DE_TOUT_COMPTE`. |
| **Changement de salaire en cours de mois** | Avenant avec `date_effet` au milieu du mois | Prorata double tranche : $\frac{J_1 \times Sal_1 + J_2 \times Sal_2}{JoursOuvrables}$ | Deux lignes d'historique de carrière | Deux lignes distinctes sur le bulletin ou ligne de complément. |
| **Cadre effectuant des heures de week-end** | `pointage_jour` le samedi ou dimanche | Non éligible aux HS habituelles, mais éligible à la majoration Dimanche (+40%) si imposé | Majoration dimanche uniquement | Statut `CADRE` vérifié par le moteur. |
| **Maladie ordinaire courte (< 3 jours)** | Arrêt de travail transmis | Maintien intégral du salaire (selon convention entreprise) | Aucun impact négatif sur le brut | Décompte du quota d'absence maladie. |
| **Maladie longue (> 3 mois)** | Certificat médical, dossier CNaPS | Bascule en congé maladie sans solde (relais CNaPS indemnités journalières) | Suppression du salaire de base | Maintien de l'affiliation CNaPS. |
| **Salarié consultant (Honoraires)** | `ref_type_intervenant = 'CONSULTANT'` | Exonération CNaPS salariale, application de la retenue libératoire ISI 5% | Pas de CNaPS/IRSA, déduction 5% ISI | Édition d'une note d'honoraires / bulletin spécial. |
| **Trop-perçu sur le mois précédent** | Montant d'erreur constaté par la DAF | Ligne de régularisation négative sur le mois courant avec motif explicite | Déduction plafonnée par la quotité saisissable | Historique du bulletin antérieur non modifié. |

---

## 15. SOURCES DE VÉRITÉ (SINGLE SOURCE OF TRUTH)

Pour éliminer toute ambiguïté et tout risque de collision entre tables, la répartition des responsabilités est gravée comme suit :

```
┌──────────────────────────────────────┬───────────────────────────────────────────┐
│ DOMAINE / DONNÉE                     │ SOURCE UNIQUE DE VÉRITÉ OFFICIELLE        │
├──────────────────────────────────────┼───────────────────────────────────────────┤
│ Identité, Enfants à charge, RIB      │ employe, employe_enfant, compte_banque    │
│ Salaire contractuel de base          │ employe_affectation_carriere (actif)      │
│ Heures travaillées, HS, Nuit, Retard │ pointage_cumul_periode (gelé à clôture)   │
│ Indemnités déplacement / repas réels │ pointage_cumul_indemnite (gelé à clôture) │
│ Droits et consommations congés       │ demande_conge, mouvement_solde_conge      │
│ Avances, Prêts, Primes ponctuelles   │ variable_paie_mensuelle (validée DAF)     │
│ SME, Plafonds, Tranches IRSA, Taux   │ paie_config_legale, bareme_irsa           │
│ Résultat de calcul du mois           │ bulletin_paie, bulletin_ligne (scellés)   │
└──────────────────────────────────────┴───────────────────────────────────────────┘
```

---

## 16. DÉTECTION ET ÉLIMINATION DES DUPLICATIONS

L'audit met en évidence trois duplications critiques à éliminer formellement lors de la conception :
1. **Doublon des Indemnités de Pointage** :
   * La table `pointage_jour` contient encore des colonnes miroirs (`indemnite_deplacement_recue`, `montant_repas`).
   * **Arbitrage de conception** : Le moteur de paie lira **EXCLUSIVEMENT** la table gelée `pointage_cumul_indemnite` et la table relationnelle `pointage_indemnite_jour`. Les colonnes miroirs de `pointage_jour` ne sont que des caches UI de saisie et ne doivent jamais alimenter le calcul de paie.
2. **Duplication des Constantes Légales** :
   * Le SME et le seuil d'exonération existaient historiquement dans `parametre_global` et dans `paie_config_legale`.
   * **Arbitrage de conception** : `paie_config_legale` est l'unique source de vérité légale. `parametre_global` ne conserve que les paramètres non millésimés (ex: `EMAIL_RH`).
3. **Duplication de Formules de Majoration dans le Frontend** :
   * Le frontend ne doit calculer aucun montant financier. Il affiche les montants calculés et retournés par l'API backend Java.

---

## 17. ARCHITECTURE CONCEPTUELLE DU FUTUR ENGINE DE PAIE

Le futur moteur de paie GALAXAM sera découpé en composants spécialisés à responsabilité unique (*Single Responsibility Principle*), évitant le piège du "God Engine" monolithique :

```
                              mg.galaxam.sirh.payroll.engine
                                             │
               ┌─────────────────────────────┴─────────────────────────────┐
               ▼                                                           ▼
     PayrollContextBuilder                                       PayrollOrchestrator
  (Collecte et snapshot des données)                          (Contrôle du flux de paie)
               │                                                           │
               ├───────────────────────────────────────────────────────────┤
               ▼                                                           ▼
    GrossSalaryCalculator                                      SocialCotisationCalculator
(Salaire base, proratas, HS, primes)                     (CNaPS plafonnée, Santé, FMFP patronal)
               │                                                           │
               ├───────────────────────────────────────────────────────────┤
               ▼                                                           ▼
       TaxEngine (IRSA / ISI)                                     DeductionNetCalculator
(Barème progressif, déductions, min)                       (Avances, acomptes, prêts, net final)
               │                                                           │
               └─────────────────────────────┬─────────────────────────────┘
                                             ▼
                                   PayrollSnapshotPersister
                         (Enregistrement scellé des bulletins)
```

### Description des responsabilités :
1. **`PayrollContextBuilder`** : Prépare pour chaque salarié un objet immuable `PayrollEmployeeContext` contenant toutes les données nécessaires (affectation, pointage gelé, congés, barèmes applicables, variables saisies). Zéro appel BDD supplémentaire après cette phase.
2. **`GrossSalaryCalculator`** : Calcule le salaire de base proratisé, valorise les heures supplémentaires, les majorations de nuit et de jours fériés, applique les retenues pour absence, et agrège les primes soumises.
3. **`SocialCotisationCalculator`** : Applique les plafonds (8 × SME) et calcule ligne par ligne les parts salariales et patronales pour la CNaPS et l'organisme médical du site.
4. **`TaxEngine`** : Détermine la base imposable, applique l'arrondi fiscal à la centaine inférieure, calcule l'impôt par tranche selon le régime de l'employé (IRSA progressif pour salarié, ISI 5% pour consultant), retranche la réduction pour enfants, et applique le plancher du minimum de perception légal.
5. **`DeductionNetCalculator`** : Ajoute les indemnités nettes d'exploitation, retranche les acomptes, avances et mensualités de prêt, et détermine le Net à Payer final.
6. **`PayrollSnapshotPersister`** : Insère en une seule transaction ACID le bulletin complet (`bulletin_paie`, `bulletin_ligne`, `bulletin_cotisation`).

---

## 18. RÉPARTITION DES RESPONSABILITÉS : SQL VS JAVA

En conformité stricte avec l'architecture hexagonale adoptée sur le projet GALAXAM :

### Ce que fait le SQL / Base de Données :
* Jointures relationnelles pour extraire les données consolidées du mois (`employe` + `affectation` + `pointage_cumul_periode`).
* Indexation performante sur `id_periode_paie` et `id_employe`.
* Intégrité référentielle, contraintes d'unicité `(id_employe, id_periode_paie)` empêchant les doubles paiements.
* Verrous pessimistes (`SELECT ... FOR UPDATE`) lors de la validation et clôture de période pour prévenir toute concurrence d'accès.

### Ce que fait le Java (Domaine & Engine) :
* **100% de la logique métier et des formules de calcul.**
* Détermination des taux horaires, calcul des tranches de majoration HS (30%, 50%).
* Algorithme du barème progressif IRSA à 6 tranches.
* Prorata temporis au jour ouvré près.
* Précision financière (`BigDecimal`), gestion des arrondis monétaires.
* Émission des événements d'audit et notifications.

---

## 19. ANALYSE DE PERFORMANCE & CHARGE

1. **Volume estimé** : Entre 50 et 500 employés, 12 à 24 mois d'historique en ligne.
2. **Résolution du risque N+1 Queries** :
   * Le calcul de paie mensuel ne doit **JAMAIS** faire une requête SQL par ligne d'employé pour chercher son barème ou son pointage.
   * Approche en **chargement par lot (Batch Bulk Loading)** :
     1. Chargement de l'exercice fiscal actif en 1 requête.
     2. Chargement des cumuls de pointage de tous les employés de la période en 1 requête indexée.
     3. Chargement des variables de paie du mois en 1 requête.
     4. Calcul pur en mémoire Java pour chaque collaborateur.
     5. Insertion en base par batch JDBC (`saveAll` / `executeBatch`).
3. **Temps d'exécution cible** : Calcul de la paie complète pour 200 salariés en **moins de 2.5 secondes**.

---

## 20. PRÉCISION FINANCIÈRE & RÈGLES D'ARRONDI (ARIARY MGA)

1. **Interdiction de `double` / `float`** :
   * Tous les calculs financiers dans le domaine Java utilisent impérativement `java.math.BigDecimal`.
2. **Échelles et Arrondis intermédiaires** :
   * Taux horaire : précision à 4 décimales (`scale = 4`, `RoundingMode.HALF_UP`) lors des calculs intermédiaires, arrondi à 2 décimales pour l'affichage.
   * Assiette CNaPS et Santé : 2 décimales.
   * Montants de cotisations salariales et patronales : 2 décimales (`RoundingMode.HALF_UP`).
3. **Arrondi final en Ariary (MGA)** :
   * La monnaie malgache (Ariary) n'a plus de sous-unité divisionnaire active en circulation fiduciaire (pas de centimes de pièces).
   * Les déclarations fiscales et bancaires se font à l'Ariary entier ou aux centimes selon le système bancaire.
   * Règle retenue : Calculs stockés en BDD avec `DECIMAL(12,2)`, Net à Payer arrondi à l'Ariary entier le plus proche (`RoundingMode.HALF_UP`) ou conservé au centime selon l'accord bancaire.

---

## 21. STRATÉGIE DE TESTABILITÉ

### 21.1 Tests Unitaires Isolés (`PayrollEngineUnitTest`) :
* Test du calcul des tranches IRSA aux frontières exactes (ex: 350 000 Ar, 400 000 Ar, 500 000 Ar, 4 000 000 Ar).
* Test de l'écrêtement CNaPS au plafond exact de 8 × SME ($2\,101\,440\text{ Ar}$).
* Test de la réduction famille nombreuse (0, 1, 2, 5 enfants) avec vérification du plancher de 3 000 Ar.
* Test du prorata temporis d'entrée en cours de mois (11 jours ouvrés sur 21).

### 21.2 Tests d'Intégration BDD (`PayrollIntegrationTest`) :
* Cycle complet : `Contrat` + `Carrière` + `Pointage validé` $\rightarrow$ Exécution Paie $\rightarrow$ Vérification de `bulletin_paie` généré en base.
* Vérification que la clôture d'une période verrouille toute modification ultérieure.

### 21.3 Tests de Régression & Non-Régression :
* Jeu de données de test avec 5 profils types (Cadre autonome TNR, Agent d'exploitation TMM avec HS, Nouvel embauché au 17 du mois, Salarié en congé sans solde partiel, Consultant ISI 5%).
* Validation que la réexécution du moteur sur ces 5 profils reproduit au centime près les résultats attendus.

---

## 22. RISQUES IDENTIFIÉS & MESURES DE MITIGATION

| N° | Risque Identifié | Gravité | Probabilité | Mesure d'Atténuation / Solution Conçue |
|---|---|---|---|---|
| **R-01** | Modification accidentelle d'une paie passée suite à une mise à jour d'employé | **CRITIQUE** | Moyenne | Dénormalisation complète (snapshotting) des bulletins dans `bulletin_paie` + trigger BDD d'immutabilité post-clôture. |
| **R-02** | Écart de calcul entre le pointage validé et la paie | **ÉLEVÉE** | Faible | La paie consomme **exclusivement** la table gelée `pointage_cumul_periode`, empêchant tout écart post-validation. |
| **R-03** | Erreur de tranche fiscale suite à un changement de loi de finances | **ÉLEVÉE** | Moyenne | Table `paie_config_legale` millésimée par date d'effet. Le moteur charge le millésime applicable à la date de la période. |
| **R-04** | Litige sur les heures supplémentaires des cadres | **MOYENNE** | Élevée | Exclusion automatique des HS codée dans le moteur sur critère `ref_categorie_professionnelle.code = 'CADRE'`. |
| **R-05** | Dépassement de la quotité saisissable sur avances/prêts (salaire net négatif) | **CRITIQUE** | Faible | Règle de garde-fou dans le moteur : les retenues volontaires sont plafonnées à la quotité cessible légale. |

---

## 23. QUESTIONS OUVERTES & DÉCISIONS À PRENDRE AVANT IMPLÉMENTATION

Avant d'entamer la moindre ligne de code pour les tables et services du module Paie, les arbitrages suivants doivent être officiellement tranchés par la direction de GALAXAM :

### 23.1 Décisions Fiscales & Légales :
1. **Arrondi de la base IRSA** : Confirmez-vous que la base imposable IRSA doit être tronquée à la centaine d'Ariary inférieure (`CENTAINE_INF`) avant calcul des tranches ?
2. **Statut de la cotisation FMFP** : Confirmez-vous l'intégration de la contribution patronale de 1% FMFP ? Si oui, son assiette est-elle le brut total ou l'assiette plafonnée CNaPS (8×SME) ?
3. **Assujettissement des Indemnités de Déplacement et de Repas** : Ces indemnités sont-elles exonérées à 100% de l'IRSA et de la CNaPS dans votre pratique actuelle ?

### 23.2 Décisions Métier GALAXAM :
4. **Valorisation des retenues pour absence** : Appliquez-vous la valorisation horaire sur base $173.3333\text{ h}$ ($\text{Retenue} = \frac{Salaire}{173.3333} \times \text{heures}$), ou une déduction en trentième ($1/30$), ou au nombre de jours ouvrés réels du mois ?
5. **Prime d'ancienneté** : Existe-t-il chez GALAXAM une prime d'ancienneté conventionnelle obligatoire (ex: +5% après 3 ans, +10% après 5 ans) ou le salaire de base intègre-t-il déjà toute la rémunération ?
6. **Arrondi du Net à Payer** : Le montant net à payer sur le bulletin et dans le fichier de virement bancaire doit-il être arrondi à l'Ariary entier le plus proche, ou inclure les décimales/centimes ?

---

## 24. QUESTIONS À POSER AU PROPRIÉTAIRE DU PROJET

Voici les questions concrètes, directes et ciblées à soumettre au chef de projet / propriétaire du SIRH GALAXAM pour finaliser les spécifications :

### A. Concernant le Bulletin de Salaire & Format PDF
1. Avez-vous un modèle type de bulletin de salaire (fichier PDF, scan ou capture Excel d'une paie réelle antérieure) que nous devrions reproduire pour respecter vos habitudes visuelles ?
2. Le bulletin doit-il obligatoirement afficher le récapitulatif des soldes de congés payés (Solde acquis, pris sur le mois, restant disponible) dans un encadré dédié ?

### B. Concernant les Primes et Indemnités
3. L'indemnité de déplacement de site (31 200 Ar à TNR, 53 200 Ar à TMM) est-elle réduite si un collaborateur est absent une partie du mois, ou est-elle versée intégralement dès lors qu'il fait partie des effectifs ?
4. Quels sont les types de primes variables que la DAF saisit habituellement chaque mois (ex: prime de rendement, prime de responsabilité, prime d'astreinte) ?

### C. Concernant les Avances et Acomptes
5. Les acomptes du 15 du mois sont-ils d'un montant forfaitaire fixe (ex: 50% du salaire de base) ou libres à la discrétion de la demande de l'employé ?
6. Accordez-vous des prêts d'entreprise remboursables sur plusieurs mois ? Si oui, quelle est la limite maximale prélevable sur le salaire mensuel net ?

### D. Concernant les Consultants et Prestations (ISI 5%)
7. Les consultants externes doivent-ils recevoir un bulletin de salaire classique ou une "Note d'honoraires avec décompte ISI 5%" distincte ?
8. Les consultants bénéficient-ils de la couverture médicale d'entreprise (OSTIE/OMSI) prise en charge par GALAXAM, ou n'ont-ils strictement aucune retenue sociale ?

### E. Concernant le Workflow et la Clôture
9. La période de paie coïncide-t-elle strictement avec le mois calendaire (du 1er au 30/31 du mois), ou avez-vous une période de décalage de paie (ex: du 21 du mois $M-1$ au 20 du mois $M$) ?
10. Qui au sein de l'organisation a le pouvoir final de clôturer la paie : l'Administrateur RH, la DAF, ou la Direction Générale ?

---

## 25. CRITÈRE D'ACHÈVEMENT DE CETTE PHASE

Conformément au mandat de la mission :
* ✅ Cartographie complète des dépendances établie.
* ✅ Inventaire exhaustif des 50 variables de paie formalisé avec code, source, formule et régime fiscal.
* ✅ Identification de l'absence de spécimen réel de bulletin consignée explicitement.
* ✅ Classification des données manquantes en CRITIQUE, IMPORTANT et OPTIONNEL.
* ✅ Règles légales et d'entreprise séparées sans invention.
* ✅ Architecture conceptuelle du Payroll Engine sans code prématuré.
* ✅ Questionnaire précis pour le propriétaire du projet rédigé.

**STATUT DU PROJET :** `CONCEPTION EN COURS — INFORMATIONS MÉTIER REQUISES (AVANT IMPLÉMENTATION)`  
Le module Paie est prêt à entrer en phase de modélisation BDD et d'implémentation dès que les arbitrages métier de la section 24 auront été validés.
