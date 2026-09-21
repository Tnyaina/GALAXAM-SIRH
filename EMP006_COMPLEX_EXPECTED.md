# FICHE EXPECTED A PRIORI — EMP006_COMPLEX (SEPTEMBRE 2026)

**Statut du document :** ÉTABLI AVANT LE PREMIER RUN RUNTIME DE CERTIFICATION  
**Date d'établissement :** 2026-09-19  
**Période certifiée :** 01/09/2026 au 30/09/2026 (30 jours calendaires, mois plein)  
**Salarié :** Herizo RAKOTO (Matricule : `EMP006`)  

---

## 1. Données Contractuelles et RH

| Attribut | Valeur | Source réglementaire / Référentiel |
| :--- | :--- | :--- |
| **Contrat** | CDI Plein Temps | Code du Travail Art. 19 |
| **Catégorie** | Agent d'exécution (Non-Cadre) | CCN & Grille interne |
| **Éligibilité Exonération HS** | `OUI` | Article 01.03.03-9° CGI |
| **Salaire de Base Contractuel** | **600 000.00 Ar** | Contrat de travail |
| **Diviseur Mensuel Légal** | **173.33 h** | Décret 68-172 & RG-POINTAGE-01 |
| **Taux Horaire Mathématique** | **3 461.604973 Ar/h** | `600 000 / 173.33` (8 décimales) |
| **Site d'affectation** | TNR (Antananarivo) | Référentiel sites |
| **Organisme de Santé** | OMSI (1.5% salarial / 5.0% patronal) | Référentiel cotisations |
| **CNaPS** | 1.0% salarial / 13.0% patronal (Plafond 8 SME) | Décret CNaPS |
| **Régime Fiscal** | IRSA progressif 2026 | Code Général des Impôts |
| **Personnes à charge** | 2 enfants éligibles (2 000 Ar / enfant) | CGI Art. 01.03.14 |

---

## 2. Calendrier, Pointages Bruts et Événements du Mois

### A. Synthèse du Pointage Brut
- **Objectif Mensuel Ouvrable :** 176 heures (22 jours ouvrés × 8h).
- **Heures réellement accomplies :** 186 heures.
- **Retards cumulés constatés :** 120 minutes (2h).
- **Absence injustifiée décomptée :** 120 minutes (2h) non récupérées.
- **Heures supplémentaires :** 14 heures au total :
  - Tranche 1 (+30%) : **10.00 h**
  - Tranche 2 (+50%) : **4.00 h**
- **Travail de Nuit (22h-05h) :** **2.00 h** (+30%)
- **Travail du Dimanche :** **4.00 h** (+40%)
- **Travail Jour Férié :** 0.00 h

### B. Congés Payés
- **Demande validée :** 1 journée (le 15/09/2026).
- **Décompte solde :** 1.0 jour imputé sur le solde acquis.
- **Règle anti-double-déduction :** La journée de congé payé n'entraîne AUCUNE minute d'absence injustifiée.

