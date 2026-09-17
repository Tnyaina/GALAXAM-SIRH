# PHASE 1C — CONTRE-AUDIT FINAL DE LA MATRICE LÉGALE, ARCHITECTURALE ET TEMPORELLE
**Date** : 2026-09-17  
**Projet** : GALAXAM-SIRH — Socle Pointage & Paie  
**Auteur** : Antigravity (Auditeur & Architecte Principal)  
**Baseline** : Backend 338/338 PASS | Frontend 16/16 PASS | Build frontend PASS | PostgreSQL opérationnel | Dette pré-1C : 0  

---

## 1. PRINCIPE CARDINAL : SÉPARATION DES RESPONSABILITÉS

```text
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 1B décide COMBIEN de minutes sont :                              │
│ REGULAR / OVERTIME_30 / OVERTIME_50.                                   │
│ (Seule propriétaire du calcul sur l'objectif réel du tronçon).         │
├────────────────────────────────────────────────────────────────────────┤
│ Phase 1C décide OÙ ces minutes se trouvent                             │
│ sur la timeline et quelles qualifications temporelles                 │
│ NUIT / DIMANCHE / FERIE elles portent.                                 │
│ (Interdiction absolue de recalculer les seuils ou volumes HS).         │
├────────────────────────────────────────────────────────────────────────┤
│ La Paie décide COMBIEN elles valent monétairement.                     │
│ (Application des taux datés du référentiel ref_taux_majoration).       │
└────────────────────────────────────────────────────────────────────────┘
```

### 1.1. Interdiction absolue de recalculer les seuils HS dans Phase 1C
L'objectif réel d'un tronçon n'est **jamais une constante universelle de 2400 minutes (40 heures)**. Il varie en fonction de :
- La frontière de période de paie 25/26 (semaines tronquées de 1 à 4 jours ouvrables) ;
- Les jours fériés chômés payés (déduction de 8h par férié) ;
- Les congés payés validés (déduction de 8h par jour de CP) ;
- Les entrées et sorties en cours de période ;
- Les horaires et calendriers spécifiques de site.

**Règle d'or** : Phase 1C ne contient aucun `if (workedMinutes <= 2400)` ni aucun seuil fixe. Elle reçoit les volumes qualifiés `regularMinutes`, `overtime30Minutes`, `overtime50Minutes` issus de la Phase 1B et les projette fidèlement sur les buckets temporels.

---

## 2. MODÈLE CANONIQUE À DEUX DIMENSIONS ORTHOGONALES

### Dimension 1 : Qualification Heures Supplémentaires (Source : Phase 1B)
```java
public enum OvertimeTier {
    REGULAR,        // Minutes qualifiées dans l'objectif réel du tronçon par Phase 1B
    OVERTIME_30,    // Minutes qualifiées HS30 par Phase 1B (1ère tranche HS du tronçon)
    OVERTIME_50     // Minutes qualifiées HS50 par Phase 1B (2e tranche HS du tronçon)
}
```

### Dimension 2 : Qualifications Temporelles Spéciales (Source : Phase 1C)
```java
public enum DaySpecialType {
    STANDARD,       // Jour ouvrable ordinaire (Lun-Ven)
    SATURDAY,       // Samedi (jour ouvrable ordinaire, majoration 0%)
    SUNDAY,         // Dimanche (repos hebdomadaire, majoration légale +40%)
    PUBLIC_HOLIDAY  // Jour férié chômé légal (majoration configurée +100%)
}

public enum NightWorkType {
    NONE,           // Hors plage nocturne [22:00, 05:00]
    HABITUEL,       // Décret 72-226 Art. 3 : majoration légale +30%
    OCCASIONNEL     // Décret 72-226 Art. 3 : majoration légale +50%
}
```

Toute unité de travail (`QualifiedTimeBucket`) porte simultanément son `OvertimeTier` et ses qualifications temporelles. **Une heure de nuit ou de dimanche n'est donc pas par défaut une heure supplémentaire.**

---

## 3. PROJECTION CHRONOLOGIQUE STRICTE SANS RECALCUL

La projection chronologique consiste exclusivement à positionner les volumes déterminés par la Phase 1B sur la frise temporelle réelle des buckets :

1. **Entrées fournies par la Phase 1B pour le tronçon** :
   - $M_{\text{regular}}$ (ex. 1920 minutes)
   - $M_{\text{hs30}}$ (ex. 180 minutes)
   - $M_{\text{hs50}}$ (ex. 0 minutes)
