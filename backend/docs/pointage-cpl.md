# Conception Base de Données et Cahier des Charges Technique et Fonctionnel : Module Pointage & Présence — SIRH Galaxam

> **Statut du document** : Complément technique aligné avec `pointage.md` v1.1.0
> **Éléments validés** : Architecture BDD finale, algorithmes de calcul, tables d'indemnités, fonctions SQL (fn_get_jours_ouvrables_periode, fn_calcul_indemnite_repas_jour), RBAC.
> **Éléments corrigés par rapport à la version initiale** :
> - Architecture BDD : **1 table `pointage_jour`** unifiée + 2 tables gelées (`pointage_semaine_calcule`, `pointage_cumul_periode`) — les 3 tables séparées (`pointage_brut`, `pointage_journalier_calcule`, `pointage_cumul_periode`) sont **abandonnées**.
> - Compensation : **hebdomadaire par tronçon proratisé** (`seuil = 8h × jours ouvrés du tronçon`) — la procédure `sp_calcul_compensation_mensuelle` est **remplacée** par la vue `v_pointage_semaine` + gel dans `pointage_semaine_calcule`.
> - Semaine à cheval : **coupure ferme au 25 du mois**, pas de report de période.
> - **Standardisation DTO (Backend-Driven Booleans)** : 0 comparaison de chaînes de statut en dur côté React. `PointageJourDTO` (`isPresent`, `isRetard`, `isAbsent`, `isConge`, `isRepos`, `isEnCours`, `isAnomalie`), `PointageAnomalieDTO` (`isIncomplet`, `isRetard`, `isAbsence`, `isPeriodeCloturee`), `PointageSyntheseMensuelleDTO` (`isPeriodeOuverte`, `isPeriodeCloturee`, `hasAnomalies`).
> **Éléments à conserver de ce document** : ratios indemnités par site (§Ratios), algorithme calcul dérives horaires, fonctions SQL utilitaires, matrice des statuts quotidiens.

---

## Architecture Métier et Principes Directeurs du Module Pointage

L'intégration du module de suivi des pointages et présences au sein du système d'information des ressources humaines (SIRH) Galaxam répond à l'exigence de rationaliser et de sécuriser le flux d'informations entre l'activité opérationnelle sur le terrain et le traitement de la paie mensuelle<sup>1</sup>. L'infrastructure de collecte s'appuie sur des dispositifs d'horodatage physiques et digitaux de type Clocker, enregistrant jusqu'à quatre empreintes horaires quotidiennes par salarié : l'entrée du matin, la sortie du matin, l'entrée de l'après-midi et la sortie de l'après-midi<sup>3</sup>. La conception proposée substitue aux retraitements manuels sur tableur un moteur de règles automatisé, garantissant la traçabilité intégrale des données conformément au Code du Travail malgache (Loi n° 2003-044) et au cadre réglementaire fiscal et social<sup>2</sup>.

Le cycle de suivi du temps de travail s'inscrit dans une période mensuelle glissante s'étendant du 26 du mois M-1 au 25 du mois M<sup>3</sup>. Le module de pointage assure le calcul automatisé des volumes horaires effectifs, la catégorisation des dérives horaires (retards stricts et départs anticipés), l'attribution des indemnités légales et conventionnelles (déplacement proratisé au réel et repas gradué), l'application des règles de compensation bilatérale différenciées selon la catégorie professionnelle (Cadre vs Non-Cadre), ainsi que l'export des éléments variables vers le moteur de calcul du bulletin de paie<sup>2</sup>.

### Synergie Inter-Modules et Topologie du Système

