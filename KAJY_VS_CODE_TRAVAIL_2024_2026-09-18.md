# CONFRONTATION MATHÉMATIQUE & FONCTIONNELLE : KAJY VS CODE DU TRAVAIL 2024

**Date :** 18 Septembre 2026  
**Auteur :** Antigravity Agentic Pair Programmer  
**Objet :** Analyse contradictoire mathématique des formules KAJY (GALAXAM_CURRENT_BUSINESS_REFERENCE) face aux exigences de l'Article 131 de la Loi n° 2024-014.  
**Statut :** AUDIT ONLY — Baseline technique 499 tests préservée.

---

## 1. DÉFINITIONS FORMELLES DES FORMULES

### 1.1. Modèle KAJY (Pratique Historique GALAXAM)

Dans le fichier de référence `OHATRA KAJY DROITS CONGES-given on sept26.xls` et dans le runtime SIRH :

1. **Acquisition mensuelle de droits :**
   $$\text{Formule}(m) = \max\Big(0, \, 30 - \text{AbsInjustifiees}(m)\Big)$$
   $$\text{DroitMois}(m) = 2{,}5 \times \frac{\text{Formule}(m)}{30}$$
2. **Base de valorisation mensuelle :**
   $$\text{NET\_BASE}(m) = \text{SalaireBase}(m) - \text{RetenueAbsence}(m) - \text{RetenueConge}(m) + \text{IndemniteConge}(m)$$
   $$\text{BaseTotale}(m) = \text{NET\_BASE}(m) + \text{HeuresSupplementaires}(m)$$
   *(Remarque capitale : AUTIMP / Primes est formellement exclu de $\text{BaseTotale}$)*.
3. **Valorisation mensuelle :**
   $$\text{ValoMois}(m) = \text{BaseTotale}(m) \times \frac{\text{DroitMois}(m)}{30}$$
4. **Taux journalier terminal (TCONGE) calculé sur une campagne de 12 mois :**
   $$\text{TCONGE}_{\text{KAJY}} = \frac{\sum_{m=1}^{12} \text{ValoMois}(m)}{\sum_{m=1}^{12} \text{DroitMois}(m)}$$
5. **Valorisation en paie d'un congé pris au mois $p$ (débit $N$ jours) :**
   $$\text{Indemnité}(p) = N \times \text{TCONGE}_{\text{applicable}}$$
   $$\text{DéductionBase}(p) = N \times \frac{\text{SalaireBase}(p)}{30}$$

---

### 1.2. Modèle Légal Impératif (Article 131 Loi n° 2024-014)

L'article 131 prescrit une règle d'ordre public de plancher social :

> *« L'employeur doit verser au travailleur, pendant toute la durée du congé, une allocation au moins égale au douzième (1/12) des salaires et des divers éléments de rémunération définis par la présente loi dont le travailleur bénéficiait au cours des douze (12) mois ayant précédé la date du congé, à l'exception des remboursements de frais. »*

