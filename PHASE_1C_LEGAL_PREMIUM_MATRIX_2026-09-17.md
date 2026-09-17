# PHASE 1C — MATRICE LÉGALE & MÉTIER DES MAJORATIONS ET CUMULS (CONTRAT DE RESPONSABILITÉ CANONIQUE)
**Date de référence** : 2026-09-17  
**Projet** : GALAXAM-SIRH — Module Pointage & Paie  
**Statut** : RÉFÉRENCE NORMATIVE VALIDÉE  

---

## 1. SOURCES JURIDIQUES APPLICABLES (MADAGASCAR)

| Règle | Texte officiel | Article | Valeur légale de référence | Statut de validation | Preuve textuelle & Observations |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Durée légale du travail** | Loi n° 2024-014 du 14 août 2024 | Art. 126 | 40 heures / semaine (173,33 h/mois) | `LEGAL_CONFIRMED` | Code du Travail 2024 Art. 126 |
| **Heures supplémentaires Tranche 1** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 4 | **+30 %** (8 premières heures HS) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 4 al. 1 |
| **Heures supplémentaires Tranche 2** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 4 | **+50 %** (au-delà de 8 heures HS) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 4 al. 2 |
| **Travail de nuit habituel** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | **+30 %** (plage 22h00–05h00) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 3 al. 1 & Loi 2024-014 Art. 132 |
| **Travail de nuit occasionnel** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | **+50 %** (plage 22h00–05h00) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 3 al. 1 |
| **Repos hebdomadaire (Dimanche)** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | **+40 %** pour travail de jour le dimanche | `LEGAL_CONFIRMED` | Décret 72-226 Art. 3 al. 2 |
| **Jour férié chômé travaillé** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | **+50 %** (référence légale) / **+100 %** (GALAXAM) | `CONFIG_DRIVEN` | Décret 72-226 Art. 3 al. 3 (+50%). `ref_taux_majoration` configuré à 1.00 (+100%). |
| **Samedi travaillé** | Décret n° 68-172 / Accord GALAXAM | Règle interne | **0 %** (aucune majoration spécifique samedi) | `GALAXAM_CONFIRMED` | Jour ouvrable ordinaire, participe à l'objectif du tronçon |
| **Non-cumul des majorations spéciales** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | Non-cumul textuel (*« ne s'ajoutent pas entre elles »*) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 3 in fine. Départage `MAX_PREMIUM` : doctrine principe de faveur (`BUSINESS_RULE_REQUIRED`). |
| **Cumul HS + Majorations spéciales** | Décret n° 68-172 mod. Décret n° 72-226 | Art. 3 | **Cumul additif** (*« se cumulent par addition »*) | `LEGAL_CONFIRMED` | Décret 72-226 Art. 3 in fine |

---

## 2. PRINCIPE CARDINAL DE SÉPARATION DES RESPONSABILITÉS

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

---

## 3. MODÈLE TEMPOREL À DEUX DIMENSIONS ORTHOGONALES

### Dimension 1 : Qualification Heures Supplémentaires (Fournie par Phase 1B)
```java
public enum OvertimeTier {
    REGULAR,        // Volume d'heures qualifié REGULAR par Phase 1B (dans l'objectif réel du tronçon)
    OVERTIME_30,    // Volume d'heures qualifié HS30 par Phase 1B (1ère tranche HS du tronçon)
    OVERTIME_50     // Volume d'heures qualifié HS50 par Phase 1B (2e tranche HS du tronçon)
}
```

### Dimension 2 : Qualifications Temporelles Spéciales (Résolues par Phase 1C)
```java
public enum DaySpecialType {
    STANDARD,       // Jour ouvrable ordinaire (Lun-Ven)
    SATURDAY,       // Samedi (jour ouvrable ordinaire, majoration 0%)
    SUNDAY,         // Dimanche (repos hebdomadaire, majoration légale +40%)
    PUBLIC_HOLIDAY  // Jour férié chômé légal (majoration configurée +100%)
}

public enum NightWorkType {
    NONE,           // Hors plage nocturne [22:00, 05:00]
    HABITUEL,       // Dans plage [22:00, 05:00], poste habituel : +30%
    OCCASIONNEL     // Dans plage [22:00, 05:00], vacation exceptionnelle : +50%
}
```

---

## 4. NORMALISATION SÉMANTIQUE : TAUX vs FACTEUR TOTAL

1. **`ref_taux_majoration.taux` stocke strictement l'incrément décimal** :
   - `HS_30` $\to$ `0.30` ($+30\%$)
   - `HS_50` $\to$ `0.50` ($+50\%$)
   - `NUIT` (ou `NUIT_HABITUELLE`) $\to$ `0.30` ($+30\%$)
   - `NUIT_OCCASIONNELLE` $\to$ `0.50` ($+50\%$)
   - `DIMANCHE` $\to$ `0.40` ($+40\%$)
   - `FERIE` $\to$ `1.00` ($+100\%$ configuré chez GALAXAM)
2. **Règle anti-double paiement de la base horaire** :
   - Pour les heures qualifiées `REGULAR` dans le planning normal : l'heure de base est **déjà rémunérée** dans le `SALAIRE_BASE` mensuel. Seul l'incrément spécial applicable est versé en sus (ex. $+0.30 \times T$ pour du travail de nuit habituel dans les heures normales).
   - Pour les heures qualifiées `OVERTIME_30` ou `OVERTIME_50` (ou repos hebdomadaire travaillé hors contrat) : l'employé perçoit l'heure de base ($1.00$) PLUS l'incrément HS PLUS l'incrément spécial retenu :
     $$\text{Facteur Total} = 1.00 + \text{Incrément}_{\text{HS}} + \text{Incrément}_{\text{Spécial}}$$
   - **Interdiction absolue** : Ne jamais multiplier deux facteurs totaux ($1.30 \times 1.40$) ni additionner deux bases horaires ($1.30 + 1.40 = 2.70$).

---

## 5. MATRICE CANONIQUE ORTHOGONALE (2D)

> **Important** : Les mentions `REGULAR`, `OVERTIME_30` et `OVERTIME_50` ci-dessous désignent les volumes **strictement qualifiés en amont par la Phase 1B**. Phase 1C n'applique aucun seuil fixe de 40h / 2400 minutes au runtime.

| Situation | Type Jour | Nuit | OvertimeTier (Phase 1B) | Majoration Spéciale | Majoration HS | Règle de cumul | Formule monétaire finale | Statut |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- | :--- | :--- |
| **Standard Normal** | `STANDARD` | `NONE` | `REGULAR` | 0.00 | 0.00 | Heures du tronçon normal | Inclus dans $\text{SALAIRE\_BASE}$ | `LEGAL_CONFIRMED` |
| **Standard + HS30** | `STANDARD` | `NONE` | `OVERTIME_30` | 0.00 | +0.30 | Non applicable | $\text{TauxHoraire} \times (1 + 0.30) = 1.30 \times T$ | `LEGAL_CONFIRMED` |
| **Standard + HS50** | `STANDARD` | `NONE` | `OVERTIME_50` | 0.00 | +0.50 | Non applicable | $\text{TauxHoraire} \times (1 + 0.50) = 1.50 \times T$ | `LEGAL_CONFIRMED` |
| **Samedi Normal** | `SATURDAY` | `NONE` | `REGULAR` | 0.00 | 0.00 | Compense objectif tronçon | Inclus dans $\text{SALAIRE\_BASE}$ | `GALAXAM_CONFIRMED` |
| **Samedi + HS30** | `SATURDAY` | `NONE` | `OVERTIME_30` | 0.00 | +0.30 | Cumul HS standard | $\text{TauxHoraire} \times (1 + 0.30) = 1.30 \times T$ | `GALAXAM_CONFIRMED` |
| **Samedi + HS50** | `SATURDAY` | `NONE` | `OVERTIME_50` | 0.00 | +0.50 | Cumul HS standard | $\text{TauxHoraire} \times (1 + 0.50) = 1.50 \times T$ | `GALAXAM_CONFIRMED` |
| **Dimanche Normal** | `SUNDAY` | `NONE` | `REGULAR` | +0.40 | 0.00 | Repos hebdo travaillé | $\text{TauxHoraire} \times (1 + 0.40) = 1.40 \times T$ | `LEGAL_CONFIRMED` |
| **Dimanche + HS30** | `SUNDAY` | `NONE` | `OVERTIME_30` | +0.40 | +0.30 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.30 + 0.40) = 1.70 \times T$ | `LEGAL_CONFIRMED` |
| **Dimanche + HS50** | `SUNDAY` | `NONE` | `OVERTIME_50` | +0.40 | +0.50 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.50 + 0.40) = 1.90 \times T$ | `LEGAL_CONFIRMED` |
| **Dimanche + Nuit Hab. Normal**| `SUNDAY` | `HABITUEL` | `REGULAR` | $\max(0.40, 0.30) = +0.40$ | 0.00 | Doctrine Faveur | $\text{TauxHoraire} \times (1 + 0.40) = 1.40 \times T$ | `BUSINESS_RULE_REQUIRED` |
| *(Option Plus Favorable)* | `SUNDAY` | `HABITUEL` | `REGULAR` | $+0.40 + 0.30 = +0.70$ | 0.00 | Cumul conventionnel | $\text{TauxHoraire} \times (1 + 0.40 + 0.30) = 1.70 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Dimanche + Nuit Hab. + HS30**| `SUNDAY` | `HABITUEL` | `OVERTIME_30` | +0.40 (Doctrine) | +0.30 | HS + MAX(Dim, Nuit) | $\text{TauxHoraire} \times (1 + 0.30 + 0.40) = 1.70 \times T$ | `BUSINESS_RULE_REQUIRED` |
| *(Option Plus Favorable)* | `SUNDAY` | `HABITUEL` | `OVERTIME_30` | +0.70 (Favorable) | +0.30 | HS + Dim + Nuit | $\text{TauxHoraire} \times (1 + 0.30 + 0.70) = 2.00 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Dimanche + Nuit Hab. + HS50**| `SUNDAY` | `HABITUEL` | `OVERTIME_50` | +0.40 (Doctrine) | +0.50 | HS + MAX(Dim, Nuit) | $\text{TauxHoraire} \times (1 + 0.50 + 0.40) = 1.90 \times T$ | `BUSINESS_RULE_REQUIRED` |
| *(Option Plus Favorable)* | `SUNDAY` | `HABITUEL` | `OVERTIME_50` | +0.70 (Favorable) | +0.50 | HS + Dim + Nuit | $\text{TauxHoraire} \times (1 + 0.50 + 0.70) = 2.20 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Dimanche + Nuit Occ. Normal**| `SUNDAY` | `OCCASIONNEL`| `REGULAR` | $\max(0.40, 0.50) = +0.50$ | 0.00 | Doctrine Faveur | $\text{TauxHoraire} \times (1 + 0.50) = 1.50 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Dimanche + Nuit Occ. + HS30**| `SUNDAY` | `OCCASIONNEL`| `OVERTIME_30` | +0.50 (Doctrine) | +0.30 | HS + MAX(Dim, Nuit) | $\text{TauxHoraire} \times (1 + 0.30 + 0.50) = 1.80 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Dimanche + Nuit Occ. + HS50**| `SUNDAY` | `OCCASIONNEL`| `OVERTIME_50` | +0.50 (Doctrine) | +0.50 | HS + MAX(Dim, Nuit) | $\text{TauxHoraire} \times (1 + 0.50 + 0.50) = 2.00 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Férié Normal** | `PUBLIC_HOLIDAY`| `NONE` | `REGULAR` | +1.00 (configuré) | 0.00 | Rémunération double | $\text{TauxHoraire} \times (1 + \text{tauxFerie}) = 2.00 \times T$ | `CONFIG_DRIVEN` |
| **Férié + HS30** | `PUBLIC_HOLIDAY`| `NONE` | `OVERTIME_30` | +1.00 (configuré) | +0.30 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.30 + \text{tauxFerie}) = 2.30 \times T$ | `CONFIG_DRIVEN` |
| **Férié + HS50** | `PUBLIC_HOLIDAY`| `NONE` | `OVERTIME_50` | +1.00 (configuré) | +0.50 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.50 + \text{tauxFerie}) = 2.50 \times T$ | `CONFIG_DRIVEN` |
| **Férié + Nuit Hab. Normal** | `PUBLIC_HOLIDAY`| `HABITUEL` | `REGULAR` | $\max(1.00, 0.30) = +1.00$ | 0.00 | Doctrine Faveur | $\text{TauxHoraire} \times (1 + \text{tauxFerie}) = 2.00 \times T$ | `CONFIG_DRIVEN` |
| *(Option Plus Favorable)* | `PUBLIC_HOLIDAY`| `HABITUEL` | `REGULAR` | $+1.00 + 0.30 = +1.30$ | 0.00 | Cumul conventionnel | $\text{TauxHoraire} \times (1 + \text{tauxFerie} + 0.30) = 2.30 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Férié + Nuit Hab. + HS30** | `PUBLIC_HOLIDAY`| `HABITUEL` | `OVERTIME_30` | +1.00 (Doctrine) | +0.30 | HS + MAX(Férié, Nuit) | $\text{TauxHoraire} \times (1 + 0.30 + \text{tauxFerie}) = 2.30 \times T$ | `CONFIG_DRIVEN` |
| *(Option Plus Favorable)* | `PUBLIC_HOLIDAY`| `HABITUEL` | `OVERTIME_30` | +1.30 (Favorable) | +0.30 | HS + Férié + Nuit | $\text{TauxHoraire} \times (1 + 0.30 + 1.30) = 2.60 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Férié + Nuit Hab. + HS50** | `PUBLIC_HOLIDAY`| `HABITUEL` | `OVERTIME_50` | +1.00 (Doctrine) | +0.50 | HS + MAX(Férié, Nuit) | $\text{TauxHoraire} \times (1 + 0.50 + \text{tauxFerie}) = 2.50 \times T$ | `CONFIG_DRIVEN` |
| *(Option Plus Favorable)* | `PUBLIC_HOLIDAY`| `HABITUEL` | `OVERTIME_50` | +1.30 (Favorable) | +0.50 | HS + Férié + Nuit | $\text{TauxHoraire} \times (1 + 0.50 + 1.30) = 2.80 \times T$ | `BUSINESS_RULE_REQUIRED` |
| **Nuit Hab. Normale** | `STANDARD` | `HABITUEL` | `REGULAR` | +0.30 (incrément seul)| 0.00 | Base déjà dans salaire | $\text{TauxHoraire} \times 0.30$ | `LEGAL_CONFIRMED` |
| **Nuit Hab. + HS30** | `STANDARD` | `HABITUEL` | `OVERTIME_30` | +0.30 | +0.30 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.30 + 0.30) = 1.60 \times T$ | `LEGAL_CONFIRMED` |
| **Nuit Hab. + HS50** | `STANDARD` | `HABITUEL` | `OVERTIME_50` | +0.30 | +0.50 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.50 + 0.30) = 1.80 \times T$ | `LEGAL_CONFIRMED` |
| **Nuit Occ. Normale** | `STANDARD` | `OCCASIONNEL`| `REGULAR` | +0.50 (incrément seul)| 0.00 | Base déjà dans salaire | $\text{TauxHoraire} \times 0.50$ | `LEGAL_CONFIRMED` |
| **Nuit Occ. + HS30** | `STANDARD` | `OCCASIONNEL`| `OVERTIME_30` | +0.50 | +0.30 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.30 + 0.50) = 1.80 \times T$ | `LEGAL_CONFIRMED` |
| **Nuit Occ. + HS50** | `STANDARD` | `OCCASIONNEL`| `OVERTIME_50` | +0.50 | +0.50 | Cumul additif Art. 3 | $\text{TauxHoraire} \times (1 + 0.50 + 0.50) = 2.00 \times T$ | `LEGAL_CONFIRMED` |
| **Férié tombant Dimanche Normal**| `PUBLIC_HOLIDAY`| `NONE` | `REGULAR` | Férié absorbe Dimanche | 0.00 | Doctrine Faveur | $\text{TauxHoraire} \times (1 + \text{tauxFerie}) = 2.00 \times T$ | `CONFIG_DRIVEN` |
| **Férié + Dimanche + HS30** | `PUBLIC_HOLIDAY`| `NONE` | `OVERTIME_30` | Férié absorbe Dimanche | +0.30 | HS + Férié | $\text{TauxHoraire} \times (1 + 0.30 + \text{tauxFerie}) = 2.30 \times T$ | `CONFIG_DRIVEN` |
| **Férié + Dimanche + HS50** | `PUBLIC_HOLIDAY`| `NONE` | `OVERTIME_50` | Férié absorbe Dimanche | +0.50 | HS + Férié | $\text{TauxHoraire} \times (1 + 0.50 + \text{tauxFerie}) = 2.50 \times T$ | `CONFIG_DRIVEN` |
| **Férié + Dimanche + Nuit Normal**| `PUBLIC_HOLIDAY`| `HABITUEL`| `REGULAR` | Férié absorbe Dimanche | 0.00 | Non-cumul [+ Nuit si fav] | $\text{TauxHoraire} \times (1 + \text{tauxFerie} [+ 0.30])$ | `CONFIG_DRIVEN` |
| **Férié + Dim + Nuit + HS30** | `PUBLIC_HOLIDAY`| `HABITUEL`| `OVERTIME_30` | Férié absorbe Dimanche | +0.30 | HS + Férié [+ Nuit si fav] | $\text{TauxHoraire} \times (1 + 0.30 + \text{tauxFerie} [+ 0.30])$ | `CONFIG_DRIVEN` |
| **Férié + Dim + Nuit + HS50** | `PUBLIC_HOLIDAY`| `HABITUEL`| `OVERTIME_50` | Férié absorbe Dimanche | +0.50 | HS + Férié [+ Nuit si fav] | $\text{TauxHoraire} \times (1 + 0.50 + \text{tauxFerie} [+ 0.30])$ | `CONFIG_DRIVEN` |

---

## 6. PROJECTION CHRONOLOGIQUE STRICTE SANS RECALCUL

1. **Entrées fournies par Phase 1B pour le tronçon** :
   - `regularMinutes`
   - `overtime30Minutes`
   - `overtime50Minutes`
2. **Algorithme de projection de Phase 1C** :
   - Les buckets du tronçon sont ordonnés chronologiquement : $B_1, B_2, \dots, B_n$.
   - Un curseur de distribution affecte les $M_{\text{regular}}$ premières minutes en `OvertimeTier.REGULAR`.
   - Les $M_{\text{hs30}}$ minutes suivantes reçoivent `OvertimeTier.OVERTIME_30`.
   - Les $M_{\text{hs50}}$ minutes suivantes reçoivent `OvertimeTier.OVERTIME_50`.
   - Si un bucket chevauche une limite de volume, il est scindé au point exact de transition.
   - **Interdiction absolue** : Phase 1C n'utilise jamais de constante comme 2400 minutes ou 40 heures.
