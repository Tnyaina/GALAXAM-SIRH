# Document de Référence Technique et Fonctionnel : Module de Gestion des Congés et Absences à Madagascar

> **État d'implémentation (mise à jour du 17/08/2026)**
> Ce document sert de référentiel métier complet. Le script `26-08-13-sp2-conges.sql`
> en est l'implémentation SQL. Deux arbitrages ont été tranchés lors de la conception :
>
> - **Pauses d'allaitement (§3.4)** : **non retenu** dans le périmètre du module Congés — ne fait l'objet d'aucune table ni colonne.
> - **Co-financement CNaPS / IJM maternité (§4.3)** : **reporté au module Paie** (phase 2) — documenté ici à titre de référence légale, mais aucune table de suivi n'existe encore.
>
> Tout le reste du présent document (acquisition, décompte calendaire, typologies, bonification d'ancienneté, prescription/FIFO, dérogations, alertes) est implémenté dans le schéma actuel.

## 1. Cadre Régalien et Normatif du Droit du Travail Malgache

### 1.1 Textes Fondamentaux et Principes Généraux

La conception d'un module informatique de gestion des congés et des
absences pour une entreprise opérant à Madagascar nécessite une
conformité absolue avec le droit du travail local. Le cadre légal
régissant les relations de travail dans le secteur privé malgache repose
principalement sur la Loi n° 2003-044 du 28 juillet 2004 portant Code du
Travail, complétée par le Décret n° 2007-563 qui fixe les modalités
d'application de la durée légale du travail^1^.

Le Code du Travail garantit le congé payé comme un droit fondamental du
salarié, visant à préserver sa santé, sa sécurité et son équilibre
socioprofessionnel^3^. Les dispositions légales relatives aux temps de
repos, absences et suspensions du contrat de travail étant d'ordre
public, toute disposition conventionnelle ou contractuelle accordant des
droits inférieurs aux minima légaux est nulle de plein droit.
L'architecture du Système d'Information Ressources Humaines (SIRH)
doit intégrer ces contraintes réglementaires directement au sein de son
moteur de règles pour en assurer l'application systématique^1^.

---

  **Textes                **Champ d'Application  **Impacts Directs sur
  Réglementaires**        & Articles de           le Système SIRH**
                          Référence**

---

  **Loi n° 2003-044 (Code Articles 13, 85, 86,    Définition des règles
  du Travail)**           87, 88, 90, 95, 97,     d'acquisition, de
                          98^1^                   décompte, des plafonds
                                                  et des suspensions du
                                                  contrat^1^.

  **Décret n° 2007-563**  Durée du travail :      Valorisation horaire
                          40h/semaine ou          des retenues pour
                          173,33h/mois^1^         absences sur la
                                                  paie^1^.

**Code de Prévoyance    Prestations familiales  Gestion de la prise en
  Sociale / CNaPS**       et indemnités           charge financière des
                          journalières^6^         congés maternité et
                                                  maladie longue
                                                  durée^1^.
------------------------------------------------------------

### 1.2 Droit de Jouissance et Conditions d'Ancienneté

Le droit de jouissance aux congés payés annuels est réglementé par
l'article 85 du Code du Travail, qui impose une période minimale de
service effectif égale à douze (12) mois continus auprès du même
employeur^1^. Durant cette première année d'engagement, le salarié
cumule des droits théoriques mois par mois, mais la possibilité de poser
des jours de repos annuel est en principe bloquée jusqu'à l'atteinte
de cette ancienneté^1^.

Une distinction s'impose donc entre la phase d'acquisition du droit et
la phase de jouissance effective^1^. Si la loi restreint l'exercice du
congé annuel pendant les douze premiers mois, le SIRH doit néanmoins
offrir la flexibilité nécessaire aux gestionnaires RH pour autoriser des
congés par anticipation via un mécanisme de dérogation explicite^1^. Par
ailleurs, cette condition d'ancienneté d'un an ne s'applique pas aux
congés d'événements familiaux ni aux congés de maternité ou de maladie,
qui sont accessibles dès l'embauche du salarié^1^.

> **Précision Technique & Best Practice RH (Calcul de l'Ancienneté)** : 
> L'application évalue la condition de 12 mois continus en calculant l'écart entre la date de la demande et la **date de début du Contrat de Travail ACTIF**. 
> Si un employé enchaîne un CDD puis un CDI sans interruption, la bonne pratique dans le SIRH est de **modifier le contrat actif existant** (changer le type et supprimer la date de fin) plutôt que de créer un nouveau contrat. Cela permet de préserver l'ancienneté globale pour l'acquisition et la jouissance des droits (ainsi que la future prime d'ancienneté à 5 ans).

### 1.3 Régime d'Acquisition et Calcul des Droits

L'acquisition du congé payé annuel s'effectue au rythme de 2,5 jours
calendaires par mois de travail effectif accompli^1^. Pour un exercice
complet de douze mois, le cumul annuel s'élève à exactement 30 jours
calendaires de congé payé^1^.

![](media/image11.png){width="6.458333333333333in"
height="0.5730949256342958in"}

Le calcul de cette acquisition s'appuie sur le temps de travail
effectif. La législation malgache assimile plusieurs motifs d'absence à
du travail effectif pour la détermination des droits à congés^13^. Il
s'agit notamment des périodes de congé de maternité^13^, des absences
consécutives à un accident du travail ou une maladie professionnelle,
des congés exceptionnels pour événements familiaux dans la limite du
quota légal^1^, ainsi que des arrêts maladie dûment justifiés par un
certificat médical dans la limite d'un plafond de six mois^1^.

En complément de l'acquisition de base, les articles 86 et 87 du Code
du Travail prévoient un mécanisme de bonification d'ancienneté^5^. Les
salariés comptant plus de trois ans de service continu bénéficient de
jours de congés supplémentaires à raison d'un jour additionnel par
tranche de cinq années d'ancienneté, sans que ces majorations ne
puissent conduire à dépasser le plafond global de congé annuel prévu par
la réglementation^5^.

> **Implémenté** : pilotée par les paramètres globaux
> `SEUIL_ANCIENNETE_BONIFICATION_ANS` (3), `PALIER_ANNEES_BONIFICATION` (5)
> et `JOURS_BONIFICATION_PAR_PALIER` (1). Écrite dans le journal des
> mouvements sous un type dédié `BONIFICATION`, distinct d'`ACQUISITION`,
> pour garder un reporting propre entre le crédit mensuel de base et la
> majoration légale d'ancienneté. Le plafond global reste garanti par
> `PLAFOND_CUMUL_SOLDE_CONGE_JOURS` (90j) déjà existant.

## 2. Décompte des Congés et Modalités de Prise

### 2.1 Principe du Décompte en Jours Calendaires Stricts

L'une des spécificités fondamentales du droit du travail malgache
réside dans le décompte des congés annuels en **Jours Calendaires
Stricts**^1^. Contrairement à d'autres législations qui appliquent un
décompte en jours ouvrés (5 jours travaillés par semaine) ou en jours
ouvrables (6 jours par semaine), le système malgache décompte
l'intégralité des jours calendaires écoulés pendant la période
d'absence^1^.

L'algorithme de décompte d'une demande de congé s'exprime par la
formule suivante :

![](media/image9.png){width="6.458333333333333in"
height="0.5730949256342958in"}

Cette méthode découle directement de la logique de calcul de la paie, où
l'acquisition mensuelle de 2,5 jours est calculée sur la base d'un
mois forfaitaire de 30 jours calendaires^1^.

### 2.2 Impact des Week-ends et Jours Fériés (Règle de l'Article 88)

L'article 88 alinéa 5 du Code du Travail dispose que le congé commence
le jour où le travailleur aurait dû travailler s'il n'avait pas été en
congé, et se termine la veille de son retour effectif au travail^14^.
Cette définition implique que l'ensemble des samedis, dimanches et
jours fériés chômés compris à l'intérieur de la période d'absence
demandée est imputé sur le solde de congé du salarié^1^.

---

  **Situation   **Date      **Date      **Date Retour **Jours     **Explication
  de la         Début**     Fin**       Effective**   Décomptés   Métier / Fondement
  Demande**                                           du Solde**  Légal**

---

  **Semaine     Lundi       Dimanche    Lundi suivant **7 jours** Ingestion continue
  complète**                                                      incluant le samedi
                                                                  et le dimanche^1^.

  **Vendredi    Vendredi    Dimanche    Lundi suivant **3 jours** Le congé s'achève
  posé seul**                                                     la veille du retour
                                                                  (dimanche soir),
                                                                  englobant le
                                                                  week-end^1^.

  **Pont /      Mercredi    Dimanche    Lundi suivant **5 jours** Les jours fériés
  Férié                                                           situés dans
  inclus**                                                        l'intervalle sont
                                                                  décomptés^1^.

**Période sur Lundi (S1)  Dimanche    Lundi (S3)    **14        Imputation continue
  2 semaines**              (S2)                      jours**     sans neutralisation
                                                                  des jours de repos
                                                                  hebdomadaires^1^.
-----------------------------------------------------------------------------------

Cette logique impose un devoir d'information et de transparence au
niveau de l'interface utilisateur du SIRH. L'outil doit afficher une
simulation exacte du nombre de jours qui seront débités avant que le
salarié ne valide sa demande, évitant ainsi les contestations liées à
l'inclusion des jours non travaillés^1^.

### 2.4 Prise en charge des Demi-Journées de Congé (0,5 j)

Afin de répondre à la réalité du terrain et aux besoins d'absences de courte durée (démarches administratives, impératifs personnels), le SIRH GALAXAM supporte la pose de **demi-journées de congé payé (0,5 j)** :

- **Attributs de saisie** : `est_demi_journee = TRUE`, accompagné du choix du moment (`moment_demi_journee IN ('MATIN', 'APRES_MIDI')`).
- **Contrainte de période** : La date de début et la date de fin doivent être strictement identiques (`date_debut = date_fin`).
- **Impact sur le solde** : Le décompte est forfaitairement égal à **0,5 jour** (`nb_jours_decomptes = 0.5`).
- **Traitement en paie** : Étant couverte par un solde de congé payé valide, la demi-journée donne droit au **maintien intégral de salaire (100%)**. Aucune retenue pour heure d'absence n'est opérée sur la paie. Seul le compteur de solde de congé est débité de 0,5 jour.

### 2.3 Règle Obligatoire de Fractionnement

Afin de préserver le rôle de repos biologique et de récupération
physique assigné au congé payé, l'article 85 du Code du Travail encadre
les modalités de fractionnement^1^. Si le congé annuel peut être pris de
manière échelonnée au cours de l'année sociale avec l'accord de
l'employeur, la loi exige qu'au moins une tranche de congé présente
une durée continue minimale d'au moins douze (12) jours calendaires
consécutifs^1^.

Le moteur de règles du SIRH doit inclure une vérification automatique de
cette contrainte. À l'approche de la clôture de l'exercice annuel, si
un employé n'a pas posé une tranche continue d'au moins 12 jours, le
système doit émettre un signalement à l'attention de l'équipe RH afin
d'inciter le salarié à planifier cette période obligatoire^1^.

## 3. Typologies des Absences et Traitements Métier Spécifiques

Chaque typologie d'absence répond à des règles d'acquisition, de
décompte, de rémunération et de justification distinctes, résumées dans
le tableau ci-dessous avant d'être détaillées dans les sous-sections
suivantes.

---

  **Type de Congé (code)** **Rémunération**   **Imputation   **Plafond /      **Justificatif   **Soumis à
                                              Solde Congé**  Quota Légal**    Obligatoire**    Préavis**

---

  **ANNUEL**               100 %              Oui            30 jours par     Non^4^           Oui (2j ou
                                                             an^1^                             15j)^1^

  **EXCEPTIONNEL**         100 %              Non            Pool partagé de  Oui^1^           Non^1^
                                                             10j/an^1^

  **PATERNITE**            100 %              Non            3 jours (imputés Oui^1^           Non^1^
                                                             sur pool 10j)^1^

  **MALADIE**              100 % (employeur)  Non            30 jours par     Oui (48h)^5^     Non^1^
                                                             épisode^1^

  **MALADIE_SANS_SOLDE**   0 % (CNaPS)        Non            Jusqu'à 150j    Oui^4^           Non^4^
                                                             (max 6 mois
    total)^1^

  **MATERNITE**            100 % (50% CNaPS + Non            98 jours         Oui^4^           Non^1^
                           50% Employeur)^6^                 consécutifs^1^

**SANS_SOLDE /           0 %                Non            Variable selon   Non^4^           Oui^1^
  SABBATIQUE**                                               accord^1^
--------------------------------------------------------------------

### 3.1 Congé Annuel Payé

Le congé annuel payé constitue la modalité standard de repos accordée
aux salariés^1^. Il est intégralement rémunéré par l'employeur sur la
base du maintien du salaire brut^1^. Les journées consommées sont
déduites du solde de congés acquis par l'employé^1^. Sa prise nécessite
le respect d'un préavis d'entreprise, paramétrable dans le SIRH à 2
jours ouvrés pour les absences courtes (inférieures ou égales à 3 jours)
et à 15 jours pour les absences de plus longue durée^1^.

### 3.2 Pool Annuel des Permissions Exceptionnelles et Événements Familiaux

Conformément aux dispositions de l'article 88 du Code du Travail, des
autorisations d'absence payées sont accordées aux salariés à
l'occasion d'événements familiaux majeurs^1^. Ces absences n'imputent
pas le solde des congés payés annuels, mais sont comptabilisées au sein
d'un **pool annuel global plafonné à 10 jours calendaires par an**^1^.

> **Précision Technique (Année Civile)** : 
> Le décompte de ces 10 jours se fait strictement sur l'**année civile** (du 1er Janvier au 31 Décembre), indépendamment de la date d'embauche de l'employé. Chaque 1er Janvier, le système réinitialise implicitement le plafond pour tout le monde (la vue SQL filtre sur `EXTRACT(YEAR FROM date_debut)`).

Une règle essentielle du droit malgache réside dans l'intégration du
**Congé de Paternité** au sein de ce même quota de 10 jours^1^. La
paternité n'accorde pas un crédit distinct de 10 jours en sus des
événements familiaux, mais constitue une imputation de 3 jours à prlever
directement sur le pool des 10 jours de permissions exceptionnelles^1^.

Les motifs pris en charge par le sous-référentiel des événements
familiaux (ref_motif_evenement_familial) comprennent :

- **Mariage du salarié** (MARIAGE) : 3 jours accordés sur présentation

  > d'un acte de mariage^1^.
  >
- **Naissance ou baptême d'un enfant** (NAISSANCE) : 2 jours (portés

  > à 3 jours au titre du congé paternité)^1^.
  >
- **Décès du conjoint ou d'un enfant** (DECES_CONJOINT_ENFANT) : 3

  > jours sur justification^1^.
  >
- **Décès d'un parent ou beau-parent** (DECES_PARENT) : 2 jours sur

  > justification^1^.
  >

Dès lors que le cumul annuel des permissions exceptionnelles d'un
salarié atteint 10 jours, toute nouvelle demande sur ces motifs doit
être bloquée par le système applicatif ou basculée vers le congé annuel
payé (sur accord de la RH) ou en congé sans solde^1^.

### 3.3 Congés Maladie : Maintien de Salaire et Bascule en Absence Non Rémunérée

L'arrêt de travail pour maladie ou accident non professionnel fait
l'objet d'un suivi spécifique afin de déterminer la charge financière
incombant à l'entreprise, en application de l'article 13 du Code du
Travail^1^ :

- **Période Maintien de Salaire (MALADIE)** : L'employeur maintient

  > la rémunération intégrale du salarié pendant un arrêt continu ne
  > dépassant pas **30 jours par épisode médical**^1^. Cette durée de
  > 30 jours s'apprécie par épisode d'arrêt et non par année
  > civile^4^.
  >
- **Bascule Automatique (MALADIE_SANS_SOLDE)** : À compter du 31ème

  > jour d'un même arrêt de travail, le SIRH applique une bascule
  > automatique de la demande vers le type MALADIE_SANS_SOLDE^1^.
  > L'employeur suspend le versement du salaire, la prise en charge
  > financière étant alors assurée par la CNaPS ou l'organisme de
  > prévoyance interentreprises^1^.
  >
- **Plafond Absolu de Suspension** : La durée maximale pendant

  > laquelle le contrat de travail peut demeurer suspendu pour motif
  > de maladie est fixée à **6 mois (180 jours)**^1^. Au-delà de ce
  > délai continu de 180 jours, l'employeur est en droit d'engager
  > une procédure de remplacement définitif ou de rupture du contrat
  > dans les conditions prévues par la loi^4^.
  >

### 3.4 Congé Maternité, Paternité et Dispositions Relatives à la Parentalité

La protection de la maternité s'appuie sur des règles strictes
combinant le Code du Travail et le Code de Prévoyance Sociale de la
CNaPS^1^ :

- **Durée du Congé de Maternité** : Le congé légal est fixé à **14

  > semaines consécutives (soit 98 jours calendaires)**, se
  > répartissant généralement en 6 semaines avant la date présumée de
  > l'accouchement et 8 semaines après celui-ci^1^. Le décompte est
  > calendaire strict et ne subit aucune neutralisation pour les jours
  > fériés ou week-ends^1^.
  >
- **Extension pour Complication Médicale** : En cas d'état

  > pathologique attesté par un certificat médical agréé et résultant
  > de la grossesse ou des couches, le congé peut être prolongé d'une
  > durée maximale de **3 semaines (21 jours)**, portant la durée
  > maximale de la suspension à 17 semaines (119 jours)^6^.
  >
  > **Implémenté** : tracé via `demande_conge.extension_pathologique_maternite`
  > (booléen, sur le même principe que `derogation_solde_negatif` /
  > `derogation_delai_preavis`) et les paramètres globaux
  > `DUREE_MAX_MATERNITE_STANDARD_JOURS` (98) et
  > `DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS` (119). Justificatif obligatoire
  > si le flag est activé.
  >

> **Non retenu** : la période d'allaitement post-retour (15 mois, pauses
> quotidiennes rémunérées) a été exclue du périmètre du module Congés —
> il ne s'agit pas d'une absence mais d'un aménagement récurrent du temps
> de présence journalier, hors du modèle `demande_conge`.

### 3.5 Congés Non Rémunérés (Sans Solde, Sabbatique et Formation)

Les absences de type congé sans solde (SANS_SOLDE), congé sabbatique
(SABBATIQUE) ou congé de formation (FORMATION) correspondent à des
suspensions conventionnelles du contrat de travail^1^. Elles ne donnent
lieu à aucune rémunération de la part de l'employeur et n'imputent pas
le solde de congés payés^1^. Le SIRH doit tracer ces absences afin de
suspendre le calcul de l'ancienneté et la génération des droits à
congés payés sur les périodes concernées lorsque la durée d'absence
excède les seuils réglementaires^1^.

> **Implémenté** : `ref_type_conge.suspend_acquisition_solde` (booléen,
> `TRUE` pour `SANS_SOLDE`, `SABBATIQUE`, `FORMATION`, `MALADIE_SANS_SOLDE`).
> Un seul flag sert deux usages légaux liés : suspendre le crédit mensuel
> de 2,5j **et** neutraliser le décompte d'ancienneté pour ces mêmes
> périodes, puisqu'il s'agit juridiquement de la même notion de "temps de
> travail effectif". Les types assimilés à du temps de travail effectif
> (`ANNUEL`, `MALADIE` dans l'épisode, `MATERNITE`, `EXCEPTIONNEL`,
> `PATERNITE`) restent à `FALSE` par défaut.

## 4. Règles Multi-Paliers de Valorisation Financière et Paie

### 4.1 Valorisation de l'Indemnité de Congé Payé (Diviseur 30)

Pour assurer une stricte équivalence financière avec le décompte en
jours calendaires, la valorisation comptable d'une journée de congé
payé reposant sur l'acquisition mensuelle de 2,5 jours exige
l'application d'un diviseur forfaitaire mensuel de **30 jours**^1^.

La base de calcul prend en compte le salaire de base brut, majoré des
primes d'ancienneté et des compléments de rémunération réguliers, ainsi
que des heures supplémentaires habituelles^1^. La formule de
valorisation d'un jour de congé payé annuel s'établit comme suit :

![](media/image8.png){width="6.458333333333333in"
height="0.8082097550306212in"}

Lorsqu'un salarié prend une semaine complète de congé (soit 7 jours
calendaires décomptés), le montant indemnise équivaut à exactement
![](media/image1.png){width="2.986655730533683in"
height="0.2705686789151356in"}, préservant ainsi la neutralité de la
paie mensuelle^1^.

