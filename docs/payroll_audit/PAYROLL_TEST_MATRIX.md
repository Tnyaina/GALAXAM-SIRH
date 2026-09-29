# Matrice Complète de Tests Métier et Techniques du Moteur de Paie GALAXAM

> **Document :** Livrable 9 — Matrice de Tests & Protocoles de Validation Déterministe  
> **Auteur :** Antigravity AI — Expert Assurance Qualité Logicielle & Paie SIRH  
> **Date :** Septembre 2026  
> **Statut :** Spécification de Test Validée  
> **Couverture requise :** 100% des règles métier, cas nominaux, cas limites (*edge cases*), résilience et immutabilité historique.

---

## 1. Cadre Méthodologique de Test

Le moteur de paie étant désormais un **noyau fonctionnel pur sans état** (`DeterministicPayrollEngine`), il peut être testé unitairement sans instancier de conteneur Spring, sans mocker de base de données relationnelle et sans latence d'exécution (moins d'une milliseconde par cas de test).

### Typologie des Tests Définis :
- **TU (Tests Unitaires de Domaine)** : Testent unitairement chaque calculateur pur (`BaseSalaryCalculatorTest`, `AbsenceCalculatorTest`, `OvertimePayCalculatorTest`, `SocialContributionCalculatorTest`, `TaxCalculatorTest`).
- **TI (Tests d'Intégration du Pipeline)** : Testent le pipeline complet de bout en bout (`DeterministicPayrollEngineTest`) avec assemblage des 14 assiettes et génération de l'arbre d'explicabilité.
- **TS (Tests de Scellement & Immutabilité Historique)** : Testent la reproductibilité exacte d'un bulletin historique face à une altération délibérée des paramètres courants en base de données.
- **TR (Tests de Résilience)** : Testent le rejet immédiat avec exception explicite en cas de configuration incomplète (absence de fallback silencieux).

---

## 2. Matrice Exhaustive des Cas de Test

| Réf. Test | Domaine / Brique | Scénario & Données en Entrée | Résultat Attendu & Vérification Formelle | Type | Criticité |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **NOM-01** | Salaire Nominal | Mensualisé standard : $S_{\text{contrat}} = 2.000.000\text{ Ar}$, 173,33 h effectuées, 0 absence, 0 HS, célibataire sans enfant, OSTIE (1%). | - Salaire Base = 2.000.000 Ar<br>- CNaPS Sal (1%) = 20.000 Ar<br>- OSTIE Sal (1%) = 20.000 Ar<br>- Brut Imposable = 1.960.000 Ar<br>- IRSA calculé selon barème<br>- Net à payer strictement conforme. | TU/TI | Bloquant |
| **NOM-02** | Salaire Nominal | Mensualisé haut salaire : $S_{\text{contrat}} = 6.000.000\text{ Ar}$, 2 enfants, OMSI (2%). | - CNaPS plafonnée à 20.000 Ar ($8 \times \text{SME}$)<br>- OMSI Sal (2%) = 120.000 Ar (déplafonné)<br>- Déduction 2 enfants = 4.000 Ar sur IRSA. | TU/TI | Bloquant |
| **PROR-01** | Prorata Temporis | Embauche le 16 d'un mois de 22 jours ouvrés (88 h réelles effectuées). $S_{\text{contrat}} = 1.733.300\text{ Ar}$ ($TH = 10.000\text{ Ar}$). | - Salaire période = $88 \times 10.000 = 880.000\text{ Ar}$<br>- Heures théoriques = 88 h<br>- Proratisation exacte sans régression. | TU | Majeur |
| **PROR-02** | Prorata Temporis | Démission le 10 du mois (40 h réelles effectuées). | - Salaire période = $40 \times 10.000 = 400.000\text{ Ar}$. | TU | Majeur |
| **ABS-01** | Absence & Retard | Retard de 1 minute. $TH = 10.000\text{ Ar}$. | - Retenue = $(1 \times 10.000) / 60 = 166,67\text{ Ar}$.<br>- Arrondi HALF_UP à 2 décimales. | TU | Mineur |
| **ABS-02** | Absence & Retard | Retard de 20 minutes (cas historique Galaxam). $TH = 10.000\text{ Ar}$. | - Calcul exact : $(20 \times 10.000) / 60 = 3.333,33\text{ Ar}$.<br>- Vérification de l'absence de troncature à 0.33 h ($3.300\text{ Ar}$). | TU | Majeur |
| **ABS-03** | Absence & Retard | Absence injustifiée de 1 jour (8 h). $TH = 10.000\text{ Ar}$. | - Retenue = $8 \times 10.000 = 80.000\text{ Ar}$. | TU | Bloquant |
| **ABS-04** | Absence & Retard | Absence injustifiée de 3 semaines (120 h). | - Retenue = $120 \times 10.000 = 1.200.000\text{ Ar}$.<br>- Salaire résiduel = $533.300\text{ Ar}$. | TU | Majeur |
| **ABS-05** | Absence & Retard | Absence mois complet (173,33 h). | - Retenue = $1.733.300\text{ Ar}$.<br>- Brut = 0,00 Ar (ou indemnités fixes seules). | TU | Bloquant |
| **HS-01** | Heures Supp. | Salarié effectuant 5 HS dans la semaine ($TH = 10.000\text{ Ar}$). | - 5 h dans la tranche 1 (30%) : $5 \times 10.000 \times 1,30 = 65.000\text{ Ar}$. | TU | Bloquant |
| **HS-02** | Heures Supp. | Salarié effectuant exactement 8 HS dans la semaine. | - 8 h dans la tranche 1 (30%) : $8 \times 10.000 \times 1,30 = 104.000\text{ Ar}$.<br>- 0 h dans la tranche 50%. | TU | Bloquant |
| **HS-03** | Heures Supp. | Salarié effectuant 12 HS dans la semaine (franchissement de seuil). | - 8 h à 30% : $104.000\text{ Ar}$.<br>- 4 h à 50% : $4 \times 10.000 \times 1,50 = 60.000\text{ Ar}$.<br>- Total HS = $164.000\text{ Ar}$. | TU | Bloquant |
| **MAJ-01** | Majorations | 4 heures de nuit travaillées en semaine ($TH = 10.000\text{ Ar}$, incluses dans les 40 h normales). | - Majoration seule (30%) : $4 \times 10.000 \times 0,30 = 12.000\text{ Ar}$.<br>- Le salaire de base n'est pas réimpacté. | TU | Bloquant |
| **MAJ-02** | Majorations | 7 heures de dimanche travaillées (incluses dans les 40 h hebdomadaires normales). | - **Vérification bug corrigé :** Majoration pure à 40% : $7 \times 10.000 \times 0,40 = 28.000\text{ Ar}$.<br>- Aucune application du facteur $1.40$ en doublon de base. | TU | Bloquant |
| **MAJ-03** | Majorations | 5 heures de jour férié légal chômé travaillé. | - Majoration férié (+100%) : $5 \times 10.000 \times 1,00 = 50.000\text{ Ar}$. | TU | Bloquant |
| **CUM-01** | Cumul Majorations | 2 heures supplémentaires de nuit un dimanche au-delà des 40 h (tranche HS 30%). | - Taux cumulé additif : $30\% \text{ (HS)} + 30\% \text{ (Nuit)} + 40\% \text{ (Dimanche)} = +100\%$ de majoration.<br>- Montant total rémunéré pour l'heure : $10.000 \times (1 + 1,00) \times 2 = 40.000\text{ Ar}$. | TU | Majeur |
| **CONG-01** | Congés Payés | Salarié prenant 3 jours de congé payé dans le mois. $TCONGE = 50.000\text{ Ar/j}$. $TH = 10.000\text{ Ar}$ (8 h/j). | - Déduction absence congé : $3 \times 8 \times 10.000 = 240.000\text{ Ar}$.<br>- Indemnité congé payé : $3 \times 50.000 = 150.000\text{ Ar}$ (ou maintien si plus favorable). | TU | Bloquant |
| **CONG-02** | Droit Congé Acquis | Mois normal de 30 jours, 0 absence. | - Droit mensuel acquis = $2,50\text{ jours}$.<br>- Enregistrement en mémoire prêt pour scellement. | TU | Majeur |
| **CONG-03** | Droit Congé Acquis | Mois avec 6 jours d'absence non rémunérée et 0 congé pris. | - Formule : $2,5 \times (30 - 6) / 30 = 2,5 \times 24 / 30 = 2,00\text{ jours}$. | TU | Majeur |
| **CNAPS-01** | Cotisation CNaPS | Brut = 1.500.000 Ar (Sous le plafond de 2.000.000 Ar). | - Assiette = 1.500.000 Ar<br>- CNaPS Sal (1%) = 15.000 Ar<br>- CNaPS Pat (13%) = 195.000 Ar. | TU | Bloquant |
| **CNAPS-02** | Cotisation CNaPS | Brut = 2.000.000 Ar (Exactement au plafond). | - Assiette = 2.000.000 Ar<br>- CNaPS Sal (1%) = 20.000 Ar<br>- CNaPS Pat (13%) = 260.000 Ar. | TU | Bloquant |
| **CNAPS-03** | Cotisation CNaPS | Brut = 4.500.000 Ar (Au-dessus du plafond). | - Assiette plafonnée = 2.000.000 Ar<br>- CNaPS Sal (1%) = 20.000 Ar<br>- CNaPS Pat (13%) = 260.000 Ar. | TU | Bloquant |
| **SANTE-01** | Cotisation Santé | Site Antananarivo : OSTIE, Taux Sal = 1%, Pat = 1%, Brut = 3.000.000 Ar (déplafonné). | - Assiette = 3.000.000 Ar<br>- OSTIE Sal = 30.000 Ar<br>- OSTIE Pat = 30.000 Ar. | TU | Bloquant |
| **SANTE-02** | Cotisation Santé | Site Toamasina : OMSI, Taux Sal = 2%, Pat = 2%, Brut = 3.000.000 Ar. | - Assiette = 3.000.000 Ar<br>- OMSI Sal = 60.000 Ar<br>- OMSI Pat = 60.000 Ar. | TU | Bloquant |
| **IRSA-01** | Fiscalité IRSA | Revenu net imposable = 300.000 Ar (Inférieur au seuil de 350.000 Ar). | - IRSA = 0,00 Ar (Exonération tranche 0%). | TU | Bloquant |
| **IRSA-02** | Fiscalité IRSA | Revenu net imposable = 380.000 Ar (Tranche 5% : de 350.001 à 400.000). | - Tranche taxable = 30.000 Ar à 5% = 1.500 Ar.<br>- **Minimum de perception applicable :** IRSA porté à 2.000 Ar. | TU | Bloquant |
| **IRSA-03** | Fiscalité IRSA | Revenu net imposable = 450.000 Ar (Tranche 10%). | - Tranche 1 (5% sur 50.000) = 2.500 Ar.<br>- Tranche 2 (10% sur 50.000) = 5.000 Ar.<br>- Total = 7.500 Ar. | TU | Bloquant |
| **IRSA-04** | Fiscalité IRSA | Revenu = 1.000.000 Ar, Salarié avec 4 enfants à charge. | - IRSA brut barème = 102.500 Ar.<br>- Déduction enfants : $4 \times 2.000 = 8.000\text{ Ar}$.<br>- IRSA final = 94.500 Ar. | TU | Bloquant |
| **IRSA-05** | Fiscalité IRSA | Revenu = 380.000 Ar (Impôt barème 1.500 Ar), 1 enfant à charge. | - Minimum fiscal = 2.000 Ar.<br>- Déduction enfant = 2.000 Ar.<br>- **Test plancher :** L'IRSA reste à 2.000 Ar (plancher non franchissable). | TU | Majeur |
| **ISI-01** | Fiscalité ISI | Consultant sous régime ISI : Brut = 2.000.000 Ar. | - CNaPS Sal/Pat = 0,00 Ar.<br>- Santé Sal/Pat = 0,00 Ar.<br>- ISI prélevé (5%) = 100.000 Ar.<br>- Net à payer = 1.900.000 Ar. | TU/TI | Bloquant |
| **ARR-01** | Arrondi & Décimales | Calcul intermédiaire générant `12345.004 Ar`. | - Arrondi final HALF_UP = `12345.00 Ar`. | TU | Mineur |
| **ARR-02** | Arrondi & Décimales | Calcul intermédiaire générant `12345.005 Ar`. | - Arrondi final HALF_UP = `12345.01 Ar`. | TU | Mineur |
| **ARR-03** | Arrondi & Décimales | Calcul intermédiaire générant `12345.006 Ar`. | - Arrondi final HALF_UP = `12345.01 Ar`. | TU | Mineur |
| **TRES-01** | Retenues Trésorerie | Acompte de 100.000 Ar perçu le 15 du mois. Net avant acompte = 800.000 Ar. | - Acompte déduit = 100.000 Ar.<br>- Net à payer final = 700.000 Ar. | TU | Bloquant |
| **TRES-02** | Quotité Cessible | Prêt de 500.000 Ar à retenir. Net disponible = 600.000 Ar. Quotité légale = 1/3 (200.000 Ar). | - Retenue écrêtée à 200.000 Ar (ou exception levée selon arbitrage DAF).<br>- Net à payer garanti = 400.000 Ar.<br>- Reliquat de 300.000 Ar tracé. | TU | Majeur |
| **ERR-01** | Résilience | Taux de cotisation santé absent pour le site du salarié à la date de paie. | - Déclenchement immédiat de `MissingPayrollParameterException`.<br>- **Aucun fallback silencieux à 1%**. | TR | Bloquant |
| **ERR-02** | Résilience | Barème IRSA manquant pour l'année fiscale courante. | - Déclenchement de `MissingPayrollParameterException` avec message explicite. | TR | Bloquant |
| **ERR-03** | Résilience | Salarié sans contrat actif à la date de la période. | - Déclenchement de `ContractNotFoundException`. | TR | Bloquant |
| **SIM-01** | Simulation Pure | Exécution du calcul d'un bulletin en mode SIMULATION. | - Retourne `PayrollResult` avec toutes ses lignes explicatives.<br>- **Vérification d'audit BDD :** Strictement 0 `INSERT`, 0 `UPDATE`, 0 `DELETE` en base de données. | TI | Bloquant |
| **HIST-01** | Immutabilité | Bulletin validé en Janvier 2026 ($S_{\text{contrat}} = 2.000.000\text{ Ar}$, CNaPS = 20.000 Ar). En Décembre 2026, le taux CNaPS passe à 2% et le salaire contractuel à 3.000.000 Ar en base. | - Consultation / Re-lecture du bulletin de Janvier 2026.<br>- Les montants affichés sont **strictement identiques** à ceux de Janvier (CNaPS = 20.000 Ar, Salaire = 2.000.000 Ar).<br>- Écart constaté = **0,00 Ar**. | TS | Bloquant |

---

## 3. Exemple d'Implémentation d'un Test Unitaire Pur en Java

Voici l'architecture canonique d'un test unitaire pur validant l'absence de régression sur la majoration du dimanche :

```java
package mg.galaxam.sirh.paie.domain.core.calculator;

import mg.galaxam.sirh.paie.domain.core.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class OvertimePayCalculatorTest {

    private final OvertimePayCalculator calculator = new OvertimePayCalculator();

    @Test
    @DisplayName("MAJ-02 : Les heures de dimanche incluses dans le forfait 40h ne doivent générer que la majoration 40% sans payer la base deux fois")
    void shouldCalculateOnlySundayIncrementWhenHoursAreWithinWeeklyQuota() {
        // Given
        BigDecimal hourlyRate = new BigDecimal("10000.00");
        BigDecimal sundayHours = new BigDecimal("7.00");
        BigDecimal sundayRateIncrement = new BigDecimal("0.40"); // 40% de majoration

        OvertimeInput input = new OvertimeInput(
            hourlyRate,
            BigDecimal.ZERO, // Pas de HS 30%
            BigDecimal.ZERO, // Pas de HS 50%
            BigDecimal.ZERO, // Pas de nuit
            sundayHours,     // 7h le dimanche
            BigDecimal.ZERO, // Pas de férié
            sundayRateIncrement
        );

        // When
        OvertimeResult result = calculator.calculate(input);

        // Then
        // Montant attendu = 7h * 10 000 Ar * 0.40 = 28 000 Ar (et NON 98 000 Ar qui correspondrait à 1.40)
        assertThat(result.sundayPayAmount())
            .isEqualByComparingTo(new BigDecimal("28000.00"));
        
        assertThat(result.explanationLines())
            .anyMatch(line -> line.formuleExplicative().equals("7.00 h * 10000.00 Ar * 40.0%"));
    }
}
```

---

## 4. Test d'Immutabilité Historique (Scellement du Bulletin)

Ce test garantit qu'un changement de données administratives n'altère jamais un bulletin validé :

```java
@Test
@DisplayName("HIST-01 : Un bulletin scellé doit être insensible à toute modification ultérieure en BDD")
void shouldRemainStrictlyIdenticalWhenParametersChangeAfterValidation() {
    // 1. Validation d'un bulletin en M1 avec CNaPS à 1%
    BulletinPaie bulletin = payrollOrchestrator.validate(employeId, PERIODE_JANVIER);
    BigDecimal netInitial = bulletin.getNetAPayer();

    // 2. Altération délibérée des configurations en BDD (Passage CNaPS à 5% et augmentation de salaire)
    employeRepository.updateSalaire(employeId, new BigDecimal("10000000.00"));
    parametreBaremeRepository.updateTauxCnaps(new BigDecimal("0.05"));

    // 3. Rechargement du bulletin scellé
    BulletinPaie bulletinRelu = bulletinPaieRepository.findById(bulletin.getId()).orElseThrow();

    // 4. Vérification d'égalité absolue
    assertThat(bulletinRelu.getNetAPayer()).isEqualByComparingTo(netInitial);
    assertThat(bulletinRelu.getDonneesEntreeSnapshot()).contains("\"salaireContractuel\": 2000000.00");
    assertThat(bulletinRelu.getReglesCalculSnapshot()).contains("\"tauxSalarial\": 0.0100");
}
```
