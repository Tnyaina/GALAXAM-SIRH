# Matrice Universelle des Variables de Paie Galaxam

**Référence du document :** GAL-SIRH-PAYROLL-AUDIT-02  
**Statut :** Matrice d'analyse exhaustive des 30 questions méthodiques  
**Date d'émission :** 29 Septembre 2026  
**Auteur :** Antigravity AI — Pair Programming Ingénierie SIRH  

---

## 1. Cadre d'Analyse et Questionnaire Méthodique

Pour chaque variable de paie identifiée chez GALAXAM, l'audit répond rigoureusement aux **30 questions fondamentales** définies dans le cahier des charges :

1. **Nature métier** : Définition fonctionnelle du concept.
2. **Type financier** : Gain, Retenue, Cotisation, Impôt, Charge patronale, Remboursement, Info.
3. **Débiteur** : Qui supporte le coût financier ? (Salarié, Employeur, Les deux).
4. **Unité** : Montant (Ar), Heures, Minutes, Jours, Taux, Quantité.
5. **Source** : Contrat, Pointage scellé, Demande DAF, Barème légal, Calcul dérivé.
6. **Saisie / Calcul** : Valeur saisie ou calculée automatiquement.
7. **Formule canonique** : Expression mathématique exacte.
8. **Assiette** : Grandeur de base servant au calcul.
9. **Taux** : Pourcentage ou multiplicateur applicable.
10. **Plafond** : Valeur maximale autorisée.
11. **Minimum** : Valeur minimale garantie.
12. **Seuil** : Condition de déclenchement.
13. **Proratisable** : Sensibilité au temps partiel ou présence incomplète.
14. **Arrondi** : Soumise ou non à arrondi.
15. **Type d'arrondi** : Règle d'arrondi (2 décimales, dizaine sup/inf, etc.).
16. **Moment d'arrondi** : Étape où intervient l'arrondi (intermédiaire vs final).
17. **Cotisable CNaPS** : Inclusion dans l'assiette brute CNaPS.
18. **Cotisable Santé** : Inclusion dans l'assiette brute OSTIE / OMSI.
19. **Imposable IRSA** : Inclusion dans le revenu brut soumis à l'IRSA.
20. **Inclus dans le Brut** : Composante du Total des Gains Bruts.
21. **Inclus dans le Net** : Composante du Net à payer bancaire.
22. **Coût Employeur** : Augmente le coût global supporté par l'entreprise.
23. **Droits Congés** : Intégrée dans l'assiette de valorisation des futurs congés ($B_{totale}$).
24. **Autre assiette** : Influence une autre assiette (ex: quotité cessible).
25. **Date d'effet** : Dépendance à un millésime ou barème daté.
26. **Dépendance Salarié** : Dépend de la situation personnelle (ancienneté, enfants).
27. **Dépendance Contrat** : Dépend du type de contrat, statut cadre, mode calcul.
28. **Dépendance Site** : Règle ou tarif variant selon TNR ou TMM.
29. **Dépendance Régime** : Conditionnée par Permanent (IRSA) vs Consultant (ISI).
30. **Affichage Bulletin** : Obligation d'apparaître explicitement sur le bulletin.

---

## 2. Tableau Récapitulatif Global des Variables

*Statuts d'audit :*  
- **CONFIRMÉ** : Règle validée par le code, les tests et les accords DAF.  
- **PROBABLE** : Règle appliquée dans le code mais méritant formalisation formelle.  
- **CONTRADICTOIRE** : Écart ou incohérence constatée entre sources (ex: Excel vs Code).  
- **À CONFIRMER** : Choix métier nécessitant un arbitrage DAF définitif.  

