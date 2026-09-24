# Audit critique — configuration Pointage & Présence

Audit de conception, **sans modification du code ni de la BDD**. Les « valeurs » ci-dessous sont les valeurs des scripts d'initialisation du dépôt, **pas une attestation des valeurs de la base en exploitation**. Le périmètre inclut `parametre_global`, les horaires et indemnités de site, la tolérance d'arrivée, ainsi que les paramètres légaux effectivement consultés par Pointage.

Les constats runtime des sections suivantes décrivent l'état relevé pendant l'audit. Un correctif ultérieur est consigné dans « Correctif appliqué après l'audit » en fin de document.

## 1. Verdict

La table `parametre_global` a un début de versionnement utile (`date_debut`, `date_fin`) et distingue `est_actif` de `est_desactivable`. Mais elle expose des **fragments de règle** comme s'ils étaient des règles autonomes. Le second repas est le cas le plus risqué : quatre interrupteurs indépendants pour deux tranches, sans validation commune, alors que le moteur ne prend l'activation que sur les seuils. Les lectures runtime mélangent en outre trois modèles : `loadActiveMap` (un inactif ressemble à un absent), `loadEtatMap` (état explicite mais erreurs SQL masquées), et des fallbacks Java. Les montants et horaires de site sont parfois datés en BDD mais lus sans filtre de date. Le système est donc *versionnable en apparence*, pas systématiquement reproductible.

**Réponse centrale :** conserver les paramètres simples réellement indépendants et versionnés ; remplacer les groupes indissociables par quelques **objets métier configurables**, activables et datés (repas supplémentaire, indemnité repas/déplacement, règle nuit). Ne pas créer une table universelle `key/value/json`.

## 2. Inventaire des paramètres codés

« Historisé » signifie ici que la table porte des dates d'effet ; `partiel` indique que des chemins runtime lisent la valeur du jour, ignorent les dates ou permettent une mutation rétroactive. `A` = activable selon le seed `est_desactivable`. `Source` cite le lecteur métier principal. Les montants sont en ariary (Ar).

| Code / valeur seed | Domaine et portée actuelle | Type / unité | A ? | Historisé ? | Source runtime et effet si désactivé | Fallback observé | Recommandation |
|---|---|---|---|---|---|---|---|
| `SEUIL_NUIT_DEBUT` = `22:00` | POINTAGE, GLOBAL avec contenu légal | TIME / heure | Non | Partiel | Batch `PointageService` lit l'état ; moteur temporel et correction DAF restent codés à 22:00 | `22:00` même si inactif | **RESTRUCTURE / LEGAL** : une plage nuit datée, lue par tous les calculs |
| `SEUIL_NUIT_FIN` = `05:00` | POINTAGE, GLOBAL/légal | TIME / heure | Non | Partiel | Même divergence ; fin de nuit du moteur temporel figée | `05:00` même si inactif | **MERGE** avec début nuit, validation début ≠ fin |
| `MONTANT_REPAS_MIDI` = `2000` | POINTAGE, GLOBAL, mais override `site_indemnite_config` | MONEY / Ar par jour | Non | Partiel | Grille, batch, fiche, synthèse ; si désactivé hors UI : plusieurs chemins échouent, un chemin de synthèse retourne 0 | Pas de montant Java dans batch, mais 0 côté synthèse si inactif | **RESTRUCTURE / MOVE_SCOPE** dans une politique d'indemnité avec priorité site explicite |
| `SEUIL_2ND_REPAS_1` = `19:30` | POINTAGE, tranche 1 | TIME / heure | Oui | Partiel | Batch : **interrupteur réel de tranche 1** ; désactivé → seuil `23:59`, montant 0 | `19:30` si manquant | **MERGE** dans `SECOND_MEAL` |
| `MONTANT_2ND_REPAS_1` = `2000` | POINTAGE, tranche 1 | MONEY / Ar | Oui | Partiel | Batch : si seuil actif mais montant désactivé, erreur de configuration ; synthèse de détail le traite comme 0 | Aucun si seuil actif, erreur ; 0 si seuil inactif | **MERGE**, supprimer toggle autonome |
| `SEUIL_2ND_REPAS_2` = `21:00` | POINTAGE, tranche 2 | TIME / heure | Oui | Partiel | Même logique ; désactivé → `23:59` et 0, avec risque de masquer la tranche 1 à partir de 23:59 | `21:00` si manquant | **MERGE** dans `SECOND_MEAL` |
| `MONTANT_2ND_REPAS_2` = `4000` | POINTAGE, tranche 2 | MONEY / Ar | Oui | Partiel | Si seuil actif mais montant désactivé : erreur batch ; lecture synthèse traite 0 | Aucun si seuil actif, erreur ; 0 si seuil inactif | **MERGE**, supprimer toggle autonome |
| `SEUIL_PAUSE_MIDI_MIN` = `60` | POINTAGE, GLOBAL | DURATION / min | Non | En BDD seulement | **Aucun lecteur Java runtime Pointage trouvé** ; seed + métadonnée UI uniquement | Aucun lecteur | **DELETE_CANDIDATE** après validation métier et vérification données réelles ; ne pas prétendre que la règle fonctionne |
| `REDUCTION_DEPLACT_REPAS_MIDI` = `1000` | POINTAGE, GLOBAL, appliqué au site discontinu si repas | MONEY / Ar par unité de trajet | Oui | Partiel | Batch exige la valeur même désactivée → erreur ; correction la charge selon état et peut également échouer ; nulle part une sémantique « off = aucune déduction » cohérente | Initialisation locale `1000` dans correction, ensuite remplacée/validée lorsque site présent | **RESTRUCTURE** comme option de politique transport/repas, `off → 0` explicite |
| `SEUIL_HEURES_SEMAINE_COMPENSATION` = `40.0` | POINTAGE, GLOBAL, nommé compensation | DURATION / h/semaine | Oui | Partiel | Synthèse prend cette valeur puis la remplace par `paie_config_legale.duree_hebdo_legale` si trouvée ; fiche l'affiche ou retombe sur autre source ; désactivé ressemble à absent | `40.0`, puis `DUREE_HEBDO_LEGALE`, puis `40` | **RENAME/DELETE_CANDIDATE** comme seuil de compensation ; distinguer référence légale et objectif hebdo effectif |
| `SEUIL_HEURES_JOUR` = `8` | RH_GENERAL, consommé par Pointage | DURATION / h/jour | Non | Partiel | Synthèse, détails et calculs de semaine ; inactif/absent devient fallback | `dureeHebdoLegale / 5` sur chemin principal, `8` ailleurs | **RENAME / MOVE_SCOPE** vers objectif journalier daté selon régime/site ; une résolution unique |
| `SEUIL_RETARD_ASSIDUITE_MIN` = `45` | PAIE, consommé par fiche Pointage | DURATION / min/mois | Oui | Partiel | Fiche : indicateur d'éligibilité à la prime ; Paie lit aussi à date ; inactif = absent | `45` | **KEEP** sous propriétaire Paie, mais ne pas dupliquer la décision métier dans la fiche |

