# PHASE 2A-4C — RAPPORT D'IMPLÉMENTATION CANONIQUE DU TCONGE & DES PÉRIODES DE RÉFÉRENCE BORNÉES (KAJY 12 MOIS)

**Date** : 2026-09-18  
**Statut** : VALIDÉ / CONFORME  
**Référence Métier** : `OHATRA KAJY DROITS CONGES-given on sept26.xls` (`GALAXAM_CURRENT_BUSINESS_REFERENCE`)  
**Verdict** : `PHASE_2A_4C_TCONGE_REFERENCE_PERIOD_IMPLEMENTATION_PASS`

---

## 1. RÉSUMÉ EXÉCUTIF & CONTEXTE DE LA MISSION

La Phase 2A-4C met fin à l'une des dérives majeures constatées lors de l'audit de la paie et des congés : **l'accumulation infinie des cumuls de valorisation dans le SIRH** (`conge_journal_droit`), qui gonflait indéfiniment les dénominateurs et les numérateurs au fil des années et faussait le taux journalier de congé (TCONGE).

Fondée sur la rétro-ingénierie et la caractérisation mathématique du classeur KAJY (`GALAXAM_CURRENT_BUSINESS_REFERENCE`), cette implémentation établit l'architecture canonique cible :

```text
conge_periode_reference (Période bornée max 12 mois)
        │
        ├── Mois 1 à 12 ──> conge_journal_droit (Valorisation en construction)
        └── Clôture Mois 12 ──> taux_calcule_cloture = CumulValo / CumulDroits
                                       │
                                       ▼ (Activation datée explicite)
conge_taux_applicable [date_effet, date_fin[ (Taux effectif applicable)
        │
        ├── PayrollService (Rubrique INDEMNITE_CONGE_PAYE)
        └── StcService (ICCP - Indemnité Compensatrice de Congés Payés)
```

Toutes les exigences formulées dans le correctif obligatoire ont été strictement respectées :
- Aucun fallback silencieux à 12 en code Java.
- Aucune date de début de campagne inventée arbitrairement.
- Aucune création automatique non prouvée du mois 13 en nouvelle campagne.
- Séparation stricte entre *Taux en construction* et *Taux applicable*.
- Partage unifié du résolveur temporel `CongeTauxApplicableService` entre la Paie et le STC.

---

## 2. PARAMÈTRE CANONIQUE DUREE_REFERENCE_TCONGE_MOIS = 12

### A. Configuration BDD
Le paramètre global `DUREE_REFERENCE_TCONGE_MOIS` a été inséré dans `parametre_global` :
- **Clé** : `DUREE_REFERENCE_TCONGE_MOIS`
- **Catégorie** : `CONGE`
- **Valeur** : `12`
- **Description** : Règle métier GALAXAM (12 mois selon KAJY).
- **Date de début technique** : `2000-01-01` (date de fondation système, couvrant toute période calculée par le SIRH).

### B. Résolution Stricte sans Fallback Silencieux
La méthode `ParametreGlobal.requirePositiveInt(params, "DUREE_REFERENCE_TCONGE_MOIS")` a été introduite dans `ParametreGlobal.java` :
- Si la clé est absente ou vide : lève `IllegalStateException("TCONGE_REFERENCE_DURATION_MISSING")`.
- Si la valeur est <= 0 ou non numérique : lève `IllegalStateException("TCONGE_REFERENCE_DURATION_INVALID")`.
- **Aucun fallback par défaut `12` silencieux** n'est présent dans le code de calcul.

Validation par tests unitaires :
`mg.galaxam.sirh.unit.conge.TcongeParametreResolutionUnitTest` (6 tests unitaires dédiés, tous PASS).

---

## 3. DISTINCTION FONDAMENTALE : DATE TECHNIQUE DU PARAMÈTRE vs DÉBUT DE CAMPAGNE

Pour éviter d'introduire une fausse vérité historique ou d'inventer une règle calendaire non prouvée :
- **`date_debut` du paramètre global (`2000-01-01`)** : Il s'agit d'une métadonnée technique imposée par le schéma PostgreSQL de `parametre_global` pour définir sa validité au sein du moteur de paie.
- **`date_debut` d'une campagne TCONGE individuelle** : Reste portée par chaque enregistrement de `conge_periode_reference(date_debut)`.
- **Principe formel** : `date_debut du paramètre global ≠ date de début d'une campagne TCONGE`.