| Code Rubrique | Libellé Métier | Famille | Source | Unité | Brut | CNaPS | Santé | IRSA | Net | Statut |
| :--- | :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `SALAIRE_BASE` | Salaire de Base Contractuel | Gain | Contrat | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `BASE_PRORATA` | Salaire Proratisé (Entrée/Sortie) | Gain | Contrat / Calendrier | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `RETENUE_ABSENCE` | Retenue Absence Décomptée | Retenue | Pointage scellé | min / Ar | - | déduit | déduit | déduit | - | **CONFIRMÉ** |
| `RETENUE_RETARD` | Retenue Retards Injustifiés | Retenue | Pointage scellé | min | - | indirect | indirect | indirect | - | **À CONFIRMER** |
| `DEDUCTION_CONGE_BASE`| Déduction Congés Pris (1/30e) | Retenue | Congés | j / Ar | - | déduit | déduit | déduit | - | **CONFIRMÉ** |
| `INDEMNITE_CONGE_PAYE`| Indemnité Congés Payés (TCONGE)| Gain | Grand-livre congés | j / Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `HEURES_SUP_30` | Heures Supplémentaires (+30%) | Gain | Pointage scellé | h / Ar | Oui | Oui | Oui | Partiel (Exo) | Oui | **CONFIRMÉ** |
| `HEURES_SUP_50` | Heures Supplémentaires (+50%) | Gain | Pointage scellé | h / Ar | Oui | Oui | Oui | Partiel (Exo) | Oui | **CONFIRMÉ** |
| `MAJORATION_NUIT` | Majoration Travail de Nuit (+30%) | Gain | Pointage scellé | h / Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `HEURES_DIMANCHE` | Majoration Dimanche (+40%) | Gain | Pointage scellé | h / Ar | Oui | Oui | Oui | Oui | Oui | **CONTRADICTOIRE** |
| `MAJORATION_FERIE` | Majoration Jour Férié (+100%) | Gain | Pointage scellé | h / Ar | Oui | Oui | Oui | Oui | Oui | **CONTRADICTOIRE** |
| `HS_EXONEREE_IRSA` | HS Exonérées (Art. 01.03.03-9°) | Info | Calcul fiscal | h / Ar | Non | — | — | déduit | Non | **CONFIRMÉ** |
| `PRIME_PERFORMANCE` | Prime de Performance Proratisée | Gain | DAF / Pointage | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `PRIME_ASSIDUITE` | Prime d'Assiduité (Seuil 45 min) | Gain | Pointage scellé | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `PRIME_ANCIENNETE` | Prime d'Ancienneté (+1%/an $\ge 3$ ans)| Gain | Carrière RH | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `PRIME_EXCEPTIONNELLE`| Gratification / Prime Exceptionnelle| Gain | DAF | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `AUTIMP` | Autres Gains Imposables | Gain | Historique / DAF | Ar | Oui | Oui | Oui | Oui | Oui | **CONTRADICTOIRE** |
| `INDEMNITE_REPAS` | Indemnité de Repas (Jour présent) | Gain | Pointage scellé | j / Ar | Oui | Oui | Oui | Oui | Oui | **PROBABLE** |
| `INDEMNITE_TRANSPORT`| Indemnité de Déplacement | Gain | Pointage scellé | j / Ar | Oui | Oui | Oui | Oui | Oui | **PROBABLE** |
| `EXTRA_DEPLACEMENT` | Extra Déplacement Non Imposable | Gain net | DAF | Ar | Non | Non | Non | Non | Oui | **CONFIRMÉ** |
| `REMBOURSEMENT_NOTE_FRAIS`| Notes de Frais Justifiées | Gain net | DAF | Ar | Non | Non | Non | Non | Oui | **CONFIRMÉ** |
| `COTISATION_CNAPS_SAL`| CNaPS Salariale (1%) | Cotisation | Moteur de Paie | % / Ar | Non | — | Non | déduit IRSA | - | **CONFIRMÉ** |
| `COTISATION_CNAPS_PAT`| CNaPS Patronale (13%) | Patronal | Moteur de Paie | % / Ar | Non | — | Non | Non | Non | **CONFIRMÉ** |
| `COTISATION_OSTIE_SAL`| OSTIE Salariale (TNR - 1%) | Cotisation | Moteur de Paie | % / Ar | Non | Non | — | déduit IRSA | - | **CONFIRMÉ** |
| `COTISATION_OSTIE_PAT`| OSTIE Patronale (TNR - 5%) | Patronal | Moteur de Paie | % / Ar | Non | Non | — | Non | Non | **CONFIRMÉ** |
| `COTISATION_OMSI_SAL`| OMSI Salariale (TMM - 1.5%) | Cotisation | Moteur de Paie | % / Ar | Non | Non | — | déduit IRSA | - | **CONFIRMÉ** |
| `COTISATION_OMSI_PAT`| OMSI Patronale (TMM - 5.5%) | Patronal | Moteur de Paie | % / Ar | Non | Non | — | Non | Non | **CONFIRMÉ** |
| `COTISATION_FMFP_PAT`| FMFP Patronale (1%) | Patronal | Moteur de Paie | % / Ar | Non | Non | Non | Non | Non | **CONFIRMÉ** |
| `IMPOT_IRSA_BRUT` | IRSA Brut (Barème 6 Tranches) | Info | Moteur de Paie | Ar | Non | Non | Non | — | Non | **CONFIRMÉ** |
| `REDUCTION_CHARGE_FAMILLE`| Réduction Enfants (2 000 Ar/enf)| Info | Moteur de Paie | Ar | Non | Non | Non | — | Non | **CONFIRMÉ** |
| `IMPOT_IRSA_NET` | IRSA Net à Payer (Min 3 000 Ar) | Impôt | Moteur de Paie | Ar | Non | Non | Non | — | - | **CONFIRMÉ** |
| `RETENUE_ISI_5` | Retenue Libératoire ISI (5%) | Impôt | Moteur de Paie | % / Ar | Non | Non | Non | — | - | **CONFIRMÉ** |
| `RETENUE_ACOMPTE_15`| Acompte de Quinzaine | Trésorerie | DAF / Trésorerie | Ar | Non | Non | Non | Non | - | **CONFIRMÉ** |
| `RETENUE_AVANCE_SPECIALE`| Avance sur Salaire (Plafond 1/10)| Trésorerie | DAF / Trésorerie | Ar | Non | Non | Non | Non | - | **CONFIRMÉ** |
| `RETENUE_ECHEANCE_PRET`| Échéance Prêt Interne (Plafond 1/3)| Trésorerie | Échéancier prêt | Ar | Non | Non | Non | Non | - | **CONFIRMÉ** |
| `RETENUE_CALL_PERSO` | Facturation Téléphonie Privée | Trésorerie | DAF / Facture | Ar | Non | Non | Non | Non | - | **CONFIRMÉ** |
| `RETENUE_DIVERSE` | Retenue Nette Exceptionnelle | Trésorerie | DAF | Ar | Non | Non | Non | Non | - | **CONFIRMÉ** |
| `REGULARISATION_PAIE`| Régularisation Mois Antérieur | Neutre | DAF | Ar | Variable | Variable | Variable | Variable | Variable | **CONFIRMÉ** |
| `INDEMNITE_COMPENSATRICE_CONGE`| ICCP au STC | Gain | STC / Congés | j / Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `INDEMNITE_PREAVIS` | Préavis non exécuté payé | Gain | STC / Contrat | Ar | Oui | Oui | Oui | Oui | Oui | **CONFIRMÉ** |
| `INDEMNITE_LICENCIEMENT`| Indemnité rupture légale | Gain net | STC / Code Travail | Ar | Non | Non | Non | Non | Oui | **CONFIRMÉ** |
| `AJUSTEMENT_ARRONDI`| Arrondi Monétaire Dizaine Ariary | Net | Moteur de Paie | Ar | Non | Non | Non | Non | $\pm$ | **CONFIRMÉ** |

---

## 3. Matrice Détaillée des 30 Questions par Variable

### 3.1 Rémunération de Base & Temps Non Travaillé

#### 1. SALAIRE_BASE (Salaire de Base Contractuel)
- **1. Nature** : Rémunération contractuelle mensuelle due pour un temps complet plein.
- **2. Famille** : Gain.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Montant (Ariary).
- **5. Source** : Contrat de travail (`employe_affectation_carriere.salaire_base`).
- **6. Saisie / Calcul** : Saisie contractuelle.
- **7. Formule** : Valeur contractuelle brute si mois complet.
- **8. Assiette** : Contrat.
- **9. Taux** : —
- **10. Plafond** : —
- **11. Minimum** : Doit être $\ge \text{SME}$ légal.
- **12. Seuil** : —
- **13. Proratisable** : Oui (si entrée/sortie en cours de mois $\rightarrow$ `BASE_PRORATA`).
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : Arrondi à 2 décimales `HALF_UP`.
- **16. Moment arrondi** : Calcul final de la ligne.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui (après cotisations et impôts).
- **22. Coût Employeur** : Oui (+ cotisations patronales).
- **23. Droits Congés** : Oui (composante majeure de $B_{totale}$).
- **24. Autre assiette** : Sert de base au taux horaire ($S/173{,}33$) et journalier ($S/30$).
- **25. Date d'effet** : Oui (historisé dans la carrière).
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Oui (uniquement régime Salarié).
- **30. Affichage** : Oui (Ligne N° 10 du bulletin).