Les paramètres `DUREE_HEBDO_LEGALE` lus par la fiche ne figurent pas dans le seed de `parametre_global` examiné : cette clé sert de fallback secondaire alors qu'une colonne `paie_config_legale.duree_hebdo_legale` existe. Les paramètres fiscaux HS (`EXONERATION_HS_IRSA_ACTIVE`, `PLAFOND_MENSUEL_HS_EXONEREES`) appartiennent à Paie, pas aux règles de présence ; ils coexistent aussi avec des colonnes de `paie_config_legale`, hors du cœur du présent audit mais à arbitrer pour éviter deux sources légales.

Paramètres adjacents **Paie** dépendant des minutes de retard/absence/HS Pointage (inventoriés pour délimiter la frontière, non à déplacer dans Pointage) :

| Code / valeur seed | Type / unité | Activation / historique | Source runtime / fallback | Recommandation |
|---|---|---|---|---|
| `SEUIL_RETARD_PENALITE_PP_MIN` = `70` | DURATION / min/mois | Désactivable ; daté en BDD | `PayrollLegalContextResolver`, fallback `70` si ligne désactivée/absente | **KEEP PAIE**, désactivation à définir comme suppression de la pénalité, pas retour à 70 |
| `SEUIL_CONGES_PENALITE_PP_JOURS` = `1.0` | DURATION / jour | Désactivable ; daté en BDD | `PayrollLegalContextResolver`, fallback `1.0` | **KEEP PAIE**, valider avec seuil retard et taux comme une seule règle composite |
| `TAUX_PENALITE_PP` = `0.25` | RATE / fraction | Désactivable ; daté en BDD | `PayrollLegalContextResolver`, fallback `0.25` même sur format invalide | **KEEP PAIE**, ne pas masquer une valeur invalide |
| `EXONERATION_HS_IRSA_ACTIVE` = `TRUE` | BOOLEAN | Désactivable ; daté en BDD | `PayrollLegalContextResolver` si clé présente ; sinon valeur de contexte légal | **LEGAL/PAIE**, arbitrer la priorité avec `paie_config_legale` |
| `PLAFOND_MENSUEL_HS_EXONEREES` = `20.00` | DURATION / h/mois | Désactivable ; daté en BDD | `PayrollLegalContextResolver` si actif ; sinon valeur de contexte légal | **LEGAL/PAIE**, même arbitrage |

## 3. Autres sources configurables réellement impliquées

