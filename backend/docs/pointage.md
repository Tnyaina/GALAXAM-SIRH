# Module Pointage & Présence — Documentation Technique & Fonctionnelle
## GALAXAM SIRH — Mémoire de Fin d'Études

> **Module** : Saisie quotidienne des présences, heures supplémentaires, indemnités liées à la présence (Déplacement, Repas), synthèse mensuelle vers la Paie
> **Version** : 1.0.0 (Spécification de conception — certains points restent à valider, voir §11)
> **Date** : 2026
>
> **Périmètre implémenté** : Saisie AM/PM par employé/jour, calcul du temps effectif, gestion retard/départ anticipé, compensation hebdomadaire (40h), heures supplémentaires, dispense de pointage (postes autonomes), indemnités Déplacement/Repas avec cascade de dérogation, jours fériés, alignement avec le module Congés, workflow de validation par période de paie, alertes d'oubli de saisie.
> **Hors périmètre (reporté au module Paie)** : Application des taux de majoration (HS_30, HS_50, NUIT, DIMANCHE, FERIE) en valeur monétaire, calcul IRSA/CNAPS/OSTIE, bulletin de paie. Le module Pointage **produit les données sources** (heures, statuts, indemnités) ; il ne calcule pas le brut/net.

---

## Table des Matières