1. **Fenêtre légale :** Les 12 mois mobiles précédant immédiatement la date de départ en congé ($\text{Mois } p-12 \text{ à } p-1$).
2. **Assiette légale :**
   $$\text{RemunLegale}(m) = \text{SalaireBase}(m) + \text{HeuresSupplementaires}(m) + \text{Primes}(m) + \text{Commissions}(m) + \text{AvantagesNature}(m) - \text{RetenuesAbsences}(m)$$
   *(À l'exclusion stricte des remboursements de frais réels professionnels)*.
3. **Allocation globale pour 30 jours calendaires de congé :**
   $$\text{AllocAnnuelleLegale} = \frac{1}{12} \sum_{k=1}^{12} \text{RemunLegale}(p-k)$$
4. **Taux journalier légal plancher (pour un décompte en 30 jours calendaires) :**
   $$\text{TauxJournalierLegal} = \frac{\text{AllocAnnuelleLegale}}{30} = \frac{\sum_{k=1}^{12} \text{RemunLegale}(p-k)}{360}$$
5. **Principe de faveur (Art. 2) :**
   L'indemnité versée doit satisfaire :
   $$\text{IndemnitéVersée} \ge N \times \text{TauxJournalierLegal}$$

---

## 2. CONDITIONS D'ÉQUIVALENCE MATHÉMATIQUE EXACTE

Les deux formules sont-elles mathématiquement équivalentes dans certains cas ? **OUI.**

### Théorème d'équivalence nominale :
Si un travailleur se trouve dans les conditions suivantes :
1. Salaire de base constant $S$ sur les 24 derniers mois ;
2. Aucune absence injustifiée ($\text{DroitMois} = 2{,}5$ pour chaque mois) ;
3. Aucune prime ni commission ($AUTIMP = 0$) ;
4. Aucune heure supplémentaire ($HS = 0$) ;
5. Les congés pris dans l'année sont indemnisés à $S/30$ ;

Alors :
- **Dans KAJY :**
  $$\text{DroitMois} = 2{,}5 \quad \Longrightarrow \quad \sum_{m=1}^{12} \text{DroitMois}(m) = 30{,}0$$
  $$\text{BaseTotale}(m) = S$$
  $$\text{ValoMois}(m) = S \times \frac{2{,}5}{30} = \frac{S}{12}$$
  $$\sum_{m=1}^{12} \text{ValoMois}(m) = 12 \times \frac{S}{12} = S$$
  $$\text{TCONGE}_{\text{KAJY}} = \frac{S}{30{,}0}$$
- **Sous l'Article 131 :**
  $$\sum_{k=1}^{12} \text{RemunLegale}(p-k) = 12 \times S$$
  $$\text{TauxJournalierLegal} = \frac{12 \times S}{360} = \frac{S}{30{,}0}$$

**Conclusion :** Dans le cas standard (salaire fixe sans primes ni absences), **KAJY et le Code du travail 2024 produisent rigoureusement le même taux journalier au centime près ($S/30$).**

---

## 3. DIVERGENCES MATHÉMATIQUES & SCÉNARIOS COMPARATIFS (A À I)

Hypothèse de base générale : Salaire de base nominal de 300 000 Ar/mois (soit 10 000 Ar/jour sur base 30). Congé pris de 10 jours au mois 18.

---

### Scénario A : Salaire constant, aucune absence, aucune prime
- Salaire : 300 000 Ar/mois constant.
- KAJY : $\text{CumulValo} = 300\,000$, $\text{CumulDroits} = 30$, $\text{TCONGE} = 10\,000{,}00$ Ar/j.
- Runtime SIRH : $10\,000{,}00$ Ar/j.
- Article 131 : $12 \times 300\,000 / 360 = 10\,000{,}00$ Ar/j.
- **Écart :** 0 Ar (0,00 %).
- **Qualification :** `LEGAL_MATCH`.

---

### Scénario B : Augmentation salariale au mois 7 (ou entre campagne et prise de congé)
- Campagne 1 (Mois 1 à 12) : Salaire = 300 000 Ar. Taux calculé à la fin du Mois 12 = 10 000,00 Ar/j.
- Au Mois 13 : Augmentation à 600 000 Ar/mois.
- Le salarié prend 10 jours de congés au Mois 18 (soit 6 mois après son augmentation).
- **KAJY / Runtime SIRH :**
  Applique le taux de la campagne 1 clôturée = **10 000,00 Ar/j**.  
  Indemnité versée pour 10 jours = $10 \times 10\,000 = 100\,000$ Ar.
- **Article 131 (12 mois précédant le congé = Mois 6 à 17) :**
  Mois 6 à 12 (7 mois) à 300 000 Ar = 2 100 000 Ar.  
  Mois 13 à 17 (5 mois) à 600 000 Ar = 3 000 000 Ar.  
  Total rémunération 12 mois = 5 100 000 Ar.  
  Allocation légale annuelle pour 30 jours = $5\,100\,000 / 12 = 425\,000$ Ar.  
  Taux journalier légal = $425\,000 / 30 = \mathbf{14\,166{,}67}$ **Ar/j**.  
  Indemnité légale minimale pour 10 jours = **141 666,67 Ar**.
- **Écart :** $-41\,666{,}67$ Ar (KAJY est **inférieur de 29,4 %** au minimum légal).
- **Cause de l'écart :** L'inertie temporelle du modèle de campagne annuelle figée face à la fenêtre glissante légale de l'article 131.
- **Qualification :** `LEGAL_DIVERGENCE` (Risque majeur de sous-paiement).

---

### Scénario C : Heures Supplémentaires variables
- Salaire fixe : 300 000 Ar.
- HS versées : 50 000 Ar/mois pendant 6 mois sur les 12 mois de référence (Total HS = 300 000 Ar).
- **KAJY / Runtime SIRH :**
  $\text{BaseTotale}$ intègre les HS ($NET\_BASE + HS$).  
  $\text{CumulValo} = 12 \times 300\,000 \times \frac{2{,}5}{30} + 6 \times 50\,000 \times \frac{2{,}5}{30} = 300\,000 + 25\,000 = 325\,000$ Ar.  
  $\text{CumulDroits} = 30{,}0$.  
  $\text{TCONGE} = 325\,000 / 30 = \mathbf{10\,833{,}33}$ **Ar/j**.
- **Article 131 :**
  Total rémunération des 12 mois = $12 \times 300\,000 + 300\,000 = 3\,900\,000$ Ar.  
  Taux journalier = $3\,900\,000 / 360 = \mathbf{10\,833{,}33}$ **Ar/j**.
- **Écart :** 0 Ar (0,00 %).
- **Cause de l'écart :** KAJY intègre fidèlement les HS dans $\text{BaseTotale}$.
- **Qualification :** `LEGAL_MATCH`.

---

### Scénario D : Primes régulières (AUTIMP = 100 000 Ar/mois)
- Salaire fixe : 300 000 Ar/mois.
- Prime de performance / production mensuelle ($AUTIMP$) : 100 000 Ar/mois chaque mois.
- **KAJY / Runtime SIRH :**
  KAJY **exclut formellement AUTIMP** de $\text{BaseTotale}$.  
  $\text{CumulValo} = 300\,000$ Ar. $\text{CumulDroits} = 30{,}0$.  
  $\text{TCONGE} = \mathbf{10\,000{,}00}$ **Ar/j**.
- **Article 131 (combiné avec Article 88) :**
  L'article 88 impose d'intégrer les primes dans la moyenne mensuelle des 12 mois.  
  Rémunération annuelle totale = $12 \times (300\,000 + 100\,000) = 4\,800\,000$ Ar.  
  Allocation annuelle = $4\,800\,000 / 12 = 400\,000$ Ar.  
  Taux journalier légal = $400\,000 / 30 = \mathbf{13\,333{,}33}$ **Ar/j**.
- **Écart :** $-3\,333{,}33$ Ar/j (KAJY est **inférieur de 25,0 %** au plancher légal).
- **Cause de l'écart :** L'exclusion d'AUTIMP par KAJY alors que l'article 88 qualifie les primes d'éléments de rémunération obligatoires dans l'assiette du congé.
- **Qualification :** `LEGAL_DIVERGENCE` (Sous-évaluation systématique si les primes constituent du salaire).

---

### Scénario E : Absence injustifiée (1 mois avec 6 jours d'absence)
- Salaire fixe : 300 000 Ar/mois.
- Au mois 3 : 6 jours d'absences injustifiées.
  - Retenue sur salaire : $6 \times 10\,000 = 60\,000$ Ar. Salaire net perçu = 240 000 Ar.
  - Droit du mois : $\text{Formule} = 30 - 6 = 24$. $\text{DroitMois} = 2{,}5 \times 24 / 30 = 2{,}0$ jours.
- **KAJY :**
  $\text{ValoMois}(3) = 240\,000 \times \frac{2{,}0}{30} = 16\,000$ Ar.  
  Les 11 autres mois : $11 \times 25\,000 = 275\,000$ Ar pour $11 \times 2{,}5 = 27{,}5$ jours.  
  $\text{CumulValo} = 275\,000 + 16\,000 = 291\,000$ Ar.  
  $\text{CumulDroits} = 27{,}5 + 2{,}0 = 29{,}5$ jours.  
  $\text{TCONGE}_{\text{KAJY}} = 291\,000 / 29{,}5 = \mathbf{9\,864{,}41}$ **Ar/j**.  
  Pour ses 29,5 jours acquis, valorisation totale = $29{,}5 \times 9\,864{,}41 = \mathbf{291\,000}$ **Ar**.
- **Article 131 :**
  Total salaires perçus sur 12 mois = $11 \times 300\,000 + 240\,000 = 3\,540\,000$ Ar.  
  Allocation légale pour l'ensemble des droits acquis = $\frac{1}{12} \times 3\,540\,000 = \mathbf{295\,000}$ **Ar**.  
  Taux journalier légal (rapporté aux 29,5 jours effectivement acquis) = $295\,000 / 29{,}5 = \mathbf{10\,000{,}00}$ **Ar/j**.
- **Écart :** $-135{,}59$ Ar/j ($-4\,000$ Ar sur le total annuel, soit $-1{,}36$ %).
- **Cause de l'écart :** Dans KAJY, l'absence pénalise deux fois : elle réduit l'assiette $\text{NET\_BASE}$ ($24/30$) ET elle réduit les droits acquis ($24/30$). La formule KAJY multiplie la réduction au carré $(\frac{24}{30})^2$, ce qui déprécie artificiellement le taux journalier moyen en deçà du taux normal.
- **Qualification :** `LEGAL_DIVERGENCE`.

---

### Scénario F : Congés déjà pris dans l'exercice de référence
- Salaire fixe : 300 000 Ar/mois.
- Au mois 4, le salarié prend 6 jours de congé payé indemnisés au taux de la campagne précédente (soit 10 500 Ar/j, supérieur au salaire de base journalier 10 000 Ar/j).
  - Déduction congé : $6 \times 10\,000 = 60\,000$ Ar.
  - Indemnité congé versée : $6 \times 10\,500 = 63\,000$ Ar.
  - $\text{NET\_BASE}(4) = 300\,000 - 60\,000 + 63\,000 = 303\,000$ Ar.
- **KAJY :**
  $\text{DroitMois}(4) = 2{,}5$ jours (car les congés pris sont assimilés à du temps plein).  
  $\text{ValoMois}(4) = 303\,000 \times \frac{2{,}5}{30} = 25\,250$ Ar.  
  $\text{CumulValo} = 11 \times 25\,000 + 25\,250 = 300\,250$ Ar.  
  $\text{TCONGE} = 300\,250 / 30 = \mathbf{10\,008{,}33}$ **Ar/j**.
- **Article 131 :**
  La rémunération perçue au mois 4 est de 303 000 Ar (salaire réel incluant l'allocation de congé).  
  Total 12 mois = $11 \times 300\,000 + 303\,000 = 3\,603\,000$ Ar.  
  Taux journalier = $3\,603\,000 / 360 = \mathbf{10\,008{,}33}$ **Ar/j**.
- **Écart :** 0 Ar (0,00 %).
- **Cause de l'écart :** L'intégration de l'indemnité de congé dans $\text{NET\_BASE}$ reflète fidèlement la rémunération effective du salarié sans divergence.
- **Qualification :** `LEGAL_MATCH`.

---

### Scénario G : Salarié avec historique KAJY réel (Fiche OHATRA)
- Salarié de l'échantillon KAJY (Havana, 12 mois d'avril à mars).
- Données réelles KAJY :
  - $\text{CumulDroits} = 30{,}00$ jours.
  - $\text{CumulValo} = 324\,854{,}70$ Ar.
  - $\text{TCONGE}_{\text{calculé}} = \mathbf{10\,828{,}49}$ **Ar/j**.
  - Somme des $NET\_BASE + HS$ = 3 898 256,40 Ar.
- **Article 131 calculé sur les mêmes 12 mois :**
  - $\text{AllocAnnuelle} = \frac{1}{12} \times 3\,898\,256{,}40 = 324\,854{,}70$ Ar.
  - $\text{TauxJournalier} = 324\,854{,}70 / 30 = \mathbf{10\,828{,}49}$ **Ar/j**.
- **Écart :** 0 Ar (0,00 %).
- **Conclusion capitale :** Sur la fiche de référence KAJY exacte, la formule KAJY et la formule de l'article 131 donnent **exactement le même résultat** car les droits acquis étaient pleins (30 jours) et qu'aucune prime $AUTIMP$ n'a été réclamée par le salarié.
- **Qualification :** `LEGAL_MATCH`.

---

### Scénario H : Salarié première année (6 mois d'ancienneté)
- Embauché il y a 6 mois, Salaire = 300 000 Ar/mois.
- Autorisé exceptionnellement à prendre 5 jours de congés par anticipation.
- **Runtime SIRH actuel :**
  Fallback `SalaireBase / 30` = **10 000,00 Ar/j**.  
  Indemnité versée = 50 000 Ar.
- **Article 131 :**
  Rémunération totale perçue sur les 6 mois de service = $6 \times 300\,000 = 1\,800\,000$ Ar.  
  Droits acquis = $6 \times 2{,}5 = 15$ jours.  
  Valeur totale des droits acquis = $\frac{1}{12} \times \text{annualisé} = \frac{1}{12} \times 3\,600\,000 = 300\,000$ Ar pour 30 jours, soit $10\,000{,}00$ Ar/j.
- **Écart :** 0 Ar (0,00 %).
- **Qualification :** `MORE_FAVORABLE_GALAXAM_RULE` (Le fallback `SalaireBase/30` garantit le maintien de salaire intégral, ce qui est strictement favorable).

---

### Scénario I : Rupture de contrat / Solde de Tout Compte (STC)
- Le salarié quitte l'entreprise au Mois 18. Son salaire a augmenté de 300 000 Ar à 500 000 Ar au Mois 13.
- Il lui reste 15 jours de congés non pris à solder en ICCP.
- **Runtime SIRH actuel :**
  `StcService` appelle le resolver de taux qui retourne le TCONGE issu de la campagne précédente (clôturée au Mois 12), soit **10 000,00 Ar/j**.  
  ICCP versée = $15 \times 10\,000 = \mathbf{150\,000}$ **Ar**.
- **Article 133 combiné à l'Article 131 :**
  L'ICCP doit être calculée sur les 12 mois ayant précédé la rupture (Mois 6 à 17).  
  Mois 6 à 12 (7 mois) à 300 000 = 2 100 000 Ar.  
  Mois 13 à 17 (5 mois) à 500 000 = 2 500 000 Ar.  
  Total 12 mois = 4 600 000 Ar.  
  Allocation journalière légale = $4\,600\,000 / 360 = \mathbf{12\,777{,}78}$ **Ar/j**.  
  ICCP légale minimale = $15 \times 12\,777{,}78 = \mathbf{191\,666{,}67}$ **Ar**.
- **Écart :** $-41\,666{,}67$ Ar (Le STC SIRH actuel est **inférieur de 21,7 %** à l'obligation légale de l'article 133).
- **Cause de l'écart :** L'application du TCONGE d'une campagne révolue pour liquider le départ du salarié au lieu de calculer l'assiette sur les 12 derniers mois de travail effectif.
- **Qualification :** `LEGAL_DIVERGENCE` (Risque de contentieux prud'homal immédiat sur le reçu pour solde de tout compte).

---

## 4. TABLEAU SYNTHÉTIQUE DES SCÉNARIOS

| Scénario | KAJY (Ar/j) | SIRH Runtime (Ar/j) | Code 2024 Art. 131 (Ar/j) | Écart Journalier | Écart % | Statut Juridique |
| :--- | :---: | :---: | :---: | :---: | :---: | :---: |
| **A. Nominal constant** | 10 000,00 | 10 000,00 | 10 000,00 | 0,00 Ar | 0,0 % | `LEGAL_MATCH` |
| **B. Augmentation salaire** | 10 000,00 | 10 000,00 | 14 166,67 | -4 166,67 Ar | **-29,4 %** | `LEGAL_DIVERGENCE` |
| **C. Heures Sup.** | 10 833,33 | 10 833,33 | 10 833,33 | 0,00 Ar | 0,0 % | `LEGAL_MATCH` |
| **D. Primes (AUTIMP)** | 10 000,00 | 10 000,00 | 13 333,33 | -3 333,33 Ar | **-25,0 %** | `LEGAL_DIVERGENCE` |
| **E. Absences injustifiées** | 9 864,41 | 9 864,41 | 10 000,00 | -135,59 Ar | -1,4 % | `LEGAL_DIVERGENCE` |
| **F. Congés passés** | 10 008,33 | 10 008,33 | 10 008,33 | 0,00 Ar | 0,0 % | `LEGAL_MATCH` |
| **G. Golden Master KAJY** | 10 828,49 | 10 828,49 | 10 828,49 | 0,00 Ar | 0,0 % | `LEGAL_MATCH` |
| **H. Première année** | N/A | 10 000,00 | 10 000,00 | 0,00 Ar | 0,0 % | `MORE_FAVORABLE_GALAXAM_RULE` |
| **I. Rupture / STC** | N/A | 10 000,00 | 12 777,78 | -2 777,78 Ar | **-21,7 %** | `LEGAL_DIVERGENCE` |
