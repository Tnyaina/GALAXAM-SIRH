# PROPOSITION DE REDESIGN UX & WORKFLOW DU MODULE CONGÉS
**Date :** 17 Septembre 2026  
**Référence :** Phase 2A-2 Audit Contradictoire Complet  
**Système :** GALAXAM SIRH  

---

## 1. DIAGNOSTIC DE L'EXPÉRIENCE UTILISATEUR ACTUELLE

### 1.1. Incohérences et Lacunes Constatées

1. **L'illusion de l'onglet "Soldes" :**
   - L'onglet actuel `/conges?tab=soldes` s'intitule "Soldes & Planning prévisionnel".
   - En réalité, son tableau (`SoldesTable.jsx`) n'affiche **aucun solde de collaborateur** ! Il affiche uniquement la liste des absences du mois en cours.
   - Pour consulter le solde réel d'un employé, le gestionnaire RH est contraint de quitter le module Congés et de naviguer vers `/employes/{id}?tab=conges`.
2. **Occultation Totale de la Dimension Financière :**
   - Le gestionnaire RH et le DAF n'ont aucune visibilité sur :
     - Le taux journalier valorisé de congé (`TCONGE`).
     - Le coût prévisionnel des congés approuvés pour la prochaine paie.
     - La dette sociale latente de l'entreprise (somme des soldes de congés valorisés en Ariary).
3. **Absence d'Explicabilité (Black-Box KAJY) :**
   - Lorsque la paie produit une ligne `INDEMNITE_CONGE` de 97 400 Ar pour 4 jours (24 350 Ar/jour), aucun écran ne permet d'expliquer d'où vient ce montant de 24 350 Ar.
4. **Disparité de Navigation entre Modules :**
   - Le module Congés est scindé entre la supervision globale (`/conges`) et la fiche individuelle (`/employes/:id?tab=conges`), sans synthèse financière consolidée.

---

## 2. STRUCTURE CIBLE DES ÉCRANS & NAVIGATION

### 2.1. Entrée Sidebar Unique
Dans la barre de navigation principale :
```text
Sidebar
└── Congés & Absences (/conges)
```
*Pas de prolifération d'entrées séparées (pas de "Validation Congés", "Soldes Congés", "TCONGE" dans la sidebar).*

---

