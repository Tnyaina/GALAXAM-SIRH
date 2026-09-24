# Pointage — décisions métier et plan d’implémentation

## Décisions confirmées

1. `pointage_jour` et ses indemnités journalières enregistrées sont la vérité de la journée. Horaires, dérogations, tarifs et règles de site proposent des valeurs au frontend ; une saisie autorisée peut les remplacer. Le backend recalcule et persiste le résultat, avec auteur et motif lorsque c’est une exception.
2. Une règle désactivée ne s’applique pas. `DISABLED` n’est jamais traité comme `MISSING` ; une règle active incomplète échoue explicitement au lieu de reprendre une constante Java ou React.
3. `MENSUEL_PRORATISE` : tarif de la période divisé par les jours ouvrables applicables ; chaque jour de présence acquis contribue à la somme. Les jours sans présence n’acquièrent pas automatiquement la fraction. Le montant total du tarif peut donc ne pas être atteint.
4. En cas de changement de site au cours d’une période, résoudre le tarif et le calendrier pour chaque segment de dates relevant de l’ancien puis du nouveau site ; calculer chaque segment et additionner. Ne jamais appliquer rétroactivement le tarif du dernier site à toute la période.
5. Tolérance d’arrivée : c’est une *heure minimale comptabilisée*, non une tolérance de retard. Si elle est active à 07:30, une arrivée brute à 06:00 reste auditée comme 06:00 mais le travail est compté depuis 07:30. Si elle est désactivée, l’heure brute est utilisée sans fallback.
6. La régularisation par congé représente un congé déjà accordé mais omis de l’outil. Elle produit un congé `VALIDE` et la journée `CONGE` dans la même transaction ; elle ne doit pas court-circuiter le moteur de droits/solde ni créer une ligne directement dans le ledger.

## Règle confirmée — fixe mensuel et changement de site

Pour `FIXE_MENSUEL`, puisqu’il est indépendant de la présence, répartir le montant par **jours calendaires** de la période couverts par chaque affectation/site, puis sommer. Exemple : 12 jours sur l’ancien site et 19 sur le nouveau dans une période de 31 jours donnent `ancien × 12/31 + nouveau × 19/31`. Les bornes exactes de période paie, et non celles du mois civil, font foi. Cette règle est confirmée par le métier.

## Correctifs sûrs déjà engagés

- La configuration de l’heure minimale comptabilisée possède un état actif/inactif ; l’heure brute et l’heure effective sont distinctes. Le calcul journalier et les buckets figés utilisent l’heure effective, tandis que l’heure brute reste visible et conservée.
- Les écrans de tolérance et la fiche employé n’affichent plus une valeur 07:30/08:00 fictive en l’absence de configuration.
- Le fallback silencieux de 1 000 Ar pour la réduction déplacement/repas dans la correction d’anomalie a été supprimé. Une réduction désactivée vaut zéro ; une réduction active utilise la valeur configurée ; une valeur absente échoue seulement si cette réduction est nécessaire.
- Migration `sp25` forward pour l’état de la règle et l’heure effective ; elle doit être appliquée dans les environnements cibles avant déploiement du backend. Les anciennes journées gardent leur heure brute comme heure effective de relecture jusqu’à correction explicite : aucun recalcul historique silencieux.

## Lot 1 — rendre les indemnités complètement génériques (priorité haute)

1. Construire un unique résolveur daté de catalogue, tarif site, périodicité, fractionnement, état et valeur par défaut. Son résultat distingue `ACTIVE`, `DISABLED`, `MISSING` ; il n’utilise jamais le libellé pour décider d’un calcul.
2. Utiliser ce résolveur dans **tous** les chemins : grille, batch, correction d’anomalie, fiche employé, export Excel/PDF, synthèse et gel. Supprimer les branches `REPAS`, `DEPLACEMENT`, `TRANSPORT` qui font diverger ces chemins.
3. Une valeur de tarif site à zéro signifie une attribution de base nulle. Si l’agent accorde exceptionnellement l’indemnité, le backend propose la valeur par défaut configurée pour son code ; la valeur effectivement enregistrée dans `pointage_indemnite_jour` prévaut. Sans valeur par défaut applicable, retourner une erreur de configuration, jamais `0` inventé ni une constante.
4. `MENSUEL_PRORATISE` : résolution datée du dénominateur ouvrable par segment/site et addition des fractions effectivement acquises. Définir précisément présence entière/demi-journée à partir de la fraction persistée, sans second calcul dans React.
5. `FIXE_MENSUEL` : calculer les segments d’affectation dans la période et ajouter une ligne figée distincte au snapshot d’indemnités de la période. Ne pas fabriquer de faux `pointage_jour` pour cette indemnité non journalière. La Paie ne lit que le snapshot validé, jamais le tarif courant.
6. Tests : nouvelle indemnité au code arbitraire ; site à zéro et octroi manuel ; désactivation ; configuration manquante ; présence partielle ; changement de site ; export/fiche/snapshot/Paie de même montant. Tester l’arrondi à chaque segment et la somme finale.

