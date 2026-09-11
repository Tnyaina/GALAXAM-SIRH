# Module Pointage & Présence — Documentation Technique & Fonctionnelle

## GALAXAM SIRH — Mémoire de Fin d'Études

> **Module** : Saisie quotidienne des présences, heures supplémentaires, indemnités liées à la présence (Déplacement, Repas), synthèse mensuelle vers la Paie
> **Version** : 1.1.0 — Décisions de conception validées (voir §11 pour l'historique des points tranchés)
> **Date** : 2026
>
> **Périmètre implémenté** : Saisie AM/PM par employé/jour, calcul du temps effectif, gestion retard/départ anticipé, compensation hebdomadaire par tronçon proratisé (seuil = `8h × jours ouvrés du tronçon`), heures supplémentaires, dispense de pointage (postes autonomes), indemnités Déplacement/Repas avec cascade de dérogation, jours fériés, alignement avec le module Congés, workflow de validation par période de paie avec **gel physique des données à la clôture** (immuabilité légale), alertes d'oubli de saisie.
> **Hors périmètre (reporté au module Paie)** : Application des taux de majoration (HS_30, HS_50, NUIT, DIMANCHE, FERIE) en valeur monétaire, calcul IRSA/CNAPS/OSTIE, bulletin de paie. Le module Pointage **produit les données sources** (heures, statuts, indemnités) ; il ne calcule pas le brut/net.

---

## Table des Matières

1. [Principes Généraux](#1-principes-généraux)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion](#3-règles-de-gestion)
4. [Statuts de la Journée de Pointage](#4-statuts-de-la-journée-de-pointage)
5. [Workflow &amp; Circuit de Validation](#5-workflow--circuit-de-validation)
6. [Automatisations &amp; Schedulers](#6-automatisations--schedulers)
7. [Notifications](#7-notifications)
8. [Sortie vers la Paie](#8-sortie-vers-la-paie)
9. [Architecture Technique](#9-architecture-technique)
10. [API REST — Endpoints](#10-api-rest--endpoints)
11. [Points Tranchés &amp; Décisions de Conception](#11-points-tranchés--décisions-de-conception)

---

## 1. Principes Généraux

- **Pas d'import automatique** de l'extrait clocker. L'agent de pointage (ou le DAF) **saisit** manuellement, par employé et par jour, dans une interface dédiée. On élimine ainsi le fichier Excel dispersé, source d'erreurs.
- **Saisie non bloquante** : un oubli de pointage n'empêche pas le fonctionnement du système. Une notification de rappel est générée, et une correction reste possible (par l'agent de pointage tant que la période n'est pas validée, ou par le DAF/Admin à tout moment avant clôture).
- **Un jour = 4 pointages maximum** : Entrée Matin, Sortie Matin, Entrée Après-midi, Sortie Après-midi.
- **Compensation hebdomadaire** : la base légale est 40h/semaine (`paie_config_legale.duree_hebdomadaire_legale_heures`). Un retard ou une sortie anticipée un jour donné peut être compensé par une heure supplémentaire faite un autre jour **de la même semaine**. Ce n'est qu'au-delà des 40h hebdomadaires que le surplus devient HS payée.
- **Cadres (catégorie `C`)** : jamais de HS payée (forcé à 0 dans la synthèse), mais le surplus d'heures reste utilisé en interne pour compenser d'éventuels retards de la semaine.
- **Postes autonomes** (`ref_fonction.dispense_pointage = TRUE`) : forfait de 8h/jour sans obligation de pointer. Le DAF/Admin peut saisir une présence a posteriori (avec rappel de notification). Ces employés **peuvent quand même pointer** pour bénéficier d'une indemnité (ex : 2ᵉ repas), sans que ce soit obligatoire.
- **Toutes les données de calcul sont historisées et paramétrables** (grilles d'arrondi, montants d'indemnités, seuils horaires) — jamais codées en dur, pour rester cohérent avec `parametre_global` et le principe d'historisation déjà en place dans Structure/Congés.

---

## 2. Modèle de Données

### 2.1 Vue d'Ensemble

```
REF_SITE (existant)
    │  heure_debut_matin / heure_fin_matin / heure_debut_apres_midi / heure_fin_apres_midi
    │  horaire_continu
    │
    ├── (N) REF_GRILLE_ARRONDI_POINTAGE   -- tranches d'arrondi de l'heure d'entrée, par site
    │
EMPLOYE (existant)
    │
    ├── (N) EMPLOYE_INDEMNITE_DEROGATION   -- Niveau 2 de la cascade (module-structure.md §4.1)
    │           id_employe / id_indemnite / beneficie (BOOL) / motif / date_debut / date_fin
    │
    ├── (N) POINTAGE_JOUR                  -- Niveau 3 : la saisie quotidienne
    │           id_employe → employe
    │           id_periode_paie → periode_paie
    │           id_site (site du jour, pour gérer les missions inter-site)
    │           date_pointage
    │           heure_entree_matin / heure_sortie_matin / heure_entree_am / heure_sortie_am
    │           id_statut_jour → ref_statut_pointage_jour
    │           heures_matin / heures_am / total_heures (décimal, calculées à la saisie)
    │           minutes_retard / minutes_depart_anticipe
    │           indemnite_deplacement_recue (BOOL) / indemnite_repas_recue (BOOL) / montant_repas
    │           utilise_vehicule_fonction_jour (BOOL)
    │           motif_ajustement / saisi_par / created_at / updated_at
    │
    └── (existant) DEMANDE_CONGE           -- si VALIDE sur une date, verrouille le statut CONGE

PERIODE_PAIE (nouveau, réutilisable par le futur module Paie)
    │
    └── (N) POINTAGE_JOUR (verrouillage par id_statut_periode)

VUES (agrégats - OBSOLÈTES / REMPLACÉES PAR JAVA)
    ├── v_pointage_jour_cpl            -- (Obsolète) Remplacé par PointageSaisieService
    ├── v_pointage_semaine             -- (Obsolète) Remplacé par PointageCalculateurEngine.calculerTroncon
    └── v_synthese_pointage_mensuelle  -- (Obsolète) Remplacé par PointageSyntheseService + PointageCalculateurEngine
```


### 2.2 Table `periode_paie`

Entité générique (réutilisée par Paie plus tard), représentant un cycle de paie (ex : 26/11/2025 → 25/12/2025).

```sql
Table periode_paie {
  id_periode_paie     INTEGER PK
  code                VARCHAR(20) UNIQUE   -- ex '2026-01' (mois de rattachement paie)
  libelle             VARCHAR(100)         -- ex 'Décembre 2025 → Janvier 2026'
  date_debut          DATE NOT NULL
  date_fin            DATE NOT NULL
  id_statut_periode   INTEGER FK → ref_statut_periode_paie
  date_validation     TIMESTAMP
  valide_par          INTEGER FK → utilisateur
  created_at          TIMESTAMP
  updated_at          TIMESTAMP
}
```

**Cycle de vie** (aligné sur le style `workflow_rh` / `demande_conge`) :

```
BROUILLON → EN_COURS → A_VALIDER → VALIDEE → CLOTUREE
```

- `BROUILLON` / `EN_COURS` : saisie libre par l'agent de pointage.
- `A_VALIDER` : période terminée (date_fin dépassée), en attente de contrôle DAF.
- `VALIDEE` : verrouillée pour saisie, prête à être consommée par la Paie.
- `CLOTUREE` : la paie a été calculée sur cette période — **aucune modification possible**, y compris par un Admin (traçabilité stricte).

### 2.3 Table `ref_tolerance_arrivee_pointage`

Règle simple, une ligne par site : plafonne le bénéfice d'une arrivée très en avance, sans arrondi en escalier. Toute arrivée **avant** `heure_plafond` est comptée comme `heure_plafond` ; toute arrivée après est comptée telle quelle (retard réel, pas d'arrondi).

```sql
Table ref_tolerance_arrivee_pointage {
  id_tolerance    INTEGER PK
  id_site         INTEGER FK → ref_site UNIQUE
  heure_plafond   TIME NOT NULL   -- ex 07:30 pour un site à 08:00 théorique
}
```

Exemple : TNR théorique 08:00, `heure_plafond = 07:30` → un employé arrivant à 07:10 est compté à partir de 07:30 (pas de bonus) ; un employé arrivant à 07:45 est compté à partir de 07:45 (retard réel de 15 min, à compenser dans la semaine).

### 2.4 Table `ref_statut_pointage_jour`

```sql
Table ref_statut_pointage_jour {
  id_statut_jour   INTEGER PK
  code             VARCHAR(30) UNIQUE
  libelle          VARCHAR(100)
  decompte_absence BOOLEAN   -- ce statut génère-t-il une absence valorisée ?
  necessite_saisie BOOLEAN   -- attend-on des heures d'entrée/sortie pour ce statut ?
}
```

Codes : `PRESENT`, `ABSENCE_NON_JUSTIFIEE`, `CONGE` (miroir du module Congés), `FERIE_TRAVAILLE`, `FERIE_CHOME`, `DISPENSE_POINTAGE` (poste autonome), `MISSION_EXTERIEURE`.

### 2.5 Table `employe_indemnite_derogation`

Table déjà annoncée dans `module-structure.md` (Niveau 2 de la cascade), formalisée ici :

```sql
Table employe_indemnite_derogation {
  id_derogation   INTEGER PK
  id_employe      INTEGER FK → employe
  id_indemnite    INTEGER FK → ref_indemnite
  beneficie       BOOLEAN NOT NULL   -- TRUE = accorde même si le site dit non / FALSE = retire même si le site dit oui
  motif           VARCHAR(200)       -- ex 'Véhicule de fonction', 'Double emploi'
  date_debut      DATE NOT NULL
  date_fin        DATE               -- NULL = dérogation active
  created_by      INTEGER FK → utilisateur
  created_at      TIMESTAMP
}
```

Usage : les 4 personnes disposant d'un véhicule de fonction ont une ligne `id_indemnite = DEPLACEMENT`, `beneficie = FALSE`, permanente (pas de `date_fin`). Si la liste s'allonge, c'est une décision de gestion de flotte — pas une règle système, donc simple ajout de ligne.

### 2.6 Table `pointage_jour`

```sql
Table pointage_jour {
  id_pointage                     INTEGER PK
  id_employe                      INTEGER FK → employe
  id_periode_paie                 INTEGER FK → periode_paie
  id_site                         INTEGER FK → ref_site      -- site du jour (mission possible)
  date_pointage                   DATE NOT NULL

  heure_entree_matin              TIME
  heure_sortie_matin              TIME
  heure_entree_am                 TIME
  heure_sortie_am                 TIME

  id_statut_jour                  INTEGER FK → ref_statut_pointage_jour

  -- Calculés à la saisie (logique Java, cf §9), jamais recalculés silencieusement après validation
  heures_matin                    DECIMAL(4,2)
  heures_am                       DECIMAL(4,2)
  total_heures                    DECIMAL(4,2)
  minutes_retard                  INTEGER DEFAULT 0
  minutes_depart_anticipe         INTEGER DEFAULT 0

  -- Indemnités (Niveau 3 de la cascade — ajustement jour par jour)
  indemnite_deplacement_recue     BOOLEAN
  indemnite_repas_recue           BOOLEAN
  montant_repas                   DECIMAL(10,2)   -- 0 / 2000 / 4000 selon règle 2e repas
  utilise_vehicule_fonction_jour  BOOLEAN DEFAULT FALSE

  motif_ajustement                TEXT
  saisi_par                       INTEGER FK → utilisateur
  id_demande_conge                INTEGER FK → demande_conge   -- NULL sauf ligne CONGE matérialisée
  genere_automatiquement          BOOLEAN DEFAULT FALSE        -- TRUE = créée par le système (CONGE/FERIE_CHOME)
  created_at                      TIMESTAMP
  updated_at                      TIMESTAMP

  CONSTRAINT uk_pointage_employe_jour UNIQUE (id_employe, date_pointage)
}
```

### 2.7 Vue `v_pointage_semaine` (temps réel, avant clôture)

**Partitionnement par `id_periode_paie`** : la période GALAXAM allant du 26 au 25, la première et dernière semaine de chaque période sont des **tronçons** (2 à 4 jours ouvrés). En ajoutant `id_periode_paie` dans le `GROUP BY`, PostgreSQL génère automatiquement deux lignes distinctes pour une semaine à cheval — pas de logique conditionnelle complexe.

**Proratisation du seuil** : comparer un tronçon de 3 jours à un seuil fixe de 40h créerait un faux déficit systématique. Le seuil est donc proratisé : `seuil = nb_jours_ouvrés_du_tronçon × 8h`.

```sql
CREATE OR REPLACE VIEW v_pointage_semaine AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    date_trunc('week', p.date_pointage)::date AS debut_semaine,
    COUNT(*)                               AS nb_jours_troncon,
    COUNT(*) * 8.0                         AS seuil_heures_troncon,   -- proratisé : 8h × jours ouvrés
    SUM(p.total_heures)                    AS total_heures_semaine,
    SUM(p.minutes_retard) / 60.0           AS heures_retard_semaine,
    GREATEST(SUM(p.total_heures) - COUNT(*) * 8.0, 0)
                                           AS heures_hs_brutes,       -- avant compensation retard
    GREATEST(COUNT(*) * 8.0 - SUM(p.total_heures), 0)
                                           AS heures_deficit_non_compense
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
WHERE s.code IN ('PRESENT', 'FERIE_TRAVAILLE')
GROUP BY p.id_employe, p.id_periode_paie, date_trunc('week', p.date_pointage);
```

> **Exemple** : Tronçon Lun 22 → Jeu 25 décembre (4 jours ouvrés) → seuil = `4 × 8h = 32h`. Un employé ayant fait 31h n'est pas en déficit de 9h (40-31) mais de seulement 1h (32-31).

### 2.8 Vue `v_synthese_pointage_mensuelle` (temps réel, avant clôture)

Vue **temps réel** pour l'affichage à l'écran pendant la saisie. Elle est remplacée par `pointage_cumul_periode` (§2.10) **au moment de la clôture** pour la paie (immuabilité légale).

```sql
CREATE OR REPLACE VIEW v_synthese_pointage_mensuelle AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    COUNT(*) FILTER (WHERE s.code = 'ABSENCE_NON_JUSTIFIEE')        AS nb_jours_absence,
    COUNT(*) FILTER (WHERE s.code = 'FERIE_TRAVAILLE')              AS nb_jours_ferie_travaille,
    COALESCE(SUM(p.total_heures) FILTER (WHERE s.decompte_absence = FALSE), 0)
                                                                    AS total_heures_travaillees,
    COALESCE(SUM(w.heures_hs_brutes), 0)                            AS heures_hs_brutes_mensuelles,
    COUNT(*) FILTER (WHERE p.indemnite_deplacement_recue)           AS nb_jours_deplacement,
    COUNT(*) FILTER (WHERE p.indemnite_repas_recue)                 AS nb_jours_repas,
    COALESCE(SUM(p.montant_repas), 0)                               AS montant_repas_total
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
LEFT JOIN v_pointage_semaine w
    ON w.id_employe = p.id_employe
   AND w.id_periode_paie = p.id_periode_paie
   AND w.debut_semaine = date_trunc('week', p.date_pointage)::date
GROUP BY p.id_employe, p.id_periode_paie;
```

### 2.9 Table `pointage_semaine_calcule` (données gelées à la clôture)

Insérée **au moment de la validation** de la période (`VALIDEE`), figée définitivement à la `CLOTUREE`. Permet la **reproductibilité stricte** des résultats en cas de contrôle de l'Inspection du Travail, CNaPS ou FISC — même si la formule de calcul évolue dans le code ultérieurement.

```sql
CREATE TABLE pointage_semaine_calcule (
    id_semaine_calcule     SERIAL PRIMARY KEY,
    id_employe             INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie        INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    debut_semaine          DATE NOT NULL,          -- lundi ISO, peut être avant la période
    nb_jours_troncon       INTEGER NOT NULL,        -- jours ouvrés réels du tronçon dans la période
    seuil_heures_troncon   DECIMAL(5,2) NOT NULL,  -- nb_jours_troncon × 8
    total_heures_travaillees DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_retard          DECIMAL(5,2) NOT NULL DEFAULT 0,
    heures_hs_brutes       DECIMAL(5,2) NOT NULL DEFAULT 0,  -- surplus au-dessus du seuil proratisé
    heures_hs_nettes       DECIMAL(5,2) NOT NULL DEFAULT 0,  -- après déduction retards (0 si Cadre)
    heures_deficit         DECIMAL(5,2) NOT NULL DEFAULT 0,  -- retard net non compensé → absence valorisée
    gele_par               INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_semaine_calcule UNIQUE (id_employe, id_periode_paie, debut_semaine)
);
```

### 2.10 Table `pointage_cumul_periode` (données gelées à la clôture)

Agrégat mensuel figé, **interface de sortie définitive vers la Paie**. Alimentée lors du passage en `VALIDEE`, modifiable par correction Admin jusqu'à `CLOTUREE`. Passée `CLOTUREE`, **aucune modification n'est possible**, garantissant l'identité des données entre la période déclarée et un éventuel contrôle a posteriori.

```sql
CREATE TABLE pointage_cumul_periode (
    id_cumul               SERIAL PRIMARY KEY,
    id_employe             INTEGER NOT NULL REFERENCES employe(id_employe),
    id_periode_paie        INTEGER NOT NULL REFERENCES periode_paie(id_periode_paie),
    nb_jours_ouvrables     INTEGER NOT NULL,          -- fn_jours_ouvrables() calculé à la clôture
    nb_jours_presents      INTEGER NOT NULL DEFAULT 0,
    nb_jours_absence       INTEGER NOT NULL DEFAULT 0,
    nb_jours_conge         INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_chome   INTEGER NOT NULL DEFAULT 0,
    nb_jours_ferie_travaille INTEGER NOT NULL DEFAULT 0,
    total_heures_travaillees DECIMAL(6,2) NOT NULL DEFAULT 0,
    heures_hs_30           DECIMAL(6,2) NOT NULL DEFAULT 0,  -- HS à 30% (> 40h/sem, <= 8h HS)
    heures_hs_50           DECIMAL(6,2) NOT NULL DEFAULT 0,  -- HS à 50% (> 8h HS dans la sem)
    heures_absence_retenues DECIMAL(6,2) NOT NULL DEFAULT 0, -- retards nets non compensés → déduction paie
    nb_jours_deplacement   INTEGER NOT NULL DEFAULT 0,
    montant_deplacement    DECIMAL(10,2) NOT NULL DEFAULT 0,
    nb_jours_repas         INTEGER NOT NULL DEFAULT 0,
    montant_repas          DECIMAL(10,2) NOT NULL DEFAULT 0,
    gele_par               INTEGER REFERENCES utilisateur(id_utilisateur),
    gele_at                TIMESTAMP NOT NULL,
    CONSTRAINT uk_cumul_employe_periode UNIQUE (id_employe, id_periode_paie)
);
```

> **Flux de vie** : `v_synthese_pointage_mensuelle` (temps réel) → validation période → INSERT dans `pointage_cumul_periode` → passage en `CLOTUREE` → données immuables consommées par Paie.

---

## 3. Règles de Gestion

### RG-PTG-01 : Saisie Quotidienne à 4 Créneaux avec Valeurs par Défaut de Pause

* [ ] Si l'employé ne pointe pas sa sortie matin / entrée après-midi (pause), le système reconstitue la pause manquante à partir de `ref_site.heure_fin_matin` et de la durée minimale de pause déduite de `ref_site.horaire_continu` — **pas de valeur hardcodée par site nommé** :

- `horaire_continu = TRUE` → pause minimale **1h** (sortie matin = `heure_fin_matin`, entrée après-midi = `heure_fin_matin + 1h`).
- `horaire_continu = FALSE` → pause minimale **2h** (sortie matin = `heure_fin_matin`, entrée après-midi = `heure_fin_matin + 2h`).

Cela couvre aussi le cas particulier RG-PTG-09 (employé TMM qui ne rentre pas le midi) : ce jour-là, on applique la pause "1h" comme s'il était sur un horaire continu, sans changer la configuration permanente du site.

### RG-PTG-02 : Plafond d'Arrivée Anticipée

Voir `ref_tolerance_arrivee_pointage` (§2.3). Toute arrivée avant `heure_plafond` est retenue comme `heure_plafond` pour le calcul du temps effectif — cela **plafonne le bénéfice d'une arrivée très en avance** sans jamais pénaliser l'employé. Au-delà du plafond, l'heure brute est utilisée (pas d'arrondi en escalier).

### RG-PTG-03 : Calcul du Temps Effectif Journalier

```
heures_matin = heure_sortie_matin - MAX(heure_entree_matin, heure_retenue_arrondie)
heures_am    = heure_sortie_am - heure_entree_am
total_heures = heures_matin + heures_am
minutes_retard = MAX(0, heure_entree_matin_arrondie - heure_theorique_site)
minutes_depart_anticipe = MAX(0, heure_theorique_fin_site - heure_sortie_am)
```

### RG-PTG-04 : Compensation Hebdomadaire — Seuil Proratisé par Tronçon

- La compensation retard/départ anticipé ↔ heure supplémentaire se calcule **par semaine**, jamais par jour ni par mois.
- La période de paie allant du **26 au 25**, la première et dernière semaine de chaque période sont des **tronçons** (2 à 4 jours). Le seuil est donc **proratisé** : `seuil_tronçon = nb_jours_ouvrés_du_tronçon × 8h` — pas un 40h fixe qui créerait un faux déficit systématique.
- **Coupure ferme au 25 du mois** : chaque journée est rattachée strictement à la période de paie dont la date est incluse (ex : le 25 décembre appartient à la période de décembre, le 26 à la période de janvier). Pas de report de semaine d'une période à l'autre.
- **Partitionnement SQL** : `id_periode_paie` est inclus dans le `GROUP BY` de `v_pointage_semaine` — une semaine à cheval génère automatiquement **deux lignes** (un tronçon par période), sans logique conditionnelle.
- Si `total_heures_troncon > seuil_troncon` : le surplus est HS (payée, sauf Cadre — RG-PTG-05).
- Si `total_heures_troncon < seuil_troncon` : le déficit restant est traité comme absence partielle valorisée (proratisée au taux `SME_COURANT / DIVISEUR_HORAIRE_ABSENCE`, cf `parametre_global`).

### RG-PTG-05 : Cadres et Heures Supplémentaires

Aligné avec `ref_categorie_professionnelle.code = 'C'` (déjà défini dans le module Contrat) :

- La HS **payée** est toujours forcée à 0 pour un Cadre, quel que soit le total réel.
- Le surplus d'heures reste utilisé en interne (dans `v_pointage_semaine`) pour compenser un déficit du même tronçon — seul l'excédent au-delà de la compensation est ignoré (non payé, non reporté).
- Dans `pointage_semaine_calcule` : `heures_hs_nettes = 0` systématiquement pour tout employé Cadre.

### RG-PTG-06 : Postes Autonomes (Dispense de Pointage) & Indemnités

- `ref_fonction.dispense_pointage = TRUE` marque les postes qui n'ont pas d'obligation de pointer.
- Pour ces employés, la synthèse mensuelle applique un **forfait de 8h/jour** sur les jours ouvrés — que l'employé ait pointé ou non.
- Le DAF/Admin dispose d'une vue « présences non saisies — postes autonomes » avec rappel de notification, pour saisir a posteriori si besoin.
- Ces employés **peuvent** pointer volontairement (ex : pour capter l'indemnité 2ᵉ repas du soir) sans que ce soit obligatoire.
- **Règle des heures** : même si une ligne `pointage_jour` existe pour le jour, les heures de l'employé autonome restent comptabilisées à **8h forfaitaires** dans `pointage_cumul_periode` — seules les **indemnités** (repas, déplacement) sont calculées sur la base du pointage réel de ce jour.
- **Dérogations d'indemnités** (ex : 4 personnes avec véhicule de fonction, autres sans indemnité déplacement) : gérées via `employe_indemnite_derogation` — indépendantes de `dispense_pointage`.

### RG-PTG-07 : Jours Fériés

- **Jour férié travaillé** : saisi comme `FERIE_TRAVAILLE` avec pointage normal (heures réelles) — la majoration `FERIE` (100%, `ref_taux_majoration`) sera appliquée par la Paie sur ces heures.
- **Jour férié non travaillé** : statut `FERIE_CHOME`, aucune heure de pointage attendue, jour compté comme travaillé/payé (pas d'absence), dérivé automatiquement de `ref_calendrier_ferie` / `site_calendrier_ferie` (pas de saisie manuelle nécessaire — pré-rempli par le système).

### RG-PTG-08 : Alignement avec le Module Congés

- Un jour couvert par une `demande_conge` au statut `VALIDE` **verrouille** automatiquement le statut du jour à `CONGE` dans `pointage_jour` (pas de saisie d'heures possible ce jour-là, cohérent avec `mouvement_solde_conge` type `PRISE`).
- En cas de demi-journée travaillée + demi-journée congé (non couvert nativement par `demande_conge` qui est en jours calendaires entiers) : voir §11, point à trancher avec toi.
- Le NBABS de la synthèse mensuelle exclut nativement les jours `CONGE` — pas de double comptage possible, la source de vérité du congé restant `demande_conge`.

### RG-PTG-09 : Indemnité Repas

Cascade à 3 niveaux (site → dérogation individuelle → ajustement du jour), conforme à `module-structure.md` §4.1 :

- **TNR** : indemnité repas par défaut (site continu, pause repas officielle).
- **TMM** : pas d'indemnité repas par défaut (site non continu) — **sauf** si l'employé ne rentre pas le midi (les 2 pointages AM/PM sont contigus sans pause réelle) : il reçoit alors le repas, **mais perd 1 000 Ar sur l'indemnité déplacement** ce jour-là (compensation logique : pas de trajet retour midi).
- **2ᵉ repas du soir** : si l'heure de sortie réelle est entre 19:30 et 21:00 → 2 000 Ar ; au-delà de 21:00 → 4 000 Ar (seuils dans `parametre_global.SEUIL_SECOND_REPAS`, à étendre avec un 2ᵉ paramètre pour le seuil 21h et les montants).

### RG-PTG-10 : Indemnité Déplacement

- Par défaut, **2 versements par jour** (1 le matin, 1 le soir) pour tout employé présent, sauf dérogation.
- **Véhicule de fonction le jour J** (`utilise_vehicule_fonction_jour = TRUE` sur `pointage_jour`) : les 2 versements du jour sont retirés (`-2`), sans affecter le statut permanent de l'employé.
- **Dérogation permanente** (4 personnes identifiées) : `employe_indemnite_derogation` avec `beneficie = FALSE` sur `id_indemnite = DEPLACEMENT` — la personne ne reçoit jamais cette indemnité, quel que soit le pointage du jour.
- Montant journalier de référence proratisé sur les jours ouvrables du mois (`site_indemnite_config.montant_reference`, périodicité `MENSUEL`).

### RG-PTG-11 : Prorata des Indemnités sur Jours Ouvrables du Mois

Fonction réutilisable `fn_jours_ouvrables(date_debut, date_fin, id_site)` (voir script SQL, §9) : compte les jours lundi-vendredi de la période, en excluant les jours fériés chômés du site (`site_calendrier_ferie`). Utilisée pour calculer le montant journalier = `montant_reference / jours_ouvrables_du_mois`.

### RG-PTG-12 : Non-Blocage de la Saisie + Correction

- L'absence de saisie un jour donné **n'est jamais bloquante** pour la suite du système.
- Une notification `POINTAGE_MANQUANT` est levée par le scheduler nocturne (voir §6/§7).
- La correction peut être faite :
  - par l'**agent de pointage**, tant que la `periode_paie` est en statut `BROUILLON`/`EN_COURS` ;
  - par le **DAF/Admin**, à tout moment tant que la période n'est pas `CLOTUREE` (traçabilité via `audit_log`, déjà existant).

### RG-PTG-13 : Verrouillage par Période de Paie

Une fois `periode_paie.id_statut_periode = VALIDEE`, plus aucune saisie/correction par l'agent de pointage. Seul un Admin peut encore rouvrir (retour à `EN_COURS`, tracé dans `audit_log`) tant que la période n'est pas `CLOTUREE`. Passée `CLOTUREE` (paie calculée), **aucune modification n'est plus possible**, quel que soit le rôle.

### RG-PTG-14 : Gel des Données à la Clôture (Immuabilité Légale)

**Avant validation** : les vues `v_pointage_semaine` et `v_synthese_pointage_mensuelle` calculent tout en temps réel pour l'affichage à l'écran.

**Au passage en `VALIDEE`** : les résultats agrégés (retards nets, heures supplémentaires nettes par tranche, indemnités totales) sont **physiquement insérés** dans `pointage_semaine_calcule` et `pointage_cumul_periode` — c'est la **source de vérité pour la Paie**.

**Pourquoi ?** Si le barème légal (taux de majoration, diviseur horaire, montants d'indemnités) ou la formule de calcul dans le code évolue dans 2 ans, les données d'une période déjà `CLOTUREE` doivent rester **100% reproductibles** lors d'un contrôle de l'Inspection du Travail, de la CNaPS ou du FISC.

Cela suit le même principe que `mouvement_solde_conge` dans le module Congés : on stocke des mouvements (source d'immuabilité) plutôt que de tout recalculer à la volée.

---

## 4. Statuts de la Journée de Pointage

| Code                      | Nécessite saisie d'heures | Génère une absence | Origine                                                                                           |
| ------------------------- | -------------------------- | -------------------- | ------------------------------------------------------------------------------------------------- |
| `PRESENT`               | Oui                        | Non                  | Saisie manuelle                                                                                   |
| `ABSENCE_NON_JUSTIFIEE` | Non                        | Oui                  | Saisie manuelle ou déduit d'un oubli non régularisé                                            |
| `CONGE`                 | Non                        | Non                  | **Matérialisé automatiquement** à la validation d'une `demande_conge` (voir §2.6 bis) |
| `FERIE_TRAVAILLE`       | Oui                        | Non                  | Saisie manuelle sur un jour de`ref_calendrier_ferie`                                            |
| `FERIE_CHOME`           | Non                        | Non                  | Matérialisé automatiquement depuis`ref_calendrier_ferie`                                      |
| `DISPENSE_POINTAGE`     | Non                        | Non                  | Auto, pour`ref_fonction.dispense_pointage = TRUE`                                               |

> `MISSION_EXTERIEURE` retiré — n'existe pas dans le contexte GALAXAM actuel.

### 4.1 Matérialisation des Jours `CONGE` (synchronisation avec le module Congés)

Contrairement à un simple calcul dynamique, on **matérialise** une ligne `pointage_jour` par jour de congé, comme pour les jours fériés — pour homogénéité de traitement (les deux passent par la même synthèse mensuelle sans requête spéciale).

Pour éviter toute incohérence si le congé est ensuite modifié/annulé, deux colonnes de traçabilité sont ajoutées à `pointage_jour` (voir §2.6) :

- `id_demande_conge` : lien vers la demande source (NULL si ligne saisie normalement).
- `genere_automatiquement` : `TRUE` pour les lignes créées par le système (congé ou férié), `FALSE` pour une saisie humaine.

**Synchronisation** :

1. `demande_conge` passe à `VALIDE` → le système crée une ligne `pointage_jour` (`statut = CONGE`, `genere_automatiquement = TRUE`, `id_demande_conge = ...`) pour chaque jour de la période, **sauf** si une ligne existe déjà pour ce jour (conflit à signaler, ne jamais écraser une saisie humaine).
2. `demande_conge` passe à `ANNULE` (après validation) → le système supprime uniquement les lignes `pointage_jour` liées à cette demande **et** encore marquées `genere_automatiquement = TRUE` (si un agent les a modifiées entre-temps, elles ne sont plus touchées automatiquement — alerte manuelle à la place).

---

## 5. Workflow & Circuit de Validation

```
PERIODE_PAIE : BROUILLON -> EN_COURS <------------------------> A_VALIDER -> VALIDEE -> CLOTUREE
                  |            |        (Rejet DAF avec           |           |          |
                  |            |         motif obligatoire)       |           |          └── Consommée par la Paie (verrou définitif)
                  |            |                                  |           └── Contrôle & Validation finale DAF (gel pointage_cumul_periode)
                  |            |                                  └── Contrôle DAF (verrou d'écriture pour l'agent)
                  |            └── Saisie quotidienne, résolution anomalies et calcul global 1-clic
                  └── Ouverture de la période (automatique ou manuelle par Admin)
```

Le workflow modernisé supprime le cloisonnement séquentiel contraignant semaine par semaine au profit d'une fluidité opérationnelle contrôlée :

1. Phase de Saisie & Consultation Libre (statut EN_COURS) :
   - L'agent de pointage saisit les présences et traite les anomalies quotidiennes.
   - En fin de mois, l'agent déclenche la consolidation globale en un clic (endpoint POST /pointages/synthese/calculer-tout). Toutes les semaines sont calculées sans verrouiller la grille. L'agent peut librement consulter, comparer et ajuster les pointages si nécessaire.

2. Phase de Transmission Sécurisée à la DAF (statut A_VALIDER) :
   - L'agent clique sur "Transmettre à la DAF" (endpoint POST /pointages/synthese/transmettre-daf).
   - Le système vérifie de manière bloquante qu'aucune anomalie non résolue ne subsiste sur l'ensemble des sites de l'entreprise.
   - Le système recalcule l'intégralité des tronçons et passe la période en statut A_VALIDER avec verrouillage pessimiste PostgreSQL (FOR UPDATE).
   - La saisie quotidienne et la régularisation des anomalies sont strictement bloquées en lecture seule pour les agents de site (contrôle au niveau de PointageSaisieService et PointageAnomalieService).
   - Une notification ciblée est adressée exclusivement aux administrateurs DAF (cible_role = 'ROLE_ADMIN').

3. Phase d'Arbitrage DAF :
   - Option A - Validation (POST /pointages/synthese/valider-daf) : la DAF valide la synthèse. Les cumuls sont figés de manière immuable dans pointage_cumul_periode et pointage_cumul_indemnite. La période passe en VALIDEE. Une notification de confirmation est envoyée aux agents (cible_role = 'ROLE_AGENT_POINTAGE').
   - Option B - Rejet pour correction (POST /pointages/synthese/rejeter-daf) : la DAF renvoie la période avec un motif de rejet obligatoire (motif_rejet). La période repasse en EN_COURS, déverrouillant la saisie pour l'agent. Une alerte prioritaire est notifiée aux agents (cible_role = 'ROLE_AGENT_POINTAGE') et un bandeau d'alerte jaune s'affiche sur la page de synthèse avec le motif explicatif.

4. Clôture Définitive (statut CLOTUREE) :
   - Une fois la paie exécutée, la période est clôturée. Toute modification ultérieure est strictement interdite.

---

## 6. Automatisations & Schedulers

Aligné sur les schedulers existants (00:00, 00:05, 01:00, 02:00) :

| Heure | Scheduler | Fréquence | Action |
| :--- | :--- | :--- | :--- |
| 00:10:00 | PointageRappelScheduler | Quotidien | Détecte les employés sans pointage_jour la veille (hors DISPENSE_POINTAGE, CONGE, FERIE_CHOME), génère POINTAGE_MANQUANT pour ROLE_AGENT_POINTAGE |
| 00:15:00 | PeriodePaieScheduler | Quotidien | Rappel des périodes arrivées à échéance |
| 00:20:00 | FerieChomeScheduler | Quotidien | Pré-crée les lignes pointage_jour en statut FERIE_CHOME pour les jours fériés à venir |

---

## 7. Notifications & Cloisonnement RBAC

Les notifications RH sont partitionnées par rôle via la colonne cible_role de la table notification_rh :

| Type d'Alerte | Destinataire Cible (cible_role) | Priorité | Déclencheur |
| :--- | :--- | :--- | :--- |
| POINTAGE_MANQUANT | ROLE_AGENT_POINTAGE | HAUTE | Aucune saisie la veille pour un employé actif non dispensé |
| PERIODE_POINTAGE_A_VALIDER | ROLE_ADMIN | HAUTE | Transmission de la période par l'agent à la DAF |
| PERIODE_POINTAGE_VALIDEE | ROLE_AGENT_POINTAGE | INFO | Confirmation de validation de la période par la DAF |
| PERIODE_POINTAGE_REJETEE | ROLE_AGENT_POINTAGE | HAUTE | Renvoi de la période par la DAF avec motif explicatif obligatoire |
| RETARD_RECURRENT | ROLE_AGENT_POINTAGE | NORMALE | Plus de 3 retards non compensés dans la semaine pour un employé |

---

## 8. Sortie vers la Paie

Le module Pointage **ne calcule pas de brut/net**. Il fournit à la Paie, via `v_synthese_pointage_mensuelle` (par employé × période de paie) :

| Colonne fournie                                           | Équivalent dans tes fichiers Excel                                                  |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| `nb_jours_absence`                                      | `NBABS`                                                                            |
| `total_heures_travaillees` / `heures_hs_mensuelles`   | Base du calcul`ABSENCES` et `HS`                                                 |
| `nb_jours_deplacement` × montant journalier proratisé | `DEPLACEMENT`                                                                      |
| `montant_repas_total`                                   | `REPAS`                                                                            |
| (via module Congés)`NBCONGE` / `TCONGE`              | Restent portés par`demande_conge` / `mouvement_solde_conge`, pas dupliqués ici |

La Paie devra encore : appliquer les taux de majoration HS (`ref_taux_majoration`), calculer `BRUTE`, `CNAPS/OSTIE/OMSI`, `IRSA`, `RED ENFT`, `AVANCE`, etc. — c'est un module à part entière, non couvert ici.

---

## 9. Architecture Technique

### 9.1 Classes Java Principales (convention `ClassMere` / `controler()`)

| Classe                               | Table / Vue                        | Rôle                                                                  |
| ------------------------------------ | ---------------------------------- | ---------------------------------------------------------------------- |
| `PointageJour.java`                | `pointage_jour`                  | Entité + calculs RG-PTG-01 à 04 dans`controler()` avant `save()` |
| `PeriodePaie.java`                 | `periode_paie`                   | Cycle de vie BROUILLON→CLOTUREE + gel physique des données           |
| `EmployeIndemniteDerogation.java`  | `employe_indemnite_derogation`   | CRUD dérogations niveau 2 (cascade indemnités)                       |
| `RefToleranceArriveePointage.java` | `ref_tolerance_arrivee_pointage` | Plafond arrivée anticipée par site                                   |
| `RefStatutPointageJour.java`       | `ref_statut_pointage_jour`       | Référentiel des statuts de journée                                  |
| `RefStatutPeriodePaie.java`        | `ref_statut_periode_paie`        | Référentiel des statuts de période                                  |
| `PointageSemaineCalcule.java`      | `pointage_semaine_calcule`       | Données hebdo gelées — INSERT à la validation                      |
| `PointageCumulPeriode.java`        | `pointage_cumul_periode`         | Données mensuelles gelées — sortie Paie immuable                    |
| `VPointageSemaine.java`            | `v_pointage_semaine`             | Vue temps réel (affichage avant validation)                           |
| `VSynthesePointageMensuelle.java`  | `v_synthese_pointage_mensuelle`  | Vue temps réel (affichage avant validation)                           |
| `PointageRappelScheduler.java`     | —                                 | Scheduler d'alerte oubli de saisie (00:10)                             |
| `PeriodePaieScheduler.java`        | —                                 | Transition automatique statuts de période (00:15)                     |
| `FerieChomeScheduler.java`         | —                                 | Pré-crée lignes FERIE_CHOME (00:20)                                  |

### 9.2 Fonction SQL Réutilisable

```sql
CREATE OR REPLACE FUNCTION fn_jours_ouvrables(
    p_date_debut DATE, p_date_fin DATE, p_id_site INT
) RETURNS INTEGER AS $$
DECLARE
    v_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM generate_series(p_date_debut, p_date_fin, '1 day') AS d
    WHERE EXTRACT(ISODOW FROM d) < 6   -- lundi à vendredi
      AND NOT EXISTS (
          SELECT 1 FROM site_calendrier_ferie scf
          JOIN ref_calendrier_ferie f ON scf.id_ferie = f.id_ferie
          WHERE scf.id_site = p_id_site AND f.date_ferie = d AND f.est_chome = TRUE
      );
    RETURN v_count;
END;
$$ LANGUAGE plpgsql;
```

### 9.3 Contrôleurs REST

| Contrôleur                       | URL de Base                   | Rôle                                  |
| --------------------------------- | ----------------------------- | -------------------------------------- |
| `PointageJourController`        | `/api/pointage/jours`       | CRUD saisie quotidienne                |
| `PeriodePaieController`         | `/api/pointage/periodes`    | Cycle de vie des périodes             |
| `PointageSyntheseController`    | `/api/pointage/synthese`    | Vues agrégées (semaine, mois)        |
| `IndemniteDerogationController` | `/api/pointage/derogations` | Gestion des dérogations individuelles |

---

## 10. API REST — Endpoints

Base URL : `/api/pointage`

### Saisie Quotidienne

| Méthode | URL                  | Rôle requis          | Description                                                   |
| -------- | -------------------- | --------------------- | ------------------------------------------------------------- |
| `GET`  | `/jours`           | AGENT_POINTAGE, ADMIN | Liste des pointages (filtres : employé/site/période/statut) |
| `GET`  | `/jours/{id}`      | AGENT_POINTAGE, ADMIN | Détail d'un pointage                                         |
| `POST` | `/jours`           | AGENT_POINTAGE, ADMIN | Créer/saisir un pointage du jour                             |
| `PUT`  | `/jours/{id}`      | AGENT_POINTAGE, ADMIN | Corriger un pointage (si période non verrouillée)           |
| `GET`  | `/jours/manquants` | AGENT_POINTAGE, ADMIN | Liste employés sans saisie sur une date donnée              |

### Gestion des Périodes

| Méthode | URL                         | Rôle requis          | Description                                                                 |
| -------- | --------------------------- | --------------------- | --------------------------------------------------------------------------- |
| `GET`  | `/periodes`               | AGENT_POINTAGE, ADMIN | Liste des périodes de paie                                                 |
| `POST` | `/periodes`               | ADMIN                 | Créer une nouvelle période                                                |
| `PUT`  | `/periodes/{id}/valider`  | ADMIN                 | → VALIDEE (gèle`pointage_semaine_calcule` + `pointage_cumul_periode`) |
| `PUT`  | `/periodes/{id}/cloturer` | ADMIN                 | → CLOTUREE (immuable, consommée par Paie)                                 |
| `PUT`  | `/periodes/{id}/rouvrir`  | ADMIN                 | VALIDEE → EN_COURS (tracé`audit_log`, interdit si CLOTUREE)             |

### Synthèses & Vues

| Méthode | URL                            | Rôle requis          | Description                                                  |
| -------- | ------------------------------ | --------------------- | ------------------------------------------------------------ |
| `GET`  | `/synthese/semaine`          | AGENT_POINTAGE, ADMIN | `v_pointage_semaine` temps réel                           |
| `GET`  | `/synthese/mensuelle`        | AGENT_POINTAGE, ADMIN | `v_synthese_pointage_mensuelle` temps réel                |
| `GET`  | `/synthese/gele/{idPeriode}` | ADMIN                 | `pointage_cumul_periode` figé (données officielles Paie) |

### Dérogations d'Indemnités

| Méthode | URL                          | Rôle requis | Description                                                         |
| -------- | ---------------------------- | ------------ | ------------------------------------------------------------------- |
| `GET`  | `/derogations`             | ADMIN        | Liste des dérogations d'indemnité individuelles                   |
| `POST` | `/derogations`             | ADMIN        | Créer une dérogation (véhicule fonction, exclusion indemnité…) |
| `PUT`  | `/derogations/{id}/fermer` | ADMIN        | Clôturer une dérogation (date_fin = aujourd'hui)                  |

---

## 11. Points Tranchés & Décisions de Conception

### Decisions validees (historique)

| Point                       | Décision retenue                                                                                                                                                                                           |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Architecture BDD            | **Option A** : 1 table `pointage_jour` unifiée (+ tables de synthèse gelées). Pas de séparation `pointage_brut` / `pointage_journalier_calcule`.                                            |
| Compensation                | **Hebdomadaire** par tronçon proratisé (`seuil = nb_jours_ouvrés × 8h`), pas mensuelle.                                                                                                         |
| Semaine à cheval           | **Coupure ferme au 25 du mois**. Chaque journée est rattachée à sa période calendaire réelle. `id_periode_paie` dans le `GROUP BY` de `v_pointage_semaine` gère ça automatiquement.      |
| Gel des données            | **Oui** : `pointage_semaine_calcule` + `pointage_cumul_periode` physiquement insérés à la validation. Les vues restent pour l'affichage temps réel uniquement.                                |
| Postes autonomes            | Journées toujours comptabilisées**8h forfaitaires**. Peuvent pointer volontairement pour capter des indemnités. Les indemnités sont calculées sur le pointage réel, les heures restent forfait. |
| Dérogations indemnités    | Deux mécanismes indépendants : ponctuel (`utilise_vehicule_fonction_jour`) + permanent (`employe_indemnite_derogation`).                                                                              |
| `MISSION_EXTERIEURE`      | **Supprimé** — non utilisé chez GALAXAM.                                                                                                                                                           |
| Ligne CONGE matérialisée  | **Oui**, avec `id_demande_conge` + `genere_automatiquement = TRUE`. Synchronisation bidirectionnelle (validation congé → création ; annulation congé → suppression lignes auto seulement).   |
| Demi-journée congé        | **Déjà implémenté** dans le module Congés (`est_demi_journee` sur `demande_conge`). La demi-journée travaillée reste soumise à pointage normal.                                           |
| 2ᵉ repas                   | Basé sur l'heure de sortie réelle : > 19h30 → +2 000 Ar ; > 21h00 → +4 000 Ar. Seuils dans`parametre_global`.                                                                                         |
| Pause non saisie            | Reconstituée depuis`ref_site.horaire_continu` : continu → 1h ; discontinu → 2h.                                                                                                                        |
| Plafond arrivée anticipée | 1 seule règle par site (`ref_tolerance_arrivee_pointage.heure_plafond`), pas de paliers en escalier.                                                                                                     |
| Verrouillage CLOTUREE       | **Strict et définitif** — aucune modification possible, quel que soit le rôle.                                                                                                                     |
| Paramètres                 | Dates de période de paie paramétrables (`periode_paie`). Montants indemnités, seuils HS, diviseur horaire dans `parametre_global` et `site_indemnite_config`.                                      |

### Point encore ouvert

1. **Menus RBAC navigation** (recommandation ci-dessous) : à valider.

---

## 12. Menus RBAC — Navigation Frontend (Recommandation)

| `id_menu`              | Libellé                    | Icône            | Route                      | Niveau accès          |
| ------------------------ | --------------------------- | ----------------- | -------------------------- | ---------------------- |
| `POINTAGE_JOURNALIER`  | Saisie & Pointage Quotidien | `ClockCheck`    | `/pointages/saisie`      | AGENT_POINTAGE + ADMIN |
| `POINTAGE_ANOMALIES`   | Gestion des Anomalies       | `AlertTriangle` | `/pointages/anomalies`   | AGENT_POINTAGE + ADMIN |
| `POINTAGE_SYNTHESE`    | Synthèse Hebdo & Mensuelle | `BarChart2`     | `/pointages/synthese`    | ADMIN                  |
| `POINTAGE_PERIODES`    | Périodes de Paie           | `CalendarRange` | `/pointages/periodes`    | ADMIN                  |
| `POINTAGE_CLOTURE`     | Validation & Clôture       | `Lock`          | `/pointages/cloture`     | ADMIN                  |
| `POINTAGE_DEROGATIONS` | Dérogations d'Indemnités  | `UserMinus`     | `/pointages/derogations` | ADMIN                  |

> **Justification** : 6 menus plutôt que 3, car Synthèse (lecture DAF), Périodes (gestion du cycle) et Dérogations (configuration rare mais critique) ont des profils d'utilisateurs et des fréquences d'usage distincts. Un agent de pointage ne voit que `POINTAGE_JOURNALIER` + `POINTAGE_ANOMALIES`.

---

*Document mis à jour le 20 août 2026 — GALAXAM SIRH — Version 1.1.0*

---

# 13. Annexes : Mises à jour de Conception (Session Août 2026)

# Journal des Modifications — Module Pointage & Présence

## Session 20–21 Août 2026

> Ce document trace toutes les décisions et modifications de conception arrêtées lors de la session de revue du module Pointage. Il sert de référence avant l'implémentation.

---

## 1. Modifications SQL

### 1.1 `26-08-05-sp0-ref.sql`

#### `ref_fonction` — Colonne ajoutée

```sql
-- Ajouté
dispense_pointage BOOLEAN NOT NULL DEFAULT FALSE
-- TRUE = poste autonome → présomption de présence 8h/j, pas d'alerte manquant
```

#### `parametre_global` & `paie_config_legale` — Pilotage Dynamique des Règles de Pointage

Tous les calculs métier (durée légale, seuils journaliers, heures de nuit, déjeuners, extras de soirée, déduction transport TMM) sont **100% dynamiques et historisés**.

##### 1. Constantes Légales Hebdomadaires (`paie_config_legale`)
Les seuils légaux nationaux sont rattachés au millésime fiscal actif (`LF_2026`, etc.) :
* `duree_hebdomadaire_legale_heures` (`40h`) : Seuil pivot hebdomadaire de déclenchement des heures supplémentaires.
* `seuil_hs_tranche1_heures` (`8h`) : Seuil maximal de la tranche 1 d'HS majorée à **+30%** (au-delà, passage à +50%).
* `plafond_hs_hebdo_heures` (`20h`) : Plafond légal d'heures supplémentaires autorisées par semaine (Décret 2007-563).

##### 2. Paramètres Opérationnels Débrayables (`parametre_global`)
Chargés temporellement via `ParametreGlobal.loadEtatMapAtDate(dateDebutPeriode, c)` :

| Code | Valeur BDD | Débrayable ? | Rôle & Comportement si Inactif |
| :--- | :---: | :---: | :--- |
| `SEUIL_HEURES_JOUR` | `8` | 🔒 Protégé | Base normale quotidienne par jour ouvré ($N \times 8\text{h}$). |
| `SEUIL_NUIT_DEBUT` | `22:00` | 🔒 Protégé | Début du travail de nuit (Art. 83 Code Travail Malgache). |
| `SEUIL_NUIT_FIN` | `05:00` | 🔒 Protégé | Fin de la tranche nocturne matinale. |
| `MONTANT_REPAS_MIDI` | `2000` (Ar) | 🔒 Protégé | Montant standard déjeuner (si non surchargé par site). |
| `SEUIL_PAUSE_MIDI_MIN` | `60` (min) | 🔒 Protégé | Pause midi minimale en minutes (détection présence midi). |
| `SEUIL_2ND_REPAS_1` | `19:30` | ⚙ Débrayable | Heure mini 1er extra. Si inactif : extra désactivé. |
| `MONTANT_2ND_REPAS_1` | `2000` (Ar) | ⚙ Débrayable | Montant 1er extra soir. Si inactif : valorisé à `0.0 Ar`. |
| `SEUIL_2ND_REPAS_2` | `21:00` | ⚙ Débrayable | Heure mini 2e extra majoré. Si inactif : extra désactivé. |
| `MONTANT_2ND_REPAS_2` | `4000` (Ar) | ⚙ Débrayable | Montant 2e extra soir. Si inactif : valorisé à `0.0 Ar`. |
| `REDUCTION_DEPLACT_REPAS_MIDI` | `1000` (Ar) | ⚙ Débrayable | Déduction déplacement TMM si midi sur site. Si inactif : **`0.0 Ar`** (aucune déduction effectuée). |

> **Règle d'or de résolution** :
> - **Paramètre inactif (`est_actif = false`)** : Résulte d'une décision opérationnelle de l'entreprise. L'indemnité ou la déduction est ramenée à `0.0 Ar`.
> - **Paramètre introuvable (clé absente)** : Erreur de configuration. Le système utilise une valeur de fallback par défaut.

#### Architecture des Services Dédiés :
- `mg.galaxam.sirh.model.paieconfig.ParametreGlobal` : Modèle central et chargeur temporel `loadEtatMapAtDate(dateRef, c)` avec accesseurs typés gérant `estActif`.
- `mg.galaxam.sirh.model.pointage.service.PointageSaisieService` : Saisie quotidienne, calcul des heures de nuit, détection des pauses et allocation du 2nd repas selon l'activation des seuils.
- `mg.galaxam.sirh.model.pointage.service.PointageAnomalieService` : Détection, classification et régularisation des anomalies.
- `mg.galaxam.sirh.model.pointage.service.PointageSyntheseService` : Compensation hebdomadaire par tronçon, application temporelle de la déduction transport TMM (respect de `est_actif`) et synthèse consolidée.
- `mg.galaxam.sirh.model.dashboard.service.DashboardService` : Agrégation des KPIs et alertes RH.

---

### 1.2 `26-08-20-sp3-pointage.sql`

#### À faire (prochaine mise à jour SQL)

**`ref_categorie_professionnelle`** — Colonne à ajouter :

```sql
est_eligible_hs BOOLEAN NOT NULL DEFAULT TRUE
-- Seed : UPDATE ref_categorie_professionnelle SET est_eligible_hs = FALSE WHERE code = 'C';
```

> Décision : c'est la catégorie (Cadre/Agent), pas la fonction, qui détermine l'éligibilité HS.

**`ref_statut_pointage_jour`** — Nouveaux codes à ajouter :

| Code                   | decompte_absence | Saisie heures | Origine                                     |
| ---------------------- | :--------------: | :-----------: | ------------------------------------------- |
| `ANOMALIE_SAISIE`    |      FALSE      |   Partielle   | Scheduler J-1 si pointage incomplet         |
| `ABSENCE_JUSTIFIEE`  |       TRUE       |      Non      | Saisie manuelle DAF/Agent                   |
| `SAMEDI_TRAVAILLE`   |      FALSE      |      Oui      | Saisie manuelle — mécaniquement = PRESENT |
| `DIMANCHE_TRAVAILLE` |      FALSE      |      Oui      | Saisie manuelle — taux DIMANCHE appliqué  |

**`pointage_jour`** — Colonnes à ajouter :

```sql
heures_nuit              DECIMAL(4,2) NOT NULL DEFAULT 0,  -- calculé auto si sortie > SEUIL_NUIT_DEBUT
montant_deplacement_jour DECIMAL(10,2),  -- NULL = cascade standard L1→L2, valeur = override ponctuel
horaire_continu_jour     BOOLEAN,        -- NULL = valeur du site, TRUE/FALSE = override du jour (TMM midi)
```

**`pointage_semaine_calcule`** — Colonnes taux gelés à ajouter :

```sql
taux_hs_30_applique    DECIMAL(5,2) NOT NULL DEFAULT 0,
taux_hs_50_applique    DECIMAL(5,2) NOT NULL DEFAULT 0,
taux_nuit_applique     DECIMAL(5,2) NOT NULL DEFAULT 0,
taux_dimanche_applique DECIMAL(5,2) NOT NULL DEFAULT 0,
taux_ferie_applique    DECIMAL(5,2) NOT NULL DEFAULT 0,
heures_hs_30           DECIMAL(5,2) NOT NULL DEFAULT 0,   -- MIN(hs_nettes, seuil_hs_tranche1_heures de paie_config_legale)
heures_hs_50           DECIMAL(5,2) NOT NULL DEFAULT 0,   -- MAX(hs_nettes - seuil_hs_tranche1_heures, 0)
```

**`pointage_cumul_periode`** — Colonne à ajouter :

```sql
heures_nuit_total DECIMAL(6,2) NOT NULL DEFAULT 0,
```

---

## 2. Nouvelles Règles de Gestion

### RG-IND-01 — Cascade Indemnités (4 niveaux, L3 gagne)

```
L0  ref_indemnite              existence et activation de l'indemnité
L1  site_indemnite_config      montant de référence par site
L2  employe_indemnite_derogation  override individuel permanent
L3  pointage_jour              override ponctuel du jour (le plus spécifique, gagne toujours)
```

L'interface **pré-remplit** les valeurs L1/L2 comme aide à la saisie. L'agent peut toujours modifier L3 pour le jour en cours.

### RG-PTG-09 bis — Détection "Ne Rentre Pas Midi" (TMM)

```
Si ref_site.horaire_continu = FALSE ET
   (heure_entree_am - heure_sortie_matin) < SEUIL_PAUSE_MIDI_MIN (60 min)
→  horaire_continu_jour = TRUE pour ce jour
→  indemnite_repas_recue = TRUE (même si site TMM = 0 par défaut)
→  montant_deplacement_jour = montant_standard - REDUCTION_DEPLACT_REPAS_MIDI
```

L'agent peut aussi cocher manuellement "Horaire continu ce jour" dans l'interface.

### RG-PTG-15 — Heures de Nuit (22h00–05h00)

```
Si heure_sortie_am > SEUIL_NUIT_DEBUT (22:00)
→  heures_nuit = heure_sortie_am - 22:00
   (cas extrême : si chevauchement minuit, MIN(heure_sortie_am, SEUIL_NUIT_FIN))
→  taux_nuit_applique = ref_taux_majoration WHERE type_majoration = 'NUIT'
```

### RG-PTG-16 — Samedi/Dimanche Travaillé

```
Samedi :
  → statut SAMEDI_TRAVAILLE
  → heures s'ajoutent au tronçon de la semaine (même mécanique que PRESENT)
  → potentiel HS si total tronçon > seuil proratisé

Dimanche :
  → statut DIMANCHE_TRAVAILLE
  → taux DIMANCHE (40%) appliqué sur les heures du jour
  → gelé dans pointage_semaine_calcule.taux_dimanche_applique
```

### RG-AUT-01 — Postes Autonomes (Présomption de Présence)

```
ref_fonction.dispense_pointage = TRUE :
  → Compté 8h/jour forfait sur jours ouvrés de la période (présomption présence)
  → N'apparaît PAS dans les alertes POINTAGE_MANQUANT
  → Peut pointer volontairement → seules les indemnités sont recalculées sur pointage réel
  → Si absent : DAF/Agent saisit manuellement ABSENCE_JUSTIFIEE ou CONGE
  → Si congé : passe par le module Congés normalement
```

### RG-PTG-04 bis — Ventilation HS_30/HS_50 (complément)

```
Par tronçon, après calcul heures_hs_nettes :
  hs_30 = MIN(heures_hs_nettes, seuil_hs_tranche1_heures)   -- paie_config_legale
  hs_50 = MAX(heures_hs_nettes - seuil_hs_tranche1_heures, 0)
Gelés dans pointage_semaine_calcule à la validation.
Sommés dans pointage_cumul_periode pour la Paie.
```

---

## 3. Décisions de Workflow & Rôles

### Circuit de validation (révisé)

```
Agent de Pointage :  saisie quotidienne + corrections + régularisations des oublis
                     (premier responsable de l'exactitude des données)
                             ↓
DAF :  révision en fin de période (vérification paie, ajustements)
       → clique VALIDER → gel physique
       → clique CLOTURER → immuable
Admin :  peut rouvrir une période VALIDEE si erreur (retour EN_COURS + audit_log)
         INTERDIT si CLOTUREE
```

### Traçabilité des corrections

`audit_log` existant avec `anciennes_valeurs JSONB` + `nouvelles_valeurs JSONB` — **suffisant**, pas de table dédiée supplémentaire. La logique Java écrit dans `audit_log` à chaque PUT.

---

## 4. Spécification UI/UX — Module Pointage

### Principe KISS (Keep It Simple and Smart)

> Une seule vue principale par onglet, un seul panneau de saisie, des actions contextuelles. L'interface guide l'agent sans le noyer.

---

### 4.1 Vue Hebdomadaire — Recommandation

**Pourquoi par semaine (et non jour ou mois) :**

- La logique métier est hebdomadaire (compensation tronçon, HS) → cohérence vue/données
- 5 à 7 colonnes = lisible même avec 30 employés
- Le mois entier (26 colonnes) est trop dense pour la saisie — utile seulement en aperçu lecture

**Architecture des vues :**

```
[Onglet POINTAGE_JOURNALIER]
  └── Vue principale : Grille hebdomadaire (par défaut)
      Navigation : < Semaine précédente | Sem. 26 Nov – 2 Déc | Semaine suivante >
      Filtres : [Site ▼]  [Période ▼]  [Afficher : Tous / Anomalies seulement]
    
  └── Vue secondaire : Aperçu mensuel (Gantt compact, lecture seule)
      Accessible via onglet "Aperçu période"
```

---

### 4.2 Maquette Grille Hebdomadaire

```
Site: TNR   Semaine: 26–30 Nov 2025   Période: Déc 2025
─────────────────────────────────────────────────────────────────────────
Employé          │ Lun 26 │ Mar 27 │ Mer 28 │ Jeu 29 │ Ven 30 │ Total
─────────────────────────────────────────────────────────────────────────
RAKETAKA         │ [P] 8h │ [!] 7h │ [P] 8h │ [A] —  │ [P] 8h │ 31h
RASOA            │ [P] 8h │ [P] 8h │ [C] —  │ [C] —  │ [P] 8h │ 16h
RAKOTO [auto]    │ [X] 8h │ [X] 8h │ [X] 8h │ [X] 8h │ [X] 8h │ 40h
─────────────────────────────────────────────────────────────────────────
Légende : [P] Présent  [!] Retard/Anomalie  [A] Absent  [C] Congé  [X] Autonome
```

- **Hover sur cellule** → tooltip : heures + retard + HS + indemnités
- **Clic sur cellule** → ouvre panneau latéral de saisie
- **Ligne [auto]** = poste autonome (`dispense_pointage`) → grisée, lecture seule sauf saisie volontaire

---

### 4.3 Panneau Latéral de Saisie

**Deux sections distinctes (KISS) :**

```
┌─ SAISIE — RAKETAKA  Mer 28 Nov 2025 ──────────────────────────┐
│                                                                 │
│  POINTAGES (logique métier)                                     │
│  Matin    Entrée [08:12]  Sortie [12:03]                        │
│  Après-midi Entrée [13:05]  Sortie [17:08]                      │
│  [ ] Horaire continu ce jour (ne rentre pas le midi)            │
│                                                                 │
│  → Retard calculé : 12 min                                      │
│  → Total : 7h 51min                                             │
│  → Seuil tronçon : 40h | Cumul semaine : 31h/40h               │
│                                                                 │
│  INDEMNITÉS (ajustables par l'agent)                            │
│  [X] Déplacement    pré-coché (L1: TNR standard)                │
│  [X] Repas midi     pré-coché (L1: TNR horaire continu)         │
│  [--] 2ᵉ repas soir  ⟵ automatique si sortie > 19h30          │
│       Sortie à 17h08 → Non applicable                           │
│                                                                 │
│  Statut jour : [PRESENT ▼]                                      │
│  Motif (si ajustement) : [________________________]             │
│                                                                 │
│  [Enregistrer]  [Marquer Absent]  [Signaler Anomalie]           │
└─────────────────────────────────────────────────────────────────┘
```

**Distinction logique métier / UX :**

| Élément                      | Qui décide                                                  | Comment affiché                   |
| ------------------------------ | ------------------------------------------------------------ | ---------------------------------- |
| 2ᵉ repas soir                 | **Backend** (calcul auto heure sortie)                 | Ligne informative, lecture seule   |
| Repas midi TMM (ne rentre pas) | **Backend** (détection pause < 60 min) OU agent coche | Checkbox + indicateur auto         |
| Déplacement                   | **Cascade L1→L2** (pré-rempli)                       | Checkbox modifiable par agent      |
| Heures retard/HS               | **Backend**                                            | Affichage calculé, non modifiable |
| Statut (PRESENT/ABSENT…)      | **Agent**                                              | Dropdown                           |

---

### 4.4 Actions Rapides (Productivité Agent)

```
En-tête de la grille :
  [Appliquer horaires théoriques à tous]  →  pré-remplit les heures théoriques du site
  [Voir anomalies seulement]              →  filtre les lignes avec ANOMALIE_SAISIE
  [Exporter semaine]                      →  CSV / PDF de la semaine affichée
```

---

### 4.5 Aperçu Mensuel (Gantt compact)

```
[Onglet "Aperçu période"]
Vue lecture seule — 26 colonnes (26 → 25)
Couleurs seulement (pas de texte dans les cellules)
Clic → bascule en vue hebdomadaire sur la semaine correspondante
```

---

## 5. Module Paie — Éléments à Implémenter (Futur)

### Ce que le Pointage lui fournit (via `pointage_cumul_periode` gelé)

| Colonne                      | Description                                                             |
| ---------------------------- | ----------------------------------------------------------------------- |
| `nb_jours_presents`        | Jours PRESENT + FERIE_TRAVAILLE + SAMEDI_TRAVAILLE + DIMANCHE_TRAVAILLE |
| `nb_jours_absence`         | Jours ABSENCE_NON_JUSTIFIEE + ABSENCE_JUSTIFIEE                         |
| `nb_jours_conge`           | Jours CONGE                                                             |
| `nb_jours_ferie_chome`     | Jours FERIE_CHOME                                                       |
| `total_heures_travaillees` | Total heures réelles pointées                                         |
| `heures_hs_30`             | HS tranche 30% (somme des tronçons)                                    |
| `heures_hs_50`             | HS tranche 50%                                                          |
| `heures_nuit_total`        | Heures de nuit (22h–5h)                                                |
| `heures_absence_retenues`  | Retards nets non compensés → déduction paie                          |
| `montant_deplacement`      | Total Ar déplacement sur la période                                   |
| `montant_repas`            | Total Ar repas sur la période                                          |

### Ce que la Paie doit faire (hors périmètre Pointage)

```
Bulletin de paie (colonnes Excel confirmées : NBABS, ABSENCES, NBCONGE, TCONGE, CONGE, HS, DEPLACEMENT, REPAS, AUTIMP, BRUTE, NETI)

BRUTE =   BASE
        - (BASE / 173.33h) × heures_absence_retenues          ← ABSENCES
        + (BASE + HS) / 30 × nb_jours_conge                   ← CONGE (diviseur calendaire)
        + (BASE / 173.33h) × heures_hs_30 × taux_hs_30        ← HS 30%
        + (BASE / 173.33h) × heures_hs_50 × taux_hs_50        ← HS 50%
        + montant_deplacement                                   ← DEPLACEMENT
        + montant_repas                                         ← REPAS
        + autres_primes_et_avantages                           ← AUTIMP

CNAPS salarial  = MIN(BRUTE, 8 × SME) × 1%
OSTIE           = MIN(BRUTE, 8 × SME) × 1%
IRSA            = barème progressif (6 tranches) − (nb_enfants × 2 000 Ar)
NETI = BRUTE − CNAPS − OSTIE − IRSA
```

> Les taux appliqués (`taux_hs_30_applique`, etc.) sont lus depuis `pointage_semaine_calcule`, pas recalculés.

---

*Document créé le 21 août 2026 — GALAXAM SIRH — À archiver dans `backend/docs/boky/`*

### 13.1 Application stricte de l'éligibilité aux HS (Cadres)

**Précision d'implémentation (Côté Backend Java)** :

- Lors du gel d'une semaine (insertion dans `pointage_semaine_calcule`), le backend DOIT vérifier l'attribut `est_eligible_hs` de la catégorie professionnelle de l'employé.
- Si `FALSE` (ex: CADRE), les valeurs `heures_hs_nettes`, `heures_hs_30`, et `heures_hs_50` sont **impérativement forcées à 0**.
- Le déficit (`heures_deficit`), en revanche, continue d'être tracé pour compenser d'éventuels retards sur d'autres semaines. (On ne cherche donc pas à biaiser le calcul amont, on applique la règle au moment du gel).
