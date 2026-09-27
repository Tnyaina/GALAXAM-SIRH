# AUDIT TECHNIQUE & FONCTIONNEL : GÉNÉRATION ET GESTION DES DOCUMENTS CONTRACTUELS RH
## Projet SIRH GALAXAM — Document de Cadrage et d'Architecture (Septembre 2026)

---

## 1. Existant & Cartographie Applicative

L'audit approfondi du code backend, du schéma de base de données PostgreSQL, de l'IHM React et des modules documentaires actuels établit la cartographie suivante :

### 1.1 Composants Backend Dédiés aux Contrats et Carrières
* **Entités de persistance (`mg.galaxam.sirh.contrat.persistence`)** :
  * `ContratTravail` : Table `contrat_travail`. Gère les champs contractuels, les liaisons vers l'employé, la filiale, le type de contrat (`ref_type_contrat`), le type d'intervenant (`ref_type_intervenant`), le statut RH (`ref_statut_rh`), l'historisation via `id_contrat_precedent`, les dates d'effet et de signature, la durée et fin de période d'essai, et le motif de sortie.
  * `EmployeAffectationCarriere` : Table `employe_affectation_carriere`. Porte les informations d'affectation : site (`ref_site`), département (`ref_departement`), service (`ref_service`), fonction (`ref_fonction`), responsable hiérarchique (`employe`), catégorie professionnelle (`ref_categorie_professionnelle`), mode de paie (`ref_mode_calcul_paie`), salaire de base (`salaire_base`) et dates de validité.
  * `WorkflowRh` & `WorkflowRhCPL` : Table `workflow_rh` et vue `v_workflow_rh_cpl`. Orchestre les demandes de mouvements, associant `id_contrat_source`, `id_contrat_cible`, `id_affectation_source`, `id_affectation_cible`, `date_effet`, `id_statut_rh`, demandeur et valideur DAF/RH.
  * `ContratPeriodeEssai` : Table `contrat_periode_essai`. Suivi unitaire et numéroté des périodes d'essai (`EN_COURS`, `CONFIRMEE`, `RENOUVELEE`, `ROMPUE`).
* **Contrôleurs REST (`mg.galaxam.sirh.contrat.api`)** :
  * `ContratGlobalController` (`/contrats`) : Recherche multi-critères paginée, supervision des contrats actifs, alertes d'échéances (60j, 30j, 7j, fin d'essai), exports CSV.
  * `ContratTravailController` (`/employes/{idEmploye}/contrats`) : Consultation de l'historique complet des contrats d'un collaborateur, consultation du contrat actif, consultation d'un contrat par ID, consultation de l'affectation historiquement liée à un contrat (`/contrats/{idContrat}/affectation`), modification d'un contrat `BROUILLON`.
  * `WorkflowRhController` : Endpoints d'embauche initiale (`POST /employes/{idEmploye}/workflows/embauche-initiale`), de transition de carrière (`POST /employes/{idEmploye}/workflows/transition`), de validation DAF (`PUT /workflows/{idWorkflow}/valider`), d'annulation (`PUT /workflows/{idWorkflow}/annuler`), de modification de brouillon (`PUT /workflows/{idWorkflow}`) et de confirmation d'essai.
* **Services Applicatifs (`mg.galaxam.sirh.contrat.application`)** :
  * `WorkflowRhService` : Orchestrateur transactionnel gérant le cycle de vie complet des contrats, de l'état `BROUILLON` à `PLANIFIE` ou `ACTIF`, l'application immédiate ou planifiée (scheduler `MouvementRhScheduler` / `RattrapageAutomatiqueService`), et la fermeture des anciens contrats/affectations lors des transitions.
  * `EmployeService` : Gestion de l'état civil, des coordonnées, CIN, données familiales et bancaires.
* **Moteurs de Domaine Métier (`mg.galaxam.sirh.contrat.domain`)** :
  * `ContratValidationEngine` : Valide l'exigence de date de fin selon le type de contrat (CDD, STAGE, PRESTATION), la cohérence des dates (`dateDebut <= dateFin`), et la durée de période d'essai (0 à 6 mois).
  * `CarriereTransitionValidator` : Matrice des transitions autorisées selon le contrat d'origine (ex: impossibilité d'avenant pour un STAGE sans passer par `EMBAUCHE_APRES_STAGE`, règles de renouvellement CDD, confirmation d'essai).
  * `AncienneteEngine` : Calcul de l'ancienneté en années, mois, jours.