### 2.2. Hub Central `/conges` : 3 Onglets Clairs

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                          GESTION DES CONGÉS & ABSENCES                      │
│   Supervision des plannings, validation des demandes et valorisation RH     │
├─────────────────────────────────────────────────────────────────────────────┤
│  [1. Planning & Absences]   [2. Demandes & Validations]   [3. Soldes & TCONGE]│
└─────────────────────────────────────────────────────────────────────────────┘
```

#### Onglet 1 : Planning & Absences (`/conges?tab=planning`)
- **Objectif :** Gestion opérationnelle de la continuité de service.
- **Composants :**
  - KPI Bar : Présents aujourd'hui, Absents aujourd'hui, Départs prévus cette semaine.
  - Sélecteur temporel : Vue Mois / Semaine civile.
  - Gantt interactif avec code couleur par type d'absence (Annuel, Maladie, Maternité, Permission).
  - Détection automatique des collisions de poste sur le même site.
  - Bouton d'action rapide : `+ Poser un Congé`.

#### Onglet 2 : Demandes & Validations (`/conges?tab=demandes`)
- **Objectif :** Circuit d'approbation RH / DAF fluide et sécurisé.
- **Composants :**
  - Filtres par statut : `En attente (badge)`, `Validées`, `Refusées`, `Annulées`.
  - Tableau de validation enrichi :
    - Collaborateur (Photo, Matricule, Poste, Site).
    - Dates et Durée (jours ouvrés + jours calendaires).
    - Solde actuel avant demande $\to$ Solde projeté après validation.
    - **Indicateur d'impact financier prévisionnel :**
      $$\text{Estimation indemnité} = \text{Jours} \times \text{TCONGE estimé}$$
    - Alertes automatiques : Dépassement de solde (anticipation), Absence de justificatif, Conflit de poste.
  - Modale de décision : Valider avec options dérogatoires, Refuser avec motif obligatoire.

#### Onglet 3 : Soldes & Grand-Livre TCONGE (`/conges?tab=soldes`) [NOUVEAUTÉ MAJEURE]
- **Objectif :** Pilotage consolidé des droits, du passif social et transparence financière.
- **KPIs Consolidés :**
  - Total des jours de congés acquis par l'ensemble des salariés.
  - Total des jours pris sur l'année.
  - Solde net restant à date.
  - **Provision financière globale des congés payés (en Ariary) :**
    $$\sum (\text{Solde Employé} \times \text{TCONGE Employé})$$
- **Tableau Exhaustif des Soldes Collaborateurs :**
  - Matricule, Nom & Prénom, Département, Site.
  - Ancienneté & Éligibilité légale (Validée par le backend).
  - Jours Acquis, Jours Bonifiés, Jours Pris, Jours Expirés.
  - **Solde Restant (en Jours).**
  - **TCONGE Applicable Actuel (Ar/jour).**
  - **Valeur Financière du Solde (Ar).**
  - **Action :** Bouton `Détail du Taux` ouvrant le tiroir d'explicabilité.

---

## 3. COMPOSANT D'EXPLICABILITÉ : LE TIROIR DE VALORISATION TCONGE

Lors d'un clic sur `Détail du Taux` (ou depuis la fiche employé), un tiroir latéral (Drawer) s'ouvre :

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DÉTAIL DE VALORISATION TCONGE — ART. 89                   │
│                    Collaborateur : RABE Jean (MAT-0042)                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  Taux Journalier Applicable : 24 350 Ar / jour                              │
│  Statut : FIGÉ AU GEL DU POINTAGE (Période Septembre 2026)                  │
│  Méthode : Moyenne pondérée des 12 derniers mois de référence               │
├─────────────────────────────────────────────────────────────────────────────┤
│  HISTORIQUE DES 12 MOIS DE RÉFÉRENCE :                                      │
│                                                                             │
│  Mois        Base Salaire   Heures Sup   Base Totale   Droit Acquis  Valo   │
│  ─────────────────────────────────────────────────────────────────────────  │
│  Août 2026   650 000 Ar      80 000 Ar   730 000 Ar    2.50 j        60 833 │
│  Juil 2026   650 000 Ar           0 Ar   650 000 Ar    2.50 j        54 167 │
│  Juin 2026   650 000 Ar      45 000 Ar   695 000 Ar    2.50 j        57 917 │
│  ... (12 mois)                                                              │
│  ─────────────────────────────────────────────────────────────────────────  │
│  TOTAL       7 800 000 Ar   245 000 Ar   8 045 000 Ar  30.00 j      670 417 │
│                                                                             │
│  FORMULE DU TAUX :                                                          │
│  TCONGE = Total Valorisation / Total Droits                                 │
│         = 670 417 Ar / 30.00 j = 22 347,23 Ar                               │
│  Comparaison règle du trentième de maintien :                               │
│  Base / 30 = 650 000 / 30 = 21 666,67 Ar                                    │
│  -> Règle la plus favorable appliquée : 22 347,23 Ar / jour                 │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. REFONTE DE L'ONGLET CONGÉS DE LA FICHE EMPLOYÉ

Dans `/employes/:id?tab=conges` :
1. **Remplacement de la logique frontend par des données certifiées :**
   - Suppression du calcul d'ancienneté en JavaScript dans `SoldeCongeWidget.jsx`.
   - Utilisation des champs d'éligibilité fournis par le backend.
2. **Enrichissement du Widget de Solde :**
   - Ajout d'une carte KPI financière :
     - **Taux journalier congé (TCONGE)** : `XX XXX Ar`
     - **Valeur du solde disponible** : `XXX XXX Ar`
3. **Conservation des 2 sous-onglets :**
   - `Demandes de Congé` (Historique, avec statut et annulation sécurisée).
   - `Registre des Mouvements` (Grand-livre auditable des débits/crédits).

---

## 5. CYCLE D'ACTIONS & DROITS D'ACCÈS (MATRICE RBAC)

| Action Utilisateur | Profil RH | Profil DAF | Employé / Manager |
| :--- | :--- | :--- | :--- |
| **Créer une demande** | Oui | Oui | Oui (pour soi ou son équipe) |
| **Simuler décompte** | Oui | Oui | Oui |
| **Valider demande** | Oui (`CONGE_VALIDATE`) | Oui | Non |
| **Dérogation solde négatif** | Non (Avertissement) | Oui (`DAF_OVERRIDE`) | Non |
| **Annuler congé futur** | Oui | Oui | Oui (tant qu'en attente) |
| **Annuler congé passé non gelé** | Oui (avec motif) | Oui | Non |
| **Annuler congé déjà payé en paie** | **INTERDIT STRICT** | **INTERDIT STRICT** | Non |
| **Consulter le TCONGE** | Oui | Oui | Paramétrable (masqué par défaut) |
| **Ajustement manuel du solde** | Non | Oui (`CONGE_ADJUST`) | Non |
