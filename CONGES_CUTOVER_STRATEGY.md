# STRATÉGIE DE REPRISE DES CONGÉS SANS HISTORIQUE COMPLET (CUTOVER) — GALAXAM SIRH

> **Document de Référence** : Spécification de l'Architecture de Cutover sans fabrication de faux passé.  
> **Exigence Fondamentale** : Le SIRH doit pouvoir commencer à gérer un salarié à n'importe quelle date sans nécessiter son historique complet de paie et de congés antérieur, sans inventer de faux bulletins ni de faux mouvements.

---

## 1. Modèle Conceptuel : Le Minimum d'État Suffisant

Le système repose sur la séparation stricte et hermétique de trois dimensions indépendantes :

```text
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                                 LEAVE OPENING STATE (T)                                 │
├────────────────────────────────┬───────────────────────────┬────────────────────────────┤
│     A. STOCK CONSOMMABLE       │  B. ACCUMULATEURS CYCLE   │   C. TCONGE APPLICABLE     │
├────────────────────────────────┼───────────────────────────┼────────────────────────────┤
│ • soldeJoursDisponibles        │ • cumulDroitsOuverture    │ • tauxJournalierApplicable │
│   (Ex: 14,50 j)                │   (Ex: 22,4575 j)         │   (Ex: 8 669,52 Ar/j)      │
│                                │ • cumulValoOuverture      │                            │
│                                │   (Ex: 240 092,52 Ar)     │                            │
│                                │ • debutPeriodeReference   │                            │
│                                │ • finPeriodeReference     │                            │
├────────────────────────────────┼───────────────────────────┼────────────────────────────┤
│ Finalité :                     │ Finalité :                │ Finalité :                 │
│ Déterminer si le salarié peut  │ Calculer le futur TCONGE  │ Indemniser les congés pris │
│ poser un congé aujourd'hui     │ lors de la clôture du     │ dans le cycle courant et   │
│                                │ cycle de référence        │ valoriser le STC           │
└────────────────────────────────┴───────────────────────────┴────────────────────────────┘
```

### Règle Cardinale de Découplage (Démontrée par l'Audit KAJY)
1. **La prise d'un congé décrémente le stock consommable (`soldeJoursDisponibles`)**.
2. **La prise d'un congé ne décrémente JAMAIS les accumulateurs du cycle (`cumulDroitsCycle`, `cumulValoCycle`)**.
   - Dans le fichier Excel `OHATRA KAJY DROITS CONGES` (Feuille1), lorsque Havana prend 15 jours de congés au Mois 11, ces 15 jours sont indemnisés au taux hérité ($8\,669,52\text{ Ar}$), mais les colonnes $U$ (cumul droits) et $V$ (cumul valorisation) continuent leur accumulation croissante brute ($+2,5\text{ j}$ et $+25\,414\text{ Ar}$).
   - **Conclusion** : Le cumul de valorisation du cycle n'est pas une "valeur financière résiduelle du stock consommable", mais une assiette de calcul de la moyenne annuelle pondérée pour l'année suivante.

---

## 2. Données Minimales Nécessaires à la Date de Cutover $T$

Pour garantir la continuité opérationnelle complète sans régression :

| Donnée | Type | Obligatoire | Rôle & Usage |
| :--- | :---: | :---: | :--- |
| `id_employe` | INT | OUI | Identifiant du salarié |
| `date_cutover` | DATE | OUI | Date d'arrêté de la reprise (ex: 30/09/2026) |
| `solde_jours_ouverture` | NUMERIC(5,2) | OUI | Solde disponible à consommer immédiatement |
| `taux_journalier_applicable` | NUMERIC(12,2) | OUI | TCONGE pour valoriser les congés pris post-cutover |
| `date_debut_reference` | DATE | OUI | Date de début du cycle de 12 mois en cours |
| `date_fin_reference` | DATE | AUTO | **Calculée automatiquement par le backend (`ReferencePeriodPolicy`)** : `date_debut.plusMonths(12).minusDays(1)`. L'utilisateur ne la saisit pas librement. |
| `cumul_droits_ouverture` | NUMERIC(8,4) | CONDITIONNEL | Droits déjà acquis dans le cycle avant cutover (NULL si inconnu) |
| `cumul_valo_ouverture` | NUMERIC(12,2) | CONDITIONNEL | Valeur déjà acquise dans le cycle avant cutover (NULL si inconnu) |
| `source_reprise` | VARCHAR | OUI | `EXCEL_KAJY`, `MIGRATION_INITIALE`, `AUDIT_RH` |
| `commentaire` | TEXT | OUI | Traçabilité de l'arrêté (qui, quand, pourquoi) |

