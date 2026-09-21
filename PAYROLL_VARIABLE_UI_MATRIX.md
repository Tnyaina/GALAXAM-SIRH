# MATRICE DES VARIABLES DE PAIE (UI ↔ BACKEND) — SIRH GALAXAM

> **Document de référence :** Matrice d'exhaustivité et de classification fiscale/sociale des variables de paie, primes, acomptes et retenues dans SIRH GALAXAM.  
> **Date :** 20 Septembre 2026  
> **Table Référentielle BDD :** `ref_type_mouvement_paie` & `paie_avance_acompte`  

---

## 1. Principes de Conception & Séparation Canonique

1. **Aucun recalcul côté client :** Les montants bruts, plafonnements légaux (1/10ème pour avances, quotité cessible 1/3) et impacts fiscaux (CNaPS, Santé, IRSA) sont calculés et scellés exclusivement par le moteur backend `PayrollExecutionEngine`.
2. **Unités sans ambiguïté :**
   - Heures/Minutes d'absence : exprimées en format lisible `X h YY min` et non en décimal trompeur.
   - Jours de congés payés : exprimés en jours ouvrables `j` (ex : `2.50 j`).
   - Montants financiers : exprimés en Ariary avec séparateur de milliers et suffixe `Ar` (ex : `502 610 Ar`).
3. **Classification fiscale et sociale explicite :** Chaque variable expose clairement à l'utilisateur si elle est soumise à cotisations sociales et à l'impôt ou si elle s'impute directement sur le Net à payer disponible.

---

## 2. Matrice Complète des Variables de Paie

| Variable (Code BDD) | Libellé Métier | Backend Supported | UI Supported | Editable (Saisie RH) | Source | Unité | Classification Fiscale / Sociale | Statut / Missing |
| :--- | :--- | :---: | :---: | :---: | :--- | :---: | :--- | :---: |
| `ACOMPTE_15` | Acompte de Quinzaine (du 15) | OUI | OUI | OUI | Saisie DAF / Demande Salarié | Ar | Déduction directe sur Net à Payer (Non soumise CNaPS/IRSA) | CONFORME |
| `AVANCE_SPECIALE` | Avance Exceptionnelle sur Salaire | OUI | OUI | OUI | Accord DRH / Direction | Ar | Déduction sur Net à Payer (Plafonnée légalement au 1/10ème Art. 68) | CONFORME |
| `CALL_PERSO` | Retenue Télécoms Privées (Call Perso) | OUI | OUI | OUI | Relevé Facturation Flotte | Ar | Retenue sur Net à Payer (Non soumise) | CONFORME |
| `RETENUE_DIVERSE` | Retenue Diverse sur Salaire | OUI | OUI | OUI | Décision RH / Contentieux | Ar | Retenue sur Net à Payer | CONFORME |
| `PRIME_PERFORMANCE` | Prime de Performance | OUI | OUI | OUI | Évaluation Managériale | Ar | **Brut Imposable** (Soumis CNaPS 1%, Santé 1%, IRSA progressif) | CONFORME |
| `PRIME_ASSIDUITE` | Prime d'Assiduité | OUI | OUI | OUI | Pointage (Automatique ou RH) | Ar | **Brut Imposable** (Soumis CNaPS 1%, Santé 1%, IRSA progressif) | CONFORME |
| `PRIME_EXCEPTIONNELLE` | Prime Exceptionnelle (Gratification) | OUI | OUI | OUI | Décision Direction | Ar | **Brut Imposable** (Soumis CNaPS 1%, Santé 1%, IRSA progressif) | CONFORME |
| `AUTIMP` | Autres Gains Imposables | OUI | OUI | OUI | Modèle Historique Excel / RH | Ar | **Brut Imposable** (Soumis CNaPS 1%, Santé 1%, IRSA progressif) | CONFORME |
| `EXTRA_DEPLACEMENT` | Indemnité Déplacement Extra (>19h30 / Mission) | OUI | OUI | OUI | Pointage / Ordre de Mission | Ar | **Indemnité Exonérée** (Frais réels / Remboursement de sujétion) | CONFORME |
| `NOTE_DE_FRAIS` | Remboursement Notes de Frais | OUI | OUI | OUI | Justificatifs Dépenses Salarié | Ar | **Net Non Imposable** (Remboursement de débours professionnel) | CONFORME |
| `REGULARISATION_CONGE`| Régularisation Congé au Retour | OUI | OUI | OUI | Dépôt Titre de Congé Validé | Ar | **Régularisation Gain Brut** (Restitution retenue absence passée) | CONFORME |

