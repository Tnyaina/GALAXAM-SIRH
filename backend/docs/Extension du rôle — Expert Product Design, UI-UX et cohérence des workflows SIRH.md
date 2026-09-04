# AUDIT INTÉGRAL DU SIRH GALAXAM

## MODE : AUDIT UNIQUEMENT — AUCUNE MODIFICATION

Tu es chargé de réaliser un **audit intégral, professionnel et contradictoire** du projet GALAXAM SIRH.

Tu dois analyser **l'intégralité du repository**, notamment :

* Frontend React/Vite
* Backend Java/Spring Boot
* PostgreSQL
* scripts SQL
* migrations
* entités JPA
* DTO
* repositories
* services
* controllers
* sécurité
* API
* tests
* configuration
* Docker
* Docker Compose
* documentation
* fichiers Markdown
* schémas
* composants UI
* routes
* workflows
* tout autre fichier pertinent.

Tu dois également, si l'environnement le permet, **te connecter à la base PostgreSQL qui tourne dans Docker** afin de comparer :

```text
Scripts SQL
      ↕
Base PostgreSQL réellement exécutée
      ↕
Entités JPA
      ↕
Repositories
      ↕
Services
      ↕
API
      ↕
Frontend
      ↕
Documentation
      ↕
Processus RH
```

Tu dois analyser les deux :

1. **ce que le projet est censé faire ;**
2. **ce que le projet fait réellement.**

---

# 1. RÈGLE ABSOLUE : NE MODIFIER RIEN

Pendant cet audit :

**NE MODIFIE AUCUN FICHIER.**

Ne modifie pas :

* code ;
* SQL ;
* DB ;
* configuration ;
* Docker ;
* documentation ;
* architecture.

Tu es uniquement en **mode observation, analyse et recommandation**.

Même si tu détectes un problème évident, ne le corrige pas.

Documente-le.

Les modifications pourront être réalisées dans une phase ultérieure après validation.

---

# 2. TON PROFIL

Tu dois raisonner simultanément comme un comité composé de :

### Architecte logiciel senior

Expert :

* architecture applicative ;
* architecture en couches ;
* modularité ;
* Clean Architecture ;
* Hexagonal Architecture ;
* DDD ;
* SOLID ;
* design patterns ;
* maintenabilité ;
* scalabilité.

### Expert Java / Spring Boot

Expert :

* Spring Boot ;
* Spring Security ;
* JPA/Hibernate ;
* REST ;
* transactions ;
* validation ;
* DTO ;
* mapping ;
* exceptions ;
* performances.

### Expert React / Frontend

Expert :

* React ;
* Vite ;
* architecture frontend ;
* composants ;
* hooks ;
* routing ;
* state management ;
* formulaires ;
* API ;
* permissions ;
* performances.

### Expert PostgreSQL

Expert :

* modèle relationnel ;
* contraintes ;
* FK ;
* index ;
* transactions ;
* normalisation ;
* cardinalités ;
* historique ;
* temporalité ;
* performance.

### Consultant SIRH senior

Expert des domaines :

* dossier employé ;
* organisation ;
* sites ;
* départements ;
* services ;
* postes ;
* fonctions ;
* contrats ;
* affectations ;
* carrière ;
* rémunération ;
* documents RH ;
* absences ;
* congés ;
* pointage ;
* temps de travail ;
* paie ;
* workflows ;
* reporting ;
* historique.

### Expert RH

Tu dois raisonner comme un consultant RH et non comme un simple développeur.

Le logiciel doit représenter correctement les processus RH réels.

### Expert paie

La paie n'est pas encore développée.

Tu dois donc :

* analyser les prérequis ;
* vérifier que l'architecture actuelle pourra l'accueillir ;
* identifier les données à préparer ;
* proposer une architecture future ;
* documenter les concepts nécessaires.

Ne considère PAS l'absence du module paie comme un bug.

### Expert UX/UI et Product Design

Tu dois être capable d'analyser :

* UX ;
* UI ;
* architecture de l'information ;
* workflows ;
* design system ;
* formulaires ;
* tableaux ;
* navigation ;
* états ;
* feedback ;
* erreurs ;
* accessibilité ;
* productivité.

Mais :

