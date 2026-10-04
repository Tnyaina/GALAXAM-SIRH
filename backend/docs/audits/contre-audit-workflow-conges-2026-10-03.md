# Contre-audit : renouvellement annuel Congés / TCONGE et clôture de paie

Date : 03/10/2026. Portée : lecture du chemin de clôture Paie → acquisition → référence → journal → lots et du workflow de correction. Tests BDD limités à `sirh_galaxam_test` ; aucune donnée de production examinée ou modifiée.

## Réponse opérationnelle

**Oui, sur le chemin nominal.** La commande `CLOSE` vérifie que la campagne est terminée et la paie validée. Son service possède une transaction JDBC ; les bulletins sont clôturés, puis `mettreAJourJournalConge` traite chaque bulletin salarié éligible. Pour la campagne `26/12/2025 → 25/01/2026`, la date métier de l'acquisition est le **25/01/2026**. Si la référence 2026 manque, `ensureLeaveReferencePeriod` crée `01/01/2026 → 31/12/2026`, une seule fois, après verrouillage de l'employé. L'acquisition de janvier est rattachée à cette référence, avec `numero_mois_reference = 1`. Si une étape échoue, la transaction de clôture est annulée.

La référence nouvellement créée est **une enveloppe vide**, pas une copie du solde ou du taux de 2025. Le droit du mois vaut normalement 2,5 jours, modulé par le service sous contrat, les absences et la règle légale active à la clôture. Le journal de paie fixe la valorisation mensuelle à partir du bulletin (`baseTotale × droitMois / DIVISEUR_JOURS_CALENDAIRE_CONGE`) et un snapshot de valeur sur le mouvement d'acquisition. Les anciens lots restent historisés et consommés en FIFO. Les cumuls de la nouvelle année partent de zéro, sauf cutover explicitement renseigné.

Le **taux TCONGE applicable** est un objet distinct de la référence. Un taux encore valide continue en janvier. Un nouveau taux calculé au terme de 2025 n'est pas activé silencieusement : l'activation datée est une action explicite, avec bornage de l'ancien taux. En l'absence de taux applicable, les calculs qui en ont besoin peuvent bloquer ; la création de la référence 2026 ne résout pas ce manque.

## Matrice de transition vérifiée

| Transition | État constaté | Résultat |
|---|---|---|
| 26/11→25/12/2025 | Acquisition datée 25/12, mois 12 de 2025 ; référence 2025 clôturée et taux moyen figé si cumuls connus | Conforme sur chemin nominal |
| 26/12/2025→25/01/2026 | Référence civile 2026 créée/résolue ; acquisition et journal janvier, mois 1 | Conforme sur chemin nominal |
| 26/01→25/02/2026 | Même référence 2026, sans deuxième création | Conforme |
| Première acquisition d'un nouveau salarié | Nouvelle référence classée `NEW_HIRE` si le premier contrat commence dans l’année de référence et qu’aucun mouvement, taux ou référence antérieur n’existe | Corrigé lors de cette contre-audit |
| Reprise/historique existant | Référence classée `FULL_HISTORY` si ancien contrat ou historique de droits/taux ; pas de création de taux fictif | Conforme |
| Référence ancienne non civile chevauchante | Erreur explicite, historique intact | Conforme ; régularisation RH requise |
| Taux expiré ou doublé | Erreur à sa date métier, pas de repli sur 2025 | Conforme |
| Deux créations concurrentes | Verrou employé et exclusion SQL de plages par employé | Conforme sur le modèle de transaction actuel |
| Décembre sans droits ou cutover `MINIMAL_OPENING` incomplet | Référence non clôturée normalement / arbitrage requis | Comportement à surveiller |

## Écarts du workflow à traiter