---

## 3. Modes de Reprise Supportés

### Mode A : `FULL_HISTORY` (Historique Complet Disponible)
- Cas où toutes les paies et pointages passés sont déjà présents ou migrés mois par mois.
- Reconstruction native par le moteur.

### Mode B : `AGGREGATED_OPENING` (Agrégats Connus — Scénario Privilégié)
- L'entreprise ne souhaite pas saisir le détail des 9 mois précédents, mais la DRH fournit la feuille de suivi KAJY arrêtée au 30/09/2026.
- Données fournies :
  - `solde_jours` $= 14,50\text{ j}$
  - `taux_applicable` $= 8\,669,52\text{ Ar/j}$
  - `date_debut_reference` $= 01/01/2026$ (date de fin $= 31/12/2026$ calculée par policy)
  - `cumul_droits_ouverture` $= 22,4575\text{ j}$
  - `cumul_valo_ouverture` $= 240\,092,52\text{ Ar}$
- **Résultat** : Continuité mathématique absolue. À la clôture du Mois 12, le SIRH calcule le même $TCONGE = 10\,828,49\text{ Ar/j}$ que si les 12 mois avaient été saisis.

### Mode C : `MINIMAL_OPENING` (Reprise Minimale — Salarié sans Données de Cycle Antérieur)
- Seuls le solde consommable et le TCONGE applicable sont connus à la date de cutover.
- **Distinction Fondamentale** :
  - `openingCumulDroits` = `UNKNOWN` (`NULL`)
  - `openingCumulValo` = `UNKNOWN` (`NULL`)
  - **MAIS** `postCutoverCumulDroits` et `postCutoverCumulValo` sont **connus et enregistrés rigoureusement** par le SIRH dès les paies suivantes.
- **Résultat opérationnel immédiat** :
  - Le salarié peut demander des congés.
  - Le solde est débité correctement.
  - La paie applique l'indemnité au taux connu.
  - L'acquisition mensuelle future alimente le solde et les cumuls post-cutover.
- **Résultat à la clôture du cycle N** : Le système refuse d'inventer un faux taux avec des zéros (`CYCLE_ACCUMULATORS_UNKNOWN`). Il bascule soit sur une reconduction explicite du taux actuel, soit sur un arbitrage RH tracé.
- **Autonomie au Cycle N+1** : Dès le cycle suivant (N+1), le cycle est 100% natif SIRH et le nouveau TCONGE se calcule automatiquement sans aucune dépendance au cutover passé !

### Mode D : `NEW_HIRE` (Nouveau Salarié Post-Cutover)
- Embauché après la date de cutover.
- Démarrage naturel à zéro sans opening snapshot.

---

## 4. Algorithme d'Initialisation (Zéro Faux Bulletin)

Lors de la soumission d'une reprise de solde :

