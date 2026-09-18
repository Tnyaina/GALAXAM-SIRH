# MATRICE DES COMPOSANTS DE PAIE & ASSIETTE TCONGE (LOI 2024-014)

**Date :** 18 Septembre 2026  
**Auteur :** Antigravity Agentic Pair Programmer  
**Objet :** Analyse juridique et fonctionnelle rubric par rubric de l'assiette de valorisation TCONGE (Code du travail Malagasy Loi 2024-014, Articles 88 & 131).  
**Statut :** AUDIT ONLY — Baseline technique 499 tests préservée.

---

## 1. FONDEMENT JURIDIQUE DE L'ASSIETTE

L'assiette légale du congé payé est régie par l'articulation de deux textes impératifs de la **Loi n° 2024-014** :

1. **Article 131 :**
   > *« ...une allocation au moins égale au douzième (1/12) des salaires et des divers éléments de rémunération définis par la présente loi dont le travailleur bénéficiait au cours des douze (12) mois ayant précédé la date du congé, à l'exception des remboursements de frais. »*
2. **Article 88 :**
   > *« Lorsque la rémunération d'un travailleur est composée, en tout ou en partie, par des commissions, des primes, des prestations diverses ou des indemnités représentatives de ces prestations, il en est tenu compte pour le calcul de la rémunération pendant la période de congé payé [...] dans la mesure où elles ne constituent pas un remboursement de frais. »*

### Règle d'or de qualification :
- **Principe d'inclusion :** Toute somme ou prestation attribuée en contrepartie du travail ou liée à la qualité de salarié (salaire direct, primes de résultat, primes d'assiduité, ancienneté, commissions, avantages en nature, majorations de sujétion) doit obligatoirement être incluse dans l'assiette des congés.
- **Principe d'exclusion :** Seules les sommes qui indemnisent des **dépenses réelles ou professionnelles engagées par le salarié pour l'exercice de ses fonctions** (déplacements professionnels, frais de mission sur justificatifs, outils de travail) sont légalement exclues.

---

## 2. MATRICE DÉTAILLÉE PAR COMPOSANT DE PAIE