Le module de pointage est positionné au centre du SIRH Galaxam et interagit avec l'ensemble des briques applicatives déjà modélisées<sup>1</sup>. Il s'articule avec le référentiel des employés et des affectations de carrière pour extraire le site de rattachement du salarié (notamment le Siège à Antananarivo TNR ou l'Agence de Toamasina TMM), sa catégorie professionnelle (CADRE ou non-cadre), ainsi que son mode de calcul de paie<sup>2</sup>. Il s'interface également avec le module des congés et absences afin de vérifier l'existence de demandes validées neutralisant l'obligation de pointage<sup>1</sup>, s'appuie sur le moteur de sécurité RBAC (*Role-Based Access Control*) pour la gestion des habilitations<sup>1</sup>, et alimente le module de notification<sup>6</sup>.

| **Module Connecté**                 | **Flux Entrants vers le Pointage**                                                                                       | **Flux Sortants du Pointage**                                                                                                      |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| **Gestion du Personnel & Contrats** | Site de rattachement, grille horaire théorique, catégorie professionnelle (CADRE/A), mode de calcul de paie<sup>2</sup>. | Statistiques d'assiduité, indicateurs de présence effectives, compteurs d'heures.                                                  |
| **Congés & Absences**               | Demandes de congé validées (demande_conge), arrêts maladie, autorisations d'absence<sup>1</sup>.                         | Signalement des absences injustifiées décelées lors du contrôle quotidien<sup>3</sup>.                                             |
| **Paramétrage & Référentiels**      | Jours fériés, périodes chômées, barèmes d'indemnités, seuils globaux et tolérances<sup>2</sup>.                          | Consommation des plafonds d'heures supplémentaires et d'indemnités<sup>2</sup>.                                                    |
| **Moteur de Paie & IRSA**           | Salaire de base, valeur horaire de l'absence<sup>2</sup>.                                                                | Cumul mensuel des heures supplémentaires nettes, déductions d'absence retenues, indemnités de déplacement et de repas<sup>2</sup>. |
| **Sécurité & Notifications (RBAC)** | Droits d'accès par rôle (ROLE_ADMIN, ROLE_AGENT_POINTAGE)<sup>1</sup>.                                                   | Alertes d'anomalies d'horodatage non résolues avant clôture de paie<sup>6</sup>.                                                   |

### Mécanisme de Compensation et Valorisation des Heures par Catégorie

La durée légale du travail à Madagascar est établie à 8 heures par jour et 40 heures par semaine<sup>2</sup>. L'analyse des grilles de suivi Clocker met en exergue le principe de compensation bilérale par **tronçon hebdomadaire proratisé** formalisé par la règle « fenoina 8h foana ny andro vao mivadika HS »<sup>3</sup>.

**Proratisation du seuil** : la période de paie allant du 26 au 25, les semaines en début et fin de période sont des **tronçons** (2 à 4 jours ouvrés). Le seuil d'heures normales est donc proratisé : `seuil_tronçon = nb_jours_ouvrés × 8h` — pas un seuil fixe de 40h qui créerait un faux déficit systématique. **Coupure ferme au 25** : chaque journée est rattachée à sa période calendaire réelle.

Ce principe comporte une distinction fondamentale selon la catégorie professionnelle du salarié :

1.  **Règle Générale (Personnel Non-Cadre)** :

    - Les heures effectuées en dehors des plages théoriques absorbent en premier lieu le cumul mensuel des retards et départs anticipés<sup>3</sup>.

    - Si le volume des heures hors horaire excède le cumul des retards, le solde net positif est qualifié et rémunéré en heures supplémentaires majorées (HS_30, HS_50, etc.)<sup>2</sup>.

    - Si les retards excèdent les heures hors horaire normal, l'intégralité de ces heures est absorbée et le solde débiteur net est converti en heures d'absence à déduire du salaire brut sur la base du diviseur horaire conventionnel de 173.33 heures<sup>2</sup>.

2.  **Règle Spécifique (Personnel Cadre)** :

    - Les retards et absences des cadres sont comptabilisés de façon stricte<sup>2</sup>.

    - Les heures effectuées au-delà de l'horaire normal servent **exclusivement à compenser et absorber** leurs retards et absences du mois<sup>3</sup>.

    - Si les heures effectuées hors horaire absorbent totalement les retards, le solde net d'heures supplémentaires est **forcé à zéro** (aucun paiement d'heures supplémentaires pour la catégorie Cadre)<sup>5</sup>.

    - Si le solde des retards reste débiteur après compensation, le reliquat net est déduit du salaire sous forme d'heures d'absence<sup>2</sup>.

\(\displaystyle \text{Montant Déduction Absence} = H_{\mathrm{absence\_retenue}} \times \frac{\text{Salaire de Base}}{173.33}\)

### Ratios et Attributions des Indemnités par Site

L'octroi des indemnités accessoires est subordonné aux règles d'affectation géographique, au calcul dynamique des jours ouvrables réels et aux heures de sortie constatées<sup>2</sup>.

| **Indemnité / Paramètre**        | **Antananarivo (TNR)**                                                                                                | **Toamasina (TMM)**                                                                                                   | **Règle d'Imputation et Source**                                                                                                                                                          |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Horaires Théoriques**          | 08:00 - 12:00 / 13:00 - 17:00                                                                                         | 08:00 - 12:00 / 14:00 - 18:00                                                                                         | Horaire continu à TNR (pause 1h) vs horaire discontinu à TMM (pause 2h)<sup>2</sup>.                                                                                                      |
| **Base Mensuelle Déplacement**   | 31 200 Ariary                                                                                                         | 53 200 Ariary                                                                                                         | Configuré au niveau de la table site_indemnite_config<sup>2</sup>.                                                                                                                        |
| **Jours Ouvrables Réels**        | Calcul dynamique \(J_{\mathrm{ouvrables\_réels}}\) | Calcul dynamique \(J_{\mathrm{ouvrables\_réels}}\) | Exclut les samedis, dimanches et jours fériés chômés réels de la période du 26 M-1 au 25 M<sup>2</sup>.                                                                                   |
| **Taux Journalier Déplacement**  | \(\displaystyle \frac{31\,200}{J_{\mathrm{ouvrables\_réels}}}\)                 | \(\displaystyle \frac{53\,200}{J_{\mathrm{ouvrables\_réels}}}\)                  | Calculé dynamiquement au réel chaque mois (ex: \(1\,485.71\ \text{Ar/j}\) pour 21 jours ouvrables)<sup>2</sup>. |
| **Attribution Déplacement**      | Prorata des jours de présence                                                                                         | Prorata des jours de présence                                                                                         | Déclenché dès qu'au moins une plage (matin ou après-midi) comporte du travail effectif<sup>3</sup>.                                                                                       |
| **Indemnité Repas de Base**      | 2 000 Ariary / jour                                                                                                   | 0 Ariary / jour                                                                                                       | Exige la présence sur les deux plages de la journée ; annulé en cas de demi-journée<sup>2</sup>.                                                                                          |
| **Second Repas (19h30 - 21h00)** | Extra de \(+2\,000\ \text{Ariary}\)         | Extra de \(+2\,000\ \text{Ariary}\)         | Déclenché si l'heure de sortie est supérieure à 19:30 et inférieure ou égale à 21:00<sup>2</sup>.                                                                                         |
| **Second Repas (\> 21h00)**      | Extra de \(+4\,000\ \text{Ariary}\)         | Extra de \(+4\,000\ \text{Ariary}\)         | Déclenché si l'heure de sortie est supérieure à 21:00<sup>2</sup>.                                                                                                                        |