| Source / valeur seed | Portée, type | Dates / activation | Lecture Pointage | Problème et cible |
|---|---|---|---|---|
| `ref_site.horaire_continu`, `heure_debut_matin`, `heure_fin_matin`, `heure_debut_apres_midi`, `heure_fin_apres_midi` | SITE, BOOLEAN + TIME | **Non datés**, `est_actif` site distinct | Grille, batch, correction, fiche | Horaires historiques rejoués avec horaires courants ; **HISTORIZE** en configuration d'horaire site datée si changements possibles |
| `site_indemnite_config` (`DEPLACEMENT` 31 200/53 200 mensuel ; `REPAS` 2 000/0 journalier selon site) | SITE × INDEMNITÉ, MONEY + périodicité | `date_debut`, `date_fin`, `est_actif` en BDD | Grille, batch, synthèse, correction | Plusieurs requêtes filtrent seulement `est_actif = TRUE`, **sans dates** ; futures/anciennes valeurs peuvent être utilisées. **KEEP/HISTORIZE_RUNTIME** : résoudre une seule ligne applicable au jour concerné |
| `ref_tolerance_arrivee_pointage.heure_plafond` = `07:30` pour deux sites | SITE, TIME | Ni dates ni activation ; une ligne/site (`UNIQUE id_site`) | Batch charge ; fiche affiche ; correction joint sans utiliser | **DORMANT** pour le calcul : écrêtage commenté, `emEffectif = em`. Historiser seulement après décision d'activation et de signification |
| `paie_config_legale.duree_hebdo_legale` = défaut SQL `40.00`, `seuil_hs_tranche1` = défaut SQL `8.00` | LEGAL, h/semaine | Versionnée par dates | Synthèse la préfère au seuil global et au 8 h HS | **KEEP** comme référence légale datée ; vérifier l'unicité temporelle et supprimer la concurrence avec paramètre global |
| `ref_taux_majoration` | LEGAL/PAIE, RATE | Référentiel Paie distinct | Valorisation Paie, non moteur de présence | **KEEP** hors Pointage ; le moteur transmet les minutes qualifiées, pas les taux monétaires |
| `ref_categorie_professionnelle.est_eligible_hs` via affectation carrière datée | CATEGORY, BOOLEAN | Affectation datée, **valeur du référentiel non historisée** | Synthèse hebdo | **HISTORICAL_CONFIGURATION_RISK** déjà identifié ; ne pas substituer un code `CADRE` codé en dur |
| `employe_indemnite_derogation` | EMPLOYEE × INDEMNITÉ | `date_debut`, `date_fin`, `beneficie` | Grille/saisie selon date | **KEEP** comme exception individuelle ; vérifier la priorité face à la règle de site dans un resolver unique |

## 4. Activation, valeur et absence : comportement actuel

Le modèle possède `est_actif` et `est_desactivable` **sur chaque version de chaque clé**, sans type de valeur. `loadActiveMapAtDate` exclut les lignes inactives : pour ses consommateurs, **DISABLED = MISSING**. `loadEtatMapAtDate` conserve l'état, mais capture toute exception SQL, journalise et retourne une carte vide ; l'erreur de lecture devient alors un « manque » susceptible de déclencher un fallback. Les getters typés retournent aussi un fallback sur valeur mal formée. Une configuration invalide n'est donc pas distinguée de l'absence.

La date est l'autre source d'ambiguïté. `EN_VIGUEUR` dans l'UI signifie seulement « date incluse dans l'intervalle », indépendamment de `est_actif` ; une ligne peut donc être **en vigueur et désactivée**, ce qui est cohérent *si* les libellés précisent « version applicable, règle désactivée ». Aujourd'hui, la juxtaposition d'un badge « En vigueur » et d'un switch « Coupé » prête à confusion. `FUTURE` et `EXPIRED` sont dérivables des dates ; `ACTIVE`/`DISABLED` décrivent une autre dimension et ne doivent pas être fusionnés en un seul statut ambigu.

Le contrôleur refuse le toggle d'une ligne `est_desactivable = false`, et refuse un `PUT` qui la désactive. Mais ce même `PUT` accepte `estDesactivable` dans le payload, **modifiable par le client** : une ligne protégée peut être reclassée en désactivable. Sa valeur et ses dates restent modifiables directement. « Système protégé » signifie donc essentiellement **toggle bloqué à cet instant**, pas constante immuable, non supprimable, légalement garantie ou historiquement figée. Aucune contrainte BDD ne lie protection et activation.

Le bouton « nouvelle version » clôt la précédente via `cloturerPrecedent`, mais l'édition directe (`PUT`) change une version passée en place. Le schéma a `UNIQUE(code_parametre, date_debut)`, pas de contrainte contre des intervalles qui se chevauchent ni de `CHECK(date_fin >= date_debut)`. Un nouveau début peut aussi tomber avant une version future. `loadActiveMapAtDate` détecte deux versions **actives** qui se chevauchent et échoue ; `loadEtatMapAtDate` écrase simplement par ordre de lecture. Le versionnement doit être validé au niveau BDD et ne pas dépendre de la bonne conduite de l'UI.