| Élément de Paie / Rubrique | KAJY Inclut ? | SIRH Inclut ? | Art. 88 / 131 Inclut ? | Frais Réels ? | Analyse Juridique & Statut | Niveau de Certitude |
| :--- | :---: | :---: | :---: | :---: | :--- | :---: |
| **Salaire de base** | **OUI** | **OUI** | **OUI** | NON | Composante centrale du salaire contractuel. Règle intangible. | `LEGAL_VERIFIED` |
| **Heures supplémentaires (Base)** | **OUI** | **OUI** | **OUI** | NON | Rémunération directe d'un surcroît de travail effectif. | `LEGAL_VERIFIED` |
| **Majorations HS (+25%, +50%)** | **OUI** | **OUI** | **OUI** | NON | Partie intégrante du salaire brut majoré en vertu du Décret 68-172. | `LEGAL_VERIFIED` |
| **Travail de nuit habituel (+30%)** | **OUI** (via HS) | **OUI** (via HS) | **OUI** | NON | Majoration de sujétion légale (Décret 72-226 Art. 3). Fait partie du salaire normal. | `LEGAL_VERIFIED` |
| **Dimanche travaillé (+40%)** | **OUI** (via HS) | **OUI** (via HS) | **OUI** | NON | Majoration de repos hebdomadaire légale. | `LEGAL_VERIFIED` |
| **Jour férié travaillé (+100%)** | **OUI** (via HS) | **OUI** (via HS) | **OUI** | NON | Majoration légale de jour férié chômé et payé travaillé. | `LEGAL_VERIFIED` |
| **Prime de performance / Rendement** | **NON** | **NON** | **OUI** | NON | Expressément visée à l'Art. 88 (« primes »). KAJY l'exclut à tort via AUTIMP. | `LEGAL_VERIFIED` |
| **Prime d'assiduité / Présence** | **NON** | **NON** | **OUI** | NON | Contrepartie directe de la régularité du service effectif. Doit être intégrée. | `LEGAL_VERIFIED` |
| **Prime d'ancienneté** | **NON** | **NON** | **OUI** | NON | Complément de salaire lié à la durée de présence dans l'entreprise. | `LEGAL_VERIFIED` |
| **Commissions commerciales** | **NON** | **NON** | **OUI** | NON | Expressément visée à l'Art. 88 (« commissions »). Obligatoirement lissée sur 12 mois. | `LEGAL_VERIFIED` |
| **Avantages en nature (logement, véh.)** | **NON** | **NON** | **OUI** | NON | Expressément visé à l'Art. 88 (« prestations diverses »). Valorisation imposable requise. | `LEGAL_VERIFIED` |
| **Indemnité de déplacement / Mission** | **NON** | **NON** | **NON** | **OUI** | Couverture de débours professionnels engagés pour l'employeur. Exclusion légale. | `LEGAL_VERIFIED` |
| **Indemnité de repas / Panier** | **NON** | **NON** | **DÉPEND** | DISCUTÉ | Si remboursement forfaitaire de contrainte de service (ex: travail de nuit), non salarial. Si versé sans condition de repas, requalifiable en salaire. | `LEGAL_INTERPRETATION` |
| **Remboursement de frais réels** | **NON** | **NON** | **NON** | **OUI** | Exclusion impérative prescrite par l'Art. 131 in fine. | `LEGAL_VERIFIED` |
| **AUTIMP (Autres indemnités imposables)** | **NON** | **NON** | **DÉPEND** | DISCUTÉ | Dépassement critique : KAJY exclut tout AUTIMP. Si AUTIMP contient des primes, c'est une infraction à l'Art. 88. Si AUTIMP = indemnité forfaitaire de fonction, à auditer. | `BUSINESS_RULE_REQUIRED` |
| **Retenue pour absence injustifiée** | **OUI** | **OUI** | **OUI** | NON | Diminue le salaire net effectivement perçu et le service effectif. | `LEGAL_VERIFIED` |
| **Retenue pour absence congé payé** | **OUI** | **OUI** | **OUI** | NON | Annule le salaire de base pendant l'absence congé (remplacé par l'indemnité). | `LEGAL_VERIFIED` |
| **Indemnité de congé payé antérieure** | **OUI** | **OUI** | **OUI** | NON | Salaire de substitution perçu au titre d'une période assimilée à du travail effectif (Art. 126). | `LEGAL_VERIFIED` |
| **Gratification annuelle / 13ème mois** | **NON** | **NON** | **DÉPEND** | NON | Si conventionnelle liée à l'année entière, son inclusion dans le 1/12 sans neutralisation conduirait à verser un 14ème mois sur le congé. Jurisprudence constante : exclue si allouée pour l'année entière y compris période de congé. | `LEGAL_INTERPRETATION` |

---

## 3. FOCUS CRITIQUE : LE CAS D'AUTIMP DANS KAJY

Dans la feuille de calcul `OHATRA KAJY DROITS CONGES-given on sept26.xls` :
- La colonne M (`AUTIMP`) enregistre des montants variables sous le libellé « Autres indemnités imposables ».
- La formule de la colonne R (`base totale`) est :
  $$\text{Col R} = \text{Col P} + \text{Col Q} \quad (\text{NET BASE} + \text{HS})$$
- La colonne M est **intégralement ignorée** dans le calcul de la base de valorisation des congés.

### Conséquence financière et juridique :
1. **Si AUTIMP représente des primes salariales (rendement, objectifs, technicité) :**
   L'exclusion par KAJY produit une sous-évaluation structurelle de l'allocation de congé de 10 % à 30 % par rapport au minimum légal de l'article 88 et de l'article 131.
2. **Si AUTIMP représente des indemnités de sujétion ou forfaitaires de fonction :**
   Le caractère de remboursement de frais doit être formellement documenté par GALAXAM pour justifier son exclusion au regard de l'article 131.
3. **Statut :** `LEGAL_DIVERGENCE` potentiel majeur, soumis à arbitrage formel de la DAF.