## Spécifications Fonctionnelles Détaillées

### Matrice de Préséance des Statuts Quotidiens et Saisie Manuel

Pour chaque jour calendaire de la période du 26 M-1 au 25 M, le moteur d'évaluation attribue un statut unique à chaque salarié actif<sup>3</sup>. En cas de dysfonctionnement de la pointeuse Clocker ou d'absence de données brutes, l'Agent de pointage habilité peut procéder à la saisie manuelle des présences<sup>1</sup>.

1.  **Préséance 1 : Jour Férié et Chômé** — Si la date figure dans site_calendrier_ferie avec est_chome = TRUE, la journée est qualifiée en FERIE<sup>2</sup>. Tout pointage effectif saisi un jour férié bascule l'intégralité des heures travaillées en heures supplémentaires majorées à 100% (HS_100)<sup>2</sup>.

2.  **Préséance 2 : Congé et Absence Validée** — Si une entrée validée existe dans demande_conge, le statut prend la valeur du code de congé correspondant (ex: ANNUEL, MALADIE, MATERNITE)<sup>1</sup>. En cas de congé sur une demi-journée, la demi-journée restante demeure soumise à l'obligation de pointage sous peine de déclencher une absence partielle<sup>3</sup>.

3.  **Préséance 3 : Mission et Déplacement Externe** — Couvert par un ordre de mission validé, la journée est qualifiée en MISSION, neutralisant la détection de retard tout en maintenant le droit aux indemnités de déplacement<sup>3</sup>.

4.  **Préséance 4 : Pointage Effectif (Travail)** — La présence de pointages valides qualifie la journée en TRAVAILLE<sup>3</sup>.

5.  **Préséance 5 : Pointage Incomplet (Anomalie)** — Si un salarié n'enregistre qu'une partie de ses pointages (ex: entrée matin présente mais aucune sortie), le système marque la journée en ANOMALIE_INCOMPLET. Aucune pénalité automatique n'est imputée ; une correction manuelle par l'Agent de pointage est requise<sup>1</sup>.

6.  **Préséance 6 : Absence Injustifiée** — En l'absence totale de pointage sur une journée ouvrable non couverte par un congé, la journée est marquée ABSENCE_INJUSTIFIEE<sup>3</sup>. Chaque journée d'absence injustifiée engendre une retenue de 8 heures normales<sup>2</sup>.

7.  **Préséance 7 : Repos Hebdomadaire (Weekend)** — Les samedis et dimanches non travaillés sont qualifiés en WEEKEND<sup>3</sup>. Tout travail effectué le dimanche est soumis à la majoration de 40% (DIMANCHE)<sup>2</sup>.

### Algorithme de Calcul et Traitement des Dérives Horaires

Le calcul des durées s'effectue à partir des horodatages \(H_{EM}\) (Entrée Matin), \(H_{SM}\) (Sortie Matin), \(H_{EA}\) (Entrée Après-midi) et \(H_{SA}\) (Sortie Après-midi)<sup>3</sup> :

1.  **Calcul des Durées Brutes et Conversions** :

    - Durée Matin : \(D_M = H_{SM} - H_{EM}\)<sup>3</sup>.

    - Durée Après-midi : \(D_A = H_{SA} - H_{EA}\)<sup>3</sup>.

    - Durée Totale Journalière : \(T_{\mathrm{jour}} = D_M + D_A\)<sup>3</sup>.

    - Conversion en heures décimales : \(H_{\mathrm{dec}} = \frac{\mathrm{Minutes\ Totales}}{60}\)<sup>3</sup>.

2.  **Détection Stricte des Retards et Départs Anticipés** :

    - Aucune franchise n'est appliquée (comptabilisation stricte dès la première minute) :

      - Retard Entrée Matin : \(R_M = \max(0, H_{EM} - H_{EM\_\mathrm{theo}})\)

      - Départ Anticipé Après-midi : \(A_A = \max(0, H_{SA\_\mathrm{theo}} - H_{SA})\)

    - Les retards et départs anticipés sont cumulés en minutes au niveau de la journée et de la période<sup>3</sup>.

3.  **Qualification des Heures Spéciales** :

    - Plafond hebdomadaire légal de 20h d'heures supplémentaires (Décret n°2007-563) : non bloquant en saisie mais déclenchant un avertissement d'audit<sup>2</sup>.

    - Travail de nuit : Heures accomplies entre 22h00 et 05h00 qualifiées en NUIT (majoration de 30% ou 40% selon accord)<sup>2</sup>.

## Modélisation de la Base de Données PostgreSQL

> **ARCHITECTURE FINALE VALIDÉE** (voir `pointage.md` §2 et `26-08-20-sp3-pointage.sql`) :
>
> L'architecture retenue est **1 table principale** `pointage_jour` + **2 tables de synthèse gelées** :
> - `pointage_jour` — saisie quotidienne + calculs métier
> - `pointage_semaine_calcule` — tronçons hebdomadaires figés à la validation
> - `pointage_cumul_periode` — cumul mensuel figé, source de vérité pour la Paie
>
> Les 3 tables ci-dessous (`ref_horaire_site`, `pointage_brut`, `pointage_journalier_calcule`) étaient une proposition initiale **non retenue**. Elles sont conservées ici comme référence d'analyse uniquement.

