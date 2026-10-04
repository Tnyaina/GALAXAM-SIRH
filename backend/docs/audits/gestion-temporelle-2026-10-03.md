# Gestion temporelle Paie–Congés–TCONGE : règle annuelle confirmée

## Problème et correction

RG-CONGE-REFERENCE-01 : la référence ordinaire de N couvre inclusivement **01/01/N → 31/12/N**. `cloturerDonneesPaie` appelle `mettreAJourJournalConge` dans la transaction de clôture, après clôture des bulletins. C'est cette clôture, et non la validation préalable, qui attribue le droit mensuel légal de 2,5 jours avant prorata et absences. Pour la campagne 26/12/2025 → 25/01/2026, la date métier d'acquisition est **25/01/2026** : le journal est janvier 2026, mois 1 de la référence 2026.

La recherche initiale à cette date était correcte, mais une référence se terminant le 31/12/2025 ne pouvait la satisfaire. La création automatique antérieure pouvait partir de l'embauche ou du lendemain de la dernière référence et produire un cycle glissant. La clôture appelle maintenant `ensureLeaveReferencePeriod` : elle verrouille l'employé, exige le paramètre de durée 12 mois au 1er janvier, retourne l'année civile ouverte si elle existe, sinon la crée dans la même transaction. Deux clôtures concurrentes n'en créent qu'une. Une référence historique non civile chevauchant l'année, une référence annuelle clôturée ou une durée incohérente provoque une erreur explicite et nécessite une régularisation RH.

La nouvelle enveloppe ne copie ni solde, ni cumul, ni lot, ni taux. Le taux TCONGE continue seulement si sa propre plage est valide ; un trou ou un chevauchement de taux bloque le calcul. Les droits 2025, leur valorisation figée et les lots historiques restent conservés et consommés selon FIFO. Les cumuls de l'enveloppe 2026 repartent de zéro, hors reprise explicite existante. L'endpoint facultatif `POST /conges/periodes-reference/{id}/reference-suivante` crée la même année civile suivante depuis une référence clôturée au 31 décembre ; il est idempotent.

L'employé 1 n'avait pas de lignes TCONGE dans `sirh_galaxam_test` au début de l'audit : les bornes exactes de l'incident réel ne peuvent pas être affirmées. Le test reproduit une référence 01/01/2025–31/12/2025, un taux continu et une clôture le 25/01/2026 ; il vérifie la création automatique de 2026, les deux journaux distincts et la conservation du droit historique.

## Résolution temporelle et transitions

| Domaine | Date métier | Action automatique | Action humaine |
|---|---|---|---|
| Pointage, nuit, tarif/site journalier | Jour réel | Version applicable au jour | Corriger trou ou chevauchement |
| Paie, fiscalité, cotisations, paramètres légaux | Fin de campagne | Version active à la clôture | Valider/clôturer paie ; configurer nouveaux taux |
| Acquisition congé et référence | Fin de campagne | Droit mensuel et enveloppe annuelle manquante | Régulariser référence conflictuelle |
| TCONGE et prise | Date du droit/lot ou de la prise selon le flux | FIFO et valeur historique du lot | Configurer taux absent |
| STC, santé | Date de départ | Version active au départ | Valider STC |
| Cutover/reprise | Date du snapshot | Aucun transfert annuel implicite | Saisie/validation reprise |
| Contrat, salaire, affectation | Jour concerné ; dernier jour employé pour salaire mensuel | Couverture datée | Avenant RH |
| Déclaration trimestrielle | Fin des campagnes incluses | Agrégation des périodes clôturées | Validation déclaration |

Les références et configurations légales ont des bornes inclusives. TCONGE conserve sa fin technique exclusive `[date_effet,date_fin[` déjà présente dans le schéma. Les versions actives multiples lèvent `AMBIGUOUS_CONFIGURATION`. Les paramètres, références et taux absents signalent la date métier et leurs bornes voisines. Aucune dernière valeur connue n'est reprise implicitement.

## Autres corrections de l'audit

Paramètres globaux : suppression des valeurs expirées/futures choisies en repli. Affectations : succession valide, chevauchement/trou refusés, prorata d'entrée fondé sur la couverture réelle. Pointage autonome : site, tarif et paramètres du jour réel. Congés : acquisition idempotente et gel de valorisation dans les bornes de campagne, cutover comparé à la clôture réelle, consultation historique sans droits futurs. Export social : classement par clôture effective et taux DNTS de la première clôture du trimestre. Le formulaire de reprise propose désormais le 1er janvier de l'année du cutover. L'écran de gestion des cycles affiche l'historique et les actions autorisées ; sa création manuelle et l'API associée imposent 01/01–31/12. La migration SP52 ajoute des contraintes anti-chevauchement sur les configurations légales, les tarifs de site et les références congés, ainsi que le menu de gestion des cycles. Elle a été appliquée uniquement à `sirh_galaxam_test` après vérification de `current_database()` et absence de conflits existants. Les trois exclusions ont été testées avec des insertions ou mises à jour contradictoires dans une transaction annulée.

## Vérification

Les tests JDBC imposent l'URL `sirh_galaxam_test` et exécutent `SELECT current_database()` en **première requête**, avant toute fixture. Chaque transaction de test est annulée. Les cas couvrent les campagnes entièrement en 2025, 26/12/2025–25/01/2026 et 26/01/2026–25/02/2026 ; transition annuelle, idempotence, taux changé/continu/manquant/chevauchant, droit de janvier, prise de décembre, entrant de décembre, salaire et site au 1er janvier, cutover, année bissextile, pointage et cotisations. **71 tests backend ciblés et 15 tests frontend passent**, sans échec. `git diff --check` passe. Aucune donnée de production touchée.

## Règles encore à confirmer

- Régularisation des anciennes références glissantes déjà enregistrées : leur chevauchement bloque volontairement la création annuelle, pour préserver les droits et journaux existants.
- Salaire mensuel et base horaire lors de deux avenants dans une campagne : le moteur garde la règle actuelle du salaire de clôture ; une répartition demanderait une règle métier.
- Seuil de pointage changeant en milieu de semaine : définir la qualification hebdomadaire.
- Journaux TCONGE importés sans campagne datée : fournir leur provenance réelle pour une consultation historique.
- DNTS : le refus existant d'un trimestre aux taux/plafonds changeants entre clôtures attend un arbitrage DAF.