1. [Principes Généraux](#1-principes-généraux)
2. [Modèle de Données](#2-modèle-de-données)
3. [Règles de Gestion](#3-règles-de-gestion)
4. [Statuts de la Journée de Pointage](#4-statuts-de-la-journée-de-pointage)
5. [Workflow & Circuit de Validation](#5-workflow--circuit-de-validation)
6. [Automatisations & Schedulers](#6-automatisations--schedulers)
7. [Notifications](#7-notifications)
8. [Sortie vers la Paie](#8-sortie-vers-la-paie)
9. [Architecture Technique](#9-architecture-technique)
10. [API REST — Endpoints](#10-api-rest--endpoints)
11. [Points Encore à Trancher](#11-points-encore-à-trancher)

---

## 1. Principes Généraux

- **Pas d'import automatique** de l'extrait clocker. L'agent de pointage (ou le DAF) **saisit** manuellement, par employé et par jour, dans une interface dédiée. On élimine ainsi le fichier Excel dispersé, source d'erreurs.
- **Saisie non bloquante** : un oubli de pointage n'empêche pas le fonctionnement du système. Une notification de rappel est générée, et une correction reste possible (par l'agent de pointage tant que la période n'est pas validée, ou par le DAF/Admin à tout moment avant clôture).
- **Un jour = 4 pointages maximum** : Entrée Matin, Sortie Matin, Entrée Après-midi, Sortie Après-midi.
- **Compensation hebdomadaire** : la base légale est 40h/semaine (`SEUIL_HEURES_SEMAINE`). Un retard ou une sortie anticipée un jour donné peut être compensé par une heure supplémentaire faite un autre jour **de la même semaine**. Ce n'est qu'au-delà des 40h hebdomadaires que le surplus devient HS payée.
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

VUES (agrégats en lecture seule)
    ├── v_pointage_jour_cpl            -- Pointage enrichi des libellés
    ├── v_pointage_semaine             -- Agrégat hebdomadaire (retard vs HS, 40h)
    └── v_synthese_pointage_mensuelle  -- Colonnes prêtes pour la Paie (NBABS, HS, DEPLACEMENT, REPAS...)
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

### 2.7 Vue `v_pointage_semaine`

Agrège par employé et semaine ISO (au sein d'une période de paie) pour appliquer la compensation retard/HS sur base 40h :

```sql
CREATE VIEW v_pointage_semaine AS
SELECT
    id_employe,
    date_trunc('week', date_pointage)::date AS debut_semaine,
    SUM(total_heures) AS total_heures_semaine,
    SUM(minutes_retard)/60.0 AS heures_retard_semaine,
    GREATEST(SUM(total_heures) - 40, 0) AS heures_hs_brutes,
    GREATEST(40 - SUM(total_heures) - (SUM(minutes_retard)/60.0), 0) AS heures_deficit_non_compense
FROM pointage_jour
WHERE id_statut_jour IN (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT')
GROUP BY id_employe, date_trunc('week', date_pointage);
```
> ⚠️ Voir §11 pour le cas des semaines à cheval sur deux périodes de paie.

### 2.8 Vue `v_synthese_pointage_mensuelle`

C'est **l'interface de sortie vers la Paie** — équivalent des colonnes NBABS/ABSENCES/HS/DEPLACEMENT/REPAS vues dans tes fichiers Excel :

```sql
CREATE VIEW v_synthese_pointage_mensuelle AS
SELECT
    p.id_employe,
    p.id_periode_paie,
    COUNT(*) FILTER (WHERE s.code = 'ABSENCE_NON_JUSTIFIEE') AS nb_jours_absence,
    COUNT(*) FILTER (WHERE s.code = 'FERIE_TRAVAILLE')       AS nb_jours_ferie_travaille,
    SUM(p.total_heures) FILTER (WHERE s.decompte_absence = FALSE) AS total_heures_travaillees,
    COALESCE(SUM(w.heures_hs_brutes), 0)                     AS heures_hs_mensuelles,
    COUNT(*) FILTER (WHERE p.indemnite_deplacement_recue)     AS nb_jours_deplacement,
    COUNT(*) FILTER (WHERE p.indemnite_repas_recue)           AS nb_jours_repas,
    COALESCE(SUM(p.montant_repas), 0)                         AS montant_repas_total
FROM pointage_jour p
JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour
LEFT JOIN v_pointage_semaine w ON w.id_employe = p.id_employe
    AND w.debut_semaine = date_trunc('week', p.date_pointage)
GROUP BY p.id_employe, p.id_periode_paie;
```

---

## 3. Règles de Gestion

### RG-PTG-01 : Saisie Quotidienne à 4 Créneaux avec Valeurs par Défaut de Pause

Si l'employé ne pointe pas sa sortie matin / entrée après-midi (pause), le système reconstitue la pause manquante à partir de `ref_site.heure_fin_matin` et de la durée minimale de pause déduite de `ref_site.horaire_continu` — **pas de valeur hardcodée par site nommé** :
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

### RG-PTG-04 : Compensation Hebdomadaire (Base 40h)

- La compensation retard/départ anticipé ↔ heure supplémentaire se calcule **par semaine**, jamais par jour ni par mois.
- Si `total_heures_semaine > 40h` : le surplus est HS (payée, sauf Cadre — RG-PTG-05).
- Si `total_heures_semaine < 40h` malgré les heures faites en plus certains jours : le déficit restant est traité comme absence partielle valorisée (proratisée au taux horaire `SME_COURANT / DIVISEUR_HORAIRE_ABSENCE`, cf `parametre_global`).

### RG-PTG-05 : Cadres et Heures Supplémentaires

Aligné avec `ref_categorie_professionnelle.code = 'C'` (déjà défini dans le module Contrat) :
- La HS **payée** est toujours forcée à 0 pour un Cadre, quel que soit le total réel.
- Le surplus d'heures reste néanmoins utilisé en interne (dans `v_pointage_semaine`) pour compenser un éventuel déficit de la même semaine — seul l'excédent au-delà de la compensation est ignoré (non payé, non reporté).

### RG-PTG-06 : Postes Autonomes (Dispense de Pointage)

- `ref_fonction.dispense_pointage = TRUE` marque les postes qui n'ont pas d'obligation de pointer.
- Pour ces employés, la synthèse mensuelle applique un **forfait de 8h/jour** sur les jours ouvrés sans qu'aucune ligne `pointage_jour` ne soit nécessaire.
- Le DAF/Admin dispose d'une vue "présences non saisies — postes autonomes" avec rappel de notification, pour saisir a posteriori si besoin (notamment pour déclencher une indemnité).
- Ces employés **peuvent** pointer volontairement (ex : pour le 2ᵉ repas du soir) sans que ce soit obligatoire — le `pointage_jour` saisi prévaut alors sur le forfait pour le calcul des indemnités de ce jour-là uniquement.

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

---

## 4. Statuts de la Journée de Pointage

| Code | Nécessite saisie d'heures | Génère une absence | Origine |
|---|---|---|---|
| `PRESENT` | Oui | Non | Saisie manuelle |
| `ABSENCE_NON_JUSTIFIEE` | Non | Oui | Saisie manuelle ou déduit d'un oubli non régularisé |
| `CONGE` | Non | Non | **Matérialisé automatiquement** à la validation d'une `demande_conge` (voir §2.6 bis) |
| `FERIE_TRAVAILLE` | Oui | Non | Saisie manuelle sur un jour de `ref_calendrier_ferie` |
| `FERIE_CHOME` | Non | Non | Matérialisé automatiquement depuis `ref_calendrier_ferie` |
| `DISPENSE_POINTAGE` | Non | Non | Auto, pour `ref_fonction.dispense_pointage = TRUE` |

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
PERIODE_PAIE : BROUILLON → EN_COURS → A_VALIDER → VALIDEE → CLOTUREE
                  │            │           │          │
                  │            │           │          └── Consommée par la Paie (verrou définitif)
                  │            │           └── Contrôle DAF (RBAC : droit_validation sur module POINTAGE)
                  │            └── Saisie quotidienne par l'agent de pointage (droit_ecriture, pas de droit_validation — cf RBAC déjà en place)
                  └── Ouverture de la période (auto ou manuelle par Admin)
```

Ceci correspond exactement à la matrice RBAC déjà définie dans `26-07-29_init.sql` : `ROLE_AGENT_POINTAGE` a `droit_lecture`/`droit_ecriture` sur `POINTAGE` mais **pas** `droit_validation` — seul `ROLE_ADMIN` peut faire passer la période en `VALIDEE`/`CLOTUREE`.

---

## 6. Automatisations & Schedulers

Aligné sur les schedulers existants (`00:00`, `00:05`, `01:00`, `02:00`) :

| Heure | Scheduler | Fréquence | Action |
|---|---|---|---|
| `00:10:00` | `PointageRappelScheduler` | Quotidien | Détecte les employés sans `pointage_jour` la veille (hors `DISPENSE_POINTAGE`, `CONGE`, `FERIE_CHOME`), génère `POINTAGE_MANQUANT` |
| `00:15:00` | `PeriodePaieScheduler` | Quotidien | Fait passer `periode_paie` de `EN_COURS` à `A_VALIDER` quand `date_fin` est dépassée, notifie le DAF |
| `00:20:00` | `FerieChomeScheduler` | Quotidien | Pré-crée les lignes `pointage_jour` en statut `FERIE_CHOME` pour les jours fériés à venir (basé sur `site_calendrier_ferie`) |

---

## 7. Notifications

Nouveaux types à ajouter à `notification_rh.type_alerte` (extension de la contrainte `chk_notif_type` existante) :

| Type d'Alerte | Priorité | Déclencheur |
|---|---|---|
| `POINTAGE_MANQUANT` | 🟠 HAUTE | Aucune saisie la veille pour un employé actif non dispensé |
| `PERIODE_POINTAGE_A_VALIDER` | 🟠 HAUTE | `periode_paie` passe en `A_VALIDER` |
| `PERIODE_POINTAGE_VALIDEE` | 🔵 INFO | Confirmation au DAF/agent après validation |
| `RETARD_RECURRENT` | 🟡 NORMALE | ≥ 3 retards non compensés dans la semaine pour un même employé (optionnel, alerte managériale) |

---

## 8. Sortie vers la Paie

Le module Pointage **ne calcule pas de brut/net**. Il fournit à la Paie, via `v_synthese_pointage_mensuelle` (par employé × période de paie) :

| Colonne fournie | Équivalent dans tes fichiers Excel |
|---|---|
| `nb_jours_absence` | `NBABS` |
| `total_heures_travaillees` / `heures_hs_mensuelles` | Base du calcul `ABSENCES` et `HS` |
| `nb_jours_deplacement` × montant journalier proratisé | `DEPLACEMENT` |
| `montant_repas_total` | `REPAS` |
| (via module Congés) `NBCONGE` / `TCONGE` | Restent portés par `demande_conge` / `mouvement_solde_conge`, pas dupliqués ici |

La Paie devra encore : appliquer les taux de majoration HS (`ref_taux_majoration`), calculer `BRUTE`, `CNAPS/OSTIE/OMSI`, `IRSA`, `RED ENFT`, `AVANCE`, etc. — c'est un module à part entière, non couvert ici.

---

## 9. Architecture Technique

### 9.1 Classes Java Principales (convention `ClassMere` / `controler()`)

| Classe | Table | Rôle |
|---|---|---|
| `PointageJour.java` | `pointage_jour` | Entité + calculs RG-PTG-01 à 04 dans `controler()` avant `save()` |
| `PeriodePaie.java` | `periode_paie` | Cycle de vie BROUILLON→CLOTUREE |
| `EmployeIndemniteDerogation.java` | `employe_indemnite_derogation` | CRUD dérogations niveau 2 |
| `RefGrilleArrondiPointage.java` | `ref_grille_arrondi_pointage` | Référentiel configurable |
| `VPointageSemaine.java` | `v_pointage_semaine` | Vue lecture seule |
| `VSynthesePointageMensuelle.java` | `v_synthese_pointage_mensuelle` | Vue lecture seule, consommée par Paie |
| `PointageRappelScheduler.java` | — | Scheduler d'alerte oubli de saisie |
| `PeriodePaieScheduler.java` | — | Transition automatique des statuts de période |

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

| Contrôleur | URL de Base | Rôle |
|---|---|---|
| `PointageJourController` | `/api/pointage/jours` | CRUD saisie quotidienne |
| `PeriodePaieController` | `/api/pointage/periodes` | Cycle de vie des périodes |
| `PointageSyntheseController` | `/api/pointage/synthese` | Vues agrégées (semaine, mois) |
| `IndemniteDerogationController` | `/api/pointage/derogations` | Gestion des dérogations individuelles |

---

## 10. API REST — Endpoints

Base URL : `/api/pointage`

| Méthode | URL | Description |
|---|---|---|
| `GET` | `/jours` | Liste des pointages (filtres employé/site/période/statut) |
| `GET` | `/jours/{id}` | Détail d'un pointage |
| `POST` | `/jours` | Créer/saisir un pointage du jour |
| `PUT` | `/jours/{id}` | Corriger un pointage (si période non verrouillée) |
| `GET` | `/jours/manquants` | Liste des employés sans saisie sur une date donnée |
| `GET` | `/periodes` | Liste des périodes de paie |
| `POST` | `/periodes` | Créer une nouvelle période |
| `PUT` | `/periodes/{id}/valider` | BROUILLON/EN_COURS → VALIDEE |
| `PUT` | `/periodes/{id}/cloturer` | VALIDEE → CLOTUREE |
| `GET` | `/synthese/semaine` | `v_pointage_semaine` filtrée |
| `GET` | `/synthese/mensuelle` | `v_synthese_pointage_mensuelle` filtrée (pour Paie) |
| `GET` | `/derogations` | Liste des dérogations d'indemnité |
| `POST` | `/derogations` | Créer une dérogation individuelle |

---

## 11. Points Encore à Trancher

**Décisions déjà validées avec toi** (pour mémoire, ne sont plus ouvertes) : pause par défaut dérivée de `horaire_continu`, plafond d'arrivée simplifié (une seule règle, pas de paliers), suppression de `MISSION_EXTERIEURE`, matérialisation des lignes `CONGE` (avec traçabilité `id_demande_conge`/`genere_automatiquement`), "permission d'absence" traitée comme absence partielle via le mécanisme retard/déficit hebdomadaire existant (RG-PTG-03/04, pas de nouveau statut), `periode_paie` unique partagée, dérogations Déplacement à deux mécanismes (ponctuel/permanent), 2ᵉ repas basé sur l'heure de sortie réelle, verrouillage `CLOTUREE` strict et définitif.

Points encore réellement ouverts :

1. **Semaine à cheval sur 2 périodes de paie** : si une semaine ISO commence dans la période de décembre et finit en janvier, sur quelle période comptabilise-t-on la compensation HS/retard ? Proposition (non encore validée) : rattacher la semaine à la période de paie qui contient le **plus de jours** de cette semaine.
2. **Demi-journée de congé annuel payé** (poser 0,5 jour de solde CP pour un après-midi, différent de la "permission" déjà tranchée) : est-ce un vrai cas d'usage chez GALAXAM ? Si oui, ça nécessite d'étendre `demande_conge` (hors périmètre Pointage).
3. **2ᵉ repas et forfait autonome** : si un poste autonome (`dispense_pointage`) pointe volontairement seulement pour capter le 2ᵉ repas du soir, doit-on valoriser ses heures réelles ce jour-là (au lieu du forfait 8h), ou seulement l'indemnité change et les heures restent forfaitaires ?
4. **Colonnes exactes de `v_synthese_pointage_mensuelle`** : à ajuster une fois le module Paie démarré, pour être sûr de fournir exactement ce dont il a besoin (probablement des colonnes supplémentaires par type d'indemnité et par tranche de majoration HS).

---

*Document généré pour mémoire de fin d'études — GALAXAM SIRH 2026*
