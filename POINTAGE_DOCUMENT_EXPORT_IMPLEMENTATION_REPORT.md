# Exports individuels de pointage — implémentation

Date : 28 septembre 2026.

## État initial
Les deux fichiers modèles existaient avec des exemples nominatifs. L’export Excel reconstruisait un autre classeur et recalculait des résultats via des formules. Le PDF reposait sur une mise en page React imprimable. Aucun de ces deux exports ne remplissait directement les modèles fournis.

## Modèles utilisés
- `backend/src/main/resources/templates/mensuel/MODELE_POINTAGE_EXCEL.xlsx` : identité, période, journal, bilan hebdomadaire, bilan final, indemnités consolidées, régularisations DAF, provenance et visas.
- `backend/src/main/resources/templates/pdf/MODELE_FICHE_POINTAGE.pdf` : deux pages A4 paysage remplissables, répétées lorsque les lignes ou colonnes dépassent leur capacité. Les exports conservent les champs et leurs apparences, avec les valeurs calculées en lecture seule.
- Sources de conception conservées sous `backend/tools/templates/` : ReportLab pour le PDF ; artifact-tool pour l’Excel. Ce sont des outils de conception, pas des dépendances du serveur.

Les exemples fictifs ont été supprimés. Les modèles ne contiennent aucune règle de rémunération.

## Données et responsabilités
`PointageDocumentService` prépare les données communes aux deux exports. `PointageExcelExportService` remplit le classeur avec Apache POI. `PointagePdfExportService` remplit et assemble les pages du PDF modèle avec Apache PDFBox 3.0.8.

Le journal expose les quantités stockées dans `pointage_indemnite_jour.quantite`, uniquement pour les indemnités accordées. Une fraction reste une fraction ; elle n’est jamais déduite du montant. Les montants proviennent de la synthèse backend. Les libellés et périodicités disponibles viennent du catalogue. Un forfait sans quantité journalière connue affiche un tiret.

Le motif de correction quotidienne est repris depuis `pointage_jour.motif_ajustement`. Les régularisations de période reprennent durée signée, motif, date, identifiant de l’auteur et statut. Les annulations restent visibles dans l’historique provisoire ; le bilan utilise les décisions du moteur existant.

Les formules Excel se limitent aux sommes du journal pour la présentation. Aucun seuil, droit à indemnité, tarif, prorata, compensation ou solde final n’est calculé dans Excel ou dans le frontend. Une quantité inconnue conserve un tiret, y compris dans son total.

## Périodes validées
Pour VALIDEE, PAIE_VALIDEE et CLOTUREE, le bilan final, les montants et les régularisations validées proviennent du snapshot GELE/CONSOMME. La présence de chaque journée et sa version sont contrôlées contre `pointage_snapshot_jour`. Un snapshot absent ou un journal divergent déclenche une erreur métier HTTP 409.

Limite : le snapshot actuel ne contient pas une copie complète des semaines, des horaires bruts et des quantités journalières. Les semaines sont donc indiquées comme reconstituées. Les horaires et quantités proviennent du journal dont la version est vérifiée ; ils ne sont pas présentés comme des champs intégralement figés. Les données d’identité/affectation ne constituent pas non plus un snapshot documentaire immuable.

## Correction du rapprochement DAF — 28 septembre 2026
La règle confirmée est désormais :
`Solde = HS30 brutes + HS50 brutes − déficit horaire + Divers signé`.
Le solde positif est ventilé une seule fois sur la période : jusqu’au palier lu dans `paie_config_legale.seuil_hs_tranche1` à 30 %, reste à 50 %. Un solde négatif devient une absence horaire. La requalification ne touche ni les badges ni les congés.

`PeriodBalance` centralise ce calcul en minutes exactes. La synthèse, la fiche individuelle, la revue DAF, les modèles et le bulletin consomment ses résultats. Les colonnes HS30/HS50 hebdomadaires demeurent visibles comme faits avant rapprochement.

La migration SP32 ajoute `minutes_hs50_finales` et a été appliquée aux bases locales de développement et de test. Elle doit être appliquée aux autres environnements avant déploiement. Les snapshots historiques restent inchangés : NULL sur cette colonne conserve la règle antérieure. Les vues validées reprennent les résultats figés au lieu de leur substituer le nouveau calcul.

La paie reventile les buckets réellement travaillés entre normal, HS30 et HS50 sans effacer leurs qualifications dimanche/férié/nuit ni changer les heures travaillées. Un complément DAF au-delà des HS pointées est payé séparément avec provenance snapshot et quantités `dafOvertime30Minutes` / `dafOvertime50Minutes`. Il n’invente aucune journée, aucun horaire ou aucune majoration spéciale. Ses taux sont ceux de la date de fin de période. Le bulletin mentionne la part « complément DAF ».