> **NE PROPOSE PAS DE MODIFICATION ESTHÉTIQUE SANS VALEUR MÉTIER.**

Par exemple :

❌ « Installer une nouvelle police parce qu'elle est plus moderne. »

Ce type de recommandation n'est pas prioritaire et ne doit pas apparaître sauf justification réelle.

Le design existant peut être conservé s'il est cohérent.

---

# 3. CONTEXTE

Le projet est un véritable SIRH destiné à être utilisé par une entreprise malgache.

Il ne doit donc pas être évalué uniquement comme un PFE.

Évalue simultanément :

### Niveau A

Qualité académique / PFE.

### Niveau B

Prêt pour utilisation réelle chez GALAXAM.

### Niveau C

Potentiel d'évolution vers un produit SIRH professionnel.

---

# 4. PREMIÈRE PHASE — EXPLORATION COMPLÈTE

Avant toute conclusion, explore le repository.

Ne suppose pas que les fichiers importants sont uniquement ceux qui semblent évidents.

Identifie :

```text
Frontend
Backend
Database
SQL
Docker
Documentation
Tests
Configuration
Deployment
Security
Assets
Scripts
```

Construis une cartographie du projet.

---

# 5. DEUXIÈME PHASE — ANALYSE DE LA BASE RÉELLE

C'est une étape obligatoire.

## 5.1 Lire les scripts

Analyse :

* CREATE TABLE ;
* ALTER TABLE ;
* enums ;
* FK ;
* indexes ;
* constraints ;
* triggers ;
* views ;
* fonctions ;
* migrations ;
* données de référence.

## 5.2 Identifier comment PostgreSQL fonctionne dans Docker

Analyse :

* docker-compose ;
* Dockerfile ;
* variables ;
* ports ;
* volumes ;
* database name ;
* username ;
* migrations ;
* initialisation.

## 5.3 Si la base est accessible

Connecte-toi à PostgreSQL.

Inspecte réellement :

* tables ;
* colonnes ;
* types ;
* contraintes ;
* FK ;
* index ;
* enums ;
* relations ;
* données de référence ;
* quelques données réelles si disponibles.

Utilise la base uniquement pour **observer et auditer**.

Ne fais :

* aucun INSERT ;
* aucun UPDATE ;
* aucun DELETE ;
* aucun ALTER ;
* aucune migration ;
* aucune modification de structure.

Si la connexion à la DB est impossible :

> indique précisément pourquoi et poursuis l'audit à partir des scripts.

---

# 6. COMPARAISON DB SCRIPT ↔ DB RÉELLE

Construis une matrice :

| Élément | Script | DB réelle | JPA | Écart    |
| ------- | ------ | --------- | --- | -------- |
| table   | ✅      | ✅         | ✅   | aucun    |
| colonne | ✅      | ❌         | ✅   | problème |
| FK      | ✅      | ❌         | –   | problème |
| enum    | ...    | ...       | ... | ...      |

Recherche particulièrement :

* migrations non appliquées ;
* colonnes absentes ;
* tables obsolètes ;
* différences de type ;
* FK manquantes ;
* contraintes absentes ;
* enums divergents ;
* données de référence incohérentes.

---

# 7. DOCUMENTATION = SOURCE DE VÉRITÉ INITIALE

Les documentations fournies représentent la vision fonctionnelle du projet.

Mais elles peuvent contenir :

* contradictions ;
* erreurs ;
* décisions anciennes ;
* informations obsolètes ;
* ambiguïtés.

Ne considère donc pas automatiquement leur contenu comme correct.

Lorsque deux documents se contredisent :

```text
Documentation A
Documentation B
Code
Database
Métier
```

présente les différences.

Puis donne une recommandation.

Ne tranche pas silencieusement.

---

# 8. MATRICE DE TRAÇABILITÉ

Pour chaque fonctionnalité majeure :

```text
Besoin RH
 ↓
Documentation
 ↓
Workflow
 ↓
Frontend
 ↓
API
 ↓
Controller
 ↓
Service
 ↓
Entity
 ↓
Repository
 ↓
Database
 ↓
Tests
```

Marque :

* ✅ cohérent ;
* ⚠️ partiel ;
* ❌ absent ;
* 🔴 contradictoire.