---

## 3. Rubriques Spécifiques Calculées par le Moteur (Non saisies manuellement)

| Rubrique Automatique | Description Moteur | Source | Unité | Classification Moteur | Représentation UI |
| :--- | :--- | :--- | :---: | :--- | :--- |
| `RETENUE_ABSENCE` | Décompte financier des heures/minutes d'absence | `pointage_cumul_periode` | Ar / min | Déduction du Brut social : $\frac{\text{Base} \times \text{Min}}{10\,400}$ | Visible dans Bulletin & Cascade |
| `DEDUCTION_CONGE_BASE`| Déduction du salaire de base pour jours pris | Mouvements congés payés | Ar / j | Déduction du Brut social : $\frac{\text{Base} \times \text{Jours}}{30}$ | Visible dans Bulletin & Cascade |
| `INDEMNITE_CONGE_PAYE`| Valorisation légale du congé selon TCONGE | Moteur KAJY / Ledger | Ar / j | Gain Brut imposable : $\text{Jours} \times \text{TCONGE}$ | Visible dans Bulletin & Cascade |
| `HEURES_SUP_30` | Majoration Heures Supplémentaires 30% | Pointage hebdomadaire | Ar / h | Gain Brut imposable : $\text{HS30} \times \text{TauxHor} \times 1.30$ | Visible dans Bulletin & Cascade |
| `HEURES_SUP_50` | Majoration Heures Supplémentaires 50% | Pointage hebdomadaire | Ar / h | Gain Brut imposable : $\text{HS50} \times \text{TauxHor} \times 1.50$ | Visible dans Bulletin & Cascade |
| `MAJORATION_NUIT` | Majoration Travail de Nuit (22h - 5h) 30% | Pointage quotidien | Ar / h | Gain Brut imposable : $\text{HNuit} \times \text{TauxHor} \times 0.30$| Visible dans Bulletin & Cascade |
| `MAJORATION_DIMANCHE`| Majoration Travail Dimanche 40% | Pointage quotidien | Ar / h | Gain Brut imposable : $\text{HDim} \times \text{TauxHor} \times 0.40$ | Visible dans Bulletin & Cascade |
| `MAJORATION_FERIE` | Majoration Jour Férié Travaillé 100% | Pointage + Calendrier | Ar / h | Gain Brut imposable : $\text{HFer} \times \text{TauxHor} \times 1.00$ | Visible dans Bulletin & Cascade |
| `INDEMNITE_REPAS` | Panier repas journalier | Pointage quotidien | Ar | Indemnité forfaitaire non imposable | Visible dans Bulletin |
| `INDEMNITE_DEPLACEMENT`| Indemnité forfaitaire déplacement | Pointage quotidien | Ar | Indemnité forfaitaire non imposable | Visible dans Bulletin |

---

## 4. Bilan d'Alignement & Actions Menées

1. **Variables UI Obsolètes Purifiées :** Le code frontend utilisait parfois un libellé raccourci historique `PRIM_EXC` dans certains formulaires par défaut. Ce code a été aligné sur `PRIME_EXCEPTIONNELLE` et rendu dynamique par interrogation de l'API `/paie/ajustements/types-mouvements`.
2. **Absence de variables orphelines :** Toutes les 11 rubriques variables déclarées en base de données dans `ref_type_mouvement_paie` sont désormais parfaitement représentées dans l'interface, catégorisées (Gain / Retenue) et enrichies de leur qualification fiscale.
3. **Workflow d'Approbation :** L'UI permet désormais de visualiser le cycle complet d'une variable : `EN_ATTENTE` $\to$ `VALIDE` $\to$ `IMPUTE_PAIE` (ou `REJETE`).