## Lot 2 — sécuriser la régularisation du congé oublié

1. Ajouter dans `CongeService` une opération explicite `enregistrerCongeDejaApprouveDepuisPointage(...)`, avec date, durée, demi-journée, type, auteur, motif et origine `REGULARISATION_POINTAGE`. Le nom doit indiquer qu’il s’agit d’une décision déjà prise, et non d’une demande ordinaire en attente.
2. Réutiliser les validations Congés existantes : type actif, couverture/chevauchement, droits, solde et mouvement `PRISE` si le type le requiert. Seul le préavis peut être dérogé, avec motif et trace. Faire échouer clairement toute configuration absente, y compris pour une permission ; retirer l’`INSERT ... ON CONFLICT DO NOTHING` qui peut annoncer un succès sans créer de congé.
3. Retourner l’identifiant de la demande validée à Pointage et le lier à la régularisation. Exécuter création/validation du congé, correction de `pointage_jour` et journal d’audit dans la **même transaction JDBC**. En cas d’échec, rien n’est validé. Protéger double clic et concurrence par unicité/idempotence et version de la journée.
4. Vérifier explicitement qu’une période de pointage/paie gelée interdit cette opération, ou la redirige vers une correction post-gel contrôlée. Tester solde insuffisant, congé existant, erreur SQL et validation DAF simultanée.

## Lot 3 — renforcer la tolérance et la source de vérité

1. Ajouter des tests d’intégration de la chaîne `06:00 brut → 07:30 effectif → minutes jour → snapshot buckets → Paie`, puis du mode désactivé `06:00 → 06:00`. Tester aussi seuil supérieur à la sortie et modification concurrente.
2. Historiser le seuil et son activation par site/date avant tout recalcul d’une période passée. L’état actuel par site n’est pas effective-dated : un replay d’ancienne période pourrait utiliser la configuration actuelle. Les snapshots validés doivent rester immuables.
3. Rendre les propositions et capacités de la grille backend-driven : horaires, indemnités, statuts et actions disponibles fournis par l’API. React présente et accompagne la saisie, sans recalcul parallèle des droits et des montants.

## Lot 4 — simplification structurelle après parité

- Conserver comme contrats de Paie les snapshots période/jour/bucket ; supprimer les colonnes et helpers dérivés seulement après inventaire de **tous** leurs lecteurs.
- Unifier l’écriture de journée et indemnités entre batch, anomalie et correction DAF. Un seul service doit assurer version, trace et persistance.
- Regrouper les composants Pointage par parcours utilisateur plutôt que par anciens micro-workflows ; conserver une seule représentation de la fiche dans onglet, impression et export.
- Ne supprimer aucune table sur la seule base du nombre de lignes ou de fichiers. Exiger parité de replay entre ancien et nouveau calcul, puis migration forward et test Paie.

## Critères de fin

- Une indemnité nouvellement créée dans le catalogue fonctionne sans changement de code, de la grille au bulletin et aux exports.
- Aucun état `DISABLED` ne déclenche une valeur fallback ; `MISSING` est visible et explicite.
- Tout congé régularisé est validé par Congés, consomme exactement le droit attendu, et est atomique avec le Pointage.
- Le gel fournit à Paie un snapshot reproductible. Modifier un pointage non gelé change la future Paie après nouveau gel, mais ne modifie jamais rétroactivement une Paie déjà validée/clôturée.
- Les tests backend, frontend, lint, build, migrations et replay monétaire sont verts avant toute suppression structurelle.