Le schéma relationnel PostgreSQL s'intègre aux structures existantes du SIRH (ref_site, employe, utilisateur, demande_conge, ref_categorie_professionnelle)<sup>1</sup>, en assurant une séparation étanche entre les horodatages bruts et les synthèses recalculées.

> SQL
>
> -- =====================================================================  
> -- SCRIPT POSTGRESQL : MODULE POINTAGE & PRÉSENCES (SIRH GALAXAM V2)  
> -- =====================================================================  
>   
> DROP VIEW IF EXISTS v_pointage_synthese_paie CASCADE;  
> DROP VIEW IF EXISTS v_pointage_journalier_cpl CASCADE;  
> DROP TABLE IF EXISTS pointage_cumul_periode CASCADE;  
> DROP TABLE IF EXISTS pointage_ajustement CASCADE;  
> DROP TABLE IF EXISTS pointage_journalier_calcule CASCADE;  
> DROP TABLE IF EXISTS pointage_brut CASCADE;  
> DROP TABLE IF EXISTS ref_horaire_site CASCADE;  
>   
> -- ------------------------------------------  
> -- 1. RÉFÉRENTIEL DES HORAIRES THÉORIQUES PAR SITE  
> -- ------------------------------------------  
> CREATE TABLE ref_horaire_site (  
> id_horaire SERIAL PRIMARY KEY,  
> id_site INT NOT NULL REFERENCES ref_site(id_site) ON DELETE CASCADE,  
> libelle_horaire VARCHAR(100) NOT NULL,  
> est_horaire_continu BOOLEAN DEFAULT TRUE,  
>   
> heure_entree_matin TIME NOT NULL DEFAULT '08:00:00',  
> heure_sortie_matin TIME NOT NULL DEFAULT '12:00:00',  
> heure_entree_aprem TIME DEFAULT '13:00:00',  
> heure_sortie_aprem TIME DEFAULT '17:00:00',  
>   
> duree_journaliere_heures NUMERIC(4,2) DEFAULT 8.00,  
> tolerance_retard_minutes INT DEFAULT 0, -- Comptabilisation stricte  
> est_actif BOOLEAN DEFAULT TRUE,  
> created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
> CONSTRAINT uk_site_horaire UNIQUE (id_site, libelle_horaire)  
> );  
>   
> INSERT INTO ref_horaire_site (id_site, libelle_horaire, est_horaire_continu, heure_entree_matin, heure_sortie_matin, heure_entree_aprem, heure_sortie_aprem) VALUES  
> (1, 'Standard TNR (Continu)', TRUE, '08:00:00', '12:00:00', '13:00:00', '17:00:00'),  
> (2, 'Standard TMM (Discontinu)', FALSE, '08:00:00', '12:00:00', '14:00:00', '18:00:00');  
>   
> -- ------------------------------------------  
> -- 2. CAPTURE DES POINTAGES BRUTS  
> -- ------------------------------------------  
> CREATE TABLE pointage_brut (  
> id_pointage_brut BIGSERIAL PRIMARY KEY,  
> id_employe INT NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,  
> date_pointage DATE NOT NULL,  
>   
> heure_entree_matin TIME,  
> heure_sortie_matin TIME,  
> heure_entree_aprem TIME,  
> heure_sortie_aprem TIME,  
>   
> source_donnee VARCHAR(50) DEFAULT 'CLOCKER_DIGITAL', -- CLOCKER_DIGITAL, SAISIE_MANUELLE  
> id_utilisateur_saisie INT REFERENCES utilisateur(id_utilisateur),  
>   
> est_verrouille BOOLEAN DEFAULT FALSE,  
> created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
> updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
>   
> CONSTRAINT uk_employe_date_pointage UNIQUE (id_employe, date_pointage)  
> );  
>   
> CREATE INDEX idx_pointage_brut_emp_date ON pointage_brut(id_employe, date_pointage);  
> CREATE INDEX idx_pointage_brut_periode ON pointage_brut(date_pointage);  
>   
> -- ------------------------------------------  
> -- 3. ÉVALUATION JOURNALIÈRE RECALCULÉE  
> -- ------------------------------------------  
> CREATE TABLE pointage_journalier_calcule (  
> id_pointage_calcule BIGSERIAL PRIMARY KEY,  
> id_pointage_brut BIGINT REFERENCES pointage_brut(id_pointage_brut) ON DELETE CASCADE,  
> id_employe INT NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,  
> id_site INT NOT NULL REFERENCES ref_site(id_site),  
> date_pointage DATE NOT NULL,  
>   
> statut_journee VARCHAR(30) NOT NULL DEFAULT 'TRAVAILLE', -- TRAVAILLE, ANOMALIE_INCOMPLET, ABSENCE_INJUSTIFIEE, FERIE, CONGE, MISSION, WEEKEND  
> id_demande_conge INT REFERENCES demande_conge(id_demande_conge) ON DELETE SET NULL,  
>   
> heures_travaillables NUMERIC(4,2) DEFAULT 8.00,  
> heures_travaillees NUMERIC(5,2) DEFAULT 0.00,  
>   
> minutes_retard INT DEFAULT 0,  
> minutes_depart_anticipe INT DEFAULT 0,  
>   
> heures_sup_30 NUMERIC(4,2) DEFAULT 0.00,  
> heures_sup_50 NUMERIC(4,2) DEFAULT 0.00,  
> heures_sup_100 NUMERIC(4,2) DEFAULT 0.00,  
> heures_nuit NUMERIC(4,2) DEFAULT 0.00,  
>   
> journee_presence NUMERIC(3,1) DEFAULT 0.0,  
> est_eligible_deplacement BOOLEAN DEFAULT FALSE,  
> est_eligible_repas BOOLEAN DEFAULT FALSE,  
> montant_extra_repas NUMERIC(10,2) DEFAULT 0.00, -- 2000 Ar (19h30-21h) ou 4000 Ar (\>21h)  
>   
> montant_indemnite_deplacement NUMERIC(10,2) DEFAULT 0.00,  
> montant_indemnite_repas NUMERIC(10,2) DEFAULT 0.00,  
>   
> est_valide BOOLEAN DEFAULT TRUE,  
> created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
> updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
>   
> CONSTRAINT uk_calcule_emp_date UNIQUE (id_employe, date_pointage)  
> );  
>   
> CREATE INDEX idx_pointage_calc_emp_date ON pointage_journalier_calcule(id_employe, date_pointage);  
> CREATE INDEX idx_pointage_calc_statut ON pointage_journalier_calcule(statut_journee);  
>   
> -- ------------------------------------------  
> -- 4. HISTORIQUE ET AUDIT DES AJUSTEMENTS  
> -- ------------------------------------------  
> CREATE TABLE pointage_ajustement (  
> id_ajustement SERIAL PRIMARY KEY,  
> id_pointage_calcule BIGINT NOT NULL REFERENCES pointage_journalier_calcule(id_pointage_calcule) ON DELETE CASCADE,  
> id_utilisateur INT NOT NULL REFERENCES utilisateur(id_utilisateur),  
>   
> type_ajustement VARCHAR(50) NOT NULL,  
> anciennes_valeurs JSONB NOT NULL,  
> nouvelles_valeurs JSONB NOT NULL,  
> motif_ajustement TEXT NOT NULL,  
>   
> date_ajustement TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP  
> );  
>   
> CREATE INDEX idx_pointage_ajustement_calc ON pointage_ajustement(id_pointage_calcule);  
>   
> -- ------------------------------------------  
> -- 5. PASSERELLE MENSUELLE ET CUMULS DE PAIE  
> -- ------------------------------------------  
> CREATE TABLE pointage_cumul_periode (  
> id_cumul SERIAL PRIMARY KEY,  
> id_employe INT NOT NULL REFERENCES employe(id_employe) ON DELETE CASCADE,  
> annee INT NOT NULL,  
> mois INT NOT NULL,  
> date_debut_periode DATE NOT NULL,  
> date_fin_periode DATE NOT NULL,  
>   
> jours_ouvrables_periode INT NOT NULL DEFAULT 22, -- Calculé dynamiquement au réel  
> jours_travailles NUMERIC(4,1) DEFAULT 0.0,  
> jours_absence_injustifiee NUMERIC(4,1) DEFAULT 0.0,  
> jours_conge_paye NUMERIC(4,1) DEFAULT 0.0,  
>   
> total_heures_normales NUMERIC(6,2) DEFAULT 0.00,  
> total_heures_travaillees NUMERIC(6,2) DEFAULT 0.00,  
> total_minutes_retard INT DEFAULT 0,  
> total_minutes_compensation INT DEFAULT 0,  
>   
> heures_absence_retenues NUMERIC(5,2) DEFAULT 0.00, -- Retards nets non compensés convertis en heures  
> heures_sup_nettes_30 NUMERIC(5,2) DEFAULT 0.00,  
> heures_sup_nettes_50 NUMERIC(5,2) DEFAULT 0.00,  
> heures_sup_nettes_100 NUMERIC(5,2) DEFAULT 0.00,  
> heures_nuit_nettes NUMERIC(5,2) DEFAULT 0.00,  
>   
> total_jours_deplacement NUMERIC(4,1) DEFAULT 0.0,  
> total_montant_deplacement NUMERIC(12,2) DEFAULT 0.00,  
>   
> total_jours_repas NUMERIC(4,1) DEFAULT 0.0,  
> total_montant_repas NUMERIC(12,2) DEFAULT 0.00,  
>   
> nb_anomalies_non_resolues INT DEFAULT 0,  
> statut_cloture VARCHAR(20) DEFAULT 'BROUILLON', -- BROUILLON, VALIDE, CLOTURE  
> cloture_par INT REFERENCES utilisateur(id_utilisateur),  
> date_cloture TIMESTAMP WITH TIME ZONE,  
>   
> created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
> updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,  
>   
> CONSTRAINT uk_cumul_emp_periode UNIQUE (id_employe, annee, mois)  
> );  
>   
> CREATE INDEX idx_pointage_cumul_emp ON pointage_cumul_periode(id_employe);  
> CREATE INDEX idx_pointage_cumul_periode ON pointage_cumul_periode(annee, mois);  
>   
> -- ------------------------------------------  
> -- 6. VUES D'EXPLOITATION  
> -- ------------------------------------------  
>   
> CREATE OR REPLACE VIEW v_pointage_journalier_cpl AS  
> SELECT  
> p.id_pointage_calcule,  
> p.id_pointage_brut,  
> p.id_employe,  
> e.matricule,  
> e.nom,  
> e.prenom,  
> CONCAT(e.nom, ' ', e.prenom) AS nom_complet,  
>   
> p.id_site,  
> s.code_site,  
> s.libelle AS libelle_site,  
>   
> p.date_pointage,  
> pb.heure_entree_matin,  
> pb.heure_sortie_matin,  
> pb.heure_entree_aprem,  
> pb.heure_sortie_aprem,  
>   
> p.statut_journee,  
> p.heures_travaillables,  
> p.heures_travaillees,  
> p.minutes_retard,  
> p.minutes_depart_anticipe,  
>   
> p.heures_sup_30,  
> p.heures_sup_50,  
> p.heures_sup_100,  
> p.heures_nuit,  
>   
> p.journee_presence,  
> p.est_eligible_deplacement,  
> p.est_eligible_repas,  
> p.montant_extra_repas,  
> p.montant_indemnite_deplacement,  
> p.montant_indemnite_repas,  
>   
> p.id_demande_conge,  
> pb.source_donnee,  
> pb.est_verrouille  
> FROM pointage_journalier_calcule p  
> JOIN employe e ON p.id_employe = e.id_employe  
> JOIN ref_site s ON p.id_site = s.id_site  
> LEFT JOIN pointage_brut pb ON p.id_pointage_brut = pb.id_pointage_brut;  
>   
> CREATE OR REPLACE VIEW v_pointage_synthese_paie AS  
> SELECT  
> c.id_cumul,  
> c.id_employe,  
> e.matricule,  
> e.nom,  
> e.prenom,  
> rcp.code AS code_categorie_pro,  
> c.annee,  
> c.mois,  
> c.date_debut_periode,  
> c.date_fin_periode,  
>   
> c.jours_ouvrables_periode,  
> c.jours_travailles,  
> c.jours_absence_injustifiee,  
> c.jours_conge_paye,  
>   
> c.heures_absence_retenues,  
> c.heures_sup_nettes_30,  
> c.heures_sup_nettes_50,  
> c.heures_sup_nettes_100,  
> c.heures_nuit_nettes,  
>   
> c.total_jours_deplacement,  
> c.total_montant_deplacement,  
> c.total_jours_repas,  
> c.total_montant_repas,  
>   
> c.nb_anomalies_non_resolues,  
> c.statut_cloture  
> FROM pointage_cumul_periode c  
> JOIN employe e ON c.id_employe = e.id_employe  
> LEFT JOIN employe_affectation_carriere eac ON e.id_employe = eac.id_employe AND eac.id_statut_rh = 2  
> LEFT JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle;