### 1.2 Composants Frontend Dédiés
* **Page de détail** : `frontend/src/features/employees/pages/EmployeFicheDetailPage.jsx`.
* **Onglet Carrière** : `CarriereTabContent.jsx`, coordonnant `CarriereHeaderCard`, `CarriereTimeline`, et `CarriereModalCoordinator`.
* **Tableaux d'historique** :
  * `ContratActifSection.jsx` : Présentation du contrat actif avec boutons d'actions ("Attestation de travail", "Mouvement / transition RH", "Rupture / fin de contrat").
  * `HistoriqueContratsTable.jsx` : Liste de tous les contrats de l'employé avec boutons contextuels : "Attestation" (contrat en cours), "Consulter" (contrat planifié, en cours, transitionné), "Certificat" & "STC" (contrat clos/rompu).
* **Modales & Vues documentaires existantes** :
  * `ContratTravailModal.jsx` : Modale affichant actuellement un aperçu HTML du contrat avec en-tête institutionnel, articles 1 à 4, et bouton `Imprimer / PDF` (via `window.print()`).
  * `AttestationTravailModal.jsx` : Modale d'aperçu d'attestation de travail avec options (ancienneté contrat/globale, avec/sans salaire), génération d'aperçu backend (`/documents/attestation/{idEmploye}`), téléchargement DOCX officiel (`/documents/attestation/{idEmploye}/docx`) et impression.
  * `CertificatTravailModal.jsx` : Modale analogue pour le certificat de travail (`/documents/certificat/{idContrat}` et `/docx`).
  * `ContractDocument.jsx` : Briques d'UI réutilisables (`DocumentFrame`, `DocumentHeader`, `ArticleBlock`, `Clause`, `DocumentFooter`).

---

## 2. Workflow Actuel (Vérifié dans le Code)

Le code source révèle précisément la séquence suivante :

```
[1. Création Employé] 
   POST /employes (EmployeController -> EmployeService)
   -> Enregistre l'employé avec état civil, CIN, adresse, etc.
   -> Crée conjoint, enfants, urgences, compte bancaire.
   -> L'employé existe SANS contrat actif.

[2. Préparation Embauche / Contrat BROUILLON]
   POST /employes/{id}/workflows/embauche-initiale (WorkflowRhService.creerBrouillonEmbauche)
   -> 2.1 Crée contrat_travail avec statut 'BROUILLON' (id_statut_rh=1).
          Génère le numéro de contrat (ex: CTR-2026-XXXX).
   -> 2.2 Crée employe_affectation_carriere avec statut 'PLANIFIE' (id_statut_rh=2), liée au contrat.
   -> 2.3 Crée workflow_rh avec statut 'BROUILLON' (id_statut_rh=1), pointant sur contrat et affectation.
   -> 2.4 Notifie les gestionnaires RH ("BROUILLON_CREE").

[3. Phase Intermédiaire : Modification / Négociation / Signature]
   -> Le contrat est modifiable via PUT /employes/{id}/contrats/{idContrat} (tant qu'il est BROUILLON).
   -> Les informations (poste, dates, salaire, filiale) sont DÉJÀ complètes en base dès cette étape !

[4. Validation DAF & Activation]
   PUT /workflows/{idWorkflow}/valider (WorkflowRhService.validerWorkflow)
   -> Verrou pessimiste (FOR UPDATE) sur workflow_rh.
   -> Si Embauche Initiale :
      * Clôture tout ancien contrat actif (si réembauche).
      * contrat.activer() -> contrat_travail.id_statut_rh passe à 'ACTIF' (id_statut_rh=4).
      * affectation.activer() -> employe_affectation_carriere.id_statut_rh passe à 'ACTIF'.
      * employe.est_actif passe à TRUE.
      * workflow_rh.valider() -> workflow_rh.id_statut_rh passe à 'ACTIF'.
   -> Si Transition de carrière (Avenant, Renouvellement, etc.) :
      * Si date d'effet présente ou passée : application immédiate (fermeture ancien contrat, activation nouveau contrat et nouvelle affectation).
      * Si date d'effet FUTURE :
        - Le nouveau contrat_travail passe au statut 'A_SIGNER' (id_statut_rh=3).
        - Le workflow passe au statut 'PLANIFIE' (id_statut_rh=2).
        - Le scheduler nocturne MouvementRhScheduler basculera le contrat en 'ACTIF' le jour de la date d'effet.
```

---

## 3. Données Réellement Disponibles pour Générer un Contrat

### 3.1 Tableau d'Inventaire Exhaustif des Données