```text
1. Transaction BDD Unique
2. Enregistrement du Solde Consommable :
   INSERT INTO mouvement_solde_conge (
       id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, created_by
   ) VALUES (
       :idEmp, 'AJUSTEMENT', :soldeJours, :dateCutover, 
       '[REPRISE_CUTOVER] Arrêté au ' || :dateCutover || ' - ' || :commentaire, :userId
   );

3. Enregistrement du Taux Applicable As-Of :
   INSERT INTO conge_taux_applicable (
       id_employe, taux_journalier, date_effet, date_fin, source_provenance, commentaire, cree_par
   ) VALUES (
       :idEmp, :tauxApplicable, :dateCutover, NULL, 'REPRISE_CUTOVER', :commentaire, :userId
   );

4. Enregistrement de la Période de Référence & Opening Accumulators :
   INSERT INTO conge_periode_reference (
       id_employe, date_debut, date_fin, statut, mode_reprise, date_cutover,
       cumul_droits_ouverture, cumul_valorisation_ouverture, created_by
   ) VALUES (
       :idEmp, :dateDebutRef, :dateFinRef, 'OUVERTE', :modeReprise, :dateCutover,
       :cumulDroitsOuv, :cumulValoOuv, :userId
   );
```

**Preuve d'intégrité** : 0 ligne insérée dans `bulletin_paie`, 0 fausse ligne dans `pointage_jour`, 0 ligne inventée dans `conge_journal_droit`.

---

## 5. Continuité Mathématique Démontrée