### Sémantique cible

`ACTIVE(value, sourceVersion, effectiveDates)` : appliquer une valeur typée et validée. `DISABLED(sourceVersion)` : règle optionnelle explicitement coupée, **aucun fallback**. `MISSING` : aucune version applicable, erreur si obligatoire ; défaut seulement si une valeur officielle, datée et testée existe. `INVALID` : échec de lecture/parse/contrainte, erreur explicite, jamais traité comme MISSING. Les états temporels (`FUTURE`, `EXPIRED`) sont des projections des dates, distinctes de l'activation. Les règles obligatoires n'ont pas de switch ; une absence de version applicable est un défaut de configuration.

## 5. Second repas : quatre clés, une règle

Le batch appelle `AttendanceEngine.calculateMealAmount` seulement si le repas est accordé et si la journée n'est ni congé complet ni férié. Ce calcul choisit la **tranche la plus haute atteinte** (montants alternatifs, non additionnés) puis ajoute le montant au repas midi. La version actuelle ne possède pas de toggle `SECOND_MEAL` : l'activation est déduite des deux **seuils** ; les toggles des montants n'ont aucune sémantique autonome. La synthèse lit et valide les montants mais ne recalcule pas le second repas dans ce bloc.

| État permis actuellement | Effet batch | Effet lecture synthèse | Risque |
|---|---|---|---|
| Seuil 1 actif, montant 1 inactif | Erreur de configuration au chargement | Montant 1 = 0 | Deux interprétations de la même configuration |
| Seuil 2 actif, montant 2 inactif | Erreur de configuration | Montant 2 = 0 | Idem |
| Seuil 2 inactif, seuil 1 actif | Seuil 2 forcé à `23:59`, montant 2 = 0 | Montant 2 = 0 | Après 23:59, la tranche 2 à 0 peut prendre priorité sur la tranche 1 |
| Les deux seuils absents | `isActif(..., true)` les considère actifs ; seuils par défaut `19:30`/`21:00`, montants requis | Montants requis | Une règle absente peut renaître par fallback horaire ; ce n'est pas une désactivation |

Le bon objet est `SecondMealRule(enabled, effectiveFrom, effectiveTo, tiers[])`. Pour deux tranches fixes, deux champs seuil/montant dans l'objet seraient plus simples ; une liste de tranches devient préférable si GALAXAM prévoit réellement une troisième tranche, un site différent ou des révisions fréquentes. **Ne pas généraliser avant ce besoin**. Une règle coupée doit produire 0 à 18:00, 20:00 et 22:00, quel que soit le contenu historique des tranches. Une règle activée mais incomplète doit lever `CONFIGURATION_MISSING`. Le choix « tranche la plus élevée » et le lien avec la case manuelle « repas accordé » doivent être confirmés par le métier avant migration.

Nom recommandé : `REPAS_SUPPLEMENTAIRE` et tranches `aPartirDe`/`montantAr`, plutôt que `XXX_1`, `XXX_2`. Une troisième tranche ne doit pas nécessiter quatre nouveaux switches/branches de code.

## 6. Repas midi, déplacement et règles composites

`MONTANT_REPAS_MIDI` coexiste avec `site_indemnite_config(REPAS)` : la grille utilise le montant de site s'il est positif, mais une ligne de site à **0** retombe sur le montant global lorsqu'on coche le repas. Ce 0 peut signifier « pas de repas automatique » ou « tarif nul » ; le code choisit implicitement la première interprétation. Le batch calcule un repas midi global + éventuel second repas, alors que la grille peut présenter un prix site. C'est une **priorité de sources ambiguë** susceptible de rendre le montant affiché et le montant persisté différents. Une politique `MealAllowanceRule` doit déclarer explicitement : éligibilité, attribution manuelle/automatique, tarif applicable par site/date, et second repas distinct mais lié.

`REDUCTION_DEPLACT_REPAS_MIDI` n'est pas une indemnité autonome : elle dépend de « site discontinu + repas reçu + déplacement ». L'activation devrait porter sur **la règle de déduction**, pas sur un montant seul. Si désactivée, déduction = 0. Actuellement, elle est marquée désactivable en BDD mais le batch exige la clé active, donc la désactivation peut bloquer la saisie. `SEUIL_PAUSE_MIDI_MIN` est exposé comme critère de détection « ne rentre pas », mais aucun usage runtime Pointage n'a été trouvé : garder l'UI de cette règle crée une promesse trompeuse.