| DONNÉE | SOURCE BDD / DTO | DISPONIBLE ? | HISTORISÉE ? | UTILISABLE DANS CONTRAT ? | NOTES TECHNIQUES |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **EMPLOYÉ** | | | | | |
| Matricule | `employe.matricule` | OUI | OUI (stable) | OUI | Format `EMP-XXXX` ou alphanumérique |
| Nom & Prénoms | `employe.nom`, `employe.prenom` | OUI | NON (état courant) | OUI | Concaténation nom complet |
| Civilité / Sexe | `employe.sexe` (`M`/`F`) | OUI | NON | OUI | Mappé en "Monsieur" / "Madame" |
| Date de naissance | `employe.date_naissance` | OUI | NON | OUI | Formatage français (ex: 14 juillet 1995) |
| Lieu de naissance | `employe.lieu_naissance` | OUI | NON | OUI | Obligatoire en BDD |
| Nationalité | `employe.nationalite` | OUI | NON | OUI | Défaut: 'Malgache' |
| Situation familiale | `employe.situation_familiale` | OUI | NON | OUI (si pertinent) | Enum ('Celibataire', 'Marie', etc.) |
| N° CIN | `employe.cin_numero` | OUI | NON | OUI | 12 chiffres obligatoire en BDD |
| Date délivrance CIN | `employe.cin_date_delivrance` | OUI | NON | OUI | Obligatoire en BDD |
| Lieu délivrance CIN | `employe.cin_lieu_delivrance` | OUI | NON | OUI | Obligatoire en BDD |
| N° CNaPS | `employe.num_affiliation_cnaps` | OUI | NON | OUI | Optionnel en BDD |
| Adresse / Domicile | `employe.adresse` | OUI | NON | OUI | Obligatoire en BDD |
| Ville employé | `employe.ville` | OUI | NON | OUI | Optionnel |
| Téléphone | `employe.telephone` | OUI | NON | OUI | Obligatoire en BDD |
| Email | `employe.email` | OUI | NON | OUI | Optionnel |
| **CONTRAT** | | | | | |
| Numéro de contrat | `contrat_travail.numero_contrat` | OUI | OUI (par contrat) | OUI | Ex: `CTR-2026-0042` |
| Type de contrat | `ref_type_contrat.libelle` & `code` | OUI | OUI (via FK) | OUI | CDI, CDD, PRESTATION, STAGE, TEMPORAIRE |
| Régime intervenant | `ref_type_intervenant.libelle` | OUI | OUI (via FK) | OUI | PERMANENT, CONSULTANT, STAGIAIRE, TEMPORAIRE |
| Date de signature | `contrat_travail.date_signature` | OUI | OUI | OUI | Optionnel en brouillon, fallback dateDebut |
| Date de début / Prise d'effet | `contrat_travail.date_debut` | OUI | OUI | OUI | Obligatoire |
| Date de fin / Terme | `contrat_travail.date_fin` | OUI | OUI | OUI | NULL pour CDI, Obligatoire CDD/Stage/Prestation |
| Période d'essai (durée) | `contrat_travail.duree_essai_mois` | OUI | OUI | OUI | En mois (0 à 6) |
| Date de fin d'essai | `contrat_travail.date_fin_essai` | OUI | OUI | OUI | Calculée ou manuelle |
| Périodes d'essai détaillées | `contrat_periode_essai` | OUI | OUI | OUI | Suivi des renouvellements |
| Statut du contrat | `ref_statut_rh.code` | OUI | OUI | OUI | BROUILLON, A_SIGNER, ACTIF, etc. |
| Comptabilisé effectif | `contrat_travail.comptabilise_effectif` | OUI | OUI | OUI | Booléen |
| Description / Missions | `contrat_travail.description` | OUI | OUI | OUI | Texte libre |
| **AFFECTATION LIÉE AU CONTRAT** | | | | | |
| Fonction / Poste | `ref_fonction.libelle` | OUI | OUI (rattaché id_contrat) | OUI | Via FK `id_fonction` |
| Site d'exercice | `ref_site.libelle` | OUI | OUI | OUI | Ex: "TNR - Antananarivo Siège" |
| Département | `ref_departement.libelle` | OUI | OUI | OUI | Ex: "Logistics" |
| Service | `ref_service.libelle` | OUI | OUI | OUI | Ex: "Administration & RH" |
| Catégorie professionnelle | `ref_categorie_professionnelle.libelle` | OUI | OUI | OUI | Ex: "HC", "Cadre", "OS1" |
| Supérieur hiérarchique | `employe.nom` / `prenom` via N+1 | OUI | OUI | OUI | Via FK `id_responsable_hierarchique` |
| Salaire de base | `employe_affectation_carriere.salaire_base` | OUI | OUI | OUI | Numérique obligatoire (>0) |
| Mode de calcul paie | `ref_mode_calcul_paie.libelle` | OUI | OUI | OUI | Mensuel, Horaire, Journalier |
| Temps de travail | Code applicatif / affectation | PARTIEL | OUI | OUI | Temps plein (40h/semaine) standard à Madagascar |
| **ENTREPRISE / FILIALE** | | | | | |
| Raison sociale filiale | `ref_filiale.raison_sociale` | OUI | NON (référentiel) | OUI | Ex: "Galaxam Logistics", "Vina sy Asa" |
| Code filiale | `ref_filiale.code_filiale` | OUI | NON | OUI | Ex: "GLX", "VA" |
| NIF filiale | `ref_filiale.nif` | OUI | NON | OUI | Ex: "3000019434" |
| STAT filiale | `ref_filiale.stat` | OUI | NON | OUI | Ex: "9876543210" |
| Adresse siège filiale | **ABSENTE en BDD** | **NON** | - | **FALLBACK** | Non présente dans `ref_filiale` (dérivée du site ou paramétrée) |
| RCS filiale | **ABSENTE en BDD** | **NON** | - | **FALLBACK** | Non présente dans `ref_filiale` |
| Téléphone / Email filiale | **ABSENTS en BDD** | **NON** | - | **FALLBACK** | Câblés en constantes dans `DocumentRhService` |
| Nom du Représentant légal | **ABSENT en BDD** | **NON** | - | **FALLBACK** | Résolu en "Direction des Ressources Humaines" |
| Fonction du Représentant | **ABSENTE en BDD** | **NON** | - | **FALLBACK** | Résolu en "Directeur des Ressources Humaines" |