### C. Variables de Paie Déclarées
- **Prime de rendement imposable (AUTIMP) :** **40 000.00 Ar** (soumise à cotisations et IRSA).
- **Indemnité de transport forfaitaire :** **30 000.00 Ar** (exonérée de cotisations et d'IRSA selon barème).
- **Acompte / Avance sur salaire :** **50 000.00 Ar** (retenue sur le salaire net).

---

## 3. Décomposition et Expected Indépendants Détaillés

### A. Rémunération Brute (Brut Social)
| Rubrique | Formule / Base de calcul | Montant Indépendant Expected |
| :--- | :--- | ---: |
| **Salaire de Base** | Mois plein (30/30) | **600 000.00 Ar** |
| **Retenue pour Absence** | $600\,000 \times 120 / (173.33 \times 60) = 600\,000 \times 2 / 173.33$ | **- 6 923.21 Ar** |
| **Déduction Congé Base** | $600\,000 \times 1 / 30$ | **- 20 000.00 Ar** |
| **Indemnité Congé Payé** | $1 \times \text{TCONGE} (20\,000.00\text{ Ar})$ | **+ 20 000.00 Ar** |
| **Heures Supplémentaires 30%** | $10\text{ h} \times 3\,461.604973 \times 1.30$ | **+ 45 000.86 Ar** |
| **Heures Supplémentaires 50%** | $4\text{ h} \times 3\,461.604973 \times 1.50$ | **+ 20 769.63 Ar** |
| **Majoration Nuit (+30%)** | $2\text{ h} \times 3\,461.604973 \times 0.30$ | **+ 2 076.96 Ar** |
| **Majoration Dimanche (+40%)** | $4\text{ h} \times 3\,461.604973 \times 0.40$ | **+ 5 538.57 Ar** |
| **Prime de Rendement (AUTIMP)**| Forfait imposable | **+ 40 000.00 Ar** |
| **TOTAL GAINS BRUTS (BRUT SOCIAL)** | Somme arithmétique | **706 462.81 Ar** |

### B. Cotisations Sociales
- **SME applicable (Septembre 2026) :** 262 680.00 Ar.
- **Plafond CNaPS (8 × SME) :** 2 101 440.00 Ar.
- Assiette CNaPS = $\min(706\,462.81, 2\,101\,440.00) = 706\,462.81\text{ Ar}$.

| Cotisation | Assiette | Taux | Part Salariale Expected | Part Patronale Expected |
| :--- | ---: | :---: | ---: | ---: |
| **CNaPS** | 706 462.81 Ar | 1.0% / 13.0% | **7 064.63 Ar** | **91 840.17 Ar** |
| **OMSI (Santé)** | 706 462.81 Ar | 1.5% / 5.0% | **10 596.94 Ar** | **35 323.14 Ar** |
| **FMFP** | 706 462.81 Ar | 0.0% / 1.0% | **0.00 Ar** | **7 064.63 Ar** |
| **TOTAL CHARGES** | | | **17 661.57 Ar** | **134 227.94 Ar** |

### C. Net Imposable et Assiette Fiscale IRSA
- **Salaire Net Imposable Social :**
  $$\text{Net Imposable} = 706\,462.81 - 7\,064.63 - 10\,596.94 = \mathbf{688\,801.24\text{ Ar}}$$
- **Exonération Fiscale des Heures Supplémentaires (CGI Art. 01.03.03-9°) :**
  - Non-cadre éligible, volume total = 14h $\le$ plafond légal de 20h.
  - $\text{HS Exonérées d'IRSA} = 45\,000.86 + 20\,769.63 = \mathbf{65\,770.49\text{ Ar}}$
  - $\text{HS Imposables} = \mathbf{0.00\text{ Ar}}$
- **Assiette Fiscale IRSA Brute :**
  $$\text{Base IRSA Brute} = 688\,801.24 - 65\,770.49 = \mathbf{623\,030.75\text{ Ar}}$$
- **Assiette Fiscale IRSA Arrondie (Arrondi légal `CENTAINE_INF`) :**
  $$\text{Base IRSA Arrondie} = \mathbf{623\,000.00\text{ Ar}}$$

### D. Liquidation de l'IRSA
| Tranche IRSA 2026 | Intervalle légal | Assiette dans tranche | Taux | Impôt calculé |
| :--- | :--- | ---: | :---: | ---: |
| **Tranche 1** | 0 à 350 000 Ar | 350 000.00 Ar | 0% | 0.00 Ar |
| **Tranche 2** | 350 001 à 400 000 Ar | 50 000.00 Ar | 5% | 2 500.00 Ar |
| **Tranche 3** | 400 001 à 500 000 Ar | 100 000.00 Ar | 10% | 10 000.00 Ar |
| **Tranche 4** | 500 001 à 600 000 Ar | 100 000.00 Ar | 15% | 15 000.00 Ar |
| **Tranche 5** | 600 001 à 4 000 000 Ar | 23 000.00 Ar | 20% | 4 600.00 Ar |
| **IRSA Brut Total** | | | | **32 100.00 Ar** |

- **Réduction pour charge de famille :** 2 enfants × 2 000 Ar = **4 000.00 Ar**
- **IRSA Net après réduction :** $32\,100.00 - 4\,000.00 = \mathbf{28\,100.00\text{ Ar}}$
- **Minimum de perception légal (2 000 Ar) :** Respecté ($28\,100.00 \ge 2\,000.00$).

### E. Net à Payer Final
- **Net avant déductions / indemnités :**
  $$688\,801.24 - 28\,100.00 = 660\,701.24\text{ Ar}$$
- **Acompte déduit :** **- 50 000.00 Ar**
- **Indemnité transport ajoutée :** **+ 30 000.00 Ar**
- **Net Avant Arrondi :**
  $$\text{Net Avant Arrondi} = 660\,701.24 - 50\,000.00 + 30\,000.00 = \mathbf{640\,701.24\text{ Ar}}$$
- **Net à Payer Final (Arrondi bancaire officiel `MROUND 10`) :**
  $$\mathbf{Net\ à\ Payer} = \mathbf{640\,700.00\text{ Ar}}$$

---

## 4. Matrice de Réconciliation A Priori

| Rubrique | Montant Indépendant Expected |
| :--- | ---: |
| Salaire de Base | 600 000.00 Ar |
| Retenue Absence | 6 923.21 Ar |
| Congés (Déduction / Indemnité) | 20 000.00 Ar / 20 000.00 Ar |
| Heures Supplémentaires (30% + 50%) | 65 770.49 Ar |
| Majorations (Nuit + Dimanche) | 7 615.53 Ar |
| Primes Imposables | 40 000.00 Ar |
| **Total Gains Bruts** | **706 462.81 Ar** |
| Cotisation CNaPS Salariale | 7 064.63 Ar |
| Cotisation Santé (OMSI) Salariale | 10 596.94 Ar |
| **Salaire Net Imposable** | **688 801.24 Ar** |
| Exonération Fiscale HS | 65 770.49 Ar |
| Base IRSA Arrondie | 623 000.00 Ar |
| IRSA Net | 28 100.00 Ar |
| Acompte sur Salaire | 50 000.00 Ar |
| Indemnité Transport Exonérée | 30 000.00 Ar |
| Net Avant Arrondi | 640 701.24 Ar |
| **Net à Payer Final** | **640 700.00 Ar** |
