# 📜 Spécification d'Architecture : Réglementation Fiscale & Sociale Évolutive (SP4 - Paie)

**Projet :** GALAXAM-SIRH  
**Auteur :** Architecte Logiciel Senior SIRH  
**Date d'entrée en vigueur :** 2026  
**Législation de référence :** Code du Travail Malgache (Loi n°2003-044), Code Général des Impôts (Loi de Finances), Décrets CNaPS & Médecine du Travail  

---

## 🎯 1. Contexte Métier & Motivation d'Architecture

Dans un SIRH d'entreprise opérant sous la réglementation malgache, les constantes de paie ne sont jamais statiques :
1. **Loi de Finances annuelle (au 1er Janvier)** :
   * Modification du barème progressif de l'IRSA (ex: passage à 6 tranches avec tranche marginale à 25% en 2026).
   * Révision du minimum légal de perception (rehaussé à 3 000 Ar).
   * Ajustement de la déduction par personne à charge (2 000 Ar / mois).
2. **Décrets présidentiels et ministériels** :
   * Revalorisation du **Salaire Minimum d'Embauche (SME national et agricole)**.
   * Répercussion automatique sur le **Plafond Mensuel de Cotisation Sociale** fixé par la loi à **8 × SME** ($262\,680 \times 8 = 2\,101\,440\text{ Ar}$).
3. **Temps de travail et congés payés** :
   * Décompte hebdomadaire légal de 40 heures et son diviseur mensuel exact de valorisation :
     $$\text{Diviseur Mensuel} = \frac{40\text{ h} \times 52\text{ semaines}}{12\text{ mois}} = 173.3333\text{ heures}$$
   * Valorisation légale d'un jour de congé payé sur base calendaire :
     $$\text{Indemnité de Congé (1 jour)} = \frac{\text{Salaire Brut}}{30}$$

### Problématique de l'ancienne approche (`parametre_global`)
Stocker ces valeurs dans une table générique clé-valeur (`parametre_global`) posait trois faiblesses critiques :
* **Absence de cohérence millésimée :** Une modification écrasait la valeur passée, risquant de fausser les calculs rétroactifs des bulletins des exercices antérieurs.
* **Typage faible :** Les valeurs stockées en chaîne de caractères (`VARCHAR`) exigeaient des parsings non sécurisés côté backend.
* **Absence de contrainte relationnelle :** Impossible de lier formellement un barème IRSA ou un taux de cotisation à son millésime de loi de finances.

---

## 🗄️ 2. Modélisation de Données : `paie_config_legale`

La table `paie_config_legale` constitue désormais le **pivot temporel de toute la paie**.

```sql
CREATE TABLE paie_config_legale (
    id_config_legale    SERIAL PRIMARY KEY,

    -- Identifiant et libellé officiel de l'exercice
    code_version        VARCHAR(30)  NOT NULL UNIQUE, -- ex: 'LF_2026', 'LF_2027'
    libelle             VARCHAR(200) NOT NULL,

    -- Validité temporelle stricte
    date_debut          DATE         NOT NULL,
    date_fin            DATE,        -- NULL = millésime actuellement EN VIGUEUR

    -- 1. Salaire Minimum & Plafonds Sociaux
    sme_national                NUMERIC(12,2) NOT NULL, -- 262 680 Ar (secteur non agricole)
    sme_agricole                NUMERIC(12,2) NOT NULL, -- 266 500 Ar (secteur agricole)
    plafond_cotisation_nb_sme   NUMERIC(4,2)  NOT NULL DEFAULT 8.00, -- Multiplicateur (8x)

    -- 2. Fiscalité IRSA (Loi de Finances)
    seuil_exoneration_irsa      NUMERIC(12,2) NOT NULL, -- 350 000 Ar
    minimum_perception_irsa     NUMERIC(12,2) NOT NULL, -- 3 000 Ar
    deduction_charge_famille    NUMERIC(12,2) NOT NULL, -- 2 000 Ar / personne
    arrondi_base_irsa           VARCHAR(20)   NOT NULL DEFAULT 'CENTAINE_INF',

    -- 3. Temps de Travail (Code du Travail Art. 83)
    duree_hebdo_legale          NUMERIC(5,2)  NOT NULL DEFAULT 40.00,
    diviseur_mensuel_heures     NUMERIC(7,4)  NOT NULL DEFAULT 173.3333,
    seuil_hs_tranche1           NUMERIC(5,2)  NOT NULL DEFAULT 8.00,  -- 8 premières HS à +30%
    plafond_hs_hebdo            NUMERIC(5,2)  NOT NULL DEFAULT 20.00, -- 20h max/semaine

    -- 4. Congés Payés (Code du Travail Art. 85)
    acquisition_conge_mensuel_jours   NUMERIC(4,2) NOT NULL DEFAULT 2.50, -- 2.5 j/mois = 30 j/an
    diviseur_valorisation_conge_jours INT          NOT NULL DEFAULT 30,   -- Brut / 30

    created_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dates_config CHECK (date_fin IS NULL OR date_fin > date_debut)
);

CREATE INDEX idx_config_legale_actif ON paie_config_legale(date_debut, date_fin)
    WHERE date_fin IS NULL;
```