### 3.2 Signalement Explicite des Informations Manquantes en BDD
Pour éviter toute invention arbitraire :
1. **Adresse physique du siège social de la filiale** : La table `ref_filiale` ne possède pas de colonne `adresse`, `ville`, `code_postal`. Dans `DocumentRhService`, la ville est actuellement déduite du libellé du site (`ref_site.libelle`, ex: `TNR-Antananarivo` -> `TNR` ou `Antananarivo`).
2. **RCS de la filiale** : La table `ref_filiale` ne possède que `nif` et `stat`. Le RCS n'y figure pas.
3. **Représentant légal et signataire patronal** : Aucun champ ne désigne le gérant ou DG par filiale dans `ref_filiale`. Actuellement, le système utilise la mention générique `"Direction des Ressources Humaines"` avec la qualité de `"Directeur des Ressources Humaines"`.

---

## 4. Audit du Système Documentaire Existant

### 4.1 Moteur DOCX Utilisé et Fonctionnement
* **Bibliothèque** : **Apache POI** (`org.apache.poi.xwpf.usermodel.*` version 5.x).
* **Templates actuels** :
  * `backend/src/main/resources/templates/documents/Attestation_de_travail_modele.docx`
  * `backend/src/main/resources/templates/documents/Certificat_de_travail_modele.docx`