Cette matrice est obligatoire.

---

# 9. AUDIT DU MODÈLE RH

Analyse notamment :

## Organisation

```text
Entreprise
 ↓
Site
 ↓
Département
 ↓
Service
 ↓
Poste / Fonction
```

Vérifie si ces concepts sont réellement distincts.

---

## Employé

Analyse :

* identité ;
* informations personnelles ;
* documents ;
* famille ;
* contacts ;
* statut ;
* historique.

---

## Contrat

Analyse :

* type ;
* CDD ;
* CDI ;
* stage ;
* dates ;
* période d'essai ;
* renouvellement ;
* rupture ;
* statut.

---

## Affectation

Analyse :

* site ;
* service ;
* poste ;
* fonction ;
* responsable ;
* période ;
* mutation ;
* historique.

---

## Carrière

Analyse :

* évolution ;
* mutation ;
* promotion ;
* reclassement ;
* rémunération ;
* historique.

---

## Congés

Analyse :

* types ;
* droits ;
* soldes ;
* demandes ;
* validation ;
* refus ;
* annulation ;
* calendrier ;
* historique.

---

## Absences

Analyse :

* justification ;
* statut ;
* durée ;
* impact pointage ;
* impact futur paie.

---

## Pointage

Analyse :

* entrée ;
* sortie ;
* présence ;
* absence ;
* retard ;
* heures supplémentaires ;
* anomalie ;
* validation ;
* verrouillage ;
* historique.

---

## Documents RH

Analyse :

* type ;
* rattachement ;
* version ;
* date ;
* expiration ;
* confidentialité ;
* stockage ;
* accès ;
* suppression ;
* audit.

---

# 10. CYCLE DE VIE DES ENTITÉS

Pour chaque entité importante, reconstruis son state machine.

Exemple :

```text
BROUILLON
 ↓
EN_VALIDATION
 ↓
ACTIF
 ↓
TERMINE
```

Détermine :

* états ;
* transitions ;
* acteurs ;
* permissions ;
* conditions ;
* actions ;
* conséquences.

Puis compare :

```text
Frontend
Backend
Database
Documentation
```

Ils doivent utiliser le même modèle logique.

---

# 11. AUDIT DES WORKFLOWS

Analyse les processus complets et non uniquement les pages.

Exemple :

```text
Création employé
 ↓
Contrat
 ↓
Affectation
 ↓
Activation
 ↓
Pointage
 ↓
Congés
 ↓
Absences
 ↓
Rémunération
 ↓
Paie
```

Pour chaque workflow :

* début ;
* prérequis ;
* étapes ;
* acteur ;
* validation ;
* résultat ;
* état suivant ;
* prochaine action.

Recherche :

* actions manquantes ;
* actions prématurées ;
* actions impossibles ;
* actions disponibles au mauvais rôle ;
* incohérences ;
* duplications ;
* ruptures UX.

---

# 12. AUDIT BACKEND COMPLET

Inspecte réellement le code.

Pour chaque domaine :

```text
Controller
 ↓
DTO
 ↓
Validation
 ↓
Service
 ↓
Domain
 ↓
Repository
 ↓
Database
```

Recherche :

* logique métier dans controllers ;
* logique métier dans repositories ;
* services trop gros ;
* duplication ;
* dépendances circulaires ;
* mauvaise gestion transactionnelle ;
* mauvaises relations JPA ;
* N+1 ;
* lazy/eager ;
* erreurs ;
* validation ;
* exceptions ;
* sécurité.

---

# 13. AUDIT FRONTEND COMPLET

Analyse :

* architecture ;
* pages ;
* composants ;
* hooks ;
* services ;
* routing ;
* state ;
* formulaires ;
* API ;
* permissions ;
* erreurs ;
* loading ;
* cache ;
* performances.

Recherche :

* composants trop gros ;
* logique métier dispersée ;
* duplication ;
* incohérences ;
* appels API dispersés ;
* problèmes de state ;
* mauvaise gestion des permissions.

---

# 14. AUDIT UX / UI / PRODUCT DESIGN

Ne juge pas simplement la beauté.

Pour chaque écran :

### Pourquoi l'utilisateur vient ici ?

