
**Titre : Conception du module SIRH (Gestion des contrats, affectations, événements et workflows RH)**

**Contexte :**

Nous concevons un système SIRH d'entreprise professionnel pour la gestion des ressources humaines. Le système doit gérer l'ensemble des parcours collaborateurs (Permanents CDI/CDD, Stagiaires, Temporaires à paie horaire/journalière, et Consultants retraités) ainsi que les transitions métier :  *Stage **$\rightarrow$** Embauche (CDD/CDI)* ,  *Renouvellement CDD* ,  *CDD **$\rightarrow$** CDI* ,  *Mutation* ,  *Promotion* ,  *Période d'essai (évaluation, confirmation, renouvellement, rupture)* , et  *Départ retraite **$\rightarrow$** Reprise consultant* .

**Architecture & Principes de Conception :**

1. **Employé (`employe`)** : Représente l'individu physique (fiche unique conservée tout au long de sa carrière, même lors de passages stage **$\rightarrow$** CDI ou retraite **$\rightarrow$** consultant).
2. **Contrat (`contrat_travail`)** : Représente la relation juridique/contractuelle. Il gère le chaînage via `id_contrat_precedent` et la traçabilité des états (`ref_statut_contrat`).
3. **Période d'essai (`contrat_periode_essai`)** : Table dédiée pour isoler les cycles d'évaluation (initiale, renouvellement, confirmation, rupture) avec dates et commentaires.
4. **Affectation (`employe_affectation_carriere`)** : Représente la situation organisationnelle et financière (site, département, service, fonction, responsable, catégorie, mode de paie, salaire de base). L'historique est préservé par fermeture de date (`date_fin`) et gestion des statuts (`PLANIFIEE`, `ACTIVE`, `HISTORISEE`).
5. **Workflow RH (`workflow_rh`)** : Moteur d'événements RH qui orchestre les transitions (lie le contrat source/cible et l'affectation source/cible) lors d'une décision validée par la DAF.
6. **Documents (`document_rh`)** : Centralisation des fichiers attachés à l'employé, avec traçabilité optionnelle vers le contrat ou le workflow concerné.
7. **Traçabilité & Descriptions** : Toutes les tables principales possèdent des champs `description` et `commentaire` pour permettre à la DAF de saisir les motifs administratifs et contextuels de chaque acte RH.