### Liaisons avec les Tables Filles
Chaque barème IRSA, chaque cotisation et chaque majoration est rattaché à son millésime :
* `bareme_irsa.id_config_legale` $\rightarrow$ `paie_config_legale(id_config_legale)`
* `ref_type_cotisation.id_config_legale` $\rightarrow$ `paie_config_legale(id_config_legale)`
* `ref_taux_majoration.id_config_legale` $\rightarrow$ `paie_config_legale(id_config_legale)`

---

## ⚙️ 3. Mécanisme de Revalorisation à Date d'Effet

Pour garantir que les bulletins passés restent **strictement reproductibles à l'identique**, le moteur applique le principe d'immutabilité temporelle :

```
             Exercice 2026                                   Exercice 2027
   [01/01/2026 ──────> 31/12/2026]              [01/01/2027 ──────> NULL (En vigueur)]
               │                                            │
        date_fin clôturée                         date_debut = 01/01/2027
     (Veille de la date d'effet)                 date_fin = NULL
```

### Algorithme de Revalorisation (`PaieConfigLegale.revaloriser(...)`) :
1. Clôture de l'exercice actif :
   ```sql
   UPDATE paie_config_legale 
   SET date_fin = :dateDebutNouveau - 1 DAY 
   WHERE date_fin IS NULL AND date_debut < :dateDebutNouveau;
   ```
2. Insertion du nouvel exercice avec `date_debut = :dateDebutNouveau` et `date_fin = NULL`.
3. Audit immédiat de l'opération dans `audit_log`.

---

## 🖥️ 4. Interface Utilisateur (UI/UX)

L'administration s'effectue dans **Paramètres > Module Paie > Exercice Fiscal & Réglementation** ([ReglementationFiscaleTab.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/parametres/paie/ReglementationFiscaleTab.jsx)) :

1. **Bannière Légale Dynamique :**
   * Récapitule les sources juridiques avec les chiffres exacts de l'exercice en vigueur (`activeConfig`).
2. **Tableau de Bord de l'Exercice en Vigueur :**
   * Cartes KPI : SME National, SME Agricole, Multiplicateur, Plafond mensuel calculé ($2\,101\,440\text{ Ar}$), Seuils IRSA, Temps de travail et Congés.
3. **Barre d'Action Épurée :**
   * **Bouton "NOUVELLE LOI DE FINANCES"** : Déclenche le workflow de revalorisation à date d'effet.
   * **Icône Crayon (Correction)** : Permet de rectifier une coquille sans clore de période.
4. **Modale Bicolonne Responsive (`size="4xl"`) :**
   * Organisation thématique en 5 blocs : Identification, Social/SME, Fiscalité IRSA, Temps de travail, Congés.
   * Calcul en temps réel du plafond mensuel lors de la saisie ($SME \times 8$).

---

## 📌 5. Conséquences pour le Moteur de Paie

À chaque calcul de bulletin de paie pour une période $[D_{\text{debut}}, D_{\text{fin}}]$ :
1. Le moteur charge le millésime applicable :
   ```sql
   SELECT * FROM paie_config_legale 
   WHERE date_debut <= :datePeriode AND (date_fin IS NULL OR date_fin >= :datePeriode)
   ORDER BY date_debut DESC LIMIT 1;
   ```
2. **Dénormalisation (Snapshot)** : Le bulletin généré enregistre en dur l'ID de la config légale, le SME appliqué, le plafond utilisé et les tranches calculées.
3. Aucune modification future de la loi de finances ne pourra altérer les montants d'une paie déjà clôturée.
