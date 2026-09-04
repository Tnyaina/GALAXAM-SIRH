# Rapport de Synthèse & Règles de Gestion : Module Congés & Absences (SIRH GALAXAM)

## 1\. Contexte & Cadre Réglementaire (Madagascar)

La gestion des congés et des absences au sein du SIRH GALAXAM est régie par la législation du travail en vigueur à Madagascar, principalement :

- **La Loi n° 2003-044 du 28 juillet 2004 portant Code du Travail** (Art. 13, 85, 88).  
- **Le Décret n° 2007-563** relatif à la durée du travail (40 heures par semaine / 173,33 heures par mois).

---

## 2\. Décompte & Acquisition des Congés Payés Annuels

### A. Acquisition du Droit

- **Cumul mensuel** : Tout salarié acquiert 2,5 jours de congé payé par mois de travail effectif.  
- **Solde annuel** : 30 jours par an (correspondant au régime des jours calendaires/ouvrables).  
- **Ancienneté requise** : Le droit de jouissance s'ouvre après 12 mois de service continu (Code du Travail, Art. 85).

### B. Mode de Décompte des Jours Posés : Calendaire Strict

L'entreprise applique un décompte en **Jours Calendaires** :

- **Formule de calcul** : `nb_jours_decomptes = (date_fin - date_debut) + 1`  
- **Règle des week-ends et fériés** : Tous les jours compris dans la période (Lundi, Mardi, Mercredi, Jeudi, Vendredi, Samedi, Dimanche ET Jours Fériés) sont intégralement décomptés du solde.  
- **Exemple 1 (1 semaine complète)** : Congé posé du Lundi au Dimanche (reprise le Lundi d'après) \-\> **7 jours décomptés**.  
- **Exemple 2 (Vendredi posé)** : Si l'employé pose son Vendredi et revient le Lundi \-\> **3 jours décomptés** (Vendredi, Samedi, Dimanche).

### C. Décompte des Demi-Journées de Congé (0,5 j)

Le système autorise la pose de **demi-journées de congé payé** pour répondre aux besoins ponctuels des salariés (ex: démarche le matin, absence l'après-midi) :

- **Règle de saisie** : `est_demi_journee = TRUE`, avec obligatoirement `date_debut = date_fin` et indication du moment (`moment_demi_journee = 'MATIN'` ou `'APRES_MIDI'`).
- **Décompte du solde** : Le nombre de jours décomptés est fixé à **0,5 jour** (`nb_jours_decomptes = 0.5`).
- **Paiement en Paie** : La demi-journée de congé payé étant couverte par le solde acquis, elle est **rémunérée à 100%** (aucun retrait sur la fiche de paie). Seul le compteur de solde est débité de 0,5 jour.

---

## 3\. Valorisation Financière (Congés vs Absences)

Pour éviter les litiges et garantir l'équité de la paie, la valorisation du taux journalier/horaire s'appuie sur deux diviseurs distincts :

| Domaine | CONGÉS PAYÉS (Forfait Calendaire Mensuel) | ABSENCES (Temps de Travail Effectif) |
| :---- | :---- | :---- |
| **Diviseur** | Diviseur \= **30 jours** | Diviseur \= **173,33 heures** (ou 21,67 jours \= 173,33 / 8h) |
| **Valeur 1j / Taux** | Valeur 1j \= (Salaire \+ HS) / 30 | Taux Horaire \= (Salaire \+ HS) / 173,33 |

- **Valorisation d'un Jour de Congé Payé** : `Valeur Journalière Congé = (Salaire de Base + Heures Supplémentaires Habituelles) / 30`  
- **Valorisation d'une Retenue pour Absence** : `Taux Horaire Absence = Salaire de Base / 173,33` (Basé sur la pointeuse Clocker / relevé de présence).

---

## 4\. Règles Cumul, Prescription et Dérogations

### A. Cumul et Prescription Triennale (Prescription 3 ans)

- **Prescription légale** : Les droits à congés se prescrivent par **3 ans** (Code du Travail, Art. 85).  
- **Plafond maximal du solde** : 30 j/an \* 3 ans \= **90 jours**.  
- **Mécanisme technique de purge** :  
  - Appliquer la règle **FIFO (First In, First Out)** lors des consommations : les jours les plus anciens sont déduits en premier.  
  - Un Job Batch automatique mensuel/annuel purge les jours non consommés qui dépassent 36 mois d'ancienneté (Génération d'un mouvement de type `EXPIRATION`).

### B. Solde Négatif & Anticipation

- **Autorisation** : Un congé peut dépasser le solde acquis (prise par anticipation) sous réserve d'une **Dérogation RH explicite** (`derogation_solde_negatif = TRUE`).  
- **Plafond d'anticipation** : Limité techniquement à **\-10 jours max** (soit environ 4 mois d'acquisition future).  
- **Rupture de contrat (STC)** : Si le salarié quitte l'entreprise avec un solde négatif, la valeur financière des jours pris en trop est directement déduite de son Solde de Tout Compte (STC).

---

## 5\. Typologies de Congés & Traitements Spécifiques