La plage nuit (`début`, `fin`) est également composite. Le batch peut lire les deux paramètres, tandis que `TemporalAttendanceEngine` qualifie ses segments avec **22:00–05:00 codés en dur**, et la correction d'anomalie utilise aussi ces valeurs fixes. Modifier les paramètres ne garantit donc pas une valorisation cohérente. La référence légale, la règle de présence et la règle Paie doivent être reliées à une **même version datée de plage nuit** ; la question de son caractère légal ou entreprise exige une validation métier/juridique.

## 7. Tolérance d'arrivée

La table dédiée a une cardinalité exacte de **0 ou 1 ligne par site** (`id_site UNIQUE`), avec une heure plafond obligatoire, sans activation ni dates. Elle n'est ni par catégorie, ni par employé, ni par période. L'API expose lecture globale et upsert admin. Le batch joint la table et stocke la valeur dans `siteHoraires`, mais le bloc censé écrêter l'arrivée anticipée est commenté et `emEffectif = em` : **aucun écrêtage n'est appliqué**. La correction joint la table sans utiliser `heure_plafond`. La fiche affiche `plafondArrivee` et calcule une pseudo-« tolérance retard » comme `max(0, 07:30 − heure_debut_matin)` ; pour un site démarrant à 08:00, cela affiche 0 min. La terminologie mélange **plancher de valorisation d'une arrivée anticipée** et **tolérance de retard**, deux règles opposées.

Le bloc commenté ne fait en outre aucune opération d'écrêtage : sa branche renvoie l'heure d'entrée brute. La présence de la table n'est donc pas une preuve d'une règle active. La valeur `07:30` est seulement un seed et un fallback d'affichage/chargement.

Comparaison de conception :

| Option | Avantage | Limite | Verdict |
|---|---|---|---|
| A. Colonne `ref_site.tolerance_arrivee_minutes` | Minimal si une valeur unique et stable par site | Confond une durée de retard avec l'heure plafond actuelle ; pas d'historique | Seulement si le métier confirme une **tolérance de retard** stable, ce qui n'est pas démontré |
| B. Règle site datée (`site_attendance_rule` ou table spécialisée) | Replay, activation et source explicites | Plus de structure | **Recommandée si la règle d'écrêtage doit réellement s'appliquer** ; nommer `heure_minimale_valorisee` ou équivalent, pas « tolérance retard » |
| C. Paramètre global | Simple | Perd la différence site et contredit la table actuelle | Non recommandé sans preuve que tous les sites ont une politique identique |

**Décision métier requise :** écrête-t-on les arrivées avant 07:30 pour les heures payables, tolère-t-on un retard après l'horaire prévu, ou les deux ? Tant que la réponse manque, ne pas migrer automatiquement la table ni activer le calcul.

## 8. Seuil hebdomadaire, objectifs et éligibilité HS

L'objectif hebdomadaire **effectif** n'est plus une constante 40 h : `AttendanceEngine` reçoit la somme des journées applicables du tronçon, ajustée par calendrier/congés/fériés. Le code de synthèse charge néanmoins `SEUIL_HEURES_SEMAINE_COMPENSATION` avec 40 h de fallback, puis remplace cette valeur par `paie_config_legale.duree_hebdo_legale` si trouvée. Ce paramètre global est donc principalement une **référence de secours/affichage**, pas le seuil effectif de compensation d'une semaine. Sa désactivation ne « désactive » pas la compensation ; elle expose le fallback. Son nom et son toggle sont trompeurs.

Recommandation : garder une **durée hebdomadaire de référence légale** dans `paie_config_legale` si c'est bien la source légale ; calculer `objectifSemaineMinutes = somme(objectifsJournaliersApplicables)` dans Pointage ; supprimer ou déprécier `SEUIL_HEURES_SEMAINE_COMPENSATION` après vérification des consommateurs/exports. `SEUIL_HEURES_JOUR` devrait être renommé `DUREE_JOURNALIERE_REFERENCE` et résolu par site/régime/date si les emplois diffèrent. Le calcul `dureeHebdoLegale / 5` et le 8 h explicite ne sont pas des sources historiques fiables. L'éligibilité HS continue d'être une capacité datée par affectation carrière, sans logique `CADRE` dans le moteur ; le booléen du référentiel catégorie lui-même doit être historisé pour le replay.

## 9. Fallbacks : inventaire et doctrine

Les valeurs suivantes sont **des repliements du code observé**, non des valeurs métier officiellement validées. Les exemples en commentaires DTO ne sont pas comptés comme comportement.

