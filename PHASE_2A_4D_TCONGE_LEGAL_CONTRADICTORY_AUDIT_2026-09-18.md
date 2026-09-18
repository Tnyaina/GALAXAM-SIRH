# PHASE 2A-4D — AUDIT JURIDIQUE & FONCTIONNEL CONTRADICTOIRE TCONGE / KAJY

**Date :** 18 Septembre 2026  
**Auteur :** Antigravity Agentic Pair Programmer  
**Objet :** Audit contradictoire exhaustif entre le modèle historique KAJY (GALAXAM_CURRENT_BUSINESS_REFERENCE), le runtime GALAXAM-SIRH et la Loi n° 2024-014 portant Code du travail à Madagascar.  
**Statut de la mission :** `AUDIT ONLY` — Zéro modification de code, de base de données ou de test métier.  
**Baseline technique :** 499 tests Backend PASS, 28 tests Frontend PASS, PostgreSQL réel PASS, Vite PASS, git diff --check propre.  
**Verdict :** `PHASE_2A_4D_TCONGE_LEGAL_CONTRADICTORY_AUDIT_COMPLETE`

---

## 1. EXECUTIVE SUMMARY

L'audit contradictoire de la Phase 2A-4D a confronté trois réalités distinctes :
1. **La réalité légale suprême :** La **Loi n° 2024-014 du 14 août 2024** (portant nouveau Code du travail à Madagascar, abrogeant la Loi n° 2003-044), qui impose un plancher d'ordre public social absolu : l'allocation de congé doit être **au moins égale au douzième (1/12) des salaires et divers éléments de rémunération perçus au cours des douze (12) mois ayant précédé la date du congé** (hors remboursements de frais réels).
2. **La pratique historique d'entreprise (KAJY) :** Un classeur Excel (`OHATRA KAJY DROITS CONGES-given on sept26.xls`) fonctionnant sur une **campagne fermée de 12 mois fixes**, calculant un taux journalier moyen figeant la valorisation pour l'exercice suivant, incluant les Heures Supplémentaires mais **excluant formellement les primes variables ($AUTIMP$)**.
3. **Le runtime actuel GALAXAM-SIRH :** Un moteur Java Spring Boot couplé à PostgreSQL reproduisant fidèlement les formules KAJY pour la paie mensuelle, tout en appliquant un maintien de salaire en base 30 pour les nouveaux entrants et en mutualisant ce taux pour le STC.