1. **Clôture manuelle prématurée — priorité haute.** L'API `POST /conges/periodes-reference/{id}/cloturer` calcule les cumuls à partir des journaux disponibles mais ne vérifie pas qu'un journal de décembre (ou la fin réelle du cycle exceptionnel) existe. Si RH clôture après quelques mois, les cumuls et le taux sont figés trop tôt ; la clôture ultérieure de la paie de décembre rencontre une référence déjà `CLOTUREE`. Recommandation : réserver cette action à la régularisation, exiger un contrôle de complétude de l'année et un motif audité avant toute clôture manuelle. L'écran seul ne suffit pas comme garde-fou.
2. **Réouverture de la paie de décembre — priorité haute.** Le workflow paie autorise `CLOTUREE → EN_COURS`, mais ne rouvre pas sa référence annuelle. Si cette référence a été clôturée lors de la première clôture de décembre, une nouvelle clôture appelle `ensureLeaveReferencePeriod` et échoue sur `TCONGE_REFERENCE_CLOSED`. Recommandation : concevoir une procédure de rectification auditable des droits/valorisations et du taux figé, sans réécrire les lots déjà consommés ni recalculer silencieusement les bulletins historiques. Aucun dégel automatique n'a été ajouté faute de règle métier de correction.
3. **Ordre des clôtures — priorité moyenne.** Le chemin `CLOSE` ne vérifie pas explicitement que la campagne précédente est clôturée. Janvier peut donc créer 2026 alors que 2025 est encore `OUVERTE` si décembre n'a pas été clôturé. Recommandation : prérequis chronologique ou alerte bloquante ciblée, avec exception explicite pour une reprise historique contrôlée.
4. **Préparation avant la clôture — priorité moyenne.** Les incohérences de référence/paramètre sont détectées dans la transaction finale, donc sans écriture partielle, mais tard pour l'utilisateur. Recommandation : contrôle préalable en lecture seule lors de la validation/simulation de paie ; la clôture reste l'unique propriétaire de la création et de l'acquisition.
5. **Traçabilité de la création automatique — priorité basse.** La référence porte `created_at`, ses bornes et le mode, et l'application écrit un log. `created_by` est NULL pour la création automatique. Pour une traçabilité durable côté données, un champ `source_creation=AUTO_CLOTURE_PAIE` ou un événement métier lié à la campagne serait préférable à une identité utilisateur fictive.

## Workflow recommandé

1. RH configure les versions datées nécessaires et régularise les références anciennes qui chevaucheraient l'année civile. Le TCONGE historique reste daté indépendamment.
2. La paie est calculée et validée. Un contrôle préalable en lecture seule expose les paramètres manquants, les taux nécessaires et la référence attendue ; il ne crée pas encore de droit.
3. `CLOSE` vérifie la fin de campagne, la validation des bulletins et, selon la règle à confirmer, la clôture chronologique. La transaction crée la référence annuelle absente, inscrit une seule acquisition par salarié et campagne, fige sa valeur, puis clôture la référence 2025 à l'acquisition de décembre.
4. La nouvelle année commence avec un cumul annuel neuf ; les lots antérieurs restent FIFO. Le taux applicable continue selon sa propre validité. L'activation d'un taux recalculé est une décision datée et auditée distincte.
5. Les corrections après clôture passent par un workflow RH/DAF dédié, qui préserve les lots et explique la régularisation. La réouverture simple de la paie de décembre ne doit pas tenter une seconde clôture de la même référence sans cette procédure.

## Vérifications et limites

Le nouveau test différencie la première référence `NEW_HIRE` d'une référence créée avec un historique (`FULL_HISTORY`). La suite JDBC de frontières Paie/Congés/TCONGE a exécuté **34 tests, 0 échec, 0 erreur** sur `sirh_galaxam_test` ; elle vérifie `current_database()` avant les fixtures et annule chaque transaction. `git diff --check` passe. Les tests portent sur le service de journal et de référence ; ils ne constituent pas une preuve complète de la commande de workflow `CLOSE` avec tous ses prérequis Pointage et Bulletins.

Le statut de la référence 2025 visible dans une installation donnée dépend de ses campagnes réellement clôturées et de ses données de reprise. La base test ne contient pas les données de production nécessaires pour conclure sur un salarié réel.

## Rectification sur la date d'effet du TCONGE (échange du 03/10/2026)

La recommandation d'activer automatiquement le taux moyen 2025 au 01/01/2026 était trop large pour le modèle de lots actuel. Pour la campagne `26/12/2025 → 25/01/2026`, le **nouveau lot de janvier** reçoit ses jours et sa valeur à la clôture du **25/01/2026**, calculée depuis le bulletin. Les **lots antérieurs** gardent leurs valeurs d'origine et sont pris en FIFO. Le taux moyen de la référence 2025 est un résultat de clôture historisé ; sa date d'effet ne doit pas être déduite mécaniquement du changement d'année. Une activation globale ne doit pas revaloriser les lots.

Une dépendance incorrecte a été supprimée dans `StcService.preparerStc` : pour un solde positif dont les lots sont disponibles, le STC utilisait déjà leur valeur, mais exigeait auparavant qu'un taux global soit résolu en premier. Il utilise maintenant directement le solde valorisé des lots et en déduit un taux moyen **d'affichage**. Le taux global reste requis pour un solde négatif, qui n'a pas de lots créditeurs à répartir. Test ajouté : départ le 25/01/2026, lot historique de 2,5 jours/25 000 Ar, aucun taux global activé ; ICCP estimée de 25 000 Ar et taux affiché de 10 000 Ar/j.

La conformité du **montant effectivement payé lors de la prise** reste un contrôle distinct : l'article 131 du Code du travail malgache 2024 prévoit une allocation minimale déterminée avec la rémunération des douze mois précédant le congé. Ce contrôle à la date du congé et un éventuel complément doivent être définis avec la DAF avant toute modification du calcul ; il ne justifie pas de réécrire les anciennes valeurs de lots. Source officielle : https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf (article 131).