2. **Mécanisme d'affectation** :
   - Les buckets ordonnés chronologiquement sont alimentés :
     - Les $M_{\text{regular}}$ premières minutes reçoivent `OvertimeTier.REGULAR` ;
     - Les $M_{\text{hs30}}$ minutes suivantes reçoivent `OvertimeTier.OVERTIME_30` ;
     - Les $M_{\text{hs50}}$ minutes suivantes reçoivent `OvertimeTier.OVERTIME_50`.
   - Si un bucket chevauche une frontière de bascule entre deux tiers, il est scindé exactement à la minute de transition.
3. **Garantie mathématique** :
   $$\sum \text{minutes}(\text{buckets avec } \text{tier } T) = \text{Volume de } T \text{ déterminé par Phase 1B}$$
   Aucune minute HS n'est créée ni détruite par la Phase 1C.

---

## 4. AUDIT DES VACATIONS TRAVERSANT MINUIT (`CROSS_MIDNIGHT_ATTENDANCE_AUDIT`)

### 4.1. État actuel frontend (`PointageGridTable.jsx`)
- Saisie sur une seule ligne journalière (`heureEntreeMatin`, `heureSortieAm`).
- Si `heureSortie < heureEntree` (ex: 21:00 $\to$ 05:00), aucun indicateur visuel n'indique explicitement que la sortie a lieu le lendemain.
- **Amélioration UI requise** : Détecter `heureSortie < heureEntree` et afficher un badge automatique :  
  `Sortie : 18/09/2026 à 05:00 (+1 jour) — Durée brute : 8h00`.

### 4.2. État actuel backend (`DailyAttendanceCalculator.java`)
- La durée brute est calculée par `Duration.between(start, end).toMinutes() + 24*60` (8h00 = 480 min).
- **Faiblesse critique détectée** : Les 480 minutes sont rattachées en bloc à la date de début (`date_pointage`), sans découpage à minuit.
  - *Samedi $\to$ Dimanche* : Les heures après minuit ne sont pas qualifiées Dimanche.
  - *Dimanche $\to$ Lundi* : Les heures après minuit contaminent le Lundi en Dimanche.
  - *Frontière 25 $\to$ 26* : Les heures après minuit restent dans la période M au lieu de basculer dans la période M+1.

### 4.3. Représentation en Base de Données
- Table `pointage_jour` :
  Colonnes actuelles : `date_pointage DATE`, `heure_entree_matin TIME`, `heure_sortie_matin TIME`, etc.
  La vacation nocturne est stockée sur la ligne du jour de début.

### 4.4. Modèle Cible Backend : `QualifiedTimeBucket`
```java
public record QualifiedTimeBucket(
    LocalDateTime start,
    LocalDateTime end,
    long minutes,
    OvertimeTier overtimeTier,
    DaySpecialType dayType,
    NightWorkType nightType,
    int payrollPeriodId
) {
    public QualifiedTimeBucket {
        if (end.isBefore(start)) throw new IllegalArgumentException("end must be >= start");
        if (minutes < 0) throw new IllegalArgumentException("minutes must be >= 0");
    }
}
```

### 4.5. Découpage automatique aux frontières
Pour une vacation unique `2026-09-17T21:00` $\to$ `2026-09-18T05:00` :
1. `21:00 - 22:00` (60 min) : `STANDARD` (17/09), `DAY`
2. `22:00 - 00:00` (120 min) : `STANDARD` (17/09), `NIGHT`
3. `00:00 - 05:00` (300 min) : qualification du 18/09, `NIGHT`

**Invariant strict** : $60 + 120 + 300 = 480\text{ minutes} = 8\text{h00}$.

### 4.6. Interaction avec la frontière 25 $\to$ 26 de période de paie
Pour une vacation `25/MM 21:00` $\to$ `26/MM 05:00` :
- `25/MM 21:00 - 26/MM 00:00` (180 min) $\to$ Imputé dans la **Période M** (qualifications HS issues du tronçon de la période M).
- `26/MM 00:00 - 26/MM 05:00` (300 min) $\to$ Imputé dans la **Période M+1** (qualifications HS issues du tronçon de la période M+1).
- **Règle absolue** : Zéro transport de surplus, déficit ou compteur HS d'une période à l'autre.

---

## 5. SCÉNARIOS DE TEST CRITIQUES OBLIGATOIRES

### Test Critique 1 : Objectif Réel Réduit (Preuve du Non-Recalcul à 2400)
- **Données Phase 1B** :
  - Semaine avec 1 jour férié chômé : Objectif réel = $4 \times 8\text{h} = 32\text{h} = 1920\text{ min}$.
  - Travail réel effectué = $35\text{h} = 2100\text{ min}$.
  - Résultat Phase 1B : `regularMinutes = 1920`, `overtime30Minutes = 180`, `overtime50Minutes = 0`.