### Quelle est son action principale ?

### Que doit-il comprendre ?

### Quelles actions sont disponibles ?

### Pourquoi sont-elles disponibles ?

### Que se passe-t-il après ?

### Quelle est la prochaine action ?

Analyse :

* navigation ;
* workflows ;
* terminologie ;
* formulaires ;
* tables ;
* filtres ;
* fiches ;
* modales ;
* notifications ;
* erreurs ;
* confirmations ;
* empty states ;
* loading ;
* accessibilité.

---

# 15. COHÉRENCE DES ACTIONS

Vérifie que les actions correspondent à l'état métier.

Exemple :

```text
Contrat BROUILLON

Modifier
Valider
Supprimer
```

Puis :

```text
Contrat ACTIF

Modifier librement ❌
Modifier avec historique ✅
Résilier
Renouveler
Créer mutation
```

L'interface doit refléter les règles métier.

---

# 16. DESIGN SYSTEM

Analyse :

* couleurs ;
* tokens ;
* boutons ;
* inputs ;
* tables ;
* badges ;
* modales ;
* tabs ;
* alertes ;
* toasts ;
* skeletons ;
* navigation.

Cherche les incohérences.

Mais ne cherche PAS à modifier le design pour le plaisir.

Le design actuel doit être conservé lorsqu'il est :

* cohérent ;
* lisible ;
* professionnel ;
* adapté au métier.

Priorité à :

> cohérence > productivité > accessibilité > esthétique.

---

# 17. TERMINOLOGIE

Crée un glossaire.

Vérifie la cohérence entre :

```text
RH
Documentation
DB
Backend
API
Frontend
UI
```

Une même notion doit conserver un vocabulaire stable.

Si plusieurs termes peuvent désigner des concepts différents, ne les fusionne pas sans analyse métier.

---

# 18. SÉCURITÉ

Audit complet :

* authentification ;
* autorisation ;
* RBAC ;
* permissions ;
* JWT ;
* cookies ;
* refresh token ;
* CORS ;
* CSRF ;
* XSS ;
* injections ;
* secrets ;
* documents ;
* logs ;
* données RH.

Accorde une attention maximale aux données personnelles et RH.

---

# 19. PERFORMANCE

Analyse :

### Backend

* SQL ;
* JPA ;
* N+1 ;
* pagination ;
* transactions ;
* cache.

### Frontend

* bundle ;
* rendering ;
* re-render ;
* API ;
* lazy loading.

### DB

* indexes ;
* jointures ;
* requêtes ;
* cardinalités.

Ne propose pas d'optimisation prématurée.

---

# 20. TESTS

Analyse :

* tests unitaires ;
* intégration ;
* API ;
* sécurité ;
* frontend ;
* workflows ;
* tests DB.

Identifie les domaines critiques sans couverture.

---

# 21. FUTURE PAIE

La paie n'est pas encore implémentée.

Tu dois donc analyser la capacité future du système à gérer :

```text
Contrat
 ↓
Rémunération
 ↓
Temps travaillé
 ↓
Absences
 ↓
Congés
 ↓
Retards
 ↓
Heures supplémentaires
 ↓
Primes
 ↓
Indemnités
 ↓
Retenues
 ↓
Cotisations
 ↓
Brut
 ↓
Net
 ↓
Bulletin
```

Propose une architecture future.

Fournis également une documentation générale de la paie ainsi qu'une analyse spécifique au contexte malgache.

Pour les règles légales malgaches, vérifie les sources actuelles lorsque l'accès Internet est disponible.

Distingue toujours :

* loi/réglementation ;
* pratique RH ;
* règle interne GALAXAM ;
* hypothèse.

---

# 22. BENCHMARK SIRH

Compare avec des solutions professionnelles existantes.

Par exemple :

* SAP SuccessFactors ;
* Workday ;
* Oracle HCM ;
* Sage HR ;
* Lucca ;
* Personio ;
* Factorial ;
* BambooHR ;
* Odoo.

Ne cherche PAS à copier.

Pour chaque pratique intéressante :

```text
Pratique observée
 ↓
Problème résolu
 ↓
Avantage
 ↓
Pertinence GALAXAM
 ↓
Adaptation nécessaire
 ↓
Priorité
```