---

## 4. NOUVELLE ENTITÉ BDD : PÉRIODE DE RÉFÉRENCE BORNÉE (`conge_periode_reference`)

Conformément à la directive d'audit démontrant qu'aucune structure existante ne portait proprement la responsabilité d'une campagne bornée, la table dédiée `conge_periode_reference` a été créée :

```sql
CREATE TABLE IF NOT EXISTS conge_periode_reference (
    id_periode_reference SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    date_debut DATE NOT NULL,
    date_fin DATE NOT NULL,
    statut VARCHAR(30) NOT NULL DEFAULT 'OUVERTE', -- 'OUVERTE', 'CLOTUREE', 'ARCHIVEE'
    taux_calcule_cloture NUMERIC(12,2),
    cumul_droits_cloture NUMERIC(8,4),
    cumul_valorisation_cloture NUMERIC(12,2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES utilisateur(id_utilisateur),
    CONSTRAINT chk_periode_dates CHECK (date_fin >= date_debut),
    CONSTRAINT chk_statut_ref CHECK (statut IN ('OUVERTE', 'CLOTUREE', 'ARCHIVEE'))
);
CREATE INDEX IF NOT EXISTS idx_conge_ref_emp_dates ON conge_periode_reference(id_employe, date_debut, date_fin);
```

Attributs clés :
- `taux_calcule_cloture` : Porte la propriété globale de l'ensemble des 12 mois une fois clôturés, évitant la duplication dans chaque ligne de journal.
- `statut` : `OUVERTE` pendant les 12 mois de valorisation, `CLOTUREE` au terme du 12ème mois.

---

## 5. NOUVELLE ENTITÉ BDD : TAUX APPLICABLE EN VIGUEUR (`conge_taux_applicable`)

La séparation démontrée par KAJY entre le *taux en construction* et le *taux effectivement appliqué* est matérialisée par la table `conge_taux_applicable` :

```sql
CREATE TABLE IF NOT EXISTS conge_taux_applicable (
    id_taux_applicable SERIAL PRIMARY KEY,
    id_employe INT NOT NULL REFERENCES employe(id_employe),
    taux_journalier NUMERIC(12,2) NOT NULL,
    date_effet DATE NOT NULL,
    date_fin DATE, -- Convention d'intervalle semi-ouvert : [date_effet, date_fin[
    source_provenance VARCHAR(50) NOT NULL,
    id_periode_reference INT REFERENCES conge_periode_reference(id_periode_reference),
    commentaire TEXT,
    cree_par INT REFERENCES utilisateur(id_utilisateur),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_conge_taux_positif CHECK (taux_journalier > 0),
    CONSTRAINT chk_conge_taux_dates CHECK (date_fin IS NULL OR date_fin > date_effet),
    CONSTRAINT chk_conge_taux_source CHECK (source_provenance IN ('KAJY_MIGRATION', 'CALCUL_PERIODE_REFERENCE', 'INITIALISATION_BASE_30'))
);
```