- **ANNUEL** : Payé | Décompté du solde | Ancienneté min 12 mois | Préavis requis.  
- **MALADIE** : Payé | Non décompté du solde | Max 30j par épisode | Bascule en Sans Solde au-delà.  
- **EXCEPTIONNEL** : Payé | Non décompté du solde | Imputé sur pool 10j/an | Subdivisé par motif.  
- **MATERNITÉ** : Payé | Non décompté du solde | 98j fixes (14 semaines) | Calendaire strict.  
- **SANS SOLDE / AUTRE** : Non Payé | Non décompté du solde | Sabbatique, Formation, etc.

### A. Congé Maladie (Maintien & Bascule)

- **Plafond par épisode** : Maintien de salaire par l'employeur limité à **30 jours par épisode continu** (correspondant au préavis standard, Art. 13).  
- **Bascule automatique** : Au-delà de 30 jours d'arrêt continu, la demande bascule automatiquement vers le type `MALADIE_SANS_SOLDE` (prise en charge CNaPS / Organisme Sanitaire).  
- **Durée maximale de suspension du contrat** : **6 mois (180 jours)**.

### B. Congés Exceptionnels & Paternité (Pool Annuel Partagé de 10 jours)

- **Cadre légal (Art. 88\)** : Les permissions exceptionnelles pour événements familiaux sont plafonnées à un **pool annuel global de 10 jours par an**.  
- **Intégration du congé Paternité** : Le congé paternité (3 jours fixes) fait légalement partie de ce pool de 10 jours.  
- **Sous-référentiel des motifs (`ref_motif_evenement_familial`)** :  
  - `MARIAGE` : 3 jours  
  - `NAISSANCE` : 2 jours (ou 3 jours si paternité)  
  - `DECES_CONJOINT_ENFANT` : 3 jours  
  - `DECES_PARENT` : 2 jours

### C. Congé Maternité

- **Durée légale fixe** : **98 jours consécutifs (14 semaines)**.  
- **Règle** : Décompte calendaire strict (Samedi, Dimanche et Jours Fériés inclus, non reportables).

---

## 6\. Circuit de Validation & Préavis

- **Délais de préavis (Paramétrables)** :  
  - Demande courte (\<= 3 jours) : Préavis minimal de **2 jours**.  
  - Demande longue (\> 3 jours) : Préavis minimal de **15 jours**.  
  - **Exceptions** : Non applicable aux événements imprévisibles (`MALADIE`, `MATERNITE`, `PATERNITE`, `EXCEPTIONNEL`).  
- **Contrôle Préventif du Solde Disponible (Saisie de la demande)** :
  - L'endpoint `/api/conges/demandes/verifier-conflits` retourne en temps réel le `soldeDisponible`.
  - Lors de la création (`POST /api/conges/demandes`), si le type de congé impute le solde (`isDecompteSoldeAcquis`) et que `nb_jours_decomptes > soldeDisponible`, la soumission est bloquée avec une alerte explicite invitant le demandeur à réduire la durée ou solliciter une `derogation_solde_negatif`.
- **Validation avec Dérogation Solde Négatif Accordée par le Valideur** :
  - Si un congé dépasse le solde disponible au moment de la validation, le système bloque la transaction.
  - Le valideur RH/Manager dispose d'un toggle d'accord exceptionnel (`derogationSoldeNegatif = true`) directement dans la modale de confirmation pour lever le blocage.
- **Annulation Post-Validation & Restitution FIFO** :
  - L'annulation d'un congé (`POST /api/conges/demandes/{id}/annuler`) est possible que la demande soit `EN_ATTENTE` ou déjà `VALIDE`.
  - Un **motif explicite est obligatoirement exigé** (`{ "motif": "..." }`) et sauvegardé dans `motif_refus`.
  - Si la demande était `VALIDE`, un mouvement créditeur compensatoire de type `ANNULATION` est automatiquement enregistré dans `mouvement_solde_conge` (`nb_jours` positif égal aux jours décomptés), restituant instantanément les droits au salarié.
  - Une notification RH automatique est générée pour prévenir les parties prenantes.

---

## 7\. Référentiel Technique & Sources

Les règles de ce rapport ont été directement implémentées dans le script PostgreSQL du SIRH GALAXAM (`conges_schema.sql` : tables `ref_type_conge`, `demande_conge`, `mouvement_solde_conge`, `parametre_global`).

### Sources Légales d'Internet (Textes officiels)

- **Loi n° 2003-044 du 28 juillet 2004 portant Code du Travail de Madagascar** (Articles 13, 85, 88\) : [Loi n° 2003-044 \- Code du Travail de Madagascar (PDF FAOLEX)](https://faolex.fao.org/docs/pdf/mad208109.pdf)  
- **Décret n° 2007-563 et régimes de la durée légale du travail** : [Base de données légale OIT / NATLEX \- Madagascar](https://eplex.ilo.org/country-detail/?code=MDG&yr=2019)

### Fichiers de Relevé de Temps & Calculs de Paie du Projet

- `Calcul salaire suivant Loi 2003-044 decret 2007-563.odt` (Calculs des HS et taux horaire)  
- `ohatra presence svt clocker.ods` & `ohatra gita fichiers-23jan26.ods` (Logique de décompte des absences/retards)  
- `IRSA DRAFT calcul new tranche 2026.ods` & `modele declaration iRSA SAFI.ods` (Calculs d'imposition et retenues brutes)  
- `conges_schema.sql` (Script de structure de la base de données PostgreSQL du SIRH GALAXAM)

