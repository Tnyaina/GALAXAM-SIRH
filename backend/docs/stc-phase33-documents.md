# STC — Phase 3.3, documents de sortie

## Sources et comportement

- Le décompte et le reçu en projet ne s'éditent que pour un STC `VALIDE` ou `REGLE`. Les postes et le net proviennent de `paie_stc`, l'identité salariée du `bulletin_paie` lié et l'employeur de `bulletin_paie_employeur_snapshot`. Si ces identités historiques manquent, la génération financière est refusée. Aucun montant n'est recalculé avec les règles ou salaires courants.
- Le décompte indique expressément qu'il n'est pas une preuve de paiement. Le reçu est titré **PROJET** : il montre les versements actifs et le solde à sa date d'édition, sans constater la remise, la signature ni une décharge. Un nouveau téléchargement peut montrer une situation de paiement ultérieure ; il ne constitue donc pas une copie figée d'un reçu signé.
- Le justificatif de règlement est attaché à une écriture identifiée du journal. Le cumul et le reste affichés sont ceux de l'instant d'enregistrement de cette écriture, selon l'ordre `created_at, id_reglement`. Une annulation ultérieure est signalée visiblement. La référence à un règlement d'un autre STC est rejetée.
- Le certificat PDF et DOCX du dossier n'exige pas que le STC soit payé. Il exige un contrat `ROMPU` ou `CLOTURE` avec une date de fin enregistrée ; il affiche les emplois et catégories de ses affectations. La route DOCX RH historique demeure et son rendu retire les mentions de dette, appréciations et détails personnels non nécessaires.
- Le bordereau reprend uniquement les booléens, dates, modes et la checklist actuellement enregistrés dans le dossier. Télécharger le PDF ne change aucune de ces données.

## Routes

Le frontend utilise le chemin canonique `/api/paie/dossier-sortie` : `/api` est le `server.servlet.context-path` et `/paie/dossier-sortie` est le mapping du contrôleur. Les deux autres mappings actuellement présents produisent `/api/v1/paie/dossier-sortie` et `/api/api/v1/paie/dossier-sortie` sur cette configuration. Aucun consommateur frontend n'a été trouvé pour ces alias ; leur retrait demande néanmoins un inventaire des clients externes. Tous les téléchargements nécessitent `PAIE_READ` ou `ROLE_ADMIN` et utilisent `Cache-Control: no-store`.

| Suffixe GET | Document |
| --- | --- |
| `/{id}/documents/decompte/pdf` | Décompte STC scellé |
| `/{id}/documents/recu-stc/pdf` | Reçu en projet, situation à l'édition |
| `/{id}/documents/quittance/{idReglement}/pdf` | Justificatif d'une écriture |
| `/{id}/documents/certificat/pdf` | Certificat PDF |
| `/{id}/documents/certificat/docx` | Certificat DOCX |
| `/{id}/documents/bordereau-remise/pdf` | Bordereau d'état enregistré |

## Vérification juridique et limites

L'[article 50 de la loi malgache 2024-014](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) énumère les dates d'entrée et de départ, la nature des emplois successifs, leurs périodes et les catégories correspondantes pour le certificat. L'article 58 du même texte concerne les travailleurs déplacés : la référence au reçu et au délai de deux mois figurant dans le rapport d'agent précédent est erronée. Aucun délai de dénonciation, mention manuscrite obligatoire ou formule de décharge n'est imprimé tant qu'une source primaire applicable n'est pas établie et validée par RH/juridique.

La décomposition financière est celle des colonnes scellées de `paie_stc`. Si un ancien STC ne dispose pas du bulletin et du snapshot employeur, un document historiquement fidèle ne peut pas être émis sans reprise documentaire contrôlée. L'identité du certificat édité avant scellement provient du contrat et du référentiel employeur courants ; les affectations historiques ne sont pas elles-mêmes des snapshots immuables. Pour garantir une réimpression binaire ou juridique identique d'un certificat ou d'un reçu signé, il faudrait archiver l'artefact remis et la preuve de remise, après décision métier. Aucune nouvelle table ni migration n'a été introduite dans cette phase.

## Vérification technique

Tests sur PostgreSQL 15 isolé : la suite backend complète compte 538 tests, 533 réussis, 0 échec, 0 erreur et 5 ignorés. La fixture documentaire crée désormais ses employés, contrats, affectations et période, sans supposer des identifiants fixes. Les fixtures qui modifiaient les bornes des périodes ont été isolées ou adaptées à SP80. Le frontend consomme le chemin canonique et distingue le contrat, le règlement et la clôture administrative. Les tests ne certifient pas la validité juridique du reçu en projet.
