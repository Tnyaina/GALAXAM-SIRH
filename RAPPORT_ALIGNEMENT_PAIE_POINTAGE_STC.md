# Rapport d’alignement Paie / Pointage / STC

## Résultat par règle

| Règle / réponse métier | Avant | Conforme ? | Changement et vérification |
|---|---|---:|---|
| HS hebdomadaires ; objectif ajusté au tronçon, notamment période et dates d’emploi | L’objectif de semaine était déjà calculé par jours ouvrables et congés, mais le premier palier HS restait entier. Les jours avant embauche/après fin d’emploi pouvaient entrer dans l’objectif. | Non, corrigé | L’objectif compte désormais l’intersection contrat/affectation avec la période. Le premier palier paramétré est réduit selon l’objectif normal du tronçon ; les congés et fériés chômés réduisent cet objectif, pas les absences. Tests lundi-mardi, 3 jours, férié, congé, absence, entrée mardi et sortie mercredi. |
| Cumul des majorations strictement additif, avec taux datés de la base | Le calculateur savait additionner, mais le contexte de paie laissait la politique non résolue en cas de collision. | Non, corrigé | La paie passe maintenant `CUMUL_ADDITIF`; les taux restent fournis par les barèmes datés en base. Huit cas chiffrés testent notamment HS+dimanche, HS+nuit, HS+férié, nuit+dimanche/férié, dimanche+férié et cumul multiple. À 10 000 Ar, HS30+dimanche donne 17 000 Ar. |
| Déplacement TNR/TMM : prorata et règles déjà établies | Calcul journalier alimenté par la configuration du site, fractions journalières et réduction configurée ; correction d’arrondi pour le forfait complet. | Oui — aucune règle de production modifiée | Test unitaire de la journée complète, demi-journée et plancher à zéro. Aucun nouveau montant ou tarif n’a été ajouté. Les exemples DAF TNR/TMM restent la référence métier. |
| Santé : taux applicable à la fin de période Paie et à la date de départ STC | Le code ne filtrait pas les dates d’effet dans les deux recherches. | Non, corrigé | Paie résout à la date de fin de campagne ; STC à la date de départ. Un test d’intégration vérifie un taux historique et son taux successeur pour les deux chemins. |
| Quotité cessible et autres retenues | Le moteur plafonne les prêts à 1/3 et les avances spéciales à 10 % par constantes Java ; les autres retenues sont imputées jusqu’au net disponible. | Non confirmé — aucune nouvelle règle appliquée | Les ratios ne sont pas justifiés par un barème légal daté et n’ont pas été remplacés par des valeurs inventées. Voir la question DAF/juridique ci-dessous. |

## Questions déjà répondues et périmètre

- `docs/payroll_audit/PAYROLL_OPEN_BUSINESS_QUESTIONS.md` a été relu pour les arbitrages A1, B1, C1, D1, D2, E1, F1, G1, H1, I1, J1, K1, K2, L1 et M1 :
  - **A1 — écart identifié, non modifié** : la réponse DAF indique une proratisation entrée/sortie au taux horaire selon les heures effectuées, mais `PayrollExecutionEngine.calculerSalaireBase` applique encore `salaire mensuel × jours calendaires sous contrat / 30`. Le snapshot Pointage ne fournit pas actuellement un compteur distinct des heures normales contractuelles de la période. Utiliser les heures effectivement travaillées telles quelles risquerait de déduire deux fois les absences, déjà retenues séparément. Il faut donc préciser si la base doit être taux horaire × heures normales théoriques sous contrat (avec absences déduites séparément) ou taux horaire × heures réellement travaillées avant tout changement de paie.
  - **B1, C1, E1, F1, G1, H1, I1, J1, K1, K2 et L1 — conformes, non modifiés** : précision minute jusqu’au calcul financier; HS incluses dans la valorisation congé historique; férié chômé payé sans ligne additionnelle; primes soumises selon leur paramétrage social/fiscal; repas sur présence; déplacement TNR/TMM proratisé selon les règles existantes; plafond CNaPS dérivé du SME daté; Santé sans plafond actif et désormais datée; minimum IRSA et plancher après réduction famille résolus depuis la configuration datée; ISI conditionné par le régime intervenant.
  - **D1 — corrigé** : objectif et premier palier adaptés au tronçon hebdomadaire réellement couvert, y compris dates d’emploi, férié et congé; une absence ne réduit pas l’objectif.
  - **D2 — corrigé** : cumul des majorations additif, avec taux datés de la base.
  - **M1 — décision encore requise** : les catégories de retenues et leurs règles légales n’ont pas de barème daté validé; aucun taux n’a été inventé.