Classe :

* À adopter ;
* À adapter ;
* À surveiller ;
* À ne pas implémenter.

---

# 23. RÈGLE CONTRE LE SUR-ENGINEERING

Ne recommande jamais :

* microservices ;
* CQRS ;
* event sourcing ;
* Kubernetes ;
* architecture distribuée ;
* messaging ;

uniquement parce que ces technologies existent.

Pour chaque recommandation architecturale :

1. problème ;
2. solution ;
3. bénéfice ;
4. coût ;
5. complexité ;
6. nécessité actuelle ;
7. nécessité future.

---

# 24. CLASSIFICATION DES PROBLÈMES

Utilise :

### 🔴 P0 — BLOQUANT

Sécurité, corruption de données ou fonctionnement gravement compromis.

### 🔴 P1 — CRITIQUE

Problème important pour la production.

### 🟠 P2 — IMPORTANT

À corriger pour atteindre un niveau professionnel.

### 🟡 P3 — AMÉLIORATION

Qualité ou UX.

### 🟢 P4 — NICE TO HAVE

Non essentiel.

---

# 25. DISTINGUER LES TYPES DE PROBLÈMES

Chaque problème doit être classé comme :

* BUG ;
* INCOHÉRENCE ;
* PROBLÈME MÉTIER ;
* PROBLÈME UX ;
* PROBLÈME ARCHITECTURAL ;
* DETTE TECHNIQUE ;
* RISQUE SÉCURITÉ ;
* PROBLÈME DE DONNÉES ;
* ÉVOLUTION PRODUIT ;
* DÉCISION MÉTIER.

Ne mélange jamais ces catégories.

---

# 26. FORMAT D'UN PROBLÈME

Pour chaque problème important :

```text
ID :
Type :
Priorité :
Domaine :

Localisation :
Fichier :
Classe :
Méthode :
Table :

Constat :

Preuve :

Pourquoi :

Impact métier :

Impact technique :

Risque futur :

Recommandation :

Solution proposée :

Effort :
Faible / Moyen / Élevé

Nécessaire pour :
PFE / GALAXAM / Produit professionnel
```

---

# 27. INCOHÉRENCES À TRANCHER

Crée une section dédiée.

Exemple :

```text
DEC-001

Question :
Un employé sans contrat peut-il être ACTIF ?

Documentation :
...

Database :
...

Backend :
...

Frontend :
...

Pratiques RH :
...

Options :
A.
B.
C.

Recommandation :
...

Conséquences :
...
```

Ne corrige jamais silencieusement une ambiguïté.

---

# 28. ARCHITECTURE CIBLE

Après l'audit, propose une architecture cible complète.

Elle doit couvrir :

```text
Frontend
   ↓
API
   ↓
Application
   ↓
Domain
   ↓
Persistence
   ↓
PostgreSQL
```

Décris :

* modules ;
* packages ;
* responsabilités ;
* dépendances ;
* DTO ;
* entities ;
* services ;
* repositories ;
* API ;
* sécurité ;
* DB ;
* tests.

L'architecture cible doit être **réaliste pour GALAXAM**.

---

# 29. MODÈLE MÉTIER CIBLE

Propose un modèle métier cible après avoir compris l'existant.

Montre les relations :

```text
Entreprise
 ↓
Site
 ↓
Organisation
 ↓
Employé
 ↓
Contrat
 ↓
Affectation
 ↓
Carrière
 ↓
Rémunération
```

et les relations avec :

```text
Documents
Congés
Absences
Pointage
Paie
```

Ne propose pas de nouvelles entités sans justification métier.

---

# 30. ARCHITECTURE UX CIBLE

Construis également une architecture UX cible.

Pour chaque module :

* objectif ;
* utilisateurs ;
* informations ;
* actions ;
* workflows ;
* états ;
* navigation ;
* relations avec les autres modules.

L'application doit donner l'impression d'être **un système unique et cohérent**, pas une collection de CRUD indépendants.

---

# 31. ROADMAP

Propose une roadmap :

### Phase 0

P0 / sécurité / intégrité.

### Phase 1

Stabilisation.

### Phase 2

Architecture.

### Phase 3

RH.

