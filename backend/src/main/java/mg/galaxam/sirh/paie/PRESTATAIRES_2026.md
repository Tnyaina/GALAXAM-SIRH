# Consultants et prestataires indépendants — démarrage septembre 2026

## Règle métier

Le libellé « consultant » ne décide pas du régime fiscal. Un consultant lié par
un contrat de travail est traité dans la paie salariale : IRSA, cotisations
applicables et STC. Le type `CONSULTANT_SALARIE` sert à ce cas. Un indépendant
est géré par contrat de prestation, facture et paiement, sans bulletin ni STC.
Le contrat de prestation s'applique à toute mission indépendante de services
(conseil, maintenance, transport, etc.) : il n'est pas réservé aux consultants.
Le type `CONSULTANT` du référentiel RH est conservé pour qualifier les anciens
dossiers indépendants, mais la création d'un nouveau prestataire se fait dans
le module distinct des contrats de travail.

| Situation | Base de calcul ou montant | Circuit de règlement |
| --- | --- | --- |
| `CONSULTANT_SALARIE` sous CDI/CDD | Salaire et éléments variables, IRSA et cotisations selon les référentiels applicables | Bulletin de paie, puis STC à la sortie |
| Indépendant, y compris `CONSULTANT` historique | Montant de la facture reçue ; aucune paie salariale ou retenue ISI générale calculée ici | Facture validée, puis enregistrement de la date et de la référence du paiement réel |

La loi de finances 2026 (loi n° 2025-021) abroge l'article 01.02.02-II du Code
des impôts et supprime les paragraphes de l'article 01.02.07 bis relatifs à la
retenue ISI générale. La règle particulière des producteurs de base subsiste ;
elle n'est pas assimilée à la catégorie « consultant » et n'est pas automatisée
par ce parcours. Texte : https://cnlegis.gov.mg/uploads/L2025-021_VF.pdf

## Déploiement des schémas

Appliquer dans l'ordre les migrations `26-10-08-sp62-isi-2026.sql` et
`26-10-08-sp63-prestataires-independants.sql`. SP62 désactive le paramètre
`TAUX_ISI`, l'indicateur `soumis_isi_5` et la rubrique `RETENUE_ISI_5` ; les
colonnes et les éventuels historiques restent lisibles. SP63 crée les tables
`prestataire_independant`, `prestataire_contrat` et `prestataire_facture`.
Le schéma canonique intègre ces changements pour les installations neuves.

## Parcours dans l'application

1. Qualifier la relation. Le contrat `PRESTATION` et le type `CONSULTANT`
   indépendant sont exclus de la population de paie. Le consultant salarié
   utilise `CONSULTANT_SALARIE` avec un contrat de travail.
2. Ouvrir **Paie & Rémunération → Prestataires indépendants** dans le menu, ou
   utiliser le lien depuis le Centre de paie, la liste des employés ou l'écran
   de nouvelle embauche. Le raccourci de la liste des employés est réservé aux
   utilisateurs ayant accès au parcours de paie des prestataires.
   Dans `/paie/prestataires`, saisir un prestataire avec NIF, puis un contrat
   de prestation avec son PDF, puis une facture rattachée au contrat. La date
   d'émission de la facture peut être postérieure à la fin de la prestation.

Le contrat créé dans ce parcours est une fiche de mission : prestataire,
référence, objet, dates et tarif éventuellement convenu. Après sa création,
joindre le PDF du contrat de prestation signé dans l'onglet **Contrats**.
Chaque facture de la mission référence ce contrat ; plusieurs factures peuvent
donc relever du même contrat. Ce parcours ne rédige pas, ne génère pas et ne
signe pas le document contractuel. L'ancien type RH `PRESTATION` et son modèle
Word restent disponibles pour lire les dossiers historiques, mais ne servent
pas à créer un nouveau contrat de travail ni un contrat indépendant dans ce
module.
3. Joindre le PDF de la facture (10 Mo maximum par document). Une facture sans
   son PDF ou sans le PDF du contrat ne peut pas être validée.
4. Le titulaire de `PAIE_VALIDATE` valide la facture. Le paiement est ensuite
   enregistré avec sa date réelle et sa référence ; ce marquage ne déclenche
   pas un virement bancaire.
   Un brouillon erroné peut être annulé avec un motif, puis recréé. Une facture
   déjà validée ou payée reste conservée et ne peut pas être annulée par ce circuit.
5. Le bouton de téléchargement restitue les PDF conservés sur le
   serveur dans `app.upload.prestataire-dir` (défaut `uploads/prestataires`).
   Les noms fournis au téléversement ne sont pas utilisés comme noms de stockage :
   le serveur conserve les nouvelles pièces avec le format
   `CONTRAT_PRESTATION_{reference}_{dateDebut}_{suffixeCourt}.pdf` ou
   `FACTURE_PRESTATAIRE_{reference}_{dateFacture}_{suffixeCourt}.pdf`. Les anciens noms UUID
   restent acceptés pour les pièces déjà enregistrées. Au
   téléchargement, il applique le format lisible
   `contrat-prestation-{reference}-{date-debut}.pdf` ou
   `facture-prestataire-{reference}-{date-facture}.pdf` (référence normalisée,
   date au format ISO).

L'écran sélectionne automatiquement le prestataire après sa création, puis le
contrat après son enregistrement. Il indique les PDF manquants avant validation
et rappelle que « Marquer payée » consigne un paiement réel déjà effectué.
Le menu est ajouté par `26-10-11-sp64-prestataires-menu.sql` sur les bases
existantes, après SP63. Il figure dans le schéma canonique pour les nouvelles
installations.

Les dossiers sans NIF doivent être examinés hors de ce circuit. La loi de
finances 2026 rend non déductibles certains achats auprès de fournisseurs non
immatriculés ; elle ne transforme pas automatiquement ces achats en paie ni
en retenue ISI. La qualification et les éventuels cas de producteurs de base
doivent faire l'objet d'un traitement comptable dédié.

## Interfaces

`GET/POST /api/v1/prestataires`, `GET/POST /api/v1/prestataires/contrats`,
`POST/GET /api/v1/prestataires/contrats/{id}/piece`,
`GET/POST /api/v1/prestataires/factures`,
`POST /api/v1/prestataires/factures/{id}/piece`,
`GET /api/v1/prestataires/factures/{id}/piece`,
`POST /api/v1/prestataires/factures/{id}/valider` et
`POST /api/v1/prestataires/factures/{id}/payer`, ainsi que
`POST /api/v1/prestataires/factures/{id}/annuler` pour un brouillon.

Les trois tables représentent des faits distincts : identité fiscale du
prestataire, engagement contractuel réutilisable, puis factures successives et
état de leur règlement. Aucun besoin de table « taux ISI éligible » ou de table
de paiement distincte tant qu'une facture n'a qu'un règlement enregistré.

L'ancien export EDI ISI et la table `paie_honoraire_prestation` ont été définitivement
supprimés du schéma de référence et du backend. Le taux ISI n'est plus un prérequis de paie.