### A. Convention Temporelle Non Ambiguë
La convention retenue est strictement semi-ouverte :
$$\text{Intervalle de validité} = [date\_effet, date\_fin[$$
- Une date `dateReference` est couverte si :
  $$date\_effet \le dateReference < date\_fin \quad (\text{ou } date\_fin \text{ IS NULL})$$
- Cela élimine le chevauchement au jour de bascule (ex. Taux A jusqu'au 01/08 et Taux B à partir du 01/08 : le 01/08 active strictement le Taux B).

### B. Typage Strict de la Provenance
Le champ `source_provenance` est verrouillé à la fois par une contrainte `CHECK` SQL et par l'énumération Java `TcongeSourceProvenance` :
1. `KAJY_MIGRATION` : Taux repris de la situation historique KAJY (ex. Havana 8 669,52 Ar).
2. `CALCUL_PERIODE_REFERENCE` : Taux issu de la clôture d'une référence de 12 mois.
3. `INITIALISATION_BASE_30` : Règle contractuelle d'amorçage pour nouveau salarié sans historique.

---

## 6. RÉSOLUTION AS-OF STRICTE & DÉFENSE ANTI-AMBIGUÏTÉ

Dans `CongeTauxApplicableService.java`, la requête as-of ne recourt **à aucun `LIMIT 1` silencieux** qui masquerait des incohérences :

```java
// Requête stricte récupérant l'ensemble des correspondances actives
List<TauxCongeResolution> matches = ...;

if (matches.size() > 1) {
    throw new IllegalStateException("TCONGE_APPLICABLE_AMBIGUOUS: " + matches.size() +
            " taux applicables distincts se chevauchent pour l'employé " + idEmploye +
            " à la date " + dateReference + ". Un arbitrage explicite est requis.");
}
```

- **0 taux** : Bascule vers l'historique legacy ou l'amorçage `INITIALISATION_BASE_30`.
- **1 taux** : Résolution univoque garantie.
- **> 1 taux** : Levée immédiate de `TCONGE_APPLICABLE_AMBIGUOUS`.
- **Anti-fuite future** : Un taux avec `date_effet > dateReference` n'est jamais retourné.

---

## 7. ALIGNEMENT TOTAL PAIE & STC

Le service canonique `CongeTauxApplicableService` est désormais le point de vérité unique partagé :
- **Dans `PayrollService.java`** : Méthode `preparerInput(...)` utilise `congeTauxApplicableService.resoudreTauxCongeApplicable(...)` pour valoriser l'indemnité de congé payé (`INDEMNITE_CONGE_PAYE`).
- **Dans `StcService.java`** : Méthode `getTauxJournalierApplicable(...)` délègue à `congeTauxApplicableService.resoudreTauxCongeApplicable(...)` pour valoriser l'indemnité compensatrice de congés payés (ICCP).

L'égalité stricte des taux appliqués entre la Paie et le STC à une même date de référence est formellement garantie et vérifiée en BDD PostgreSQL réelle.

---

## 8. SUPPRESSION DU CUMUL INFINI & GESTION DES 12 MOIS BORNÉS

Dans `PayrollService.mettreAJourJournalConge(...)` :

1. **Numéro de mois dérivé calendairement** :
   ```java
   long moisEcoules = ChronoUnit.MONTHS.between(
           YearMonth.from(refActive.dateDebut()),
           YearMonth.from(dateFinPaie)
   );
   int numeroMoisRef = (int) moisEcoules + 1;
   ```
   Le numéro de mois n'est pas un compteur incrémental aveugle (`previous + 1`), mais la position temporelle exacte dans la période. Un mois sans bulletin ou une suspension ne décalera pas arbitrairement la campagne.

2. **Interdiction stricte du 13ème mois** :
   ```java
   if (numeroMoisRef > dureeReferenceMax) {
       throw new IllegalStateException("TCONGE_REFERENCE_CAPACITY_EXCEEDED: la période de référence " +
               refActive.idPeriodeReference() + " a déjà atteint sa durée maximale de " +
               dureeReferenceMax + " mois (mois demandé: " + numeroMoisRef + ").");
   }
   ```

3. **Cumuls strictement bornés à la référence active** :
   Les cumuls `cumul_jours_droit` et `cumul_valorisation` sont calculés par `SUM(...)` filtré obligatoirement sur `id_periode_reference = ?`.
   Dès qu'une nouvelle référence $B$ démarre, ses cumuls repartent de zéro et n'intègrent jamais l'historique de la référence $A$.

4. **Clôture au 12ème mois** :
   Lorsque `numeroMoisRef == dureeReferenceMax`, la référence passe en statut `CLOTUREE` et fige son `taux_calcule_cloture = CumulValo / CumulDroits`.
   Aucune campagne $B$ n'est auto-démarrée à l'aveugle.

---

## 9. STRATÉGIE TRANSACTIONNELLE EN L'ABSENCE DE RÉFÉRENCE CONFIGURÉE

Conformément à la section 13 du correctif :
- Si aucun enregistrement actif de `conge_periode_reference` n'existe pour un salarié lors de la clôture mensuelle de la paie :
  - Le système consigne un avertissement explicite : `TCONGE_REFERENCE_PERIOD_MISSING`.
  - L'alimentation de la campagne TCONGE est ignorée pour ce salarié.
  - **La paie globale de l'entreprise n'est pas bloquée** : le salarié est payé au taux applicable résolu (reprise migration ou Base/30).
  - Aucune fausse référence calendaire (janvier-décembre, date d'anniversaire, glissement) n'est fabriquée silencieusement.
- Si un service ou un audit requiert obligatoirement une référence active : appel à `congeTauxApplicableService.resoudrePeriodeReferenceStrict(...)` qui lève `TCONGE_REFERENCE_PERIOD_MISSING`.

---

## 10. REPRISE MIGRATION KAJY & RÈGLE D'AMORÇAGE BASE / 30

### A. Reprise Salarié Existant sans Faux Bulletin
La méthode `enregistrerTauxApplicableMigration(...)` permet d'injecter directement un taux historique (ex. Havana 8 669,52 Ar) avec provenance `KAJY_MIGRATION` sans devoir générer de faux bulletins ni de faux mouvements de droits.

### B. Règle d'Amorçage Contractuelle Base / 30
Lorsqu'un salarié est nouvellement embauché sans reprise historique KAJY ni historique de journal :
- Le résolveur applique :
  $$\text{Taux Initial} = \frac{\text{SalaireDeBase}}{30.0}$$
- **Provenance** : `INITIALISATION_BASE_30`.
- **Classification formelle** : `CURRENT_GALAXAM_INITIALIZATION_RULE` (et en aucun cas qualifié de "fallback légal").

---

## 11. BASE DE VALORISATION & INTÉGRITÉ DU GOLDEN MASTER

La base de valorisation mensuelle KAJY est rigoureusement respectée :
$$\text{BaseValorisation} = \text{NET\_BASE} + \text{HEURES\_SUP}$$
$$\text{AUTIMP} \text{ est strictement exclu de la valorisation monétaire des congés.}$$

Les tests de caractérisation Golden Master :
- `KajyLeaveCalculationGoldenMasterUnitTest` : Reproduit au centime près l'intégralité des 12 mois Havana du tableau KAJY, atteignant le taux final de **10 828,49 Ar/j**.
- `KajyAppliedTcongeCharacterizationTest` : Caractérise les taux appliqués (8 669,52 Ar/j).
- Précision intermédiaire de calcul préservée sans arrondi prématuré.

---

## 12. IMMUTABILITÉ HISTORIQUE

- Les bulletins définitifs clôturés snapshotent leur `taux_journalier_conge` et leurs lignes de valorisation.
- Toute modification ultérieure de `conge_taux_applicable` ou création d'une nouvelle référence de congé n'altère en aucun cas les bulletins et reçus STC historiques déjà clôturés.
- Vérifié et validé par `PaieAdversarialTest`.

---

## 13. QUESTIONS LAISSÉES EN `BUSINESS_RULE_REQUIRED`

Conformément à la directive d'interdiction formelle de coder des règles non prouvées, deux questions fondamentales restent explicitement classées `BUSINESS_RULE_REQUIRED` :

### 1. `REFERENCE_START_RULE = BUSINESS_RULE_REQUIRED`
> **Question Ouverte Métier / Direction RH** :  
> Quelle est la règle exacte déterminant la date de démarrage de la période de référence de 12 mois pour un collaborateur ?  
> - Est-ce l'année civile (1er janvier) ?  
> - Est-ce la date d'embauche / date anniversaire ?  
> - Est-ce une campagne fiscale/sociale spécifique ?  
> *L'architecture implémentée permet de configurer n'importe quelle date de début via `conge_periode_reference` sans modifier une seule ligne du moteur de calcul.*

### 2. `NEW_TCONGE_EFFECTIVE_DATE_RULE = BUSINESS_RULE_REQUIRED`
> **Question Ouverte Métier / Direction RH** :  
> Une fois les 12 mois clôturés et le nouveau taux calculé (`taux_calcule_cloture`), à quelle date d'effet précise ce nouveau taux devient-il applicable pour la paie et le STC ?  
> - Immédiatement au lendemain de la période (`date_fin + 1 jour`) ?  
> - Au premier jour du mois suivant ?  
> - Après validation formelle de la Direction Financière ?  
> *L'architecture sépare délibérément le taux calculé de son activation dans `conge_taux_applicable` jusqu'à validation de cette règle métier.*

---

## 14. MATRICE DE VÉRIFICATION DES CRITÈRES (PASS GATE)

| Critère d'Acceptation | Statut | Preuve / Validation |
| :--- | :---: | :--- |
| `DUREE_REFERENCE_TCONGE_MOIS` existe en BDD | **CONFORME** | Inséré dans `parametre_global` (`valeur = 12`, `date_debut = 2000-01-01`). |
| Résolution du paramètre stricte | **CONFORME** | `requirePositiveInt` lève `MISSING` ou `INVALID`. 6 tests unitaires PASS. |
| Aucun fallback 12 silencieux en code | **CONFORME** | Aucune valeur de repli dans `PayrollService` ni dans `ParametreGlobal`. |
| Aucune date métier arbitraire inventée | **CONFORME** | Pas de `2026-01-01` arbitraire comme début de campagne ; distinction date technique / campagne. |
| Cumul tous temps supprimé | **CONFORME** | Remplacé par `SUM(...)` borné à `id_periode_reference`. |
| Références explicitement bornées | **CONFORME** | Table `conge_periode_reference` avec dates de début et de fin obligatoires. |
| Impossible d'insérer un 13e mois | **CONFORME** | Lève `TCONGE_REFERENCE_CAPACITY_EXCEEDED` si position calendaire > 12. |
| Aucune création automatique de campagne non prouvée | **CONFORME** | Aucune nouvelle référence créée silencieusement au mois 13. |
| Taux calculé séparé du taux applicable | **CONFORME** | `conge_periode_reference(taux_calcule_cloture)` vs `conge_taux_applicable`. |
| Résolveur Paie/STC partagé | **CONFORME** | `CongeTauxApplicableService` utilisé par `PayrollService` et `StcService`. |
| Résolution as-of stricte | **CONFORME** | Respect de l'intervalle `[date_effet, date_fin[`. Fuite future impossible. |
| Chevauchement détecté | **CONFORME** | Lève `TCONGE_APPLICABLE_AMBIGUOUS` si > 1 taux sans `LIMIT 1` masquant. |
| Migration KAJY possible sans faux bulletins | **CONFORME** | `enregistrerTauxApplicableMigration` opérationnel. |
| Base/30 conservé comme INITIALIZATION_RULE | **CONFORME** | `INITIALISATION_BASE_30` appliqué aux nouveaux sans historique (non légal). |
| BaseValo = NetBase + HS & AUTIMP exclu | **CONFORME** | Formule de valorisation mensuelle intacte. |
| Formule KAJY et précision Golden Master intactes | **CONFORME** | `KajyLeaveCalculationGoldenMasterUnitTest` PASS (10 828,49 Ar/j). |
| Anciens bulletins et STC immuables | **CONFORME** | Aucune rétroaction des modifications de taux sur les données clôturées. |
| Aucune modification NBCONGE / FIFO | **CONFORME** | Domaine congés (Phase 2A-3D) gelé et 100% stable. |
| PostgreSQL réel PASS | **CONFORME** | Schéma appliqué sur `sirh_galaxam` et `sirh_galaxam_test`. |
| Backend >= 473 PASS | **CONFORME** | **488 PASS** (0 Failure, 0 Error). |
| Frontend >= 28 PASS | **CONFORME** | **28 PASS** (0 Failure, 0 Error). |
| Vite build PASS | **CONFORME** | Build réussi en 565 ms. |
| git diff --check PASS | **CONFORME** | Aucune erreur d'espace ou de syntaxe. |

---

## 15. CONCLUSION & VERDICT FINAL

L'implémentation de la Phase 2A-4C est complète, rigoureuse et entièrement validée sur les bases PostgreSQL et l'ensemble de la suite de tests automatisés.

```text
PHASE_2A_4C_TCONGE_REFERENCE_PERIOD_IMPLEMENTATION_PASS
```