* **Mécanisme de substitution** :
  1. Chargement du template `.docx` via `ClassPathResource` (avec fallback sur le système de fichiers).
  2. Construction d'une table associative de variables (ex: `map.put("[NOM DE L'ENTREPRISE]", ...)`, `map.put("[N° de référence]", ...)`).
  3. **Remplacement chirurgical de paragraphes entiers** via `remplacerTexteParagrapheComplet(XWPFParagraph p, String nouveauTexte)` : Cette méthode vide les runs du paragraphe et réécrit le texte formaté avec la police `Calibri`. Cela évite la fragmentation classique des runs dans Word OpenXML où `[NIF]` pouvait être scindé en `[` puis `NIF` puis `]`.
  4. **Remplacement par balayage récursif** (`remplacerVariables`) sur tous les paragraphes, tableaux, en-têtes et pieds de page.
  5. **Suppression des footers orphelins** pour éviter les mentions confidentielles résiduelles.
  6. Écriture du flux binaire dans un `ByteArrayOutputStream` puis renvoi HTTP avec `Content-Disposition: attachment; filename="..."` et type MIME `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.

### 4.2 Comparaison : Fichiers Modèles DOCX vs Implémentation
L'analyse binaire et XML des deux fichiers `.docx` stockés dans `resources/templates/documents/` démontre :
* **Dans les modèles `.docx` bruts** :
  * Des balises textuelles entre crochets : `[NOM DE L'ENTREPRISE]`, `[Adresse complète]`, `[Code postal, Ville]`, `[Pays]`, `[Téléphone]`, `[Adresse e-mail]`, `[Site web]`, `[LOGO]`, `[RCS]`, `[NIF]`, `[STAT]`, `[N° de référence]`, `[Ville]`, `[JJ/MM/AAAA]`, `[Nom et prénom du représentant]`, `[Fonction — ex. Directeur des Ressources Humaines]`, `[Nom et prénom du salarié]`, `[Date d'entrée]`, `[Date de sortie]`, etc.
* **Dans `DocumentRhService.java`** :
  * Le code neutralise proactivement les variables manquantes (RCS inexistant en base, mentions vides) pour ne jamais générer un document officiel avec des crochets orphelins ou des phrases tronquées (ex: `, immatriculée sous le numéro au RCS , sous le NIF...`).
  * Pour le certificat, la liste des postes successifs est injectée dynamiquement à partir des lignes de `employe_affectation_carriere`.
* **Écart majeur constaté** :
  * Il n'existe **AUCUN template `.docx` pour les contrats** (`Contrat_CDI_modele.docx`, `Contrat_CDD_modele.docx`, etc.).
  * Actuellement, la seule visualisation de contrat existante dans l'application est **purement frontend** (`ContratTravailModal.jsx`), qui produit un affichage web avec `window.print()`. Le backend n'a aucun endpoint de génération de contrat.

### 4.3 Analyse de `document_rh` et du Stockage
* **Table `document_rh`** :
  * Possède déjà les colonnes : `id_document`, `id_employe`, `id_type_document`, `id_contrat` (FK vers `contrat_travail`), `id_workflow` (FK vers `workflow_rh`), `nom_original`, `nom_stockage`, `chemin_fichier`, `extension`, `taille_octet`, `date_document`, `commentaire`, `uploaded_by`, `created_at`, `updated_at`.
  * La vue `v_document_rh_cpl` projette également `id_contrat` et `id_workflow`.
* **Écart Backend Java** :
  * Les classes Java `DocumentRh.java` et `DocumentRhCPL.java` **n'ont pas encore les champs `idContrat` et `idWorkflow`** déclarés ! Leurs méthodes `inserer`, `modifier` et `mapResultSet` ignorent ces deux colonnes, bien qu'elles soient présentes dans la table SQL.
* **Écart Contrôleur `DocumentRhController`** :
  * `POST /employes/{idEmploye}/documents` n'accepte pas encore `idContrat` en paramètre optionnel.
* **Référentiel `ref_type_document`** :
  * L'enregistrement `(6, 'Contrat de travail', 'Contrat', 'Contrat signé entre employé et entreprise', FALSE)` existe déjà dans le seed de base.

---

## 5. Réponses Précises aux 9 Questions du Workflow RH

1. **À quel moment le contrat existe-t-il en BDD ?**
   * Dès l'étape de préparation : lors de l'appel à `POST /employes/{idEmploye}/workflows/embauche-initiale` (ou `POST /workflows/transition`). Le contrat est immédiatement persisté dans `contrat_travail` avec le statut `BROUILLON` (ou `A_SIGNER` pour une transition future) et possède déjà son numéro officiel unique (`numero_contrat`).
2. **À quel moment possède-t-on suffisamment d'informations pour générer le document ?**
   * **Dès le statut `BROUILLON`** ! Lors de la création du brouillon, la filiale, le type de contrat, les dates de début/fin, la période d'essai, la fonction, le site, le département, la catégorie professionnelle et le salaire de base sont obligatoirement renseignés et liés via `employe_affectation_carriere`. La DAF/RH peut (et doit) donc générer le contrat avant toute signature.
3. **À quel moment le workflow est-il validé ?**
   * Lorsque l'utilisateur ayant l'autorité `EMPLOYE_VALIDATE` ou `ROLE_ADMIN` clique sur "Valider" (`PUT /workflows/{idWorkflow}/valider`).
4. **Un statut `A_SIGNER` existe-t-il réellement et comment est-il utilisé ?**
   * **OUI**. `A_SIGNER` est configuré en BDD dans `ref_statut_rh` (id=3, code='A_SIGNER', ordre=25).
   * Dans le code actuel, il est positionné par `WorkflowRhService.validerTransition` sur le nouveau contrat lorsque la date d'effet est future (`estFutur == true`).
   * Il est également géré dans `ContratGlobalController` pour filtrer les contrats en attente de signature.
5. **Le contrat peut-il être généré avant activation ?**
   * **OUI, absolument**. C'est même la condition sine qua non de son existence : un contrat doit être généré, imprimé et signé par les parties **avant** d'être activé le jour de la prise de fonction.
6. **`document_rh` permet-il déjà d'associer le document signé au contrat ?**
   * **Au niveau BDD : OUI** (colonne `id_contrat INTEGER REFERENCES contrat_travail(id_contrat)`).
   * **Au niveau code Java : PARTIEL** (la colonne existe en SQL et dans la vue, mais manque dans les attributs, getters/setters et requêtes de `DocumentRh.java` et `DocumentRhController.java`).
7. **L'upload du contrat signé existe-t-il déjà dans l'UI ?**
   * Il existe un téléversement générique dans l'onglet "Documents RH" (`UploadDocumentModal.jsx`), mais il ne propose pas d'associer le fichier à un contrat spécifique, et l'onglet "Carrière & Affectation" ne propose aucun bouton direct "Déposer le contrat signé" sur la fiche du contrat.
8. **La validation doit-elle dépendre de la présence du contrat signé ?**
   * **NON**. Aucune règle métier du SIRH GALAXAM ni du Code du Travail malgache n'impose un blocage système strict de la validation si le scan n'a pas encore été versé. Le système doit rester souple : le contrat signé peut être téléversé avant ou après la validation RH.
9. **Que se passe-t-il pour une transition future/planifiée ?**
   * Le contrat cible est créé, passe au statut `A_SIGNER` lors de la validation, le workflow passe à `PLANIFIE`. Le contrat peut être généré à tout moment. La nuit de la date d'effet à minuit, le cron `MouvementRhScheduler` exécute `WorkflowRhService.autoAppliquerMouvementsEchus(c)`, clôture l'ancien contrat à J-1 et passe le nouveau contrat et son affectation en `ACTIF`.

---

## 6. Analyse des 4 Types de Documents Contractuels

Conformément à la règle impérative de **ne rédiger aucune clause juridique arbitraire** et d'attendre les modèles officiels validés par la Direction Juridique / DAF de GALAXAM, l'architecture doit associer chaque type de contrat à un modèle documentaire paramétré :

| Type de Contrat | Code Référentiel | Exige Date Fin | Durée Essai Autorisée | Données Métier Clés Requises | Modèle DOCX Prévu |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **CDI** | `CDI` | **NON** (`date_fin = null`) | Oui (0 à 6 mois) | Prise d'effet, période d'essai, poste, classification, salaire de base, temps de travail (40h) | `Contrat_CDI_modele.docx` |
| **CDD** | `CDD` | **OUI** (`date_fin` requise) | Oui (selon terme) | Date de début, date de fin précise, motif de recours (terme précis), période d'essai éventuelle, poste, salaire | `Contrat_CDD_modele.docx` |
| **Stage** | `STAGE` | **OUI** (`date_fin` requise) | Non (ou conventionnelle) | Établissement de formation/université, tuteur/maître de stage, gratification de stage (indemnité), objectifs de stage | `Contrat_Stage_modele.docx` |
| **Prestation** | `PRESTATION` | **OUI** (`date_fin` requise) | Non | NIF/STAT du prestataire (si indépendant), description détaillée des livrables / prestations, honoraires hors charges sociales | `Contrat_Prestation_modele.docx` |

### Architecture de Découplage Modulaire des Modèles
Le moteur documentaire ne doit pas coder en dur des conditions `if (cdi) ... else if (cdd)`. Il doit s'appuyer sur une résolution de template par code :
`String templateFile = "Contrat_" + typeContratCode.toUpperCase() + "_modele.docx";`
Si le template spécifique n'est pas encore présent sur disque, le moteur fournit un template générique ou un message explicite tout en permettant la génération via le moteur standardisé.

---

## 7. Architecture Cible & Proposition du Changement Minimal

Pour respecter l'architecture existante sans aucune duplication :

### 7.1 Architecture Backend

```
mg.galaxam.sirh.contrat
  ├── api
  │    ├── DocumentOfficielRhController.java
  │    │     ├── GET /documents/attestation/{idEmploye}
  │    │     ├── GET /documents/attestation/{idEmploye}/docx
  │    │     ├── GET /documents/certificat/{idContrat}
  │    │     ├── GET /documents/certificat/{idContrat}/docx
  │    │     ├── GET /documents/contrat/{idContrat}       <-- NOUVEAU (Aperçu JSON)
  │    │     └── GET /documents/contrat/{idContrat}/docx  <-- NOUVEAU (Téléchargement DOCX)
  │    │
  │    ├── DocumentRhController.java
  │    │     └── POST /employes/{idEmploye}/documents     <-- ENRICHI avec paramètre optionnel idContrat
  │    │
  │    └── dto
  │         └── DocumentRhDTO.java
  │               └── ContratTravailDocumentDTO          <-- NOUVEAU DTO structuré pour le contrat
  │
  ├── application
  │    └── DocumentRhService.java
  │         ├── preparerContrat(Integer idContrat, Connection c)
  │         └── genererContratDocx(ContratTravailDocumentDTO dto, OutputStream os)
  │
  └── persistence
       ├── DocumentRh.java     <-- AJOUT mapping idContrat & idWorkflow
       └── DocumentRhCPL.java  <-- AJOUT mapping idContrat & idWorkflow