### 4.2 Retenue pour Absence Effective (Diviseur 173,33)

À l'inverse de la valorisation des congés payés, le calcul d'une
retenue sur salaire motivée par une absence non rémunérée ou un retard
s'appuie sur la durée légale du travail effectif définie par le Décret
n° 2007-563 (40 heures hebdomadaires, soit 173,33 heures mensuelles)^1^.

Pour déterminer la valeur d'une heure d'absence, le système doit
diviser le salaire de base brut par 173,33 heures^1^. Dans le cas d'une
retenue comptabilisée en jours travaillés effectifs, le diviseur
équivalent est de 21,67 jours (résultant du rapport
![](media/image6.png){width="2.939530839895013in"
height="0.2605971128608924in"})^1^.

![](media/image12.png){width="6.458333333333333in"
height="0.7567782152230971in"}

![](media/image4.png){width="6.458333333333333in"
height="0.7567782152230971in"}

+-----------------+-----------------+-----------------+-----------------+
| **Domaine       | **Base du       | **Formule de    | **Alignement    |
| d               | Diviseur        | Calcul          | Réglementaire** |
| 'Application** | Métier**        | Financier**     |                 |
+=================+=================+=================+=================+
| **Indemnité de  | **30 jours**    | ![](m           | Cohérence avec  |
| Congé Payé**    | (Forfait        | edia/image3.png | l'acquisition  |
|                 | Calendaire      | ){width="2.2536 | à 2,5j/mois et  |
|                 | Mensuel)^1^     | 668853893262in" | le décompte     |
|                 |                 | height="0.26084 | continu^1^.     |
|                 |                 | 09886264217in"} |                 |
|                 |                 |                 |                 |
|                 |                 | \[cite: 1, 4\]  |                 |
+-----------------+-----------------+-----------------+-----------------+
| **Retenue pour  | **173,33        | ![](me          | Application du  |
| Absence         | heures** (Base  | dia/image10.png | Décret n°       |
| Effective**     | Temps           | ){width="2.3370 | 2007-563 sur le |
|                 | Effectif)^1^    | 002187226597in" | temps de        |
|                 |                 | h               | travail         |
|                 |                 | eight="0.281691 | effectif^1^.    |
|                 |                 | 81977252844in"} |                 |
|                 |                 |                 |                 |
|                 |                 | \[cite: 1, 4\]  |                 |
+-----------------+-----------------+-----------------+-----------------+

### 4.3 Co-financement CNaPS / Employeur pour le Congé Maternité

> **Reporté au Module Paie (Phase 2)** : cette section reste la référence
> légale, mais aucune table de suivi (IJM, répartition CNaPS/employeur,
> statut de remboursement) n'existe encore dans le schéma Congés actuel.
> À reprendre lors de la conception du module Paie.

Pendant la durée légale du congé de maternité (98 jours), la salariée
conserve le bénéfice de son salaire intégral^6^. L'effort financier
fait l'objet d'un mécanisme de co-financement d'ordre public entre la
Caisse Nationale de Prévoyance Sociale (CNaPS) et l'employeur^5^ :

1. **Prestation de la CNaPS (Demi-Salaire)** : La CNaPS prend en charge

   > 50 % du salaire journalier sous forme d'Indemnités Journalières
   > de Maternité (IJM)^5^. Ce versement intervient en deux fractions
   > (une première tranche de 8 semaines et une seconde de 6 semaines),
   > sous réserve de la constitution du dossier médical^6^.
   >
2. **Part Employeur** : L'employeur a l'obligation légale de verser

   > les 50 % restants de la rémunération brute ainsi que le maintien
   > des avantages en nature^5^.
   >
3. **Pénalité en cas de Non-Affiliation** : Si l'employeur n'a pas

   > immatriculé la salariée ou n'est pas à jour de ses cotisations
   > auprès de la CNaPS, la loi met l'intégralité du versement du
   > salaire (100 %) à sa charge exclusive^6^.
   >

## 5. Architecture de Gestion des Soldes, Prescriptions et Dérogations

### 5.1 Architecture en Journal des Mouvements (Ledger Dynamic)

Pour assurer une traçabilité rigoureuse et éviter les altérations de
données associées aux compteurs statiques, le module SIRH s'appuie sur
une structure en **Journal des Mouvements (Ledger Dynamic)**^4^. Le
solde n'est jamais modifié par une mise à jour directe d'une variable
unique, mais calculé dynamiquement par la somme algébrique des
enregistrements de la table mouvement_solde_conge^4^.

Les mouvements de solde pris en charge sont :

- ACQUISITION : Crédit mensuel de +2,5 jours généré automatiquement à

  > chaque clôture de période de paie^1^.
  >
- PRISE : Débit de jours correspondant à la durée d'un congé

  > validé^4^.
  >
- AJUSTEMENT : Mouvement exceptionnel de régularisation créditeur ou

  > débiteur initié par la DRH, devant comporter un motif
  > d'explication^4^.
  >
- ANNULATION : Crédit correctif restituant les jours au solde en cas

  > d'annulation d'une demande antérieurement validée^4^.
  >
- EXPIRATION : Débit d'extinction de droits généré lors de la purge

  > triennale^1^.
  >

### 5.2 Règle Purge FIFO et Prescription Triennale (Plafond 90 Jours)

Aux termes de l'article 85 du Code du Travail, le droit aux congés
payés se prescrit par **trois (3) ans (36 mois)**^1^.

Lors de l'imputation des congés pris, le moteur d'imputation applique
le principe FIFO (*First In, First Out*), en consommant en priorité les
droits acquis les plus anciens^1^. Le cumul maximal du solde de congés
ne peut excéder le plafond légal de **90 jours**
(![](media/image2.png){width="1.795in"
height="0.26090113735783027in"})^1^. Un traitement automatisé exécuté en
fin d'exercice contrôle la date d'acquisition de chaque tranche de
solde non consommée. Toute fraction de solde dont l'ancienneté dépasse
36 mois est purgée du système via l'émission d'un mouvement
d'extinction de type EXPIRATION^1^.

### 5.3 Gestion de l'Anticipation et Soldes Négatifs

Bien qu'un salarié ne puisse en principe poser des jours non encore
acquis, des contraintes opérationnelles peuvent justifier un départ par
anticipation^1^. Le SIRH autorise la validation de congés entraînant un
solde négatif uniquement si les conditions suivantes sont réunies^1^ :

- **Validation de la Dérogation RH** : La demande doit comporter

  > l'activation explicite de l'indicateur de dérogation
  > (derogation_solde_negatif = TRUE)^1^.
  >
- **Plafond d'Anticipation** : La valeur du solde négatif ne peut

  > dépasser le seuil absolu de **-10 jours**, correspondant à environ
  > 4 mois d'acquisition future^1^.
  >
- **Recouvrement en cas de Départ** : En cas de rupture du contrat de

  > travail alors que le solde du salarié est négatif, la valeur
  > financière des jours pris en avance est retenue sur la paie de
  > clôture ou sur le Solde de Tout Compte (STC)^1^.
  >

## 6. Modélisation de la Base de Données et Spécifications Techniques

### 6.1 Dictionnaire des Tables et Référentiels

Le modèle relationnel (implémenté sous PostgreSQL pour la solution SIRH
GALAXAM) s'appuie sur une séparation stricte entre les tables de
référence, les tables transactionnelles et les vues de synthèse^4^.

---

  **Table**                      **Description et Rôle   **Colonnes Clés / Clefs
                                 Métier**                Étrangères**

---

  ref_pool_conge_annuel          Référentiel des quotas  id_pool (PK), code
                                 globaux partagés^4^.    (PERMISSIONS_FAMILIALES),
                                                         plafond_jours_an (10.0)^4^.

  ref_type_conge                 Paramétrage des         id_type_conge (PK), code
                                 typologies de congés et (ANNUEL, MALADIE, etc.),
                                 comportements           est_paye,
                                 applicatifs             decompte_solde_acquis,
                                 associés^4^.            plafond_jours_episode,
                                                         id_pool_conge_annuel,
                                                         **suspend_acquisition_solde**
                                                         (TRUE = période non
                                                         assimilée à du temps de
                                                         travail effectif)^4^.

  ref_motif_evenement_familial   Sous-référentiel        id_motif (PK), code (MARIAGE,
                                 définissant les durées  NAISSANCE, etc.),
                                 accordées par événement nb_jours_autorises,
                                 familial^4^.            justificatif_requis^4^.

  ref_statut_demande_conge       États du cycle de vie   id_statut_demande_conge (PK),
                                 des demandes            code^4^.
                                 (BROUILLON, EN_ATTENTE,
                                 VALIDE, REFUSE,
    ANNULE)^4^.

  demande_conge                  Table transactionnelle  id_demande_conge (PK),
                                 des demandes de         id_employe (FK), date_debut,
                                 congés^4^.              date_fin, nb_jours_decomptes,
                                                         derogation_solde_negatif,
                                                         derogation_delai_preavis,
                                                         **extension_pathologique_
                                                         maternite**^4^.

mouvement_solde_conge          Journal comptable des   id_mouvement (PK), id_employe
                                 variations de soldes de (FK), type_mouvement
                                 congés^4^.              (ACQUISITION,
                                                         **BONIFICATION**, PRISE,
                                                         AJUSTEMENT, ANNULATION,
                                                         EXPIRATION), nb_jours^4^.
----------------------------------------------------------------------------------

### 6.2 Dictionnaire des Paramètres Globaux

Les constantes réglementaires de l'application sont inscrites dans la
table parametre_global afin de permettre une adaptation du système en
cas d'évolution législative sans modification du code source^4^.

---

  **Code Paramètre**                    **Valeur Métier**       **Description / Fondement Légitime**

---

  DIVISEUR_JOURS_CALENDAIRE_CONGE       30                      Diviseur en jours calendaires/mois pour la
                                                                valorisation du congé payé^1^.

  PLAFOND_CUMUL_SOLDE_CONGE_JOURS       90                      Plafond maximal du solde cumulé de congés
                                                                (![](media/image13.png){width="0.8941240157480315in"
                                                                height="0.2287292213473316in"}, Art. 85)^1^.

  DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS   36                      Durée en mois avant expiration des droits à congés
                                                                acquis^1^.

  PLAFOND_SOLDE_CONGE_NEGATIF           -10                     Seuil maximal d'anticipation négative autorisé par
                                                                dérogation RH^1^.

  DELAI_PREAVIS_COURT_JOURS             2                       Préavis minimal (en jours) pour une demande courte
                                                                (![](media/image7.png){width="0.7476399825021872in"
                                                                height="0.22844488188976378in"})^1^.

  DELAI_PREAVIS_LONG_JOURS              15                      Préavis minimal (en jours) pour une demande longue
                                                                (![](media/image14.png){width="0.7476399825021872in"
                                                                height="0.22844488188976378in"})^1^.

  SEUIL_JOURS_PREAVIS_LONG              3                       Seuil de durée déclenchant l'exigence du préavis
                                                                long^4^.

  FRACTIONNEMENT_TRANCHE_MIN_JOURS      12                      Durée minimale obligatoire de la grande tranche
                                                                annuelle (Art. 85)^1^.

  DUREE_MAX_MATERNITE_STANDARD_JOURS    98                      Durée légale standard du congé maternité (14
                                                                semaines calendaires, Art. 85).

  DUREE_MAX_MATERNITE_PATHOLOGIQUE_JOURS 119                    Durée maximale en cas d'extension pour état
                                                                pathologique (98 + 21j), cf.
                                                                extension_pathologique_maternite.

  SEUIL_ANCIENNETE_BONIFICATION_ANS     3                       Ancienneté minimale (années) à partir de laquelle
                                                                la bonification de congé s'applique (Art. 86/87).

  PALIER_ANNEES_BONIFICATION            5                       Nombre d'années par tranche donnant droit à un
                                                                jour de bonification supplémentaire (Art. 86/87).

JOURS_BONIFICATION_PAR_PALIER         1                       Nombre de jours de congé supplémentaires accordés
                                                                par tranche d'ancienneté atteinte (Art. 86/87).
----------------------------------------------------------------------------------------------------------------

### 6.3 Moteur d'Alertes et Notifications Automatiques

Le système intègre un sous-système de notifications RH (notification_rh)
qui déclenche des alertes ciblées lors du franchissement de seuils de
contrôle^4^.

---

  **Code Type d'Alerte**           **Déclencheur Automatique / Règle Métier**            **Destinataires**

---

  DEMANDE_CONGE_EN_ATTENTE          Demande soumise à l'état EN_ATTENTE non traitée      Manager Hiérarchique /
                                    depuis plus de 48h^4^.                                DRH^4^

  SOLDE_CONGE_FAIBLE                Solde disponible d'un employé franchissant un seuil  Employé / Gestionnaire
                                    minimal                                               RH^4^
                                    (![](media/image5.png){width="0.7476399825021872in"
    height="0.22844488188976378in"})^4^.

  SOLDE_CONGE_NEGATIF               Validation d'une demande générant un solde négatif   Responsable RH / DAF^1^
                                    sous dérogation^1^.

  RETOUR_CONGE_J                    Échéance de la date de fin de congé pour vérification Manager direct^4^
                                    de la reprise du travail^4^.

  MALADIE_PLAFOND_EPISODE_ATTEINT   Arrêt maladie continu atteignant 30 jours (bascule    Service RH / Paie^1^
                                    sans solde) ou 180 jours (limite de suspension)^1^.

  POOL_EXCEPTIONNEL_EPUISE          Atteinte du quota de 10 jours de permissions          Employé / Gestionnaire
                                    exceptionnelles sur l'année^1^.                      RH^4^

  FRACTIONNEMENT_NON_RESPECTE       Approche de la fin de l'exercice sans qu'une        Gestionnaire RH^4^
                                    tranche de 12j consécutifs ne soit posée^1^.

BONIFICATION_ANCIENNETE_APPLIQUEE Mouvement BONIFICATION généré à l'anniversaire       Employé / Gestionnaire
                                    d'ancienneté (Art. 86/87)^4^.                        RH^4^
-----------------------------------------------------------------------------------------------

## 7. Recommandations de Mise en Œuvre et Arbitrages Métier

Le déploiement opérationnel du module de gestion des congés dans un
environnement d'entreprise à Madagascar requiert une attention
particulière sur plusieurs choix d'architecture applicative et
d'interfaçage :

Pour prévenir les contestations sociales liées au décompte en jours
calendaires stricts, le portail utilisateurs doit intégrer un composant
d'affichage pédagogique du temps d'absence^1^. Lorsque le salarié
saisit une période couvrant un week-end, le système doit détacher
explicitement le nombre de jours ouvrés et le nombre de jours de repos
hebdomadaires inclus afin de justifier le nombre total de jours
décomptés du solde^1^.

Sur le plan de l'interfaçage avec le module de paie, les concepteurs
doivent garantir l'étanchéité des deux formules de valorisation
financière^1^. L'application du diviseur 30 pour les indemnités de
congés payés d'une part, et du diviseur 173,33 heures (ou 21,67 jours)
pour la retenue des absences non rémunérées d'autre part, doit faire
l'objet de tests de régression automatisés lors de chaque mise à jour
du moteur de paie^1^.

En ce qui concerne la gestion des indemnités de maternité, le SIRH doit
fournir un état récapitulatif des montants d'Indemnités Journalières de
Maternité (IJM) avancés par l'employeur^6^. Ce module de suivi
facilitera le rapprochement comptable lors de la réception des
remboursements effectifs versés par la CNaPS^6^.

Enfin, la procédure d'expiration des congés au terme de la période de
prescription triennale de 36 mois doit s'accompagner d'un processus de
pré-alerte automatique à destination des salariés concernés six mois
avant l'échéance^1^. Cette démarche permet d'inciter la consommation
des jours anciens, réduisant ainsi les charges financières liées aux
provisions pour congés payés au bilan de l'entreprise^1^.

#### Sources des citations

1. conges.md
2. Code du Travail à Madagascar 2024 \| PDF \| Droit - Scribd,

   > [[https://fr.scribd.com/doc/95983994/Mada-Code-Du-Travail]{.underline}](https://fr.scribd.com/doc/95983994/Mada-Code-Du-Travail)
   >
3. DROIT DE CONGE.pdf
4. 26-08-13-sp2-conges.sql
5. Congés et absences en Madagascar 2026 : guide RH - Illizeo,

   > [[https://illizeo.com/absences-mondiales/madagascar/]{.underline}](https://illizeo.com/absences-mondiales/madagascar/)
   >
6. Droits et obligations sur le congé de maternité à Madagascar. - MDG

   > Services,
   > [[https://www.madagascar-services.com/blog/fr/2018/03/19/conge-de-maternite-a-madagascar-droits-obligations/]{.underline}](https://www.madagascar-services.com/blog/fr/2018/03/19/conge-de-maternite-a-madagascar-droits-obligations/)
   >
7. Code de Travail Malgache: Dispositions Générales \| PDF \| Heures

   > supplémentaires - Scribd,
   > [[https://fr.scribd.com/document/827066405/DROIT-MALGACHE-DE-TRAVAIL]{.underline}](https://fr.scribd.com/document/827066405/DROIT-MALGACHE-DE-TRAVAIL)
   >
8. La sécurité sociale à Madagascar - Cleiss,

   > [[https://www.cleiss.fr/docs/regimes/regime_madagascar.html]{.underline}](https://www.cleiss.fr/docs/regimes/regime_madagascar.html)
   >
9. Nombre de jours de congés pour les employés à Madagascar -

   > Assistelio Relocation,
   > [[https://www.mada-relocation.com/conges/]{.underline}](https://www.mada-relocation.com/conges/)
   >
10. Congés exceptionnels pour événements familiaux (mariage, décès) :

    > toutes les durées,
    > [[https://www.juritravail.com/Actualite/conges-exceptionnels-a-combien-de-jours-avez-vous-droit/Id/1267]{.underline}](https://www.juritravail.com/Actualite/conges-exceptionnels-a-combien-de-jours-avez-vous-droit/Id/1267)
    >
11. Les congés pour événements familiaux \| Gérer le personnel -

    > Editions Tissot,
    > [[https://www.editions-tissot.fr/oe/content.aspx?codeSpace=GLP&amp;idSGML=8a0b5f11-b0dc-4a02-90eb-d433c9b2be71&amp;op=1&amp;chapitre=C4&amp;pageNumber=2§ion=P02C4F030]{.underline}](https://www.editions-tissot.fr/oe/content.aspx?codeSpace=GLP&idSGML=8a0b5f11-b0dc-4a02-90eb-d433c9b2be71&op=1&chapitre=C4&pageNumber=2&section=P02C4F030)
    >
12. Droit du travail à Madagascar : ce que les employeurs doivent savoir

    > (EOR) - Africa HR,
    > [[https://africa-hr.com/fr/blog/madagascar-labour-law/]{.underline}](https://africa-hr.com/fr/blog/madagascar-labour-law/)
    >
13. Congés pour évènements familiaux (décès, mariage, naissance\...) -

    > Restauration de collectivités - Code du travail numérique,
    > [[https://code.travail.gouv.fr/contribution/les-conges-pour-evenements-familiaux/1266-restauration-de-collectivites]{.underline}](https://code.travail.gouv.fr/contribution/les-conges-pour-evenements-familiaux/1266-restauration-de-collectivites)
    >
14. Droits de congé à Madagascar \| PDF \| Grossesse - Scribd,

    > [[https://fr.scribd.com/document/632653038/Calcul-Conges-payes-a-Madagascar]{.underline}](https://fr.scribd.com/document/632653038/Calcul-Conges-payes-a-Madagascar)
    >
15. DES LIEUX DE TRAVAIL FAVORABLES À LA FAMILLE - Unicef,

    > [[https://www.unicef.org/madagascar/en/media/15091/file/Outil_Lieux%20de%20Travail%20favorables%20a%20la%20famille_VF_avec%20nouveaux%20annexes.pdf.pdf]{.underline}](<https://www.unicef.org/madagascar/en/media/15091/file/Outil_Lieux%20de%20Travail%20favorables%20a%20la%20famille_VF_avec%20nouveaux%20annexes.pdf.pdf>)
    >
16. Protection sociale CNaPS - FIRAISAN'NY SENDIKAN'NY MPIASA ETO

    > MADAGASIKARA,
    > [[https://fisema.mg/fiche-cnaps/]{.underline}](https://fisema.mg/fiche-cnaps/)
    >
17. Indemnités Congé Maternité CNPS \| PDF \| Congé maladie \|

    > Accouchement - Scribd,
    > [[https://fr.scribd.com/document/791348722/Salarie-CNPS]{.underline}](https://fr.scribd.com/document/791348722/Salarie-CNPS)
    >
18. PROCEDURES DE GESTION DE LA MAIN D'ŒUVRE - Unité Coordination

    > Projets,
    > [[https://www.ucp.mg/tel_pdf/pdf/MG_P178279_PGMO_FA_202211.pdf]{.underline}](https://www.ucp.mg/tel_pdf/pdf/MG_P178279_PGMO_FA_202211.pdf)
    >
19. Madagascar French \| PDF \| International Labour Organization \|

    > Employment - Scribd,
    > [[https://www.scribd.com/document/845132906/madagascar-french]{.underline}](https://www.scribd.com/document/845132906/madagascar-french)
    >
20. Le régime malgache de sécurité sociale,

    > [[https://www.univ-tlse2.fr/medias/fichier/madagascar-regime-de-securite-sociale-pour-salaries-version-n2_1418995342833-pdf?ID_FICHE=38825&amp;INLINE=FALSE]{.underline}](https://www.univ-tlse2.fr/medias/fichier/madagascar-regime-de-securite-sociale-pour-salaries-version-n2_1418995342833-pdf?ID_FICHE=38825&INLINE=FALSE)
    >