## Procédures Stockées et Fonctions PostgreSQL Métier

> **Note** : La procédure `sp_calcul_compensation_mensuelle` ci-dessous était une proposition initiale basée sur une compensation **mensuelle**. La décision finale retient une compensation **hebdomadaire par tronçon proratisé** via `v_pointage_semaine` (voir `pointage.md` RG-PTG-04 et §2.7). Les fonctions utilitaires `fn_get_jours_ouvrables_periode` et `fn_calcul_indemnite_repas_jour` restent **validées** et réutilisées dans l'architecture finale (rebaptisée `fn_jours_ouvrables` dans le script).

> SQL
>
> -- =====================================================================  
> -- 1. CALCUL DYNAMIQUE DU NOMBRE DE JOURS OUVRABLES RÉELS D'UNE PÉRIODE  
> -- =====================================================================  
> CREATE OR REPLACE FUNCTION fn_get_jours_ouvrables_periode(  
> p_id_site INT,  
> p_date_debut DATE,  
> p_date_fin DATE  
> ) RETURNS INT AS \$\$  
> DECLARE  
> v_nb_jours INT := 0;  
> BEGIN  
> SELECT COUNT(\*)::INT INTO v_nb_jours  
> FROM generate_series(p_date_debut, p_date_fin, '1 day'::interval) d  
> WHERE EXTRACT(ISODOW FROM d) NOT IN (6, 7) -- Exclusion Samedi (6) et Dimanche (7)  
> AND NOT EXISTS (  
> SELECT 1  
> FROM site_calendrier_ferie scf  
> JOIN ref_calendrier_ferie rcf ON scf.id_ferie = rcf.id_ferie  
> WHERE scf.id_site = p_id_site  
> AND rcf.date_ferie = d::date  
> AND rcf.est_chome = TRUE  
> );  
>   
> RETURN GREATEST(v_nb_jours, 1);  
> END;  
> \$\$ LANGUAGE plpgsql;  
>   
> -- =====================================================================  
> -- 2. CALCUL GRADUÉ DE L'INDEMNITÉ DE REPAS ET DU SECOND REPAS  
> -- =====================================================================  
> CREATE OR REPLACE FUNCTION fn_calcul_indemnite_repas_jour(  
> p_id_site INT,  
> p_heure_sortie_aprem TIME,  
> p_presence_complete BOOLEAN  
> ) RETURNS TABLE(montant_base NUMERIC(10,2), extra_repas NUMERIC(10,2), montant_total NUMERIC(10,2)) AS \$\$  
> DECLARE  
> v_base NUMERIC(10,2) := 0.00;  
> v_extra NUMERIC(10,2) := 0.00;  
> BEGIN  
> -- Récupération du repas de base du site  
> SELECT COALESCE(montant_reference, 0.00) INTO v_base  
> FROM site_indemnite_config  
> WHERE id_site = p_id_site  
> AND id_indemnite = 2  
> AND est_actif = TRUE  
> LIMIT 1;  
>   
> -- Annulation de l'indemnité de base si demi-journée  
> IF NOT p_presence_complete THEN  
> v_base := 0.00;  
> END IF;  
>   
> -- Évaluation du second repas selon l'heure de sortie  
> IF p_heure_sortie_aprem IS NOT NULL THEN  
> IF p_heure_sortie_aprem \> '21:00:00'::TIME THEN  
> v_extra := 4000.00;  
> ELSIF p_heure_sortie_aprem \> '19:30:00'::TIME THEN  
> v_extra := 2000.00;  
> END IF;  
> END IF;  
>   
> RETURN QUERY SELECT v_base, v_extra, (v_base + v_extra);  
> END;  
> \$\$ LANGUAGE plpgsql;  
>   
> -- =====================================================================  
> -- 3. PROCÉDURE DE COMPENSATION MENSUELLE (CADRE VS NON-CADRE)  
> -- =====================================================================  
> CREATE OR REPLACE PROCEDURE sp_calcul_compensation_mensuelle(  
> p_id_employe INT,  
> p_annee INT,  
> p_mois INT,  
> p_date_debut DATE,  
> p_date_fin DATE  
> ) AS \$\$  
> DECLARE  
> v_code_categorie VARCHAR(10);  
> v_total_retard_min INT := 0;  
> v_total_hs_brutes_min INT := 0;  
> v_net_min INT := 0;  
> v_heures_absence NUMERIC(5,2) := 0.00;  
> v_heures_hs_nettes NUMERIC(5,2) := 0.00;  
> BEGIN  
> -- 1. Récupération de la catégorie professionnelle  
> SELECT rcp.code INTO v_code_categorie  
> FROM employe_affectation_carriere eac  
> JOIN ref_categorie_professionnelle rcp ON eac.id_categorie_professionnelle = rcp.id_categorie_professionnelle  
> WHERE eac.id_employe = p_id_employe AND eac.id_statut_rh = 2  
> LIMIT 1;  
>   
> -- 2. Cumul des retards et des HS brutes de la période  
> SELECT  
> COALESCE(SUM(minutes_retard + minutes_depart_anticipe), 0),  
> COALESCE(ROUND(SUM(heures_sup_30 + heures_sup_50 + heures_sup_100) \* 60), 0)  
> INTO v_total_retard_min, v_total_hs_brutes_min  
> FROM pointage_journalier_calcule  
> WHERE id_employe = p_id_employe  
> AND date_pointage BETWEEN p_date_debut AND p_date_fin;  
>   
> v_net_min := v_total_hs_brutes_min - v_total_retard_min;  
>   
> -- 3. Ventilation selon la catégorie professionnelle  
> IF v_code_categorie = 'CADRE' THEN  
> IF v_net_min \< 0 THEN  
> -- Solde débiteur net : déduction sous forme d'heures d'absence  
> v_heures_absence := ROUND(ABS(v_net_min) / 60.0, 2);  
> v_heures_hs_nettes := 0.00;  
> ELSE  
> -- Retards totalement absorbés, pas de paiement d'HS  
> v_heures_absence := 0.00;  
> v_heures_hs_nettes := 0.00;  
> END IF;  
> ELSE  
> -- Non-cadre : Compensation standard  
> IF v_net_min \< 0 THEN  
> v_heures_absence := ROUND(ABS(v_net_min) / 60.0, 2);  
> v_heures_hs_nettes := 0.00;  
> ELSE  
> v_heures_absence := 0.00;  
> v_heures_hs_nettes := ROUND(v_net_min / 60.0, 2);  
> END IF;  
> END IF;  
>   
> -- 4. Mise à jour de la table de cumul mensuel  
> UPDATE pointage_cumul_periode  
> SET total_minutes_retard = v_total_retard_min,  
> total_minutes_compensation = LEAST(v_total_retard_min, v_total_hs_brutes_min),  
> heures_absence_retenues = v_heures_absence,  
> heures_sup_nettes_30 = CASE WHEN v_code_categorie = 'CADRE' THEN 0 ELSE v_heures_hs_nettes END  
> WHERE id_employe = p_id_employe AND annee = p_annee AND mois = p_mois;  
> END;  
> \$\$ LANGUAGE plpgsql;