| Valeur | Classe/méthode ou chemin | Pourquoi elle existe / source équivalente | Peut disparaître ? |
|---|---|---|---|
| `08:00`, `12:00`, `13:00`, `17:00` | `PointageService` grille/batch, `PointageAnomalieService`, `PointageEmployeService`, synthèse autonome | Remplissage quand horaires `ref_site` absents ; colonne site existe | Oui **après** contrainte de complétude par site/date et replay ; aujourd'hui les chemins ne sont pas alignés |
| `14:00`, `18:00` | Batch/correction pour site discontinu | Variante historique quand colonnes site nulles | Même condition ; ne pas remplacer par horaire continu |
| `07:30` | Batch et fiche | Valeur manquante de table tolérance ; règle calculée actuellement inactive | Supprimable du calcul après décision ; ne pas considérer comme valeur officielle |
| `19:30`, `21:00`, `23:59` | `PointageService` second repas | Seuils par défaut ; `23:59` sert de pseudo-neutralisation | **À supprimer** lors de la règle composite ; `DISABLED` ne doit pas utiliser 23:59 |
| `2 000`, `4 000` | Seeds BDD second repas ; aucun fallback monétaire actif dans le batch | Valeurs initiales, non garanties en exploitation | Garder comme données historiques seulement, pas comme constantes Java |
| `2 000` repas midi | Seed global et site 1 ; grille retombe sur global si tarif site 0 | Double source ambiguë | Supprimer le fallback implicite seulement après priorité site/globale décidée |
| `1 000` | Initialisation locale de correction d'anomalie | Seed réduction transport équivalent ; valeur ensuite lue/validée pour site connu | Oui, supprimer après resolver commun ; ne doit jamais ressusciter une règle désactivée |
| `22:00`, `05:00` | `PointageService`, `PointageAnomalieService`, `TemporalAttendanceEngine` | Plage seed dans `parametre_global`, mais moteur temporel la fige | Ne disparaît qu'après source légale datée unique ; sinon risque de divergence monétaire |
| `8 h`, `40 h`, `dureeHebdoLegale / 5` | Synthèse, fiche et objectifs | Paramètre journalier, paramètre hebdo et table légale concurrents | Oui après resolver quotidien/hebdo unifié et configuration obligatoire |
| `8 h` première tranche HS | Synthèse | `paie_config_legale.seuil_hs_tranche1` existe | Oui si version légale obligatoire |
| `45 min` assiduité | Fiche, Paie | Paramètre PAIE seed | Oui si règle obligatoire et datée ; une désactivation ne doit pas signifier 45 min |
| `TRUE` horaire continu | Plusieurs `COALESCE`/catch de site | `ref_site.horaire_continu` existe | Oui si site requis et données complètes |
| `0` indemnité / déplacement | Synthèse, grille, correction | Parfois « non accordé », parfois « configuration absente » | Seulement si `DISABLED` ou non-éligibilité explicite ; pas en cas d'erreur SQL |

Doctrine : **A — obligatoire** (horaires applicables, objectif, taux/limites légaux utilisés pour la paie) : absence ou invalidité = erreur explicite et contextualisée. **B — optionnel** (second repas, déduction transport, éventuel écrêtage) : désactivé = effet nul, même si d'anciennes valeurs restent stockées ; actif mais incomplet = erreur. **C — défaut officiel** : autorisé seulement avec décision métier/documentation, version et point de résolution unique. Aucun `catch` SQL silencieux ne doit être un fallback.

## 10. Portées et source canonique recommandée

| Famille | Propriétaire recommandé | Source canonique | Raison |
|---|---|---|---|
| Horaires, objectif journalier, éventuel écrêtage | SITE (éventuellement régime/contrat si variations prouvées) | Configuration d'horaire datée | Les différences site existent ; le replay en a besoin |
| Éligibilité HS | CATEGORY via affectation datée, valeur de catégorie à historiser | Résolution employé/date | Le moteur ne doit connaître que `overtimeEligible` |
| Durée de référence, tranche HS, taux légaux, plage nuit si légale | LEGAL | `paie_config_legale`/référentiel légal versionné et resolver unique | Cycle légal distinct des accords d'entreprise |
| Repas midi, second repas, déduction transport | COMPANY + SITE si tarifs/attributions diffèrent | Politique d'indemnité datée, avec override site explicite | Impact financier et règles composites |
| Dérogation individuelle d'indemnité | EMPLOYEE | `employe_indemnite_derogation` datée | Exception, pas nouvelle règle globale |
| Prime d'assiduité et pénalité PP | PAYROLL-PERIOD / PAIE, selon règle juridique | Configuration Paie datée | Le pointage fournit retards/absences, Paie décide le montant |
| Congés et absences | CONGÉS / contrat si applicable | Moteur Congés et statuts datés | Éviter une règle « Pointage » concurrente |