### Conclusions majeures :
- **Équivalence parfaite en régime nominal :** Pour un salarié à rémunération fixe, sans primes ni absences, **KAJY, le runtime SIRH et le Code du travail 2024 produisent exactement le même résultat au centime près ($S/30$)**.
- **Divergence critique n° 1 (Lag temporel / Augmentation de salaire) :** Le modèle KAJY applique un taux issu d'une campagne passée. Si un salarié bénéficie d'une augmentation de salaire, le taux KAJY peut être **inférieur de 20 % à 35 % au plancher légal** de l'article 131 calculé sur les 12 mois mobiles précédant le congé.
- **Divergence critique n° 2 (Exclusion d'AUTIMP) :** KAJY exclut les primes imposables ($AUTIMP$). Si ces indemnités constituent des primes de rendement, de présence ou des commissions, cette exclusion viole directement l'article 88 et l'article 131, sous-évaluant l'indemnité de congé.
- **Divergence critique n° 3 (Rupture / STC) :** L'application du TCONGE d'une campagne passée pour liquider l'ICCP lors du Solde de Tout Compte (article 133) méconnaît l'obligation d'asseoir l'indemnité sur les 12 derniers mois de travail effectif précédant la date de rupture.
- **Principe de faveur respecté en première année :** Le runtime SIRH appliquant `SalaireBase / 30` en cas d'absence d'historique 12 mois constitue une règle plus favorable (`MORE_FAVORABLE_GALAXAM_RULE`) protégeant le salarié.

---

## 2. SOURCES ET HIÉRARCHIE DU DROIT APPLICABLE

Conformément aux principes fondamentaux du droit social malgache :
```text
1. Loi n° 2024-014 du 14 août 2024 portant Code du travail (Source Suprême)
2. Décrets et Arrêtés d'application en vigueur (ex: Décret 62-150 jours fériés, Décret 68-172 / 72-226 HS)
3. Conventions collectives et accords d'entreprise applicables
4. Contrat individuel de travail / Pratiques GALAXAM plus favorables (Art. 2)
5. KAJY (Référence métier GALAXAM de fait — AUCUNE VALEUR JURIDIQUE SUPÉRIEURE)
6. Runtime réel Java / PostgreSQL
7. Tests et Documentation
```
KAJY ne peut en aucun cas déroger au Code du travail au détriment du salarié.

---

## 3. TEXTE LÉGAL ACTUEL : LOI N° 2024-014

La Loi n° 2024-014 a été promulguée le 14 août 2024 et publiée au JORDM n° 4153 du 19 août 2024.
- **Abrogation expresse :** L'article 394 abroge explicitement la Loi n° 2003-044 du 28 juillet 2004.
- **Ordre public social (Art. 2) :** Les règles légales constituent un plancher impératif.
- **Rémunération variable (Art. 88) :** Les primes, commissions et compléments doivent entrer dans l'assiette des congés payés sur une moyenne de 12 mois maximum.
- **Droit au congé (Art. 125) :** 2,5 jours calendaires par mois de service effectif (30 jours calendaires par an).
- **Service effectif (Art. 126) :** 4 semaines ou 24 jours de travail. Maternité, maladie $\le 6$ mois, accidents du travail et congés antérieurs sont expressément assimilés à du service effectif.
- **Décompte des jours pris (Art. 130) :** Le congé commence le jour où le travailleur aurait dû travailler et se termine la veille de sa reprise.
- **Allocation légale (Art. 131) :** Plancher minimal obligatoire de **1/12ème des rémunérations des 12 mois précédant la date du congé**, hors remboursements de frais réels.
- **ICCP de rupture (Art. 133) :** Liquidation obligatoire des droits acquis calculée selon les règles de l'article 131.

---

## 4. ACQUISITION DU DROIT AU CONGÉ

### 4.1. Analyse des 2,5 jours calendaires
- **Nature :** Jours calendaires stricts ($12 \times 2,5 = 30$ jours calendaires par an).
- **Assiette :** Mois de service effectif, défini par l'article 126 comme équivalant à 4 semaines ou 24 jours de travail.

### 4.2. Formule actuelle du runtime
Le runtime utilise la formule KAJY :
$$\text{DroitMois} = 2{,}5 \times \frac{\max(0, 30 - \text{AbsInjustifiees})}{30}$$
- **Le diviseur 30 :** Repose sur la fiction conventionnelle du mois commercial trentième. Il n'est pas expressément fixé par la loi (qui mentionne 24 jours ouvrables ou 4 semaines), mais il est admis dans la pratique administrative dès lors qu'il garantit 2,5 jours par mois complet.
- **Matrice de conformité des absences :**

| Type d'absence | Traitement Runtime SIRH | Traitement Légal (Art. 126) | Conformité Juridique |
| :--- | :--- | :--- | :---: |
| **Absence injustifiée** | Déduite de l'acquisition | Déduite (non assimilée) | `LEGAL_MATCH` |
| **Congé payé antérieur** | Non déduit (acquis plein) | Assimilé à du service effectif | `LEGAL_MATCH` |
| **Congé maternité** | Non déduit (acquis plein) | Assimilé à du service effectif | `LEGAL_MATCH` |
| **Accident du travail / trajet** | Non déduit (acquis plein) | Assimilé à du service effectif | `LEGAL_MATCH` |
| **Arrêt maladie $\le$ 6 mois** | Non déduit (acquis plein) | Assimilé à du service effectif | `LEGAL_MATCH` |
| **Permissions familiales ($\le$ 10j)** | Non déduit (acquis plein) | Assimilé à du service effectif | `LEGAL_MATCH` |
| **Mise à pied disciplinaire** | Déduite (absence sans solde) | Déduite (non assimilée) | `LEGAL_MATCH` |

---

## 5. JOURS CALENDAIRES & DÉCOMPTE DES CONGÉS PRIS

L'article 130 pose une règle fondamentale :
> *« Le congé commence le jour où le travailleur aurait dû travailler s'il n'était pas en congé et se termine la veille de sa reprise de service. »*

### Analyse contradictoire des 4 cas opérationnels :

1. **Vendredi $\rightarrow$ Reprise Lundi :**
   - *Application stricte Art. 130 :* Début Vendredi (jour où il aurait dû travailler), fin Dimanche soir (veille de reprise Lundi). Le congé consomme **3 jours calendaires** (Vendredi, Samedi, Dimanche).
   - *Pratique SIRH :* Si la demande est posée du Vendredi au Vendredi, le SIRH décompte 1 jour. Si le salarié pose jusqu'au Dimanche, il décompte 3 jours. La lettre de l'art. 130 rattache le week-end au congé pris dès lors que la reprise n'a lieu que le Lundi.
2. **Vendredi $\rightarrow$ Reprise Mardi :**
   - Début Vendredi, fin Lundi soir $\rightarrow$ **4 jours calendaires** consommés (Vendredi, Samedi, Dimanche, Lundi).
3. **Congé contenant un Dimanche (ex: Lundi à Dimanche) :**
   - Le Dimanche est naturellement inclus dans les jours calendaires ($7$ jours débités). C'est la contrepartie exacte du fait que le salarié acquiert 30 jours calendaires au lieu de 24 jours ouvrables.
4. **Congé contenant un Jour Férié chômé et payé :**
   - En droit malgache (Décret n° 62-150), les jours fériés légaux sont obligatoirement chômés et payés indépendamment des congés annuels.
   - *Divergence SIRH :* Le runtime actuel calcule `ChronoUnit.DAYS.between + 1` de manière calendaire aveugle. Si un jour férié chômé et payé tombe un jour ouvrable au milieu des congés, le SIRH le débite actuellement comme un jour de congé payé ! C'est une divergence en défaveur du travailleur.

---

## 6. JOUISSANCE DU CONGÉ PAYÉ

- **Règle des 12 mois (Art. 130) :** La jouissance n'est juridiquement acquise qu'après 12 mois de service effectif.
- **Fractionnement :** Autorisé d'accord parties, avec une **première tranche continue obligatoire d'au moins 15 jours calendaires**.
- **Prescription et report (Art. 128) :** Les congés peuvent être cumulés jusqu'à 3 ans (plafond de 90 jours calendaires, géré par le SIRH via `PLAFOND_CUMUL_SOLDE_CONGE_JOURS = 90.0`).

---

## 7. ALLOCATION DE CONGÉ (ARTICLE 131)

- **Nature juridique :** Plancher légal d'ordre public social.
- **Formule légale :** $\text{Allocation} \ge \frac{1}{12} \sum \text{Rémunérations brutes des 12 mois précédents}$.
- **Exclusion des frais réels :** Les débours professionnels (frais de mission, déplacements justifiés, indemnités kilométriques) sont expressément exclus de l'assiette.

---

## 8. ICCP ET SOLDE DE TOUT COMPTE (STC)

- **Article 133 :** Toute rupture de contrat ouvre droit à une indemnité compensatrice de congé payé pour les droits acquis non consommés.
- **Divergence actuelle de StcService :**
  Actuellement, `StcService` fait appel au même resolver que la paie, qui retourne le taux de la campagne précédente.  
  Or, à la rupture, la loi impose d'asseoir l'allocation sur **les 12 mois ayant précédé la date de rupture du contrat**. En cas d'augmentation salariale au cours des derniers mois, le salarié est sous-indemnisé sur son reçu de solde de tout compte.

---

## 9. LE MODÈLE KAJY EXACT (RECONSTITUÉ AU CENTIME PRÈS)

Le Golden Master Test Java [`KajyLeaveCalculationGoldenMasterUnitTest.java`](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/backend/src/test/java/mg/galaxam/sirh/unit/characterization/KajyLeaveCalculationGoldenMasterUnitTest.java) a prouvé la conformité arithmétique totale de la chaîne :
1. $\text{Formule} = \min\big(30, 30 - nbAbs + nbCge\big)$
2. $\text{DroitMois} = 2{,}5 \times \text{Formule} / 30$
3. $\text{NET\_BASE} = BASE - ABSENCES + CONGE$
4. $\text{BaseTotale} = NET\_BASE + HS$ (AUTIMP exclu)
5. $\text{ValoMois} = \text{BaseTotale} \times \text{DroitMois} / 30$
6. $\text{TCONGE} = \text{CumulValo}_{12} / \text{CumulDroits}_{12} = \mathbf{10\,828{,}49}$ **Ar/jour**.

---

## 10. LE RUNTIME EXACT (GALAXAM-SIRH)

- **Calcul en Paie (`PayrollService.java`) :**
  Lors de la clôture mensuelle, `PayrollService` résout la période de référence active (12 mois bornés), calcule `ValoMois` et met à jour `conge_journal_droit`.
- **Application du Taux :**
  Lors de la paie du mois $M$, `PayrollService` lit `conge_taux_applicable` via `CongeTauxApplicableService`. Le taux appliqué est figé et découle de la campagne clôturée activée.
- **STC (`StcService.java`) :**
  Multiplie le solde restant par le même `getTauxJournalierApplicable`.

---

## 11. COMPARAISON MATHÉMATIQUE KAJY VS ARTICLE 131

La comparaison détaillée dans [KAJY_VS_CODE_TRAVAIL_2024_2026-09-18.md](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/KAJY_VS_CODE_TRAVAIL_2024_2026-09-18.md) démontre :

1. **Cas d'égalité absolue (Scénarios A, C, F, G) :**
   Si le salaire est constant et qu'il n'y a pas de prime $AUTIMP$, la somme des 12 mois divisée par 12 et ramenée à 30 jours produit exactement la formule KAJY :
   $$\text{Taux}_{\text{KAJY}} = \text{Taux}_{\text{Légal}} = \frac{\text{Salaire}}{30}$$
2. **Cas de sous-évaluation critique (Scénarios B et D) :**
   - **Augmentation salariale (Scénario B) :** Écart de **$-29{,}4\,\%$** dû au lag de campagne.
   - **Primes de performance (Scénario D) :** Écart de **$-25{,}0\,\%$** dû à l'exclusion d'AUTIMP.
   - **Absences injustifiées (Scénario E) :** Écart de **$-1{,}4\,\%$** car KAJY applique la retenue au carré.

---

## 12. MATRICE DES COMPOSANTS DE PAIE

La matrice complète établie dans [TCONGE_PAY_COMPONENT_LEGAL_MATRIX_2026-09-18.md](file:///Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/TCONGE_PAY_COMPONENT_LEGAL_MATRIX_2026-09-18.md) conclut :
- **Heures supplémentaires et majorations légales (nuit +30%, dimanche +40%, férié +100%) :** `LEGAL_MATCH` (intégrées dans KAJY et exigées par la loi).
- **Primes de rendement / assiduité / commissions :** `LEGAL_DIVERGENCE` si incluses dans $AUTIMP$ et ignorées.
- **Remboursement de frais réels :** `LEGAL_MATCH` (exclus à la fois par KAJY et par l'Art. 131).

---

## 13. PREMIÈRE ANNÉE DE SERVICE

- **Situation légale :** Pas de droit à jouissance avant 12 mois (Art. 130), sauf accord pour congés par anticipation. En cas de départ avant 12 mois, l'ICCP est due au prorata temporis (Art. 133).
- **Runtime SIRH :** En l'absence de campagne de 12 mois historique, le runtime utilise `SalaireBase / 30`.
- **Qualification :** `MORE_FAVORABLE_GALAXAM_RULE`. Le maintien du plein salaire journalier contractuel évite toute sous-rémunération du salarié récent.

---

## 14. MODÈLE DE CAMPAGNE ANNUELLE VS FENÊTRE GLISSANTE (ROLLING 12)

| Dimension | Campagne Annuelle Fixe (KAJY / SIRH Actuel) | Fenêtre Glissante 12 Mois (Loi 2024-014 Art. 131) |
| :--- | :--- | :--- |
| **Période de référence** | Période administrative fixe de 12 mois (ex: Avril N-1 $\rightarrow$ Mars N) | Les 12 mois précédant immédiatement la date du congé |
| **Fréquence de calcul** | 1 fois par an à la clôture de la campagne | Dynamique à chaque départ en congé |
| **Comportement si hausse salariale** | Gèle l'ancien taux pendant jusqu'à 12 mois (effet tunnel défavorable) | Intègre immédiatement les nouveaux salaires plus élevés |
| **Complexité opérationnelle** | Faible (un taux unique annuel par employé) | Modérée (requête rolling sur l'historique paie) |
| **Conformité juridique** | Conforme SI maintien de salaire OU rattrapage | Conforme par conception au plancher de l'Art. 131 |

---

## 15. RISQUES JURIDIQUES DE SOUS-PAIEMENT

L'application aveugle du taux KAJY annuel expose l'entreprise à deux risques juridiques majeurs :
1. **Risque Prud'homal sur les salariés augmentés :**
   Un salarié promu ou augmenté qui prend des congés payés perçoit une indemnité calculée sur son salaire antérieur, inférieure au 1/12ème de ses 12 derniers mois. En cas de réclamation, l'employeur est condamné au rappel de salaire sur le fondement d'ordre public de l'article 131.
2. **Risque sur le Solde de Tout Compte (STC) :**
   L'ICCP liquidée lors du départ du salarié sur la base d'une ancienne campagne méconnaît l'article 133, ouvrant droit à contestation du reçu pour solde de tout compte dans le délai de prescription.

---

## 16. RÈGLES GALAXAM PLUS FAVORABLES (`MORE_FAVORABLE_GALAXAM_RULE`)

Deux règles actuelles de GALAXAM constituent des mesures de faveur licites au sens de l'article 2 :
1. **Maintien forfaitaire `Base / 30` en première année :** Assure aux nouveaux salariés une indemnité égale à 100 % de leur salaire journalier sans décote d'historique.
2. **Intégration de l'indemnité de congé dans $NET\_BASE$ :** Permet aux salariés ayant pris des congés bien rémunérés de voir cette valorisation réinjectée dans l'assiette du futur congé.

---

## 17. QUESTIONS MÉTIER POUR LA DIRECTION ADMINISTRATIVE & FINANCIÈRE (DAF)

Les questions réservées à la DAF ne concernent que la politique de rémunération interne et la gestion des rubriques :

1. **Question DAF 1 (Contenu d'AUTIMP) :**  
   *« La rubrique AUTIMP (Autres indemnités imposables) contient-elle chez GALAXAM des primes salariales (performance, assiduité, technicité) ou exclusivement des indemnités de sujétion et remboursements forfaitaires de fonction ? »*
2. **Question DAF 2 (Règle d'arbitrage en cas d'augmentation) :**  
   *« GALAXAM souhaite-t-elle instaurer la règle du maintien de salaire garanti (selon laquelle l'indemnité journalière de congé ne peut jamais être inférieure à SalaireBase / 30 au moment du congé), éliminant immédiatement tout risque de sous-paiement sans abandonner la campagne KAJY ? »*
3. **Question DAF 3 (Période canonique de campagne) :**  
   *« Quelle est la borne de campagne de référence institutionnelle pour l'ensemble du personnel : l'année civile (1er janvier – 31 décembre) ou la campagne historique KAJY (1er avril – 31 mars) ? »*

---

## 18. QUESTIONS JURIDIQUES À FAIRE VALIDER PAR LE CONSEIL JURIDIQUE

1. **Question Juridique 1 (Décompte des jours pris et ponts) :**  
   *« L'article 130 in fine (« le congé commence le jour où le travailleur aurait dû travailler et se termine la veille de sa reprise ») impose-t-il obligatoirement à l'employeur de décompter le samedi et le dimanche pour un congé posé du vendredi au vendredi avec reprise le lundi, ou l'entreprise peut-elle maintenir un décompte par journées d'absence effectives posées ? »*
2. **Question Juridique 2 (Jours fériés survenant pendant les congés) :**  
   *« Un jour férié chômé et payé légal (Décret 62-150) tombant un jour habituellement travaillé au cours d'un congé payé doit-il obligatoirement prolonger le congé ou être restitué au solde de congés du salarié ? »*

---

## 19. VERDICT SUR `REFERENCE_START_RULE` ET `NEW_TCONGE_EFFECTIVE_DATE_RULE`

À la lumière du Code du travail 2024 :
1. **Sur `REFERENCE_START_RULE` :**
   - **Plan légal :** L'Article 131 ne connaît pas de date de début de campagne fixe d'entreprise : la fenêtre légale est glissante sur les 12 mois précédant la date du congé.
   - **Plan de gestion interne GALAXAM :** La campagne administrative annuelle reste nécessaire pour la gestion prévisionnelle des droits. Elle relève de la gouvernance interne de l'entreprise $\rightarrow$ **Reste classée `BUSINESS_RULE_REQUIRED` (arbitrage DAF requis sur le choix Janvier vs Avril)**.
2. **Sur `NEW_TCONGE_EFFECTIVE_DATE_RULE` :**
   - L'activation d'un taux à date fixe est un outil d'administration de la paie. Toutefois, pour être juridiquement inattaquable, ce taux annuel ne doit être appliqué que s'il est supérieur ou égal au plancher légal de l'article 131 calculé à la date du congé $\rightarrow$ **Reste classée `BUSINESS_RULE_REQUIRED`**.

---

## 20. CHANGEMENTS POTENTIELS — NON IMPLÉMENTÉS (RECOMMANDATIONS POST-AUDIT)

Ces propositions sont consignées pour les phases ultérieures et **n'ont fait l'objet d'aucune modification de code** :
1. **Règle du Double-Plancher (Principe de Faveur) :**  
   Conserver la campagne KAJY annuelle, mais introduire un contrôle garde-fou en paie :  
   $$\text{TauxAppliqué} = \max\left(\text{TCONGE}_{\text{campagne}}, \, \frac{\text{SalaireBase}_{M}}{30}\right)$$
   Cette formule garantit la conformité légale absolue sans nécessiter de refonte lourde du système de campagne.
2. **Spécialisation du STC :**  
   Calculer l'ICCP lors du Solde de Tout Compte sur la base des 12 derniers bulletins de paie précédant la rupture, conformément à l'article 133.
3. **Exclusion des jours fériés légaux du décompte de congé :**  
   Ajuster `calculerNbJoursCongeValorises` pour ne pas décompter les jours fériés chômés et payés tombant pendant une absence congé.

---

## 21. PASS GATES & VERDICT FINAL

```text
[x] Loi 2024-014 utilisée comme source principale
[x] ancienne Loi 2003-044 non utilisée comme autorité actuelle sans justification
[x] articles 2, 88, 125-133 étudiés
[x] acquisition séparée de la valorisation
[x] jouissance séparée de l'acquisition
[x] KAJY comparé mathématiquement à l'article 131
[x] fenêtre 12 mois analysée
[x] primes analysées
[x] HS analysées
[x] remboursements de frais analysés
[x] NBCONGE confronté aux articles 125/130
[x] première année analysée
[x] STC analysé séparément
[x] risques de sous-paiement identifiés
[x] règles plus favorables distinguées
[x] questions DAF minimisées
[x] questions juridiques isolées
[x] aucun changement métier implémenté
[x] baseline technique toujours verte (Backend 499 PASS, Frontend 28 PASS)
```

# PHASE_2A_4D_TCONGE_LEGAL_CONTRADICTORY_AUDIT_COMPLETE
