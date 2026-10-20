# Dossier de Contexte — Stage de Fin d'Études & PFE SIRH GALAXAM Logistics
## Système d'Information des Ressources Humaines Centralisé, Sécurisé et Conforme au Droit Malagasy
### Référence Académique (IT University) & Spécification Opérationnelle de Terrain
### **Document de contexte historique — à confronter au dépôt actuel**

> **État de l'infrastructure (octobre 2026)** : La cible actuelle est un seul `docker-compose.yml` avec PostgreSQL 15, backend Spring Boot 4.1.0 et frontend React servi par Nginx. L'accès prévu est `http://localhost:8088` ; pgAdmin peut utiliser `127.0.0.1:5433` lorsque les conteneurs fonctionnent. Le premier démarrage technique ne démontre pas une installation ou une recette validée dans l'entreprise. Les dates, métriques et validations de ce dossier historique demandent des preuves indépendantes avant insertion dans le mémoire.

---

> **Objectif et Nature du Document :**  
> Ce dossier constitue la **référence centrale, canonique et transférable** pour la rédaction du Mémoire de Fin d'Études (Licence en Informatique — Option Développement, IT University) et la conduite opérationnelle du projet SIRH de **GALAXAM Logistics Madagascar**.  
> Il synthétise l'ensemble des données vérifiées sur le terrain : identité de l'entreprise d'accueil, diagnostic critique des processus préexistants (« Avant SIRH »), analyse des problématiques réelles de gestion des ressources humaines dans le secteur logistique et douanier malgache, cadre juridique (Loi n° 2024-014, CGI LF 2026, CNaPS), architecture logicielle déployée et règles de gestion effectives du dépôt.

---

## 1. Fiche Synoptique du Stage & Acteurs du Projet