---

#### 2. RETENUE_ABSENCE (Retenue pour Absences Décomptées)
- **1. Nature** : Déduction de salaire pour heures non travaillées hors congés autorisés.
- **2. Famille** : Retenue sur salaire brut.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Minutes d'absence (converties en heures) et montant Ariary.
- **5. Source** : Snapshot de pointage scellé (`pointage_cumul_periode.minutes_absence_finale`).
- **6. Saisie / Calcul** : Calculée à partir des présences et de la compensation hebdomadaire.
- **7. Formule** : $\text{Montant} = \text{Salaire Base} \times \frac{\text{Minutes Absence}}{173{,}33 \times 60}$.
- **8. Assiette** : Salaire de base contractuel.
- **9. Taux** : Taux horaire = $\text{Base} / 173{,}33$.
- **10. Plafond** : Plafonnée au salaire de base (ne peut dépasser le salaire de la période).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès la première minute d'absence injustifiée.
- **13. Proratisable** : Linéaire à la minute.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : Pleine précision intermédiaire, arrondi à 2 décimales `HALF_UP` en montant.
- **16. Moment arrondi** : Arrondi monétaire de la ligne.
- **17. Cotisable CNaPS** : Déductible (diminue l'assiette brute cotisable).
- **18. Cotisable Santé** : Déductible.
- **19. Imposable IRSA** : Déductible (diminue le revenu brut).
- **20. Inclus Brut** : Déduit du Brut.
- **21. Inclus Net** : Déduit du Net.
- **22. Coût Employeur** : Diminue le coût employeur.
- **23. Droits Congés** : Réduit l'acquisition des congés ($F = 30 - J_{abs}$).
- **24. Autre assiette** : Diminue l'assiette de toutes les cotisations.
- **25. Date d'effet** : Dépend du diviseur (173.33) en vigueur.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui (inapplicable aux cadres forfaités sans pointage).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Oui (Salariés permanents).
- **30. Affichage** : Oui (Ligne N° 20 du bulletin avec mention des heures exactes).

---

#### 3. DEDUCTION_CONGE_BASE (Déduction Congés Pris - Trentième)
- **1. Nature** : Retenue de la part de salaire mensuel forfaitaire afférente aux jours de congés pris.
- **2. Famille** : Retenue sur salaire brut.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Jours calendaires de congés.
- **5. Source** : Module Congés (`mouvement_solde_conge` / validation DAF).
- **6. Saisie / Calcul** : Calculée.
- **7. Formule** : $\text{Montant} = \text{Salaire Base} \times \frac{J_{\text{conge}}}{30{,}0}$.
- **8. Assiette** : Salaire de base contractuel.
- **9. Taux** : Taux journalier trentième = $\text{Base} / 30$.
- **10. Plafond** : Plafonnée au salaire de base (pour 30 jours de congés).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès 0.5 jour de congé validé.
- **13. Proratisable** : Au demi-jour ou jour calendaire.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : Arrondi à 2 décimales `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Déductible (neutralisé par l'indemnité versée).
- **18. Cotisable Santé** : Déductible.
- **19. Imposable IRSA** : Déductible.
- **20. Inclus Brut** : Déduit du Brut.
- **21. Inclus Net** : Déduit du Net.
- **22. Coût Employeur** : Diminue le brut avant réinjection de l'indemnité.
- **23. Droits Congés** : Les jours de congés ne réduisent pas la formule d'acquisition ($F$).
- **24. Autre assiette** : Base nette de congé.
- **25. Date d'effet** : Dépend du diviseur congés (30 jours).
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 25 : "Déduction Salaire Congé (1/30e)").

---

#### 4. INDEMNITE_CONGE_PAYE (Indemnité de Congés Payés)
- **1. Nature** : Rémunération légale de maintien de salaire pendant le congé (Art. 90 Code du Travail).
- **2. Famille** : Gain brut.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Jours de congés et Montant (Ar).
- **5. Source** : Grand-livre de valorisation des congés (`conge_taux_applicable`).
- **6. Saisie / Calcul** : Calculée ($J_{\text{conge}} \times \text{TCONGE}$).
- **7. Formule** : $\text{Montant} = J_{\text{conge}} \times \text{TCONGE}_{\text{historique}}$.
- **8. Assiette** : Taux moyen journalier historique du cycle précédent.
- **9. Taux** : $\text{TCONGE} = \sum V_{12} / \sum D_{12}$. Si nouveau embauché sans historique : $\text{Base}/30$.
- **10. Plafond** : —
- **11. Minimum** : L'indemnité globale ne peut pas être inférieure au salaire continué (règle du plus favorable Art. 90).
- **12. Seuil** : Dès 0.5 jour de congé validé.
- **13. Proratisable** : Oui.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : Arrondi à 2 décimales `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui (+ cotisations patronales).
- **23. Droits Congés** : Oui (inclus dans $S_{net\_base}$ pour valoriser les futurs droits).
- **24. Autre assiette** : Assiette de cotisations et impôt.
- **25. Date d'effet** : Oui (période de validité du TCONGE).
- **26. Dépend Salarié** : Oui (propre à l'historique du collaborateur).
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 26 : "Indemnité Congés Payés (TCONGE)").

---

### 3.2 Heures Supplémentaires et Sujétions

#### 5. HEURES_SUP_30 (Heures Supplémentaires Majorées à 30%)
- **1. Nature** : Rémunération des heures travaillées entre la 41e et la 48e heure hebdomadaire.
- **2. Famille** : Gain brut.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Heures (ou minutes) et Ariary.
- **5. Source** : Snapshot de pointage scellé (`minutes_hs30_finales`).
- **6. Saisie / Calcul** : Calculée par compensation hebdomadaire.
- **7. Formule** : $\text{Montant} = \text{Heures HS30} \times \text{Taux Horaire} \times (1 + 0{,}30)$.
- **8. Assiette** : Taux horaire de base ($\text{Base} / 173{,}33$).
- **9. Taux** : 130 % du taux horaire (Majoration +30%).
- **10. Plafond** : Plafond légal hebdomadaire : 8 heures par semaine.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Au-delà de 40 heures de travail effectif hebdomadaire.
- **13. Proratisable** : À la minute.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : Arrondi à 2 décimales `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui (dans la limite du plafond global CNaPS).
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : **Exonéré d'IRSA dans la limite du plafond mensuel de 20h/mois** (CGI Art. 01.03.03-9° pour les non-cadres). Imposable au-delà.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : **Oui** (règle Galaxam documentée : les HS entrent dans $B_{totale}$).
- **24. Autre assiette** : Alimente le compteur d'exonération fiscale IRSA.
- **25. Date d'effet** : Oui (`ref_taux_majoration`).
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui (exclus pour les Cadres).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 30 du bulletin).

---

#### 6. HEURES_SUP_50 (Heures Supplémentaires Majorées à 50%)
- **1. Nature** : Rémunération des heures travaillées au-delà de la 48e heure hebdomadaire.
- **2. Famille** : Gain brut.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Heures et Ariary.
- **5. Source** : Snapshot de pointage scellé (`minutes_hs50_finales`).
- **6. Saisie / Calcul** : Calculée par compensation hebdomadaire.
- **7. Formule** : $\text{Montant} = \text{Heures HS50} \times \text{Taux Horaire} \times (1 + 0{,}50)$.
- **8. Assiette** : Taux horaire de base ($\text{Base} / 173{,}33$).
- **9. Taux** : 150 % du taux horaire (Majoration +50%).
- **10. Plafond** : Plafond légal de 20 heures sup hebdo au total.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès la 49e heure hebdomadaire.
- **13. Proratisable** : À la minute.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Exonéré si le contingent de 20h/mois n'est pas épuisé par la tranche 30%, imposable pour le solde.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : Oui (inclus dans $B_{totale}$).
- **24. Autre assiette** : Compteur d'exonération fiscale.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui (exclus pour les Cadres).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 40 du bulletin).

---

#### 7. MAJORATION_NUIT (Travail de Nuit 22h-05h)
- **1. Nature** : Majoration d'incommodité pour travail de nuit (Art. 80 Code Travail).
- **2. Famille** : Gain brut (complément de sujétion).
- **3. Qui supporte** : Employeur.
- **4. Unité** : Heures et Ariary.
- **5. Source** : Snapshot de pointage scellé (`heures_nuit_total`).
- **6. Saisie / Calcul** : Calculée par partition temporelle.
- **7. Formule** : $\text{Montant} = \text{Heures Nuit} \times \text{Taux Horaire} \times 0{,}30$ (la base 1.0 étant déjà payée dans le salaire ou les HS).
- **8. Assiette** : Taux horaire de base.
- **9. Taux** : +30 % (habituel ou occasionnel selon convention).
- **10. Plafond** : 7 heures max par vacation de nuit (22h à 05h).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Travail entre 22h00 et 05h00.
- **13. Proratisable** : À la minute.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui (non couverte par l'exonération des 20h HS).
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : Oui (rémunération brute).
- **24. Autre assiette** : Assiette fiscale et sociale.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 50 du bulletin).

---

#### 8. HEURES_DIMANCHE (Travail du Dimanche)
- **1. Nature** : Majoration pour privation du repos hebdomadaire dominical (Art. 81 Code Travail).
- **2. Famille** : Gain brut.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Heures et Ariary.
- **5. Source** : Snapshot de pointage scellé (`heures_dimanche_total`).
- **6. Saisie / Calcul** : Calculée.
- **7. Formule** :
  - *Heures normales de dimanche* : $\text{Heures} \times \text{Taux Horaire} \times 0{,}40$ (la base est dans le salaire).
  - *Heures sup de dimanche* : $\text{Heures} \times \text{Taux Horaire} \times (1 + \text{Taux HS} + 0{,}40)$.
- **8. Assiette** : Taux horaire de base.
- **9. Taux** : +40 %.
- **10. Plafond** : —
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Travail effectué un dimanche de 00h00 à 24h00.
- **13. Proratisable** : À la minute.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : Oui.
- **24. Autre assiette** : Assiette sociale et fiscale.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 60 du bulletin).

---

#### 9. MAJORATION_FERIE (Travail Jour Férié Chômé)
- **1. Nature** : Majoration pour travail accompli lors d'une fête légale chômée et payée.
- **2. Famille** : Gain brut.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Heures et Ariary.
- **5. Source** : Snapshot de pointage scellé croisé au calendrier férié.
- **6. Saisie / Calcul** : Calculée.
- **7. Formule** : $\text{Heures} \times \text{Taux Horaire} \times 1{,}00$ (+100% en sus du salaire continué).
- **8. Assiette** : Taux horaire de base.
- **9. Taux** : +100 % (doublement du salaire horaire).
- **10. Plafond** : —
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Travail un jour férié officiel déclaré.
- **13. Proratisable** : À la minute.
- **14. Arrondi** : Oui.
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : Oui.
- **24. Autre assiette** : Assiette sociale et fiscale.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 70 du bulletin).

---

### 3.3 Primes et Indemnités d'Exploitation

#### 10. PRIME_PERFORMANCE (Prime de Performance Proratisée)
- **1. Nature** : Rémunération variable récompensant la productivité et la présence.
- **2. Famille** : Gain brut (Prime).
- **3. Qui supporte** : Employeur.
- **4. Unité** : Montant Ariary.
- **5. Source** : DAF (`paie_avance_acompte` avec `type_mouvement = 'PRIME_PERFORMANCE'`).
- **6. Saisie / Calcul** : Saisie d'une base brute, puis proratisée par le moteur.
- **7. Formule** : $\text{Base} \times \frac{\text{Jours Présents}}{\text{Jours Ouvrables}} \times (1 - \text{Malus})$.
- **8. Assiette** : Montant saisi par la DAF.
- **9. Taux** : Prorata présences, malus pénalité 25% si retards > 70 min et congés > 1j.
- **10. Plafond** : Plafonnée à la base saisie.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Présence au cours du mois.
- **13. Proratisable** : **Oui** (au prorata des jours de travail effectif).
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : **À confirmer** (actuellement exclu par `PayrollService.java`, seule `netBase + HS` est incluse).
- **24. Autre assiette** : Brut imposable et social.
- **25. Date d'effet** : Oui (seuils configurables).
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 90 : mention du prorata et de la pénalité éventuelle).

---

#### 11. PRIME_ASSIDUITE (Prime d'Assiduité)
- **1. Nature** : Forfait récompensant la ponctualité rigoureuse.
- **2. Famille** : Gain brut (Prime).
- **3. Qui supporte** : Employeur.
- **4. Unité** : Montant forfaitaire (Ar).
- **5. Source** : DAF / Paramètres (`SEUIL_RETARD_ASSIDUITE_MIN = 45`).
- **6. Saisie / Calcul** : Conditionnelle automatique.
- **7. Formule** : Forfait plein si $\text{Retards Cumulés} < 45\text{ min}$, sinon **0 Ar** (tout ou rien).
- **8. Assiette** : Forfait saisi ou paramétré.
- **9. Taux** : —
- **10. Plafond** : Montant du forfait.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Perte totale dès la 45e minute de retard cumulé.
- **13. Proratisable** : Non (binaire).
- **14. Arrondi** : Non.
- **15. Règle arrondi** : —
- **16. Moment arrondi** : —
- **17. Cotisable CNaPS** : Oui.
- **18. Cotisable Santé** : Oui.
- **19. Imposable IRSA** : Oui.
- **20. Inclus Brut** : Oui.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Brut social et fiscal.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 100 du bulletin).

---

#### 12. INDEMNITE_REPAS (Indemnité Forfaitaire de Repas)
- **1. Nature** : Participation aux frais de repas de midi pour les journées travaillées.
- **2. Famille** : Gain brut imposable (règle Galaxam actuelle).
- **3. Qui supporte** : Employeur.
- **4. Unité** : Jours de présence et Montant (ex: 2 000 Ar/jour).
- **5. Source** : Pointage scellé (`nb_jours_repas` $\times$ tarif site).
- **6. Saisie / Calcul** : Calculée à partir des jours de présence validés.
- **7. Formule** : $\text{Jours Repas Éligibles} \times \text{Tarif Journalier Site}$. Si cantine/repas fourni : 0 Ar.
- **8. Assiette** : Présence effective de midi.
- **9. Taux** : 2 000 Ar par jour (TNR).
- **10. Plafond** : 1 repas par jour travaillé.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Présence sur la pause méridienne.
- **13. Proratisable** : Strictement proportionnelle aux jours présents.
- **14. Arrondi** : Non (multiple de 1 000 Ar).
- **15. Règle arrondi** : —
- **16. Moment arrondi** : —
- **17. Cotisable CNaPS** : **Oui** (règle actuelle : gain brut payé en fin de mois).
- **18. Cotisable Santé** : **Oui**.
- **19. Imposable IRSA** : **Oui**.
- **20. Inclus Brut** : **Oui**.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : **NON** (exclu explicitement de la valorisation congés).
- **24. Autre assiette** : Déclenche la réduction de 1 000 Ar sur le déplacement à TMM.
- **25. Date d'effet** : Oui (`site_indemnite_config`).
- **26. Dépend Salarié** : Oui (dérogations individuelles possibles).
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : **Oui** (TNR : 2 000 Ar versés ; TMM : repas en nature à 0 Ar).
- **29. Dépend Régime** : Salariés permanents et temporaires.
- **30. Affichage** : Oui (Ligne N° 510 : "Indemnité de Repas").

---

#### 13. INDEMNITE_TRANSPORT (Indemnité Forfaitaire de Déplacement)
- **1. Nature** : Prise en charge des frais de transport domicile-travail.
- **2. Famille** : Gain brut imposable (règle Galaxam actuelle).
- **3. Qui supporte** : Employeur.
- **4. Unité** : Montant mensuel proratisé ou tarif journalier.
- **5. Source** : Pointage scellé / Configuration site (`site_indemnite_config`).
- **6. Saisie / Calcul** : Calculée selon les jours travaillés.
- **7. Formule** : $\text{Forfait Site} \times \frac{\text{Jours Présents}}{\text{Jours Ouvrables}} - \text{Réduction Repas Midi (TMM)}$.
- **8. Assiette** : Jours de présence physique.
- **9. Taux** : Tarif site (ex: 28 000 Ar mensuel à TNR, tarif journalier à TMM).
- **10. Plafond** : Plafonnée au forfait mensuel contractuel.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Présence effective sur site.
- **13. Proratisable** : Oui.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : **Oui** (règle actuelle).
- **18. Cotisable Santé** : **Oui**.
- **19. Imposable IRSA** : **Oui**.
- **20. Inclus Brut** : **Oui**.
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui.
- **23. Droits Congés** : **NON** (exclu de $B_{totale}$).
- **24. Autre assiette** : Brut imposable et social.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui (dérogations possibles).
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : **Oui** (règles et forfaits différents TNR vs TMM).
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 500 : "Indemnité de Déplacement").

---

### 3.4 Cotisations Sociales & Charges Patronales

#### 14. COTISATION_CNAPS_SAL (Cotisation CNaPS Salariale 1%)
- **1. Nature** : Cotisation légale de sécurité sociale à la charge du travailleur (Caisse Nationale de Prévoyance Sociale).
- **2. Famille** : Cotisation sociale salariale.
- **3. Qui supporte** : Salarié (retenue sur bulletin).
- **4. Unité** : Pourcentage (1 %) et Montant (Ar).
- **5. Source** : Référentiel légal (`ref_type_cotisation.code = 'CNAPS'`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\min(\text{Assiette Sociale}, 8 \times \text{SME}) \times 1{,}00\,\%$.
- **8. Assiette** : Total des gains bruts soumis à cotisation sociale.
- **9. Taux** : 1,00 %.
- **10. Plafond** : $8 \times \text{SME}$ ($2\,101\,440\text{ Ar}$ jusqu'au 28/02/2026, puis $2\,400\,000\text{ Ar}$ dès le 01/03/2026).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary de rémunération brute.
- **13. Proratisable** : Non (plafond mensuel).
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : — (est la cotisation elle-même).
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : **Déductible** du revenu brut pour former le net imposable.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre (part salariale).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Diminue l'assiette IRSA.
- **25. Date d'effet** : Oui (évolue avec le décret SME).
- **26. Dépend Salarié** : Non (taux universel).
- **27. Dépend Contrat** : Oui (inapplicable aux consultants/temporaires).
- **28. Dépend Site** : Non (caisse nationale).
- **29. Dépend Régime** : Salariés permanents uniquement.
- **30. Affichage** : Oui (Ligne N° 200 du bulletin).

---

#### 15. COTISATION_CNAPS_PAT (Cotisation CNaPS Patronale 13%)
- **1. Nature** : Contribution patronale de sécurité sociale (retraite, accidents du travail, prestations familiales).
- **2. Famille** : Cotisation patronale.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Pourcentage (13 %) et Montant (Ar).
- **5. Source** : Référentiel légal (`ref_type_cotisation`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\min(\text{Assiette Sociale}, 8 \times \text{SME}) \times 13{,}00\,\%$.
- **8. Assiette** : Même assiette plafonnée que la CNaPS salariale.
- **9. Taux** : 13,00 %.
- **10. Plafond** : $8 \times \text{SME}$.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales `HALF_UP`).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : —
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Non (n'impacte pas le net du salarié).
- **22. Coût Employeur** : **OUI** (augmente directement le coût global).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Déclarations trimestrielles DNTS.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Non.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (colonne patronale Ligne N° 300).

---

#### 16. COTISATION_SANTE_SAL (Cotisation Médicale Salariale - OSTIE / OMSI)
- **1. Nature** : Cotisation médicale de santé au travail (médecine d'entreprise).
- **2. Famille** : Cotisation sociale salariale.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Pourcentage (1% à TNR, 1.5% à TMM) et Montant (Ar).
- **5. Source** : Affiliation de site (`site_cotisation`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\text{Assiette Santé} \times \text{Taux Salarial Site}$.
- **8. Assiette** : Assiette brute sociale (plafonnée à $8 \times \text{SME}$ si l'organisme/site est configuré comme plafonné, ce qui est le cas pour OSTIE et OMSI).
- **9. Taux** :
  - OSTIE (TNR) : 1,00 %.
  - OMSI (TMM) : 1,50 %.
- **10. Plafond** : $8 \times \text{SME}$ (`est_plafonne_sante = true`).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : —
- **19. Imposable IRSA** : **Déductible** du revenu brut (déduction sociale officielle CGI).
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre (part salariale).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Diminue l'assiette IRSA.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Non.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : **OUI (DIFFÉRENCE MAJEURE TNR 1% vs TMM 1.5%)**.
- **29. Dépend Régime** : Salariés permanents (et consultants si conventionné).
- **30. Affichage** : Oui (Ligne N° 210 du bulletin avec mention de l'organisme).

---

#### 17. COTISATION_SANTE_PAT (Cotisation Médicale Patronale - OSTIE / OMSI)
- **1. Nature** : Part patronale de médecine d'entreprise.
- **2. Famille** : Charge patronale.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Pourcentage (5% TNR, 5.5% TMM) et Montant (Ar).
- **5. Source** : Affiliation de site (`site_cotisation`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\text{Assiette Santé} \times \text{Taux Patronal Site}$.
- **8. Assiette** : Même assiette que la part salariale.
- **9. Taux** :
  - OSTIE (TNR) : 5,00 %.
  - OMSI (TMM) : 5,50 %.
- **10. Plafond** : $8 \times \text{SME}$.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : —
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Non.
- **22. Coût Employeur** : **OUI**.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Bordereau trimestriel médical.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Non.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : **OUI (TNR 5% vs TMM 5.5%)**.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (colonne patronale Ligne N° 310).

---

#### 18. COTISATION_FMFP_PAT (Fonds Malgache de Formation Professionnelle 1%)
- **1. Nature** : Contribution patronale obligatoire au financement de la formation professionnelle continue (Décret n° 2018-991).
- **2. Famille** : Charge patronale.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Pourcentage (1,00 %) et Montant (Ar).
- **5. Source** : Référentiel légal (`ref_type_cotisation`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\text{Assiette Sociale Totale} \times 1{,}00\,\%$.
- **8. Assiette** : Brut total déplafonné (ou adossé à la DNTS CNaPS selon doctrine).
- **9. Taux** : 1,00 %.
- **10. Plafond** : **Déplafonné** (en droit FMFP) ou plafonné à la CNaPS (en pratique de déclaration conjointe). *Voir Livrable 6 pour arbitrage DAF.*
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Non.
- **22. Coût Employeur** : **OUI**.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Déclaration FMFP trimestrielle.
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Non.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (colonne patronale Ligne N° 330).

---

### 3.5 Fiscalité (IRSA & ISI)

#### 19. IMPOT_IRSA_NET (Impôt sur le Revenu Net à Déduire)
- **1. Nature** : Retenue à la source fiscale sur les salaires (Code Général des Impôts Madagascar).
- **2. Famille** : Impôt.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Montant Ariary.
- **5. Source** : Barème progressif légal (`bareme_irsa`).
- **6. Saisie / Calcul** : Calculé automatiquement par tranche.
- **7. Formule** :
  1. $\text{Assiette} = \text{Net Imposable Social} - \text{HS Exonérées}$.
  2. $\text{Assiette Arrondie} = \text{Assiette} \text{ arrondie à la centaine inférieure}$.
  3. $\text{IRSA Brut} = \sum (\text{Part de tranche} \times \text{Taux tranche})$.
  4. $\text{IRSA Arrondi} = \text{IRSA Brut} \text{ arrondi à la dizaine supérieure}$.
  5. $\text{Réduction Enfants} = \text{Nb Enfants} \times 2\,000\text{ Ar}$.
  6. $\text{IRSA Net} = \max(\text{IRSA Arrondi} - \text{Réduction Enfants}, \text{Minimum Perception})$.
- **8. Assiette** : Revenu Net Imposable Fiscal arrondi à la centaine inférieure.
- **9. Taux** : Barème progressif à 6 tranches :
  - $0$ à $350\,000\text{ Ar}$ : $0\,\%$
  - $350\,001$ à $400\,000\text{ Ar}$ : $5\,\%$
  - $400\,001$ à $500\,000\text{ Ar}$ : $10\,\%$
  - $500\,001$ à $600\,000\text{ Ar}$ : $15\,\%$
  - $600\,001$ à $4\,000\,000\text{ Ar}$ : $20\,\%$
  - $> 4\,000\,000\text{ Ar}$ : $25\,\%$
- **10. Plafond** : Plafonné au salaire net disponible.
- **11. Minimum** : **Minimum légal de perception = 3 000 Ar** (Loi de Finances 2026, anciennement 2 000 Ar).
- **12. Seuil** : Dès que le revenu net imposable est positif.
- **13. Proratisable** : Non (barème mensuel).
- **14. Arrondi** : **OUI (Double règle légale stricte)**.
- **15. Règle arrondi** :
  - Assiette : Centaine d'Ariary inférieure (`CENTAINE_INF`).
  - Impôt brut : Dizaine d'Ariary supérieure (`DIZAINE_SUP`).
- **16. Moment arrondi** : Avant application des tranches (assiette) et avant déduction des charges familiales (impôt).
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : — (est l'impôt lui-même).
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre (impôt du salarié collecté pour le fisc).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : État 301 / SAFI DGI (25 colonnes).
- **25. Date d'effet** : Oui (millésimes fiscaux annuels).
- **26. Dépend Salarié** : Oui (enfants à charge $\le 21$ ans).
- **27. Dépend Contrat** : Oui (exclusif du régime ISI).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents uniquement.
- **30. Affichage** : Oui (Ligne N° 420 : montant net prélevé ; Lignes N° 400 et 410 en informations explicatives).

---

#### 20. RETENUE_ISI_5 (Retenue Libératoire ISI 5%)
- **1. Nature** : Retenue fiscale libératoire à la source pour prestataires et consultants indépendants (+60 ans) (Art. 01.02.04 CGI).
- **2. Famille** : Impôt.
- **3. Qui supporte** : Prestataire / Consultant.
- **4. Unité** : Pourcentage (5 %) et Montant (Ar).
- **5. Source** : Référentiel fiscal légal.
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\text{Total Honoraires Bruts} \times 5{,}00\,\%$.
- **8. Assiette** : Montant brut des honoraires ou prestations.
- **9. Taux** : 5,00 % fixe.
- **10. Plafond** : —
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Dès le premier ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales `HALF_UP`).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de décompte.
- **17. Cotisable CNaPS** : Non (non soumis).
- **18. Cotisable Santé** : Non (sauf si contrat de mise à disposition).
- **19. Imposable IRSA** : **NON** (l'ISI est libératoire de l'IRSA).
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à verser.
- **22. Coût Employeur** : Neutre.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Bordereau e-Hetra ISINT (fichier XML EDI).
- **25. Date d'effet** : Oui.
- **26. Dépend Salarié** : Oui (profil consultant +60 ans ou prestataire temporaire).
- **27. Dépend Contrat** : Oui (`PRESTATION`, `CONSULTANT`).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Régime ISI exclusif.
- **30. Affichage** : Oui (Ligne N° 430 sur le décompte d'honoraires).

---

### 3.6 Retenues Financières et Trésorerie

#### 21. RETENUE_ACOMPTE_15 (Acompte de Quinzaine)
- **1. Nature** : Paiement anticipé en milieu de mois sur le salaire déjà acquis (travail échu).
- **2. Famille** : Retenue de trésorerie.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Montant Ariary.
- **5. Source** : Module Trésorerie DAF (`paie_avance_acompte`).
- **6. Saisie / Calcul** : Saisie approuvée et confirmée payée.
- **7. Formule** : $\min(\text{Montant Demandé}, \text{Net Disponible Avant Trésorerie})$.
- **8. Assiette** : Net disponible avant déductions personnelles.
- **9. Taux** : —
- **10. Plafond** : Plafonné à 100 % du net disponible (priorité N° 1).
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Versement effectif confirmé au 15 du mois.
- **13. Proratisable** : Non.
- **14. Arrondi** : Non.
- **15. Règle arrondi** : —
- **16. Moment arrondi** : —
- **17. Cotisable CNaPS** : Non (post-brut).
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Diminue le solde disponible pour les avances et les prêts.
- **25. Date d'effet** : Date de paiement effective.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Non.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Tous.
- **30. Affichage** : Oui (Ligne N° 600 du bulletin).

---

#### 22. RETENUE_AVANCE_SPECIALE (Avance Exceptionnelle sur Salaire)
- **1. Nature** : Prêt consenti par l'employeur sur le travail futur (Art. 68 Code Travail).
- **2. Famille** : Retenue de trésorerie.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Montant Ariary.
- **5. Source** : Demande DAF approuvée avec suivi du solde restant.
- **6. Saisie / Calcul** : Saisie d'un capital, amorti avec respect du plafond légal.
- **7. Formule** : $\min(\text{Solde Restant Dû}, \text{Plafond 1/10e Légal})$.
- **8. Assiette** : Net disponible après acompte de quinzaine.
- **9. Taux** : Plafond légal = **10,00 % (1/10e)** du salaire net disponible.
- **10. Plafond** : $\text{Net Après Acompte} \times 0{,}10$.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Existence d'une avance en cours.
- **13. Proratisable** : Non.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Diminue la quotité cessible restante pour les prêts.
- **25. Date d'effet** : Date de déblocage.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Non.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Tous.
- **30. Affichage** : Oui (Ligne N° 610 du bulletin avec rappel du solde restant).

---

#### 23. RETENUE_ECHEANCE_PRET (Échéance de Prêt d'Entreprise)
- **1. Nature** : Mensualité de remboursement de prêt interne accordé par l'entreprise.
- **2. Famille** : Retenue de trésorerie.
- **3. Qui supporte** : Salarié.
- **4. Unité** : Montant Ariary.
- **5. Source** : Échéancier de prêt actif (`paie_pret_echeance`).
- **6. Saisie / Calcul** : Échéance contractuelle amortie selon capacité légale.
- **7. Formule** : $\min(\text{Mensualité Échue}, \text{Capacité Quotité Cessible Restante})$.
- **8. Assiette** : Net après acompte.
- **9. Taux** : Quotité cessible maximale = **33,33 % (1/3)** (Art. 72 Code du Travail).
- **10. Plafond** : $(\text{Net Après Acompte} \times 0{,}3333) - \text{Avances Retenues}$.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Prêt actif avec échéance non échue.
- **13. Proratisable** : Non (si la capacité est insuffisante, l'échéance est prélevée partiellement et le solde reporté).
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne de bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Déduit du Net à payer.
- **22. Coût Employeur** : Neutre.
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Tableau d'amortissement du prêt (`capital_restant_du`).
- **25. Date d'effet** : Date de première échéance.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Non (apurement obligatoire au STC en cas de départ).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés.
- **30. Affichage** : Oui (Ligne N° 620 : "Échéance Prêt N° X").

---

### 3.7 Éléments de Sortie et Solde de Tout Compte (STC)

#### 24. INDEMNITE_COMPENSATRICE_CONGE (ICCP au STC)
- **1. Nature** : Indemnisation financière du solde de congés payés acquis non pris lors de la rupture de contrat (Art. 90 Code Travail).
- **2. Famille** : Gain brut de rupture.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Jours de solde restant et Montant Ariary.
- **5. Source** : Grand-livre des congés (`VSoldeCongeEmploye.solde_actuel`).
- **6. Saisie / Calcul** : Calculée automatiquement.
- **7. Formule** : $\text{Solde Jours Restants} \times \text{TCONGE}$. Si pas de TCONGE : $\text{Solde} \times \frac{\text{Dernier Salaire}}{30}$.
- **8. Assiette** : Solde de jours réels audité dans le grand-livre.
- **9. Taux** : TCONGE.
- **10. Plafond** : Plafonné aux droits réellement acquis.
- **11. Minimum** : 0 Ar.
- **12. Seuil** : Rupture effective du contrat.
- **13. Proratisable** : Proportionnel au solde exact (au centième de jour).
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne STC.
- **17. Cotisable CNaPS** : **OUI** (élément substitutif de salaire soumis à cotisations).
- **18. Cotisable Santé** : **OUI**.
- **19. Imposable IRSA** : **OUI**.
- **20. Inclus Brut** : Oui (Total Brut STC).
- **21. Inclus Net** : Oui.
- **22. Coût Employeur** : Oui (+ cotisations patronales).
- **23. Droits Congés** : Non (clôture des droits).
- **24. Autre assiette** : Assiette sociale et fiscale STC.
- **25. Date d'effet** : Date de départ.
- **26. Dépend Salarié** : Oui.
- **27. Dépend Contrat** : Oui.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés permanents.
- **30. Affichage** : Oui (Ligne N° 700 sur le reçu STC).

---

#### 25. INDEMNITE_LICENCIEMENT (Indemnité Légale de Licenciement)
- **1. Nature** : Dédommagement légal pour rupture à l'initiative de l'employeur hors faute lourde (Art. 35 Code Travail).
- **2. Famille** : Indemnité légale de rupture.
- **3. Qui supporte** : Employeur.
- **4. Unité** : Montant Ariary.
- **5. Source** : Moteur d'ancienneté (`AncienneteEngine`) et barème légal/conventionnel.
- **6. Saisie / Calcul** : Calculée ou arbitrée par la DAF.
- **7. Formule** : Calculée selon les tranches d'ancienneté réelle continue (ex: 10 à 20 jours de salaire par année de service).
- **8. Assiette** : Salaire moyen des 12 derniers mois.
- **9. Taux** : Selon barème conventionnel.
- **10. Plafond** : —
- **11. Minimum** : Plancher légal d'ancienneté.
- **12. Seuil** : Motif de rupture = LICENCIEMENT (hors faute grave).
- **13. Proratisable** : Au mois d'ancienneté.
- **14. Arrondi** : Oui (2 décimales).
- **15. Règle arrondi** : `HALF_UP`.
- **16. Moment arrondi** : Ligne STC.
- **17. Cotisable CNaPS** : **NON (EXONÉRATION SOCIALE LÉGALE STRICTE)**.
- **18. Cotisable Santé** : **NON**.
- **19. Imposable IRSA** : **NON (EXONÉRATION FISCALE LÉGALE DÉMANDÉE)**.
- **20. Inclus Brut** : Non inclus dans le brut imposable/cotisable (versé directement en net).
- **21. Inclus Net** : Oui (versé sur le reçu libératoire).
- **22. Coût Employeur** : Oui (coût sec entreprise).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Reçu de Solde de Tout Compte.
- **25. Date d'effet** : Date de notification du licenciement.
- **26. Dépend Salarié** : Oui (ancienneté exacte).
- **27. Dépend Contrat** : Oui (CDI uniquement).
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Salariés.
- **30. Affichage** : Oui (Ligne N° 720 du reçu STC).

---

#### 26. AJUSTEMENT_ARRONDI (Ajustement Monétaire du Net)
- **1. Nature** : Régularisation d'écart monétaire pour assurer un paiement bancaire exact en dizaine d'Ariary.
- **2. Famille** : Écart monétaire net.
- **3. Qui supporte** : Salarié (si retenue d'arrondi) ou Employeur (si gain d'arrondi).
- **4. Unité** : Montant Ariary (entre $-5{,}00\text{ Ar}$ et $+5{,}00\text{ Ar}$).
- **5. Source** : Moteur de paie (`MoneyRoundingPolicy.netPay`).
- **6. Saisie / Calcul** : Calculé automatiquement.
- **7. Formule** : $\text{Net à Payer Arrondi} - \text{Net Avant Arrondi}$.
- **8. Assiette** : Net à payer théorique.
- **9. Taux** : —
- **10. Plafond** : $\le 5{,}00\text{ Ar}$.
- **11. Minimum** : $\ge -5{,}00\text{ Ar}$.
- **12. Seuil** : Si le net calculé n'est pas un multiple de 10 Ariary.
- **13. Proratisable** : Non.
- **14. Arrondi** : Arrondi à la dizaine d'Ariary la plus proche (`HALF_UP`).
- **15. Règle arrondi** : `round(valeur / 10) * 10`.
- **16. Moment arrondi** : Ultime étape de calcul du bulletin.
- **17. Cotisable CNaPS** : Non.
- **18. Cotisable Santé** : Non.
- **19. Imposable IRSA** : Non.
- **20. Inclus Brut** : Non.
- **21. Inclus Net** : Oui (ajustement final du virement).
- **22. Coût Employeur** : Impact marginal ($\pm 5\text{ Ar}$).
- **23. Droits Congés** : Non.
- **24. Autre assiette** : Fichier d'ordre de virement bancaire.
- **25. Date d'effet** : Non.
- **26. Dépend Salarié** : Non.
- **27. Dépend Contrat** : Non.
- **28. Dépend Site** : Non.
- **29. Dépend Régime** : Tous.
- **30. Affichage** : Oui si non nul (Ligne N° 999 : "Ajustement Arrondi Monétaire").