Le test de caractérisation [`CongeCutoverContinuityUnitTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/characterization/CongeCutoverContinuityUnitTest.java) démontre la propriété fondamentale :

$$\text{Simulation 12 Mois Complète} \equiv \text{Opening State (Mois 9)} + \sum_{m=10}^{12} \text{Événements Post-Cutover}$$

### Chiffres Validés par Test Automatisé
- **Opening au Mois 9** : Droits $= 22,4575\text{ j}$, Valorisation $= 240\,092,52\text{ Ar}$.
- **Événements calculés en SIRH (Mois 10 à 12)** :
  - Mois 10 : Droits $= 2,5000\text{ j}$, Valo $= 29\,433,02\text{ Ar}$
  - Mois 11 : Droits $= 2,5000\text{ j}$, Valo $= 25\,414,00\text{ Ar}$
  - Mois 12 : Droits $= 2,4992\text{ j}$, Valo $= 29\,445,83\text{ Ar}$
- **Cumul Final au Mois 12** :
  - Droits $= 22,4575 + 7,4992 = \mathbf{29,9567\text{ j}}$ (Concordance exacte à $10^{-4}$)
  - Valorisation $= 240\,092,52 + 84\,292,85 = \mathbf{324\,385,38\text{ Ar}}$ (Concordance à $\le 0,02\text{ Ar}$)
  - **TCONGE Clôture** $= 324\,385,3763 / 29,9566667 = \mathbf{10\,828,49\text{ Ar/jour}}$ (Identique au centime près à la cellule W14 de l'Excel).

---

## 6. Comportement Face aux Données Manquantes

### Règle Anti-Invention Silencieuse
1. **Cas : Solde Connu, TCONGE Inconnu** :
   - Le système refuse le calcul du bulletin de paie et lève une exception bloquante :
     `LEAVE_RATE_MISSING : Aucun taux TCONGE configuré pour l'employé [Matricule] au [Date]. Un arbitrage RH est requis.`
   - Le fallback technique silencieux $SalaireBase / 30$ est **définitivement proscrit** pour les salariés historiques repris sans taux explicite.
2. **Cas : Solde Connu, TCONGE Connu, Cumuls Cycle Inconnus (Mode C)** :
   - Le système autorise toutes les opérations courantes (prises, absences, paies).
   - En fin de cycle, si les cumuls d'ouverture sont `NULL`, la clôture automatique refuse de diviser par zéro ou de calculer sur une base partielle et lève :
     `CYCLE_ACCUMULATORS_UNKNOWN : Clôture impossible sans données antérieures. Veuillez reconduire le taux existant ou saisir un taux de clôture validé.`

---

## 7. Cycle de Vie du TCONGE et Transition N $\rightarrow$ N+1

```text
Année N (Cycle en cours)
├── TCONGE Actif = 8 669,52 Ar/j (Hérité de N-1 ou Cutover)
└── Accumulation de la référence :
    ├── Opening State : 22,4575 j | 240 092,52 Ar
    └── Mois 10-12 post-cutover : +7,4992 j | +84 292,85 Ar
        │
        ▼ (31 Décembre Année N)
CLÔTURE DU CYCLE N :
Nouveau TCONGE calculé = 324 385,38 / 29,9567 = 10 828,49 Ar/j
        │
        ▼
ENREGISTREMENT DU NOUVEAU TAUX DANS conge_taux_applicable :
• date_effet = 01/01/N+1
• date_fin = NULL
• source_provenance = 'CALCUL_PERIODE_REFERENCE'
• id_periode_reference = Ref_Année_N
        │
        ▼
OUVERTURE DU CYCLE N+1 :
• Période [01/01/N+1, 31/12/N+1]
• cumul_droits_ouverture = 0
• cumul_valo_ouverture = 0
• Les congés pris en Année N+1 utilisent automatiquement 10 828,49 Ar/j !
```

---

## 8. Modifications BDD Nécessaires

Pour intégrer ces états d'ouverture sans créer de table superflue :

```sql
-- 1. Extension de conge_periode_reference pour accueillir le Cutover Snapshot
ALTER TABLE conge_periode_reference 
    ADD COLUMN IF NOT EXISTS mode_reprise VARCHAR(30) DEFAULT 'FULL_HISTORY',
    ADD COLUMN IF NOT EXISTS date_cutover DATE,
    ADD COLUMN IF NOT EXISTS cumul_droits_ouverture NUMERIC(8,4),
    ADD COLUMN IF NOT EXISTS cumul_valorisation_ouverture NUMERIC(12,2);

-- Contrainte de cohérence des modes de reprise
ALTER TABLE conge_periode_reference
    DROP CONSTRAINT IF EXISTS chk_mode_reprise;

ALTER TABLE conge_periode_reference
    ADD CONSTRAINT chk_mode_reprise CHECK (
        mode_reprise IN ('FULL_HISTORY', 'AGGREGATED_OPENING', 'MINIMAL_OPENING', 'NEW_HIRE')
    );

-- 2. Ajout de la valeur REPRISE_CUTOVER dans la provenance des taux
ALTER TABLE conge_taux_applicable
    DROP CONSTRAINT IF EXISTS chk_source_provenance;

ALTER TABLE conge_taux_applicable
    ADD CONSTRAINT chk_source_provenance CHECK (
        source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30', 'REPRISE_CUTOVER')
    );
```

---

## 9. API Backend d'Initialisation

### Endpoint : `POST /api/conges/soldes/cutover-opening`

```json
{
  "idEmploye": 61,
  "dateCutover": "2026-09-30",
  "modeReprise": "AGGREGATED_OPENING",
  "soldeJours": 14.50,
  "tauxJournalierApplicable": 8669.52,
  "dateDebutReference": "2026-01-01",
  "dateFinReference": "2026-12-31",
  "cumulDroitsOuverture": 22.4575,
  "cumulValorisationOuverture": 240092.52,
  "commentaire": "Reprise KAJY Havana arrêtée à fin Septembre 2026"
}
```

---

## 10. UX Frontend d'Initialisation

Le composant [InitialisationSoldeModal.jsx](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/frontend/src/components/conges/InitialisationSoldeModal.jsx) est enrichi d'un sélecteur de mode :
- **Mode Simple (Reprise Minimale)** : Saisie du solde disponible et du TCONGE applicable.
- **Mode Avancé (Agrégats du cycle de référence)** : Dépliage optionnel permettant de renseigner les cumuls déjà acquis dans l'exercice pour préserver le calcul exact du prochain TCONGE.

---

## 11. Stratégie de Rollback

Si une erreur de saisie sur l'état d'ouverture est constatée :
1. Une annulation atomique supprime le mouvement `AJUSTEMENT` lié au cutover, archive la période de référence et désactive le taux applicable.
2. Le système interdit le rollback si des bulletins de paie postérieurs au cutover ont déjà été validés et gelés (immutabilité comptable).