Exemple couvert pour un palier paramétré de 8 h : 16 h de HS − 2 h d’absence − 5 h de Divers = 9 h, soit 8 h à 30 % et 1 h à 50 %. Un Divers positif de 10 h sans HS pointées produit 8 h à 30 % et 2 h à 50 %, sans augmenter les heures réellement travaillées.

## Endpoints et sécurité
- GET `/pointages/employes/{idEmploye}/export/excel`.
- GET `/pointages/employes/{idEmploye}/export/pdf`.

Paramètres existants : `date`, `period`, `idPeriode`. Les exports utilisent toujours une période de paie réelle ; `period` ne force plus un mois civil. Sans période correspondante, une erreur explicite remplace le repli vers une période arbitraire ou le mois civil. Les deux actions exigent POINTAGE_READ ou ROLE_ADMIN. MIME et nom du fichier sont fournis par le backend. Content-Disposition est exposé par CORS pour permettre au frontend de lire ce nom en déploiement avec des origines distinctes.

## Frontend et suppression
Le menu d’export de la fiche télécharge les fichiers backend et utilise leur nom serveur. Les erreurs JSON d’un téléchargement sont rendues lisibles. Le PDF téléchargé peut être ouvert puis imprimé dans le lecteur PDF.

`frontend/src/features/attendance/components/modals/PointageFichePrintModal.jsx` a été supprimé après vérification de toutes ses références. Le helper client de nommage des périodes, devenu sans appelant, a été retiré. Les autres vues et modèles restent utilisés.

## Robustesse et vérification
Les colonnes d’indemnités sont dynamiques. Le PDF retire les cases vides du journal et du récapitulatif. Excel insère les lignes supplémentaires et retire les lignes inutilisées en conservant les styles. Le PDF répète les pages du modèle pour les journées, indemnités et régularisations supplémentaires ; les motifs longs sont répartis sans suppression de texte. Les observations quotidiennes longues sont renvoyées à la partie observations du bilan PDF.

Les variables Excel résiduelles et les champs PDF attendus absents constituent des erreurs. Une lecture SQL des quantités ou du catalogue qui échoue ne produit pas silencieusement un export incomplet.

Tests couverts : quantités fractionnaires, montants consolidés, bilan fourni par le backend, formules de présentation seulement, débordements de lignes/colonnes/pages, motifs longs, PDF valide, champs en lecture seule, absence de placeholders, snapshot absent, version divergente et priorité des valeurs figées. Le client API est testé pour les deux formats, les noms serveur et les erreurs.

## Résultats
- Backend : `mvn test package`, 159 tests, 0 échec, 0 erreur, 0 ignoré.
- Build backend : `mvn test package`, réussi.
- Frontend : suite complète, 33 tests réussis sur sept fichiers.
- Build frontend : `npm run build`, réussi.
- ESLint des fichiers frontend concernés : réussi.
- `git diff --check` : réussi.
- Contrôle visuel : modèle et export Excel rendus ; PDF rempli rendu avec les formulaires activés et contrôlé sur ses deux pages.

Ces contrôles incluent la chaîne JDBC jusqu’au bulletin dans la base locale de test, avec rollback des jeux d’essai. Ils ne constituent pas une validation manuelle sur la base de production. Les changements métier sans rapport avec les exports, déjà présents dans le dépôt, ont été conservés.

## Adaptation des périodes et modèles — 28 septembre 2026
- Bornes et libellé issus de la période sélectionnée, sans imposer le 1er au dernier jour du mois.
- Journal PDF : retrait des champs/lignes inutilisés et recalcul de la largeur des colonnes selon les indemnités réellement disponibles. La colonne d’indemnité vide est supprimée. Pagination au-delà de la capacité d’une page.
- Excel : sections dimensionnées aux journées, semaines, indemnités et ajustements ; observation étendue dans l’espace inutilisé. Période multilignes et provenance agrandie.
- Le frontend affiche la règle de ventilation fournie par le backend ; aucune formule métier ni valeur de palier front.
- Suppression du repli à 8 h lors de la saisie/sauvegarde de la configuration. Un palier absent doit être renseigné, pas remplacé silencieusement.
- Migration SP33 : `minutes_palier_hs30` fige le paramètre effectivement utilisé. NULL historique reste « non historisé ». Appliquée aux bases locales de développement et de test ; à appliquer sur les autres environnements avant déploiement.
- Régressions ajoutées : palier de 6 h, période du 25 septembre au 24 octobre, absence de colonnes/lignes PDF inutilisées et export par période depuis une vue mensuelle.