Les paramètres `POINTAGE`, `RH_GENERAL` et `PAIE` de la même table n'ont pas la même autorité ni le même cycle de validation. `categorie` est seulement un libellé de regroupement, pas une garantie de périmètre ou de type. Un `AttendanceConfigResolver` **typé et limité** serait utile : `resolveSiteSchedule(site, date)`, `resolveDailyTarget(employee, date)`, `resolveSecondMealRule(site, date)`, `resolveMealAllowance(site, date)`, `resolveArrivalRule(site, date)`, `resolveNightRule(date)`, `resolveWeeklyReference(date)`. Chaque résultat expose état, valeur, portée et version source. Il ne faut pas en faire une abstraction générique opaque qui cache l'origine métier.

## 11. Modèle BDD actuel et cible mesurée

Le modèle actuel stocke `valeur VARCHAR(100)` sans type, unité ni validation par code. Une version peut être modifiée en place ; plusieurs intervalles peuvent se recouvrir ; chaque fragment du second repas a ses propres dates et son propre toggle. Les dates de `site_indemnite_config` ne sont pas systématiquement appliquées par les requêtes Pointage. La table de tolérance est non datée alors que son application changerait les heures historiques. L'UI rassemble ces familles sous « paramètres globaux » et affiche des fragments indépendants.

**Cible minimale, sans table universelle :**

```text
legal_work_time_version             → références légales réellement communes
site_work_schedule_version          → horaires et objectif par site/régime, datés
attendance_allowance_rule           → code REPAS_MIDI / REPAS_SUPPLEMENTAIRE / REDUCTION_TRANSPORT,
                                      portée, enabled, dates, version
attendance_allowance_rule_tier      → seulement pour REPAS_SUPPLEMENTAIRE si tranches évolutives
site_indemnite_config               → conserver et rendre sa résolution effective-dated
site_arrival_rule_version           → uniquement si le métier active réellement l'écrêtage/tolérance
```

Deux champs de tranche dans `attendance_allowance_rule` seraient plus simples qu'une table `tier` si deux tranches resteront fixes. La table de tranches vaut le coût si la troisième tranche, des priorités ou des périmètres de site sont attendus. Dans les deux cas, l'activation se situe **sur l'objet métier**, pas sur chaque seuil/montant.

