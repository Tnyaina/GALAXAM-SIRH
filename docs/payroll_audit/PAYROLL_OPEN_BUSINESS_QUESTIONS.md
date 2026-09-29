# Questions Métier Ouvertes pour la DAF & Direction Générale GALAXAM

> **Document :** Livrable 6 — Arbitrages Décisionnels Métier & Réglementaires
> **Auteur :** Antigravity AI — Expert Paie & Audit SIRH
> **Date :** Septembre 2026
> **Statut :** Formel — Prêt pour Soumission DAF
> **Principe directeur :** Aucune question technique n'est posée ici. Seules les ambiguïtés qui modifient le montant net à payer, le coût employeur ou la conformité légale font l'objet d'une consultation.

---

## Sommaire des Questions Arbitrées

- [A. Salaire de Base &amp; Prorata Temporis](#a-salaire-de-base--prorata-temporis)
- [B. Absences, Retards &amp; Valorisation](#b-absences-retards--valorisation)
- [C. Congés Payés &amp; Droits Acquis](#c-congés-payés--droits-acquis)
- [D. Heures Supplémentaires &amp; Cumuls de Majorations](#d-heures-supplémentaires--cumuls-de-majorations)
- [E. Jours Fériés &amp; Temps Assimilé](#e-jours-fériés--temps-assimilé)
- [F. Primes &amp; Régime Social / Fiscal](#f-primes--régime-social--fiscal)
- [G. Indemnité de Repas](#g-indemnité-de-repas)
- [H. Indemnité de Déplacement (TNR vs TMM)](#h-indemnité-de-déplacement-tnr-vs-tmm)
- [I. Cotisation CNaPS &amp; Plafond](#i-cotisation-cnaps--plafond)
- [J. Cotisation Santé (OSTIE vs OMSI)](#j-cotisation-santé-ostie-vs-omsi)
- [K. Impôt sur les Revenus Salariaux (IRSA)](#k-impôt-sur-les-revenus-salariaux-irsa)
- [L. Impôt Synthétique Intermittent (ISI)](#l-impôt-synthétique-intermittent-isi)
- [M. Acomptes, Avances &amp; Quotité Cessible](#m-acomptes-avances--quotité-cessible)

---

## A. Salaire de Base & Prorata Temporis

### Question A1 : Méthode de Proratisation des Entrées / Sorties en Cours de Mois -> reponse : option A, par rapport au taux horaire/horaire effectuer

- **Pourquoi nous devons la poser :** En cas d'embauche le 15 du mois ou de démission en cours de période, le salaire de base ne peut être versé intégralement. Deux méthodes de calcul coexistent dans les SIRH et produisent des montants différents.
- **Comportement actuel du SIRH :** Utilise le taux horaire contractuel ($S_{\text{contrat}} / 173,33$) multiplié par le nombre d'heures théoriques entre la date d'embauche et la fin du mois.
- **Comportement Excel / historique :** Règle des jours ouvrés réels ($S_{\text{contrat}} \times \text{JoursTravaillés} / \text{JoursOuvrésDuMois}$).
- **Exemple chiffré :** Salarié à 1.733.300 Ar (Taux horaire = 10.000 Ar). Embauché un mois de 22 jours ouvrés (176 h réelles). Il effectue 11 jours (88 h).
  - *Méthode Horaire 173,33 :* $88 \times 10.000 = 880.000\text{ Ar}$.
  - *Méthode Jours Ouvrés :* $1.733.300 \times 11 / 22 = 866.650\text{ Ar}$ (Écart : $13.350\text{ Ar}$).
- **Option A (Recommandée) :** Méthode des heures réelles au taux horaire officiel $173,33$ pour une parfaite cohérence avec les retenues d'absence.
- **Option B :** Méthode du prorata en jours ouvrés du calendrier réel du mois.
- **Impact de la décision :** Détermine l'algorithme de calcul de la brique `BaseSalaryCalculator` lors de l'intégration du contrat.

---

## B. Absences, Retards & Valorisation

### Question B1 : Règle d'Arrondi et Précision des Minutes de Retard : reponse -> option A conversion precision minute exacte

- **Pourquoi nous devons la poser :** Les pointeuses enregistrent les pointages à la minute près. La méthode de conversion en fraction d'heure crée des discordances d'arrondi entre la compta et la paie.
- **Comportement actuel du SIRH :** Retenue calculée à la minute exacte : $(\text{Minutes} \times \text{TauxHoraire}) / 60$.
- **Comportement Excel / historique :** Conversion en heures avec 2 décimales préalables ($20\text{ min} = 0,33\text{ h}$).
- **Exemple chiffré :** Salarié à 1.733.300 Ar ($TH = 10.000\text{ Ar}$). Retard de 20 minutes.
  - *SIRH :* $(20 \times 10.000) / 60 = 3.333,33\text{ Ar}$.
  - *Excel :* $0,33 \times 10.000 = 3.300,00\text{ Ar}$ (Écart : $33,33\text{ Ar}$).
- **Option A (Recommandée) :** Conservation de la précision à la minute exacte jusqu'à l'évaluation financière finale, avec arrondi standard à 2 décimales.
- **Option B :** Arrondi intermédiaire des temps d'absence au centième d'heure (ou tranche de 15 minutes) avant valorisation.
- **Impact de la décision :** Évite les litiges collaborateurs lors de la confrontation entre le relevé de pointage et la ligne du bulletin.

---

## C. Congés Payés & Droits Acquis

### Question C1 : Inclusion des Heures Supplémentaires dans l'Assiette de Valorisation du Taux Congé ($TCONGE$) -> option A inclusion des HS

- **Pourquoi nous devons la poser :** Le document de référence Galaxam (`OHATRA KAJY DROITS CONGES`) stipule : $\text{BaseTotale} = \text{NetBase} + \text{HeuresSupp}$. Or, certaines entreprises excluent les HS exceptionnelles de la base de congé pour ne retenir que les éléments fixes permanents.
- **Comportement actuel du SIRH :** Intègre les HS dans le cumul brut soumis au calcul du taux moyen journalier $TCONGE$.
- **Comportement Excel / historique :** Formule Excel conforme : $\text{Base} = \text{SalaireBase} - \text{Absences} + \text{HS}$.
- **Exemple chiffré :** Salarié ayant 1.000.000 Ar de salaire de base et ayant effectué 500.000 Ar de HS en période de haute activité.
  - *Si HS incluses :* Assiette = 1.500.000 Ar $\rightarrow$ Valorisation du droit mensuel $V_m$ élevée.
  - *Si HS exclues :* Assiette = 1.000.000 Ar $\rightarrow$ Valorisation $V_m$ standard.
- **Option A (Recommandée - Conforme DGI/Code Travail) :** Maintenir l'inclusion de la rémunération totale perçue pour travail effectif (Salaire base + HS) conformément à la règle du maintien de salaire.
- **Option B :** Restreindre l'assiette au salaire de base net d'absences uniquement.
- **Impact de la décision :** Conditionne la formule de mise à jour du journal de droit aux congés (`conge_journal_droit`).

---

## D. Heures Supplémentaires & Cumuls de Majorations

### Question D1 : Seuil de Déclenchement des Heures Supplémentaires : Hebdomadaire vs Période -> réponse DAF : HS hebdomadaires ; objectif adapté aux jours ouvrables du tronçon. Ex. lundi-mercredi = 24 h.

- **Pourquoi nous devons la poser :** Le Code du Travail Malagasy définit la durée légale à 40 heures par semaine. Mais Galaxam utilise des périodes de paie décalées (du 26 au 25 du mois suivant). Les semaines coupées à cheval sur deux périodes doivent être traitées sans biaiser les seuils de 40 heures.
- **Comportement actuel du SIRH :** Calcul par tronçon de semaine civile. L'objectif et le premier palier sont proratisés selon la durée normale attendue du tronçon (période, dates d'emploi, congé et férié chômé) ; une absence ne réduit pas l'objectif.
- **Comportement Excel / historique :** Somme brute mensuelle des heures dépassant l'obligation de la période.
- **Exemple chiffré :** Un collaborateur travaille 48 h pendant la semaine à cheval (3 jours sur le mois M, 3 jours sur le mois M+1).
  - *Approche Période isolée :* Les 8 HS risquent d'être réparties arbitrairement ou perdues.
  - *Approche Semaine Civile ISO :* Les 8 HS sont rattachées soit à la semaine complète, soit au prorata des jours de dépassement.
- **Option A (Recommandée) :** Calcul strict à la semaine civile complète (du lundi au dimanche), puis rattachement des HS à la période contenant la fin de la semaine.
- **Option B :** Déclenchement au dépassement du forfait mensuel d'heures théoriques.
- **Impact de la décision :** Architecture de l'agrégation dans `PointageMonthlySnapshot` et `OvertimePayCalculator`.

### Question D2 : Traitement d'une Heure Supplémentaire Réalisée un Dimanche (Cumul Additif vs Multiplicatif) -> réponse DAF : option A, cumul additif

- **Pourquoi nous devons la poser :** Lorsqu'un salarié effectue une heure supplémentaire un dimanche, bénéficie-t-il d'un cumul additif des majorations ou multiplicatif ?
- **Comportement actuel du SIRH :** Le contexte légal de production transmet `CUMUL_ADDITIF`. Les taux des buckets restent résolus depuis les barèmes datés en base.
- **Comportement Excel / historique :** Traité de façon additive : Majoration HS (ex: 30% ou 50%) + Majoration Dimanche (40%).
- **Exemple chiffré :** $TH = 10.000\text{ Ar}$. 1 heure supplémentaire au-delà de 40 h réalisée un dimanche (tranche 30%).
  - *Cumul Additif :* Majoration $= 30\% + 40\% = 70\%$. Heure payée à $170\%$ du taux $= 17.000\text{ Ar}$.
  - *Cumul Multiplicatif :* $10.000 \times 1,30 \times 1,40 = 18.200\text{ Ar}$.
- **Option A (Recommandée - Règle Galaxam confirmée) :** Cumul strictement additif des majorations sur la base du taux horaire contractuel.
- **Option B :** Cumul multiplicatif en cascade.
- **Impact de la décision :** Élimine définitivement le bug identifié dans `OvertimePayCalculator`.

---

## E. Jours Fériés & Temps Assimilé

### Question E1 : Valorisation du Jour Férié Chômé Tombant un Jour Habituellement Travaillé -> ne rien changer, option A

- **Pourquoi nous devons la poser :** À Madagascar, les jours fériés légaux sont chômés et payés pour les salariés mensualisés, sans récupération possible. Il convient de confirmer si le férié non travaillé maintient 100% du salaire sans générer de ligne additionnelle.
- **Comportement actuel du SIRH :** Inclus d'office dans les 173,33 h du salaire de base. Aucune ligne d'indemnité supplémentaire n'est créée si le salarié chôme.
- **Comportement Excel / historique :** Identique.
- **Option A (Recommandée) :** Maintien dans le forfait mensuel (pas de ligne visible, salaire de base intact).
- **Option B :** Ligne explicative spécifique *"Indemnité jour férié chômé"* compensée par une déduction de base équivalente.
- **Impact de la décision :** Lisibilité du bulletin pour le salarié.

---

## F. Primes & Régime Social / Fiscal

### Question F1 : Assujettissement Social et Fiscal de la Prime d'Ancienneté et de Rendement -> option A 

- **Pourquoi nous devons la poser :** Certaines indemnités professionnelles bénéficient de régimes d'exonération, mais les primes de rendement ou d'ancienneté constituent du salaire pur.
- **Comportement actuel du SIRH :** Totalement soumises à CNaPS, Santé et IRSA.
- **Comportement Excel / historique :** Totalement soumises.
- **Option A (Recommandée) :** Intégration totale dans le Brut Cotisable et Imposable pour toute prime liée au travail.
- **Option B :** Traitement d'exception (sur justificatif légal).
- **Impact de la décision :** Paramétrage définitif des types de primes dans `TypeLigneBulletin`.

---

## G. Indemnité de Repas

### Question G1 : Condition de Présence et Seuil d'Exonération de l'Indemnité Repas -> option A, stricte au presence.

- **Pourquoi nous devons la poser :** L'indemnité repas (panier) est versée pour compenser les contraintes de service. À Madagascar, les indemnités représentatives de frais réels sont exonérées d'IRSA et de CNaPS sous réserve de respecter les limites administratives (repas sur lieu de travail en journée continue).
- **Comportement actuel du SIRH :** Ajoutée au salaire brut mais exonérée de cotisations selon le paramétrage du site.
- **Comportement Excel / historique :** Versée forfaitairement sans proratisation en cas de télétravail ou demi-journée.
- **Exemple chiffré :** Forfait repas de 5.000 Ar/jour. Un salarié est absent 3 jours non rémunérés.
  - *Option A :* L'indemnité repas est proratisée et n'est payée que pour les jours de présence effective.
  - *Option B :* L'indemnité repas est un forfait mensuel fixe maintenu quelle que soit l'absence (sauf congé complet).
- **Option A (Recommandée) :** Condition stricte de présence effective : $\text{Montant} = \text{ForfaitJournalier} \times \text{JoursPrésents}$.
- **Option B :** Forfait mensuel fixe.
- **Impact de la décision :** Interfaçage direct avec le snapshot de pointage dans `AllowanceCalculator`.

---

## H. Indemnité de Déplacement (TNR vs TMM)

### Question H1 : Règle de Déduction des Forfaits Déplacement (TNR / TMM) -> réponse DAF : indemnité séparée des primes, proratisée ; conserver les règles établies.

- **Pourquoi nous devons la poser :** Dans le fichier Excel historique de paie, la ligne `AUTIMP` contient les indemnités de déplacement. Si un salarié a des absences non justifiées ou des congés sans solde, le forfait transport doit-il être réduit ?
- **Comportement actuel du SIRH :** Le taux journalier provient du montant configuré pour le site et est proratisé par les jours ouvrables de la période ; fractions et réduction configurée sont appliquées par jour. Le montant mensuel complet est préservé lorsque les conditions actuelles de présence intégrale sont remplies.
- **Comportement Excel / historique :** Versement intégral du montant forfaitaire (50.000 Ar TNR / 25.000 Ar TMM) sauf instruction explicite de la direction.
- **Option A (Recommandée) :** Forfait mensuel garanti non proratisé tant que le contrat est actif, sauf mois complet d'absence.
- **Option B :** Proratisation stricte au prorata des jours de travail effectifs sur site (excluant télétravail et absences).
- **Impact de la décision :** Évite les écarts récurrents entre les attentes de la DAF et le calcul automatique du SIRH.

---

## I. Cotisation CNaPS & Plafond

### Question I1 : Taux et Plafond CNaPS Applicable (8 x SME vs Plafond Historique Fixe) -> dynamique alors option A

- **Pourquoi nous devons la poser :** La législation CNaPS fixe le plafond des cotisations à 8 fois le Salaire Minimum d'Embauche (SME). Lorsque le SME est revalorisé par décret gouvernemental, le plafond change automatiquement.
- **Comportement actuel du SIRH :** Plafond configuré à 2.000.000 Ar en base ($8 \times 250.000\text{ Ar}$ SME), soit une cotisation salariale maximale de 20.000 Ar (1%) et patronale de 260.000 Ar (13%).
- **Comportement Excel / historique :** Identique ($1\% \text{ plafonné à } 20.000\text{ Ar}$).
- **Option A (Recommandée) :** Plafond dynamique calculé à partir de la valeur datée du SME ($8 \times \text{SME}$).
- **Option B :** Plafond monétaire fixe saisi manuellement dans les paramètres légaux.
- **Impact de la décision :** Historisation et mise à jour transparente lors des futurs décrets gouvernementaux malagasy.

---

## J. Cotisation Santé (OSTIE vs OMSI)

### Question J1 : Plafonnement Éventuel de la Cotisation Santé Interentreprises -> ce qui se fait au code malgache.

- **Pourquoi nous devons la poser :** À Antananarivo (OSTIE), le taux est usuellement de 1% salarié et 1% employeur sans plafond officiel (ou avec plafond spécifique selon convention). À Toamasina (OMSI), le taux constaté est de 2% salarié et 2% employeur.
- **Comportement actuel du SIRH :** Prévoit un champ `plafond_sante` dans `site_cotisation`, mais celui-ci est actuellement `NULL` (cotisation déplafonnée sur le brut total).
- **Comportement Excel / historique :** Déplafonné sur l'ensemble de l'assiette brute.
- **Option A (Recommandée) :** Cotisation 100% déplafonnée sur l'assiette santé brute (comportement historique Galaxam).
- **Option B :** Application d'un plafond aligné sur le plafond CNaPS si l'organisme l'impose.
- **Impact de la décision :** Élimine toute incertitude dans `SocialContributionCalculator`.

---

## K. Impôt sur les Revenus Salariaux (IRSA)

### Question K1 : Minimum de Perception Fiscal et Seuil d'Exonération -> option A, toujours minimum meme si on a des reduciton enfant a charge le minimum est celui a prendre

- **Pourquoi nous devons la poser :** La Loi de Finances malagasy a fait évoluer le minimum de perception de l'IRSA (historiquement fixé à 2.000 Ar, puis porté à 3.000 Ar dans certaines lois de finances). De plus, les tranches de revenus inférieures à 350.000 Ar sont taxées à 0%.
- **Comportement actuel du SIRH :** Minimum de perception fixé à 2.000 Ar si l'impôt calculé est inférieur à 2.000 Ar (sauf exonération totale).
- **Comportement Excel / historique :** Applique 2.000 Ar comme plancher fiscal.
- **Exemple chiffré :** Salarié dont l'impôt brut selon barème est de 1.200 Ar.
  - *Avec minimum 2.000 Ar :* IRSA prélevé = 2.000 Ar.
  - *Sans minimum :* IRSA prélevé = 1.200 Ar.
- **Option A (Recommandée) :** Paramétrage explicite du seuil de minimum de perception (2.000 Ar) dans la table `parametre_bareme` avec date de validité.
- **Option B :** Application stricte du barème progressif sans plancher artificiel sauf exigence fiscale notifiée.
- **Impact de la décision :** Conformité aux contrôles fiscaux de la Direction Générale des Impôts (DGI).

### Question K2 : Mode d'Imputation de la Réduction pour Personnes à Charge -> ne peut pas descendre du minimum, toujours le minim option A

- **Pourquoi nous devons la poser :** Le Code Général des Impôts prévoit une réduction d'impôt de 2.000 Ar par personne à charge (enfant mineur). Cette réduction s'impute-t-elle sur l'impôt calculé avant ou après application du minimum de perception ?
- **Comportement actuel du SIRH :** Déduction appliquée sur l'impôt calculé : $\max(\text{MinimumFiscal}, \text{IRSA}_{\text{brut}} - (\text{NbEnfants} \times 2.000))$.
- **Comportement Excel / historique :** L'IRSA final ne descend jamais en dessous du minimum de perception (2.000 Ar), même si le salarié a 5 enfants.
- **Option A (Recommandée) :** Respect strict du plancher : l'impôt après réduction d'enfants ne peut être inférieur au minimum de perception.
- **Option B :** La déduction peut ramener l'impôt à 0 Ar.
- **Impact de la décision :** Modélisation de l'algorithme d'écrêtement fiscal dans `TaxCalculator`.

---

## L. Impôt Synthétique Intermittent (ISI)

### Question L1 : Statut et Périmètre d'Application de l'ISI chez Galaxam -> ISI concerne les consultant externe mais pour l'instant on a pas de test donc vaut mieux ne plus toucher, on regarde juste selon son regime.

- **Pourquoi nous devons la poser :** Le système contient une logique pour l'ISI (Impôt Synthétique Intermittent au taux de 5%). Il faut clarifier si l'ISI concerne des consultants externes ou certains salariés sous contrat spécifique au sein de Galaxam.
- **Comportement actuel du SIRH :** Conditionné par l'indicateur contractuel `regime_fiscal = 'ISI'`. Dans ce cas, aucune cotisation CNaPS ni Santé n'est prélevée, et l'ISI remplace intégralement l'IRSA.
- **Comportement Excel / historique :** Non présent sur la majorité des bulletins mensuels permanents.
- **Option A (Recommandée) :** Conserver la gestion exclusive : si Régime = ISI, alors Prélèvement = $5\% \times \text{Brut}$, avec exonération totale des charges sociales CNaPS/Santé salariées et patronales.
- **Option B :** Désactiver l'ISI du moteur de paie standard s'il relève de la comptabilité fournisseurs/honoraires.
- **Impact de la décision :** Simplification ou maintien de la branche fiscale alternative.

---

## M. Acomptes, Avances & Quotité Cessible

### Question M1 : Plafonnement des Retenues au Titre de la Quotité Cessible Légale -> réponse DAF : paramétrer la règle ; barèmes/catégories à confirmer par DAF/juridique

- **Pourquoi nous devons la poser :** En droit du travail malagasy, la quotité saisissable/cessible limite les retenues sur salaire (hors impôts et cotisations) à un pourcentage maximum du salaire net (généralement 1/3 ou 1/5) pour garantir un reste à vivre minimal au collaborateur.
- **Comportement actuel du SIRH :** L'acompte est retenu jusqu'au net disponible ; les avances spéciales employeur sont plafonnées à 10 % et les prêts internes à 1/3 par constantes Java. Ces ratios et leur assiette ne sont pas justifiés par un barème juridique daté et ne couvrent pas la distinction entre acompte, avance spéciale, cession et saisie.
- **Comportement Excel / historique :** Gestion manuelle par la DAF qui reporte le reliquat au mois suivant.
- **Exemple chiffré :** Salaire net avant retenues = 300.000 Ar. Échéance de prêt = 250.000 Ar.
  - *Sans quotité cessible :* Net à payer = 50.000 Ar (ou négatif si acompte supérieur).
  - *Avec quotité 1/3 :* Retenue maximale = 100.000 Ar. Net versé = 200.000 Ar. Reliquat reporté = 150.000 Ar.
- **Option A (Recommandée) :** Détecter le dépassement de la quotité cessible paramétrable, bloquer le calcul en alertant le gestionnaire RH avec le montant du reliquat non imputé.
- **Option B :** Autoriser la déduction intégrale jusqu'à épuisement du net (plancher à 0 Ar) et reporter automatiquement le solde impayé dans le registre des avances.
- **Impact de la décision :** Conception du module de déductions de trésorerie `TreasuryDeductionCalculator`.
