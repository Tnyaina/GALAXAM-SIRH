# Consultants et prestataires indépendants — démarrage septembre 2026

## Règle métier

Le libellé « consultant » ne décide pas du régime fiscal. Un consultant lié par
un contrat de travail est traité dans la paie salariale : IRSA, cotisations
applicables et STC. Le type `CONSULTANT_SALARIE` sert à ce cas. Un indépendant
est géré par contrat de prestation, facture et paiement, sans bulletin ni STC.

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
2. Dans `/paie/prestataires`, saisir un prestataire avec NIF, puis un contrat
   de prestation avec son PDF, puis une facture rattachée au contrat. La date
   d'émission de la facture peut être postérieure à la fin de la prestation.
3. Joindre le PDF de la facture (10 Mo maximum par document). Une facture sans
   son PDF ou sans le PDF du contrat ne peut pas être validée.
4. Le titulaire de `PAIE_VALIDATE` valide la facture. Le paiement est ensuite
   enregistré avec sa date réelle et sa référence ; ce marquage ne déclenche
   pas un virement bancaire.
   Un brouillon erroné peut être annulé avec un motif, puis recréé. Une facture
   déjà validée ou payée reste conservée et ne peut pas être annulée par ce circuit.
5. Le bouton de téléchargement restitue les PDF conservés sur le
   serveur dans `app.upload.prestataire-dir` (défaut `uploads/prestataires`).

L'écran sélectionne automatiquement le prestataire après sa création, puis le
contrat après son enregistrement. Il indique les PDF manquants avant validation
et rappelle que « Marquer payée » consigne un paiement réel déjà effectué.

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

L'ancien export EDI ISI répond HTTP 410 et n'est plus affiché. Le bordereau
`paie_honoraire_prestation` reste en base pour lecture historique mais n'est
plus généré par une campagne 2026. Le taux ISI n'est plus un prérequis de paie.