## Architecture Technique, Notifications et Intégration API

### Habilitations RBAC et Navigation Frontend

L'accès aux fonctionnalités du module de pointage est gouverné par la table role_permission et la structure des menus dans ref_menu et role_menu<sup>1</sup>.

> SQL
>
> INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES  
> ('POINTAGE_JOURNALIER', 'Saisie & Pointage Quotidien', 'ClockCheck', '/pointages/quotidien', 1, 2, 'POINTAGE'),  
> ('POINTAGE_ANOMALIES', 'Gestion des Anomalies', 'AlertTriangle', '/pointages/anomalies', 2, 2, 'POINTAGE'),  
> ('POINTAGE_CLOTURE', 'Validation & Clôture Paie', 'Lock', '/pointages/cloture', 3, 2, 'POINTAGE')  
> ON CONFLICT (id_menu) DO NOTHING;  
>   
> INSERT INTO role_menu (id_role, id_menu) VALUES  
> (1, 'POINTAGE_JOURNALIER'), (1, 'POINTAGE_ANOMALIES'), (1, 'POINTAGE_CLOTURE'),  
> (2, 'POINTAGE_JOURNALIER'), (2, 'POINTAGE_ANOMALIES')  
> ON CONFLICT DO NOTHING;

### Intégration avec la Table notification_rh et Contrôle de Clôture