### Phase 4

Pointage / congés / absences.

### Phase 5

Paie.

### Phase 6

Reporting.

### Phase 7

Industrialisation.

Pour chaque phase :

* tâches ;
* dépendances ;
* priorité ;
* effort ;
* risque ;
* résultat attendu.

---

# 32. SCORE

Attribue une note /10 pour :

* architecture ;
* backend ;
* frontend ;
* database ;
* API ;
* sécurité ;
* RH ;
* contrats ;
* organisation ;
* carrière ;
* documents ;
* pointage ;
* congés ;
* absences ;
* future paie ;
* UX ;
* UI ;
* workflows ;
* performance ;
* tests ;
* maintenabilité ;
* documentation.

Puis donne :

## Score PFE

## Score GALAXAM Production

## Score Produit SIRH professionnel

Justifie chaque note.

---

# 33. FORCES

Identifie explicitement ce qui est déjà bon.

Ne produis pas uniquement des problèmes.

Explique :

* bonnes décisions ;
* bons patterns ;
* bonnes abstractions ;
* bonnes structures ;
* bonnes pratiques RH ;
* bonnes décisions UX ;
* bonnes décisions DB.

---

# 34. DETTE TECHNIQUE

Identifie :

* code mort ;
* duplication ;
* complexité ;
* abstractions inutiles ;
* hardcoding ;
* dépendances ;
* problèmes de structure ;
* documentation obsolète.

---

# 35. DOCUMENTATION MANQUANTE

Identifie les documentations nécessaires :

* Architecture ;
* modèle métier ;
* DB ;
* API ;
* sécurité ;
* RBAC ;
* règles RH ;
* contrats ;
* pointage ;
* congés ;
* absences ;
* paie ;
* règles malgaches ;
* déploiement ;
* backup ;
* tests.

---

# 36. RAPPORT FINAL

Le rapport final doit suivre exactement cette structure :

# 1. Executive Summary

# 2. Cartographie du repository

# 3. Architecture actuelle

# 4. État réel de la base PostgreSQL

# 5. Comparaison Scripts SQL ↔ DB réelle ↔ JPA

# 6. Compréhension fonctionnelle

# 7. Matrice de traçabilité

# 8. Audit modèle RH

# 9. Audit contrats / affectations / carrière

# 10. Audit congés / absences

# 11. Audit pointage

# 12. Audit future paie

# 13. Audit Backend

# 14. Audit Frontend

# 15. Audit API

# 16. Audit sécurité

# 17. Audit performance

# 18. Audit tests

# 19. Audit UX/UI

# 20. Audit workflows

# 21. Audit terminologie

# 22. Audit Design System

# 23. Benchmark SIRH

# 24. Incohérences

# 25. Décisions métier à prendre

# 26. Forces

# 27. Faiblesses

# 28. Dette technique

# 29. Architecture cible

# 30. Modèle métier cible

# 31. Architecture UX cible

# 32. Roadmap

# 33. Priorités P0 → P4

# 34. Scores

# 35. Documentation manquante

# 36. Conclusion

---

# 37. RÈGLE FINALE

Tu dois répondre à cette question :

> **"Si une équipe professionnelle devait reprendre ce repository aujourd'hui pour exploiter ce SIRH chez GALAXAM et le faire évoluer progressivement vers un véritable produit SIRH professionnel, qu'est-ce qui fonctionne réellement, qu'est-ce qui est incorrect, qu'est-ce qui est dangereux, qu'est-ce qui manque, quelles décisions métier doivent être prises et quelle architecture faut-il construire ?"**

Tu dois baser tes conclusions sur des preuves issues :

1. du code ;
2. de la documentation ;
3. des scripts ;
4. de la base PostgreSQL réelle lorsque accessible ;
5. des workflows ;
6. des règles métier ;
7. du benchmark professionnel.

Ne suppose pas.

Ne corrige pas silencieusement.

Ne sur-engineer pas.

Ne cherche pas à rendre le logiciel simplement "plus joli".

Cherche à rendre le système :

**correct → cohérent → maintenable → sécurisé → utilisable → professionnel → évolutif.**

Et surtout :

> **Audit d'abord. Décision ensuite. Modification seulement après validation explicite.**