```

### 7.2 Architecture Frontend
1. **API Client (`frontend/src/api/documentService.js`)** :
   * `getContratData(idContrat)` : Récupère les données préparées par le backend.
   * `downloadContratDocx(idContrat, filename)` : Télécharge le fichier `.docx` généré.
2. **Gestionnaire de documents (`frontend/src/features/employees/api/documentRhService.js`)** :
   * Support du paramètre `idContrat` dans `uploadDocument`.
3. **Fiche Contrat & Modal (`ContratTravailModal.jsx`)** :
   * Intégration du bouton **"Télécharger DOCX"** officiel.
   * Intégration du bouton **"Déposer le contrat signé"** ouvrant l'upload pré-rempli avec `idContrat` et le type de document "Contrat de travail" (ID 6).
   * Préservation de l'aperçu et du bouton `Imprimer / PDF`.
4. **Onglet Documents RH (`DocumentsRhTab.jsx`)** :
   * Affichage du badge du contrat rattaché si `idContrat` est renseigné sur la ligne du document.

---

## 8. Modèle de Données & Mapping des Variables Documentaires

Le mapping unifié suivant fait le lien direct entre les entités Java / DTO et les variables du document :

| Variable Template | Champ Source Java / DTO | Exemple Concret de Valeur |
| :--- | :--- | :--- |
| `{{ENTREPRISE_NOM}}` | `dto.getEntreprise().getNom()` | GALAXAM LOGISTICS |
| `{{ENTREPRISE_ADRESSE}}` | `dto.getEntreprise().getAdresse()` | Enceinte Madcour, Ankorondrano, Antananarivo |
| `{{ENTREPRISE_NIF}}` | `dto.getEntreprise().getNif()` | 3000019434 |
| `{{ENTREPRISE_STAT}}` | `dto.getEntreprise().getStat()` | 9876543210 |
| `{{CONTRAT_NUMERO}}` | `dto.getContrat().getNumeroContrat()` | CTR-2026-0042 |
| `{{CONTRAT_TYPE}}` | `dto.getContrat().getTypeContratLibelle()` | Contrat à Durée Indéterminée |
| `{{CONTRAT_DATE_DEBUT}}`| `dto.getContrat().getDateDebutTexte()` | 1er octobre 2026 |
| `{{CONTRAT_DATE_FIN}}` | `dto.getContrat().getDateFinTexte()` | 31 mars 2027 (ou "Indéterminée") |
| `{{CONTRAT_DATE_SIGNATURE}}`| `dto.getContrat().getDateSignatureTexte()`| 28 septembre 2026 |
| `{{CONTRAT_DUREE_ESSAI}}`| `dto.getContrat().getDureeEssaiTexte()` | 3 mois (jusqu'au 31 décembre 2026) |
| `{{EMPLOYE_NOM_COMPLET}}`| `dto.getEmploye().getNomComplet()` | RAKOTO Jean |
| `{{EMPLOYE_MATRICULE}}` | `dto.getEmploye().getMatricule()` | EMP-2026-0012 |
| `{{EMPLOYE_DATE_NAISSANCE}}`| `dto.getEmploye().getDateNaissanceTexte()`| 14 juillet 1995 |
| `{{EMPLOYE_LIEU_NAISSANCE}}`| `dto.getEmploye().getLieuNaissance()` | Toamasina |
| `{{EMPLOYE_CIN}}` | `dto.getEmploye().getNumeroCin()` | 101 234 567 890 |
| `{{EMPLOYE_CIN_DELIVRANCE}}`| `dto.getEmploye().getCinDelivranceTexte()` | Délivrée le 12/03/2015 à Antananarivo |
| `{{EMPLOYE_ADRESSE}}` | `dto.getEmploye().getAdresse()` | Lot IV B 12 Ter, Itaosy |
| `{{POSTE_FONCTION}}` | `dto.getAffectation().getFonction()` | Agent opérations |
| `{{POSTE_SITE}}` | `dto.getAffectation().getSite()` | TNR - Antananarivo Siège |
| `{{POSTE_DEPARTEMENT}}` | `dto.getAffectation().getDepartement()` | Logistics |
| `{{POSTE_SERVICE}}` | `dto.getAffectation().getService()` | Opérations & Transit |
| `{{POSTE_CATEGORIE}}` | `dto.getAffectation().getCategorie()` | OS1 |
| `{{POSTE_SUPERIEUR}}` | `dto.getAffectation().getSuperieur()` | ANDRIANTSOA Hery |
| `{{REMUNERATION_BASE}}`| `dto.getAffectation().getSalaireBaseTexte()` | 850 000 Ar par mois |
| `{{REMUNERATION_MODE}}`| `dto.getAffectation().getModeCalcul()` | Mensuel |
| `{{SIGNATAIRE_VILLE}}` | `dto.getSignataire().getVille()` | Antananarivo |
| `{{SIGNATAIRE_DATE}}` | `dto.getSignataire().getDateTexte()` | 27 septembre 2026 |
| `{{SIGNATAIRE_NOM}}` | `dto.getSignataire().getNom()` | Direction des Ressources Humaines |
| `{{SIGNATAIRE_FONCTION}}`| `dto.getSignataire().getFonction()` | Directeur des Ressources Humaines |

---

## 9. Modifications Nécessaires vs NON Nécessaires

### 9.1 Modifications Nécessaires
1. **Backend - Modèle de persistance** :
   * Ajouter `idContrat` et `idWorkflow` dans `DocumentRh.java` et `DocumentRhCPL.java` (avec colonnes SQL, getters/setters et persistance).
2. **Backend - Service documentaire (`DocumentRhService.java`)** :
   * Créer `preparerContrat(Integer idContrat, Connection c)` qui rassemble employé, contrat, affectation liée (via `EmployeAffectationCarriere.rechercherParContrat`), filiale et signataire.
   * Créer `genererContratDocx(ContratTravailDocumentDTO dto, OutputStream os)` réutilisant Apache POI et le même algorithme robuste de remplacement que l'attestation et le certificat.
3. **Backend - Contrôleurs REST** :
   * Ajouter dans `DocumentOfficielRhController.java` :
     * `GET /documents/contrat/{idContrat}`
     * `GET /documents/contrat/{idContrat}/docx`
   * Mettre à jour `DocumentRhController.java` (`POST /employes/{idEmploye}/documents`) pour accepter `idContrat` optionnel.
4. **Backend - Templates DOCX modèles** :
   * Mettre à disposition les modèles Word de base dans `backend/src/main/resources/templates/documents/` :
     * `Contrat_CDI_modele.docx`
     * `Contrat_CDD_modele.docx`
     * `Contrat_STAGE_modele.docx`
     * `Contrat_PRESTATION_modele.docx`
5. **Frontend - Services et Composants** :
   * Étendre `documentService.js` avec `getContratData` et `downloadContratDocx`.
   * Étendre `documentRhService.js` pour passer `idContrat` lors de l'upload.
   * Enrichir `ContratTravailModal.jsx` avec les boutons "Télécharger DOCX" et "Déposer le contrat signé".

### 9.2 Modifications STRICTEMENT NON Nécessaires
* **NE PAS créer une nouvelle table de base de données** : la table `document_rh` a déjà toutes les colonnes requises (`id_contrat`, `id_workflow`).
* **NE PAS créer un nouveau moteur de génération documentaire** : Apache POI XWPF est déjà installé, testé et utilisé pour l'attestation et le certificat.
* **NE PAS inventer de statut supplémentaire** : les statuts `BROUILLON`, `A_SIGNER`, `ACTIF` suffisent amplement et existent déjà dans `ref_statut_rh`.
* **NE PAS ajouter un nouvel onglet dans l'IHM** : l'onglet "Carrière & Affectation" et l'onglet "Documents RH" couvrent l'intégralité du besoin métier sans surcharger la navigation.
* **NE PAS bloquer la validation RH si le scan n'est pas encore téléversé** : préserver l'autonomie et la souplesse du workflow RH.

---

## 10. Matrice des Risques

| Risque Identifié | Impact | Probabilité | Mesure d'Atténuation Prévue |
| :--- | :--- | :--- | :--- |
| **Contrat sans affectation rattachée** (anomalie de données) | Échec de la génération | Faible | `preparerContrat` effectue un fallback propre sur l'affectation active ou des valeurs par défaut sans lever de `NullPointerException`. |
| **Tentative de génération avec ID contrat inexistant** | Erreur 404 / 500 | Faible | Validation stricte et levée d'une `SirhBusinessException` (code `RESOURCE_NOT_FOUND`) avec message explicite. |
| **Permissions d'accès aux contrats d'un tiers** | Violation de confidentialité | Moyenne | Contrôle `@PreAuthorize("isAuthenticated()")` et vérification de la cohérence de l'employé rattaché. |
| **Rupture de mise en page Word (run splitting POI)** | Remplacement incomplet | Moyenne | Utilisation de l'approche éprouvée dans `DocumentRhService` : remplacement de paragraphe complet et balayage récursif POI. |
| **Clauses juridiques contestables** | Risque légal / contentieux | Élevé si arbitraire | **Aucune clause juridique rédigée arbitrairement**. Utilisation d'un squelette contractuel standardisé reprenant les mentions factuelles SIRH validées et préparant l'accueil des modèles officiels de la DAF. |

---

## 11. Plan d'Implémentation Étape par Étape

1. **Étape 1 (Persistance & DTOs)** :
   * Enrichir `DocumentRh.java` et `DocumentRhCPL.java` avec `idContrat` et `idWorkflow`.
   * Créer `DocumentRhDTO.ContratTravailDocumentDTO`.
2. **Étape 2 (Service Métier Backend)** :
   * Implémenter `preparerContrat` et `genererContratDocx` dans `DocumentRhService.java`.
   * Ajouter les modèles Word `.docx` dans `backend/src/main/resources/templates/documents/`.
3. **Étape 3 (Contrôleurs REST Backend)** :
   * Exposer `GET /documents/contrat/{idContrat}` et `GET /documents/contrat/{idContrat}/docx` dans `DocumentOfficielRhController.java`.
   * Permettre la transmission de `idContrat` dans `DocumentRhController.java`.
4. **Étape 4 (Frontend)** :
   * Mettre à jour `documentService.js` et `documentRhService.js`.
   * Mettre à jour `ContratTravailModal.jsx` (Téléchargement DOCX, Upload document signé).
5. **Étape 5 (Validation & Non-régression)** :
   * Tests unitaires et d'intégration backend (génération CDI, CDD, Stage, Prestation, cas d'erreurs, permissions).
   * Vérification de non-régression de l'Attestation et du Certificat de travail.
   * Build Maven et build Vite frontend.