En cas de détection d'anomalies non résolues (ANOMALIE_INCOMPLET), le système n'effectue pas de pénalité automatique<sup>3</sup>. À la place, le batch de pré-clôture génère des enregistrements de notification<sup>6</sup> :

> SQL
>
> INSERT INTO notification_rh (id_utilisateur, type_alerte, priorite, titre, message, lien)  
> SELECT  
> u.id_utilisateur,  
> 'BROUILLON_EN_ATTENTE',  
> 'HAUTE',  
> 'Anomalies de pointage non résolues',  
> CONCAT('Il reste ', c.nb_anomalies_non_resolues, ' anomalies de pointage avant clôture.'),  
> '/pointages/anomalies'  
> FROM pointage_cumul_periode c  
> CROSS JOIN utilisateur u  
> WHERE c.nb_anomalies_non_resolues \> 0  
> AND u.id_role = 1 -- Administrateurs / DAF  
> AND c.statut_cloture = 'BROUILLON';

### Endpoints RESTful (API Spring Boot)

| Méthode HTTP | Endpoint API | Rôle Requis | Description et Usage |
| :--- | :--- | :--- | :--- |
| GET | /api/pointages/saisie | ROLE_ADMIN, ROLE_AGENT_POINTAGE | Consultation de la grille journalière avec tolérances et indemnités de site |
| POST | /api/pointages/saisie/batch | ROLE_AGENT_POINTAGE, ROLE_ADMIN | Sauvegarde par lot, synchronisation pointage_jour et pointage_indemnite_jour |
| GET | /api/pointages/anomalies | ROLE_ADMIN, ROLE_AGENT_POINTAGE | Détection et filtrage des anomalies, retards et absences |
| PUT | /api/pointages/anomalies/{idPointage}/regulariser | ROLE_AGENT_POINTAGE, ROLE_ADMIN | Régularisation d'anomalie avec synchronisation stricte pointage_indemnite_jour |
| GET | /api/pointages/synthese | ROLE_ADMIN, ROLE_AGENT_POINTAGE | Synthèse globale de période et totaux par collaborateur |
| GET | /api/pointages/synthese/semaines | ROLE_ADMIN, ROLE_AGENT_POINTAGE | Découpage des tronçons hebdomadaires (40h et majorations) |
| POST | /api/pointages/synthese/calculer-tout | ROLE_AGENT_POINTAGE, ROLE_ADMIN | Consolidation globale 1-clic de toutes les semaines (période EN_COURS) |
| POST | /api/pointages/synthese/transmettre-daf | ROLE_AGENT_POINTAGE, ROLE_ADMIN | Transmission DAF avec vérification 0 anomalie entreprise et verrou A_VALIDER |
| POST | /api/pointages/synthese/valider-daf | ROLE_ADMIN | Validation finale DAF, figeage dans pointage_cumul_periode et passage VALIDEE |
| POST | /api/pointages/synthese/rejeter-daf | ROLE_ADMIN | Rejet DAF avec motif obligatoire et réouverture en EN_COURS |