| Dimension | Détail / Donnée Officielle |
|:---|:---|
| **Candidat / Étudiant** | **ANDRIAMALALA Rivo Ny Aina Tojonirina** (Matricule ITU : `ETU003080`) |
| **Cursus Universitaire** | Licence en Informatique — Option Développement Logiciel |
| **Établissement Académique** | **IT University (ITU)** — Andoharanofotsy, Antananarivo, Madagascar |
| **Encadreur Pédagogique (ITU)** | **Dr RAKOTOARIMALALA Tsinjo Tony** |
| **Entreprise d'Accueil** | **GALAXAM Logistics SARL** (Code filiale : `GLX`) |
| **Encadreur Professionnel** | **Mme RALAMBOMANANA Tatamo Michele** (Gérante, Directrice Administrative, Financière et RH) |
| **Dirigeant / Fondateur** | **M. RANDRASANA Eric Jean-Joël** (Fondateur & Directeur Général, 25 ans d'exercice en 2026) |
| **Période Conventionnée Initiale** | Du **06 juillet 2026 au 06 octobre 2026** (3 mois conventionnels indiqués sur la fiche de stage) |
| **Période Réelle Prolongée** | Du **06 juillet 2026 au 06 novembre 2026** (Prolongation formelle d'un mois accordée) |
| **Rattachement Interne** | Directement rattaché au **Département Logistics** (absence de service informatique interne propre) |
| **Dépôt & Branches Git** | Dépôt Git du projet ; la stratégie de branches et un déploiement opérationnel demandent une vérification séparée. La cible retenue est une installation locale par Docker Compose. |

### 1.1 Chronologie Réelle du Projet & Courriel Officiel de Prolongation

Bien que la convention de stage initiale auprès de l'IT University (ITU) mentionnait une durée indicative de 3 mois (du 6 juillet au 6 octobre 2026), **le sujet du SIRH n'a été formellement cadré, validé et lancé par la direction de GALAXAM que vers la fin du mois de juillet 2026 (vers le 24-27 juillet 2026)**.

Les trois premières semaines (du 6 au 24 juillet 2026, soit 15 jours ouvrés) ont constitué une phase d'immersion opérationnelle, d'observation des flux douaniers et fret, et d'audit exploratoire des classeurs Excel existants.

Par conséquent, à la date d'échéance théorique du **06 octobre 2026**, le volume de travail effectif consacré au développement du SIRH n'était que d'environ **52 jours ouvrés**, ne permettant pas d'atteindre le total requis de **66 jours ouvrés** de réalisation et de recette technique. Face à ce décalage de périmètre temporel, la Direction Administrative et Financière a formellement sollicité une prolongation d'un mois auprès de l'école :

> **Courriel officiel de demande de prolongation (06 octobre 2026 à 13:27) :**  
> **De :** GALAXAM DAF (`daf@galaxam.com`)  
> **À :** Dr RAKOTOARIMALALA Tsinjo Tony (`tsinjo@itu.mg`)  
> **Date :** Mardi 6 octobre 2026, 13:27  
> **Objet :** *Demande de prolongation de stage – ETU003080 ANDRIAMALALA Rivo Ny Aina Tojonirina*  
>  
> *« Bonjour,*  
> *Nous vous informons que le stage de fin d’études de M. ANDRIAMALALA Rivo Ny Aina Tojonirina (ETU003080), initialement prévu jusqu’au 6 octobre 2026, nécessite une prolongation d’un mois.*  
> *Le projet ayant été défini et lancé vers la fin du mois de juillet, certains travaux de finalisation et de validation restent à réaliser.*  
> *Nous sommes donc favorables à une prolongation du stage jusqu’au 6 novembre 2026.*  
> *Nous vous remercions de bien vouloir nous indiquer les éventuelles démarches à effectuer pour formaliser cette prolongation. »*

### 1.2 Justification Temporelle de l'Estimation Agile (Todo List : 66 Jours Ouvrés)

Le document de planification de référence de l'entreprise (`docs/context/Todo list GLX.xlsx`, feuille *Estimation Agile info*) établit un backlog prévisionnel structuré en **6 Sprints totalisant rigoureusement 66 jours ouvrés de développement technique** :
- **Phase 0 : Immersion, Découverte Métier & Audit de l'Existant** (06/07/2026 au 24/07/2026) : 15 jours ouvrés.
- **Sprint I : Analyse, Modélisation & Socle PostgreSQL** : 14 jours ouvrés.
- **Sprint II : Sécurité IAM/RBAC & Dossier Salarié / Carrière** : 8 jours ouvrés.
- **Sprint III : Module Pointage & Suivi des Présences** : 10 jours ouvrés.
- **Sprint IV : Module Congés & Absences (FIFO & Décompte Fériés)** : 11 jours ouvrés.
- **Sprint V : Moteur de Paie, Déclarations Fiscales & STC** : 15 jours ouvrés.
- **Sprint VI : Tests d'intégration, recette et préparation de l'installation locale** : 8 jours ouvrés.

$$\text{Total Réalisation Technique} = 14 + 8 + 10 + 11 + 15 + 8 = \mathbf{66\text{ jours ouvrés}}$$
$$\text{Total Global Stage (Immersion + Dév)} = 15 + 66 = \mathbf{81\text{ jours ouvrés (du 06/07/2026 au 06/11/2026)}}$$

Au 06 octobre 2026 (52 jours ouvrés de dev écoulés), le projet se trouvait en cours d'achèvement du Sprint V (Paie). Les **14 jours ouvrés restants** pour finaliser la recette DAF, les tests de conformité légale et le déploiement justifient mathématiquement l'extension jusqu'au **06 novembre 2026**.

---

## 2. Profil de l'Entreprise d'Accueil & Environnement Opérationnel

### 2.1 Identité Juridique & Historique de GALAXAM
**GALAXAM Logistics SARL** est une société de droit privé malgache fondée en juillet 2001, cumulant un quart de siècle d'expertise dans les métiers du transit, de la douane et de la logistique intégrée à Madagascar :
- **Raison Sociale** : GALAXAM Logistics SARL
- **Identifiants Fiscaux et Légaux** :
  - **NIF** : `3 000 019 43 4`
  - **STAT** : `52 296 11 2001 0 00056`
  - **RCS** : `2001B66`
- **Siège Social** : 3e étage, Tour Forello, Tanjombato, Antananarivo (`TNR`)
- **Contact DAF / Direction** : `daf@galaxam.com` — Téléphone : `+261 32 12 131 01` / `+261 34 00 000 35`
- **Affiliation Professionnelle** : Membre agréé du **GPCAD** (Groupement Professionnel des Commissionnaires Agréés en Douane de Madagascar).
- **Réseau International de Correspondants** : Présence et partenariats actifs sur quatre continents :
  - *États-Unis* : MEBS Global Reach (New York)
  - *Europe / Scandinavie* : Blue Water Shipping (Copenhague, Danemark)
  - *Afrique Australe* : Intraspeed Ltd (Afrique du Sud)
  - *Asie* : Réseau de correspondants portuaires et aéroportuaires en Chine (Shenzhen, Guangzhou, Shanghai).

### 2.2 Métiers & Activités Opérationnelles
1. **Transit & Dédouanement (Customs Clearance)** : Traitement des déclarations en douane (système SYDONIA World / Tradenet), formalités d'inspection avant embarquement et mainlevée des marchandises à l'importation comme à l'exportation.
2. **Freight Forwarding (Fret Multimodal)** : Acheminement aérien (aéroport international d'Ivato TNR) et maritime (port de Toamasina TMM).
3. **Transport Intérieur & Distribution (Inland Haulage / Door-to-Door)** : Flotte terrestre et partenaires assurant la liaison sécurisée entre les ports maritimes, les magasins sous douane et les entrepôts intérieurs.
4. **Distribution du Dernier Kilomètre & Projet Phare Humanitaire 2026** :
   - GALAXAM est le prestataire logistique national adjudicataire pour la distribution des **MID** (**Moustiquaires Imprégnées d'Insecticide à Longue Durée d'Action**) vers l'ensemble des **CSB** (**Centres de Santé de Base**) de la Grande Île.
   - Cette opération logistique de santé publique mobilise une coordination humaine intense sur des zones enclavées, imposant une flexibilité extrême des agents de terrain.

### 2.3 Répartition Géographique des Sites
| Code Site | Nom & Emplacement | Horaires Officiels | Régime & Spécificités Opérationnelles |
|:---|:---|:---|:---|
| **`TNR`** | **Antananarivo — Siège**<br>Tour Forello, Tanjombato | **08h00–12h30 / 13h30–17h00** | Continu avec coupure méridienne de 1h (pause déjeuner). Pôle administratif, financier, direction et déclarants aéroport/route. |
| **`TMM`** | **Toamasina — Agence Portuaire**<br>Grand Port de l'Est | **08h00–12h00 / 14h00–18h00** | Coupure méridienne de 2h adaptée aux rythmes portuaires. Coordination quai, débarquement conteneurs, manutention. |
| **`TLE`** | **Toliara — Bureau Sud**<br>Antenne régionale | Selon vacations d'opérations | Opérations douanières et fret du littoral Sud-Ouest. |

---

## 3. Structure Humaine & Catégories Professionnelles

L'effectif opérationnel géré en direct dans le SIRH compte **environ 19 à 25 collaborateurs**, répartis au sein d'une structure organisationnelle sans département informatique propre (l'étudiant-développeur est intégré directement au sein du **Département Logistics**, sous la tutelle de la DAF/RH).

Le personnel se divise en deux catégories socioprofessionnelles aux réalités de travail radicalement opposées :

```mermaid
graph TD
    subgraph Population 1 : Personnel Cadre & Administratif
        DG[Direction Générale : M. RANDRASANA Eric]
        DAF[DAF & RH : Mme RALAMBOMANANA Tatamo]
        COMPTA[Chef Comptable & Contrôle Financier]
        OPE[Operations & Logistics Manager]
    end

    subgraph Population 2 : Personnel Operationnel de Terrain
        DEC[Chefs Déclarants & Aides-Déclarants Douane]
        PASS[Passeurs de pièces douanières & Coursiers]
        MAG[Responsables d'Entrepôt & Magasiniers]
        MANUT[Manutentionnaires & Chauffeurs-Livreurs]
        TRANS[Agents de Transmission & Agents Polyvalents]
        GARD[Gardiens de Site & Sécurité Physique]
    end

    Population 1 -->|Forfaitisés - Rémunération Mensuelle Stable - Dispense Pointage| REGIME_CADRE[Autonomie de Gestion]
    Population 2 -->|Badgeage Quotidien Strict - Heures Supplémentaires - Indemnités| REGIME_AGENT[Régime Horaire & Sujétions]
```

1. **Les Cadres et Dirigeants (Sédentaires)** : Forfaitisés mensuellement, autonomes dans l'organisation de leur temps (`dispense_pointage = TRUE`), sans heures supplémentaires récurrentes mais portant la responsabilité de la validation financière.
2. **Les Agents Opérationnels et Gens de Terrain** :
   - Soumis aux impératifs physiques du dédouanement et du fret : vacations nocturnes lors du déchargement de navires à Toamasina, départs à l'aube pour les convois de transport terrestre vers les CSB, travail le dimanche ou jours fériés lors des urgences douanières.
   - Cette population dépend directement de la valorisation de ses sujétions : **heures supplémentaires (HS 30% et 50%)**, **travail de nuit (22h00–05h00)**, **travail du dimanche**, **indemnités de repas** et **indemnités de déplacement**.

---

## 4. Diagnostic Critique de l'Existant (« Avant SIRH »)

Avant la conception et le déploiement de GALAXAM SIRH, l'entreprise se trouvait dans une situation critique consécutive à l'abandon contraint de son ancien logiciel de paie (dont les licences et l'historique étaient devenus inaccessibles). La gestion des ressources humaines était entièrement exécutée sur un ensemble de **classeurs Microsoft Excel disparates, non reliés et manipulés manuellement**.

Ce mode de gestion artisanal engendrait des défaillances systémiques :

### 4.1 Climat Social Dégradé & Contestation Salariale Récurrente
- **Opacité des Bulletins de Paie** : Les fiches de paie générées manuellement sous Excel ne fournissaient aucun détail ventilé sur les retenues opérées.
- **Sentiment de Salaires « Tronqués »** : Les collaborateurs constataient régulièrement des retenues pour retards ou absences qu'ils ne pouvaient vérifier, des acomptes déduits deux fois ou des heures supplémentaires de nuit oubliées.
- **Tensions Sociales DAF / Collaborateurs** : La DAF devait consacrer plusieurs journées chaque début de mois à traiter des réclamations individuelles houleuses, sans support d'audit opposable pour justifier les calculs.

### 4.2 Goulot d'Étranglement Mensuel pour la DAF
- La clôture mensuelle de la paie mobilisait la Direction Administrative et Financière pendant **5 à 7 jours ouvrés complets**.
- Ce temps était gaspillé à recopier à la main des totaux d'un classeur à l'autre :
  $$\text{Fiches de pointage papier/Excel} \longrightarrow \text{Calcul des heures} \longrightarrow \text{Tableau des acomptes} \longrightarrow \text{Grille de paie Excel} \longrightarrow \text{Reconfection des bulletins}$$
- Le risque d'erreur humaine par écrasement de cellule ou mauvaise formule Excel était permanent.

### 4.3 Déconnexion Temporelle entre Pointage, Congés et Paie
- Les congés payés étaient suivis sur une feuille libre, sans mise à jour automatique des droits acquis légaux (2,5 jours par mois de service effectif).
- Un collaborateur pouvait poser des congés sans que son solde réel ne soit vérifié, conduisant à des **soldes négatifs invisibles** ou à des régularisations arbitraires lors des départs.
- Les absences injustifiées saisies par les chefs de site n'étaient pas synchronisées avec les retenues sur salaire, entraînant des trop-perçus non recouvrables.

### 4.4 Risque Fiscal et Social Majeur (Contrôles CNaPS & Fiscaux)
- Les déclarations sociales obligatoires étaient préparées par consolidation manuelle :
  1. **IRSA (Impôt sur les Revenus Salariaux et Assimilés)** : Télédéclaration mensuelle obligatoire sur le portail **e-Hetra** de la Direction Générale des Impôts (DGI) avant le **15 du mois suivant**. Tout retard ou discordance expose l'entreprise à une pénalité fiscale automatique de 10% à 25% majorée d'intérêts moratoires.
  2. **CNaPS (Caisse Nationale de Prévoyance Sociale)** : Déclaration nominative des salaires cotisables et versement trimestriel des cotisations plafonnées à 8× le Salaire Minima d'Embauche (SME).
  3. **Médecine du Travail (OSTIE à Tana, OMSI en province)** : Calcul des cotisations d'adhésion interentreprises.
- L'Inspection du Travail de Madagascar exige la tenue d'un **Registre Général du Personnel** et d'un **Livre d'Émargement des Heures de Présence** infalsifiables (Arrêté n° 198-IGT). Des feuilles Excel modifiables à volonté constituaient une vulnérabilité juridique critique en cas de contentieux prud'homal.

---

## 5. Problématique Centrale du PFE & Objectifs de Recherche

### 5.1 Problématique Centrale
> **Comment concevoir et déployer une architecture logicielle SIRH centralisée, hautement disponible et sécurisée, capable d'automatiser le cycle complet de gestion du personnel (contrats, pointages hétérogènes multi-sites, congés légaux, rémunérations et obligations déclaratives malgaches), tout en restaurant la transparence sociale, l'auditabilité financière et la conformité stricte aux lois n° 2024-014 et 2014-038 à Madagascar ?**

### 5.2 Sous-Problèmes de Recherche & d'Ingénierie
1. **Intégrité & Découplage Temporel** : Comment historiser les évolutions de carrière (promotions, revalorisations, mutations de site) sans fausser rétroactivement les calculs de paie et de pointage des périodes passées ?
2. **Algorithmique des Sujétions Temporelles** : Comment calculer avec une précision à la minute les heures supplémentaires hebdomadaires (Loi 2024-014, Art. 109-110), les majorations de nuit (22h–05h) et du dimanche, tout en appliquant la compensation d'heures entre jours de la même semaine ?
3. **Modélisation Financière des Droits à Congés (FIFO)** : Comment implémenter le modèle canonique malgache (`OHATRA KAJY DROITS CONGES`) assurant l'acquisition à 2,5 j/mois, la valorisation au trentième ($T_{\text{conge}} = \text{Salaire}/30$) et la consommation par lots FIFO immuables sans solde négatif ?
4. **Scellement & Immutabilité des Données de Paie** : Comment garantir qu'une période de paie validée par la DAF devienne inviolable et reproductible à l'identique plusieurs années après son exécution ?
5. **Ergonomie & Adhésion Utilisateur** : Comment concevoir des interfaces React intuitives offrant aux agents de pointage et à la DAF une visibilité immédiate sur les anomalies de saisie et les alertes d'échéances ?

---

## 6. Cadre Légal et Réglementaire Malagasy Intégré

Le système ne se contente pas d'enregistrer des données : il encode de façon déterministe le corpus réglementaire malgache en vigueur :

### 6.1 Code du Travail ([Loi n° 2024-014 du 14 août 2024 portant Code du Travail](https://cnlegis.gov.mg/uploads/L2024-014-VF.pdf) & [Loi n° 2003-044](https://www.assemblee-nationale.mg/media/archives_textes/pl/2003-044/Loi-n-2003-044-finale-Portant-Code-du-Travail-1.pdf))
- **Âge légal d'embauche** : 16 ans révolus stricts (**Art. 100**).
- **Durée légale du travail** : **40 heures par semaine** (**Art. 109**), soit un diviseur mensuel officiel de **173,33 heures** ([Décret n° 72-226 du 6 juillet 1972](https://cnlegis.gov.mg/)).
- **Heures supplémentaires** (**Art. 110-112** & Décret n° 72-226) :
  - Majoration de **+30%** pour les 8 premières heures de dépassement hebdomadaire (de la 41e à la 48e heure).
  - Majoration de **+50%** pour toute heure effectuée au-delà de la 48e heure hebdomadaire.
- **Travail de Nuit** (**Art. 115**) : Tout travail accompli entre **22h00 et 05h00 du matin**, majoré de **+30%** en régime habituel.
- **Repos Hebdomadaire & Dimanche** (**Art. 120-121**) : Repos obligatoire de 24h consécutives le dimanche. Les heures travaillées le dimanche ouvrent droit à une majoration légale de **+40%**.
- **Jours Fériés Chômés et Payés** (**Art. 122-124**) : Travaillés, ils sont rémunérés avec une majoration légale de **+100%**.
- **Congés Payés** (**Art. 125-135**) :
  - Droit acquis : **2,5 jours calendaires par mois** de service effectif (30 jours par an).
  - Décompte calendaire : Exclusion des jours fériés chômés et payés tombant pendant le congé (**Art. 127**).
  - Prescription triennale : Les congés se prescrivent par **3 ans (36 mois / 90 jours)** (**Art. 129**). Le SIRH alerte la DAF sans purger unilatéralement les droits acquis.
  - Certificat de travail obligatoire à l'expiration du contrat (**Art. 42**).

### 6.2 Fiscalité Salariale — Barème IRSA 2026 (Loi de Finances 2026)
L'impôt sur les Revenus Salariaux et Assimilés (IRSA) est calculé par tranches progressives sur le Revenu Net Imposable (après déduction des cotisations sociales ouvrières) :

| Tranche de Revenu Net Imposable Mensuel (Ar) | Taux d'Imposition |
|:---|:---:|
| De 0 à 350 000 Ar | **0 %** |
| De 350 001 à 400 000 Ar | **5 %** |
| De 400 001 à 500 000 Ar | **10 %** |
| De 500 001 à 600 000 Ar | **15 %** |
| De 600 001 à 4 000 000 Ar | **20 %** |
| Au-delà de 4 000 000 Ar | **25 %** |

- **Minimum de perception IRSA** : **3 000 Ar** par mois (en vigueur depuis le 01/01/2026 selon la Loi de Finances 2026).
- **Réduction pour charge de famille** : **2 000 Ar** par enfant / personne à charge par mois.
- **Désactivation légale de l'ISI 5%** : La retenue générale d'ISI sur prestataires externes n'est plus prélevée par la paie salariale en 2026. La contrainte SQL `CHECK (soumis_isi_5 = FALSE)` protège la base contre toute application erronée.

### 6.3 Cotisations Sociales & Organismes Médicaux (Révision 2026)
- **Salaire Minima d'Embauche (SME)** :
  - Jusqu'au 28/02/2026 : 262 680 Ar.
  - **Depuis le 01/03/2026** : **300 000 Ar** (Décret présidentiel / Loi de Finances 2026).
- **CNaPS (Prévoyance Sociale)** :
  - Plafond mensuel cotisable : $8 \times \text{SME} = 8 \times 300\,000 = \mathbf{2\,400\,000\text{ Ar}}$.
  - Part Salariale (Ouvrière) : **1 %** (Retraite).
  - Part Patronale : **13 %** (Prestations familiales) + **8 %** (Retraite) + **1 %** (Accidents du travail) = **22 %**.
- **Médecine du Travail (OSTIE à Tana / OMSI / AMIT)** :
  - Part Salariale : **1 %** sur le salaire brut non plafonné.
  - Part Patronale : **5 %** (ou taux spécifique selon la filiale/site).
- **FMFP (Fonds Malgache de Formation Professionnelle)** : Cotisation patronale exclusive de **1 %** sur la masse salariale brute plafonnée.

---

## 7. Architecture Logicielle & Choix Technologiques Réels

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              GALAXAM SIRH — STACK TECHNIQUE                            │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                        │
│   CLIENT / FRONTEND                                                                    │
│   ├── Framework : React 19 avec Single Page Application (SPA)                          │
│   ├── Build Tool : Vite.js (HMR ultra-rapide, bundle optimisé)                         │
│   ├── Design System : Vanilla CSS tokens + Tailwind CSS v4 (palette Navy / Professionnelle)│
│   ├── Composants UI : Tableaux paginés, Modales transactionnelles, Lucide React Icons  │
│   └── Authentification UI : Axios Interceptors avec Refresh Token automatique HttpOnly │
│                                                                                        │
│   SERVEUR / BACKEND                                                                    │
│   ├── Langage & Runtime : Java 17 LTS (OpenSDK)                                        │
│   ├── Framework Applicatif : Spring Boot 4.1.0 (voir backend/pom.xml)                   │
│   ├── Architecture Métier : Monolithe modulaire orienté Domaine (DDD léger)            │
│   ├── Sécurité IAM : Authentification Stateless JWT HS512 (HMAC-SHA512)                │
│   ├── Accès aux Données : Couche Active Record JDBC pure (bean.ClassMere) + HikariCP   │
│   ├── Transactions & Concurrence : TransactionRunner avec SELECT ... FOR UPDATE        │
│   └── Moteur Documentaire : Apache POI (génération dynamique de contrats et fiches)   │
│                                                                                        │
│   BASE DE DONNÉES                                                                      │
│   ├── Système : PostgreSQL 15 (conteneur Docker local)                                         │
│   ├── Structure : 6 scripts canoniques séquentiels (01 à 06) sans ALTER circulaire     │
│   ├── Intégrité Temporelle : Extension btree_gist avec contraintes d'exclusion EXCLUDE  │
│   └── Traçabilité : Table immuable audit_log stockant les deltas JSONB                 │
│                                                                                        │
│   INFRASTRUCTURE & DÉPLOIEMENT                                                         │
│   ├── Conteneurisation : Dockerfile multi-stage build (Maven packaging + Eclipse Temurin)│
│   ├── Cible actuelle : installation locale sur un seul poste avec Docker Compose          │
│   └── Accès prévu sur le poste local : http://localhost:8088                    │
│                                                                                        │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Synthèse des 6 Modules Métier Canoniques

| N° | Module SIRH | Source SQL Principale | Rôle Métier & Innovations Déployées |
|:---:|:---|:---|:---|
| **01** | **Structure & Organisation** | `01_schema_core_securite.sql`<br>`02_schema_referentiels_rh.sql` | Modélisation des filiales (`GLX`), sites géographiques (`TNR`, `TMM`), départements (`LOG`), services et fonctions. Paramétrage dynamique versionné par date d'effet dans `parametre_global` et gestion du plancher d'arrivée par site. |
| **02** | **Sécurité, IAM & Traçabilité** | `01_schema_core_securite.sql` | Authentification JWT HS512 (clé $\ge 64$ octets), refresh tokens sécurisés en cookie `HttpOnly`, 2 rôles seedés (`ROLE_ADMIN`, `ROLE_AGENT_POINTAGE`), table d'audit `audit_log` en JSONB immuable. |
| **03** | **Employé, Contrat & Carrière** | `03_schema_employes_contrats.sql` | Dissociation tripartite : identité civile (`employe`), contrat juridique (`contrat_travail`) et affectation salariale (`employe_affectation_carriere`). Matrice de 17 événements gérée par `CarriereTransitionValidator`, verrou pessimiste et génération de contrats Word via Apache POI. |
| **04** | **Pointage & Présence** | `05_schema_pointage.sql` | Cycle de paie décalé du 26 au 25 du mois suivant. Découpage en tranches spatio-temporelles (`TemporalAttendanceEngine`), compensation hebdomadaire des heures, saisie journalière, régularisations d'anomalies et 4 tables de snapshots gelés. |
| **05** | **Congés & Absences** | `04_schema_conges.sql` | Moteur FIFO par lots de valorisation (`CongeLotValorisationService`), gestion unifiée des 2 flux (nouvel embauché avec carence 12 mois vs cutover DAF à date d'arrêté), décompte dynamique excluant les jours fériés chômés (Art. 127), blocage absolu du solde négatif (`RG-CON-05`), détection des collisions d'équipe (`CongeContinuiteService`), examen triennal DAF de prescription (Art. 129). |
| **06** | **Paie, Cotisations & STC** | `06_schema_paie.sql` | Moteur déterministe à 6 paliers : brut, cotisations CNaPS/Santé, base imposable, barème IRSA progressif 2026, acomptes/prêts, net à payer. Clôture irréversible, génération de bulletins PDF officiels et procédure légale de Solde de Tout Compte (STC). |

---

## 9. Cadre d'Audit, Méthodologie & Rédaction du Mémoire

Dans le document final du Mémoire de Fin d'Études destiné au jury universitaire de l'ITU, il convient d'adopter une **distinction académique stricte** entre :
1. **L'Analyse et la Conception Formelle** : Les modèles conceptuels, diagrammes de classes UML, modèles relationnels normalisés (3NF/BCNF) et diagrammes d'états-transitions.
2. **Les Réalisations Techniques Effectives** : Le code source Java 17 testé, les API REST documentées, les vues et contraintes PostgreSQL opérationnelles.
3. **La Validation par les Tests Automatisés** : La suite complète de tests unitaires et d'intégration JUnit 5 couvrant les règles métier critiques (calculs de paie, compensation d'heures, transitions de contrat, décompte de congés).
4. **Le Déploiement Opérationnel** : L'installation locale par Docker Compose reste à confirmer en conditions réelles d'utilisation par GALAXAM.
5. **Les Perspectives et Dette Technique Assumée** : Les points réservés pour de futurs développements (authentification biométrique directe par pointeuse physique ZKTeco, portail mobile pour les chauffeurs en déplacement).

---

## 10. Note de Validité Juridique et Technique

Le présent document reflète l'état technique exact et vérifié du projet en **octobre 2026**.  
Toute modification apportée aux barèmes fiscaux ou conventions sociales lors des futures Lois de Finances devra être répercutée dans les tables de configuration datée (`paie_config_legale` et `parametre_global`) sans altération du code applicatif compilé.