- La cotisation Santé historique est testée aux deux dates métier ; les règles conformes de déplacement et de congé n’ont pas été refondues.
- Aucun calcul Congés n’a été modifié. Aucun schéma, migration ou donnée persistante de test n’a été conservé.
- Aucun shadow run complet, refactor de PayrollService ou refonte STC n’a été engagé.

## Quotité cessible — décision nécessaire

Le Code du travail (loi n° 2024-014) distingue les prélèvements obligatoires, les avances spéciales employeur, les saisies/cessions selon le Code de procédure civile et les acomptes retenus d’office. Il impose un accord écrit signé en deux exemplaires pour les avances spéciales ; il invalide les retenues contractuelles non prévues et encadre les compensations employeur. Le Code de procédure civile consulté présente un barème progressif à l’article 685, en tranches annuelles exprimées en francs. Ces textes ne valident pas une quotité unique de 1/3 pour les prêts ni de 10 % pour les avances spéciales.

Le code distingue actuellement : acompte (`ACOMPTE_15`), avance spéciale (`AVANCE_SPECIALE`), prêt interne (`paie_pret`), retenues `CALL_PERSO`/`RETENUE_DIVERSE`, et retenues de STC. Il ne porte pas de types dédiés à la saisie ou à la cession.

**Question DAF/juridique :** pour chaque prêt interne, avance spéciale, acompte, saisie, cession, `CALL_PERSO`/retenue diverse et retenue STC, quelle base juridique et quelle procédure autorisent le prélèvement ? Pour les prêts et saisies/cessions, faut-il appliquer le barème progressif de l’article 685 ; quelles sont ses tranches actuellement applicables en Ariary, son assiette, ses correctifs/exceptions et son traitement du reliquat ? Pour une avance spéciale, quel plafond par échéance appliquer en présence de l’accord écrit exigé par l’article 100 ?

Sources : [Code du travail malgache, loi n° 2024-014](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf), articles 100 à 103 ; [Code de procédure civile — Ministère de la Justice](https://www.justice.mg/wp-content/uploads/textes/1TEXTES%20NATIONAUX/DROIT%20PRIVE/les%20codes/CODE%20DE%20PROCEDURE%20CIVILE.pdf), article 685.

## Tests et validation

- Tests ciblés : 33 tests unitaires réussis, dont 17 `AttendanceEngineTest` et 8 `OvertimePremiumCollisionTest` ; 4 tests de chaîne Pointage → bulletin réussis ; 1 test d’intégration des taux Santé Paie/STC réussi.
- Les tests d’intégration vérifient `SELECT current_database()` et exigent `sirh_galaxam_test` avant toute insertion ; leurs transactions sont annulées en fin de test. Vérification externe effectuée : `current_database() = sirh_galaxam_test`.
- `mvn -q -DskipTests package` : réussi.
- `git diff --check` : réussi.
- Aucune migration exécutée.

## État final

- Semaines partielles et dates d’emploi : corrigé et testé.
- Cumul additif des majorations : corrigé et testé.
- Déplacement TNR/TMM : conforme, production inchangée.
- Taux Santé datés Paie/STC : corrigé et testé.
- Quotité cessible, retenues diverses et STC : en attente de décision DAF/juridique ; les constantes actuelles ne sont pas considérées comme juridiquement validées.