- **Validation Phase 1C** :
  - Phase 1C doit affecter exactement $1920\text{ min}$ en `REGULAR` et $180\text{ min}$ en `OVERTIME_30`.
  - **Preuve** : Bien que $2100 < 2400$, Phase 1C ne doit PAS classer les 2100 minutes en `REGULAR`. Elle respecte scrupuleusement la décision de la Phase 1B.

### Test Critique 2 : Objectif Réduit + Nuit + HS30
- Un tronçon à objectif réduit atteint sa bascule HS pendant une tranche de nuit (ex: à 23h30).
- Le bucket nocturne `22:00 - 00:00` est scindé :
  - `22:00 - 23:30` (90 min) : `REGULAR` + `NIGHT`
  - `23:30 - 00:00` (30 min) : `OVERTIME_30` + `NIGHT`
- Projection exacte sans perte de qualification.

### Test Critique 3 : Frontière 25/26 et Indépendance de Période
- Vacation `25/09 21:00` $\to$ `26/09 05:00`.
- Période M (25/09) : l'employé est en heures normales $\to$ le bucket 21h-00h reçoit `REGULAR`.
- Période M+1 (26/09) : nouvelle période, nouveau tronçon $\to$ le bucket 00h-05h reçoit les qualifications du tronçon de la période M+1.
- Aucune contamination inter-périodes.

---

## 6. STATUTS NORMATIFS ET COMPORTEMENT PAR DÉFAUT DES MAJORATIONS SPÉCIALES

1. **Non-cumul textuel des majorations spéciales (Décret n° 72-226 Art. 3)** :
   - *« Les majorations fixées au présent article ne s'ajoutent pas entre elles ; elles se cumulent éventuellement par addition avec les majorations pour heures supplémentaires. »*
   - Départage entre plusieurs sujétions spéciales (ex: Dimanche + Nuit, Férié + Nuit) :
     - **Doctrine de faveur** : application de la majoration la plus élevée (`MAX_PREMIUM`).
     - **Option d'entreprise plus favorable** : cumul conventionnel additif.
     - **Statut** : Classé sous `BUSINESS_RULE_REQUIRED` en l'absence d'arbitrage écrit formel de GALAXAM. L'option hypothétique n'est pas estampillée `GALAXAM_CONFIRMED`.
2. **Cumul Heures Supplémentaires + Majoration Spéciale** :
   - Textuellement établi et confirmé : cumul additif obligatoire (`LEGAL_CONFIRMED`).
   - $\text{Facteur} = 1.00 + \text{MajorationHS} + \text{MajorationSpécialeRetenue}$.

---

## 7. QUESTIONS D'ARBITRAGE RH POUR GALAXAM

1. **Qualification Nuit Habituelle vs Occasionnelle** :  
   > Chez GALAXAM, tout travail de nuit (22h00–05h00) est-il rémunéré au taux unique de **30 %** (considéré comme nuit habituelle selon la configuration actuelle `NUIT = 0.30`), ou le SIRH doit-il distinguer les nuits occasionnelles (**50 %**) ?
2. **Collision entre majorations spéciales (Dimanche + Nuit, Férié + Nuit)** :  
   > En application de l'interdiction de cumul du Décret n° 72-226 Art. 3 (*« ne s'ajoutent pas entre elles »*), appliquez-vous la règle doctrinale de faveur **`MAX_PREMIUM`** ($\max(40\%, 30\%) = 40\%$ pour Dimanche + Nuit) ou une **règle d'entreprise plus favorable** cumulant la nuit avec la sujétion de jour ($40\% + 30\% = 70\%$) ?

---

## 8. BASELINE TECHNIQUE VÉRIFIÉE

```text
Backend : 338/338 PASS
Frontend : 16/16 PASS
Build Frontend : PASS
git diff --check : PASS
Modifications de code : 0
```

---

## 9. VERDICT FINAL DU CONTRE-AUDIT

```text
============================================================
              VERDICT DU CONTRE-AUDIT FINAL
           READY_FOR_PHASE_1C_IMPLEMENTATION
============================================================
```

Les responsabilités sont étanches :
- **Phase 1B** décide **COMBIEN** de minutes sont `REGULAR` / `OVERTIME_30` / `OVERTIME_50`.
- **Phase 1C** décide **OÙ** ces minutes se trouvent sur la timeline et **QUELLES** qualifications temporelles (`NUIT`, `DIMANCHE`, `FERIE`) elles portent.
- **La Paie** décide **COMBIEN** elles valent via `ref_taux_majoration`.