## Feuille de Route d'Implémentation

| **Phase**                        | **Livrables Principaux**                                                                                                                                  | **Dépendances & Prérequis**                            |
|----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------|
| **Phase 1 : SGBD & Structures**  | Exécution du script DDL PostgreSQL (ref_horaire_site, pointage_brut, pointage_journalier_calcule, pointage_cumul_periode)<sup>2</sup>.                    | BDD socle, rôles et référentiels déployés<sup>1</sup>. |
| **Phase 2 : Fonctions & Moteur** | Déploiement des fonctions fn_get_jours_ouvrables_periode, fn_calcul_indemnite_repas_jour et de la procédure sp_calcul_compensation_mensuelle<sup>2</sup>. | Scripts de structure validés<sup>2</sup>.              |
| **Phase 3 : Services & UI**      | Développement des Controllers/Services Java REST Boot et des composants React (Saisie, Anomalies, Clôture)<sup>1</sup>.                                   | Endpoints API validés<sup>1</sup>.                     |
| **Phase 4 : Recette & Paie**     | Tests de compensation sur données réelles Excel (Cas Rakoto, Raketaka, Rasoa) et vérification du transfert vers la paie<sup>3</sup>.                      | Jeux de données Clocker réels<sup>3</sup>.             |

L'architecture ainsi finalisée assure une automatisation complète de la chaîne de traitement du temps de travail au sein du SIRH Galaxam, tout en s'alignant sur la réglementation sociale et fiscale malgache<sup>1</sup>.

#### Sources des citations

1.  26-07-29_init.sql

2.  26-08-05-sp0-ref.sql

3.  ohatra gita fichiers-23jan26.xlsx

4.  ohatra presence svt clocker.xlsx

5.  26-08-10-sp1-contrat.sql

6.  26-08-10-sp2-notifications.sql