Contraintes cibles : montant ≥ 0 ; seuils strictement croissants ; tranches uniques par priorité et règle ; `date_fin >= date_debut` ; absence de recouvrement pour une même règle + portée (contrainte d'exclusion ou validation transactionnelle verrouillée) ; une seule version applicable à une date ; valeurs TIME/DURATION/MONEY typées ; `enabled = true` implique configuration complète ; références de site/catégorie valides ; impossibilité de désactiver une règle déclarée obligatoire. Les versions historiques utilisées dans un gel ne doivent plus être modifiables en place, même si une nouvelle version peut commencer aujourd'hui.

`SYSTEM_REQUIRED` signifie « non désactivable et version applicable obligatoire », **pas « valeur immuable »**. `CONFIGURABLE_REQUIRED` permet la modification via nouvelle version mais pas la désactivation. `OPTIONAL_RULE` autorise le toggle daté. Ces natures devraient être contrôlées côté serveur/BDD, jamais réécrites librement par un `PUT` client.

## 12. UI cible et migration prudente

L'écran actuel offre un toggle, un badge de date, « Système protégé/Toujours actif », une édition directe et « Nouvelle version » **pour chaque clé**. Cela rend possibles les états incohérents du second repas. L'édition directe et le toast promettant que les périodes scellées sont immuables ne garantissent pas que les journées non scellées ou les recalculs historiques retrouveront les mêmes paramètres. Une UI cible présenterait une carte par règle : activation unique, période d'effet, portée, champs typés et aperçu de résultat. Pour le second repas : une case ON/OFF et un tableau `à partir de | montant`, avec erreur inline si montant manquant ou seuils désordonnés. Pour la nuit : une plage et sa source légale. Pour l'horaire de site : calendrier des versions du site. Les versions passées seraient consultables, non éditables ; les actions « modifier aujourd'hui » et « programmer une nouvelle version » seraient explicites.

Migration proposée **sans exécution dans cet audit** : (1) relever les valeurs **réelles** et chevauchements de chaque installation, ainsi que les snapshots déjà gelés ; (2) décider les règles métier ouvertes ci-dessous ; (3) introduire les résolutions typées en parallèle avec tests comparatifs sur périodes représentatives ; (4) migrer les quatre clés du second repas vers un objet versionné, en préservant l'historique des montants réellement appliqués ; (5) basculer l'UI vers les objets puis déprécier les clés anciennes ; (6) retirer les fallbacks seulement après données complètes et replay Paie/Pointage. Aucun `DELETE` automatique de paramètre ou de table n'est conseillé sur la seule base de cet audit.

## 13. Décisions classées

| Décision | Élément | Motif |
|---|---|---|
| **KEEP** | `paie_config_legale` comme référence légale datée ; dérogations employé ; contrats Paie des buckets | Cycle de vie et consommateurs propres |
| **RENAME** | `SEUIL_HEURES_JOUR` → durée journalière de référence ; `SEUIL_HEURES_SEMAINE_COMPENSATION` → référence hebdomadaire **si maintenue** ; `heure_plafond` → heure minimale valorisée **si règle confirmée** | Les noms actuels décrivent mal l'effet |
| **MERGE** | Quatre clés second repas en règle + tranches ; début/fin nuit en plage | Une activation et une validation communes |
| **RESTRUCTURE** | Repas midi/site et déduction transport ; distinction `ACTIVE/DISABLED/MISSING/INVALID` | Éviter le fallback contradictoire |
| **DELETE_CANDIDATE** | `SEUIL_PAUSE_MIDI_MIN` et paramètre hebdo de compensation après vérification métier/données | Aucun usage runtime trouvé pour le premier ; le second n'est pas l'objectif hebdo effectif |
| **HISTORIZE** | Horaires site, politique arrivée si appliquée, valeur d'éligibilité catégorie, résolutions des tarifs de site | Replay et paie historiques |
| **MOVE_SCOPE** | Repas/déplacement global vers politique entreprise + override site explicite ; seuils légaux vers propriétaire légal ; prime assiduité vers Paie | Une source de vérité par règle |

## 14. Questions métier à trancher

1. Le second repas est-il une seule règle ON/OFF, ou chaque tranche peut-elle réellement être désactivée séparément ? Une tranche supérieure remplace-t-elle toujours l'inférieure ? Le repas supplémentaire exige-t-il une case « repas midi accordé » ?
2. Un tarif `REPAS = 0` sur un site veut-il dire « pas d'attribution automatique », « montant nul » ou « utiliser le global » ? Quelle source prime dans la grille, le batch et le gel ?
3. La réduction déplacement/repas peut-elle être désactivée sans bloquer la saisie ? S'applique-t-elle par demi-trajet ou par journée ?
4. La valeur `07:30` est-elle un **plancher d'heures valorisées**, une **tolérance de retard**, ou une simple information abandonnée ? À partir de quelle date doit-elle s'appliquer ?
5. Les horaires et objectifs diffèrent-ils par site seulement, ou aussi par contrat/régime/catégorie ? Quelle valeur était applicable aux périodes historiques ?
6. La plage 22:00–05:00 est-elle légalement fixe pour tous, ou configurable par accord ? Qui approuve une nouvelle version et comment Paie la relit-elle ?
7. `SEUIL_HEURES_SEMAINE_COMPENSATION` est-il encore une vraie règle indépendante ? Si oui, quel cas justifie qu'il diffère de la somme des objectifs journaliers ?
8. Une configuration optionnelle désactivée dans une période historique doit-elle être conservée comme version `DISABLED` pour le replay, même si les valeurs de tranches restent stockées ? Recommandation technique : oui.

## Sources du dépôt examinées

- Schémas : `backend/conception/bdd/01_schema_core_securite.sql`, `02_schema_referentiels_rh.sql`, `05_schema_pointage.sql`.
- Résolution et API : `ParametreGlobal.java`, `ParametreGlobalController.java`, `PointageController.java`, `RefToleranceArriveePointage.java`.
- Exécution : `PointageService.java`, `PointageAnomalieService.java`, `PointageSyntheseService.java`, `PointageEmployeService.java`, `AttendanceEngine.java`, `TemporalAttendanceEngine.java`.
- UI : `frontend/src/features/administration/components/payroll/ParametresGlobauxTab.jsx` ; constantes d'affichage/saisie dans `pointageCalculations.js`.

## Correctif appliqué après l'audit

Le runtime distingue désormais `ACTIVE`, `DISABLED` et `MISSING` avec `ParametreGlobal.resolve`. Une règle d'indemnité désactivée renvoie zéro ; elle ne reprend ni la valeur stockée ni un fallback. Pour les tranches du second repas, la désactivation du seuil **ou** du montant neutralise la tranche entière. Une clé manquante garde uniquement le fallback historique explicitement fourni par le chemin concerné ; un montant requis manquant reste une erreur. La réduction transport désactivée vaut zéro dans la saisie et la régularisation. Une plage de nuit obligatoire désactivée est signalée comme invalide, au lieu de réutiliser silencieusement 22:00/05:00.

La fiche employé marque la règle d'assiduité désactivée comme inactive ; son seuil ne réapparaît pas côté interface. L'état de configuration est résolu à la date de début de la période de fiche. Les erreurs de lecture de `loadEtatMap` sont maintenant remontées au lieu de devenir une carte vide susceptible d'activer un fallback. Vérification réalisée : compilation backend avec tests ignorés et lint frontend réussis ; les tests n'ont pas été exécutés pour ce correctif.
