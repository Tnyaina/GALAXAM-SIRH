# Evolutions du schema web de production

Les six fichiers `backend/conception/bdd/01...06` et `docker/init-local.sql` deviennent le point de départ figé de cette filière de déploiement. Le runner vérifie leur empreinte contre `docker/canonical.sha256` avant toute migration. Ils servent uniquement aux bases vierges. Pour toute évolution ultérieure, ajouter ici `0002_description.sql`, puis `0003_description.sql`, etc. Ne pas rejouer les six scripts sur une base existante et ne pas modifier une migration déjà livrée.

Le service `migrate` attend PostgreSQL, prend un verrou SQL, vérifie les empreintes et applique les migrations absentes dans une seule transaction. Il refuse une migration modifiée, un historique absent de la livraison ou une migration ajoutée hors ordre. Une erreur annule la transaction et empêche le démarrage normal du backend. L'historique est dans `public.galaxam_schema_history` et fait partie de la sauvegarde.

`0001` vérifie le point de départ canonique, sans prétendre convertir une ancienne base inconnue. Une base historique d'une autre version doit être comparée et adaptée par une migration dédiée avant adoption. Les noms des tables vérifiés ne constituent pas une certification complète du schéma.

Exemple de prochaine migration, à adapter au besoin réel :

```sql
ALTER TABLE public.employe ADD COLUMN nouvelle_reference varchar(100);
```

Règles de livraison :

- Un numéro unique à quatre chiffres, croissant ; nom ASCII sans espace.
- SQL PostgreSQL transactionnel uniquement. Pas de `BEGIN`, `COMMIT`, `ROLLBACK`, commandes psql, `CREATE DATABASE`, `VACUUM` ni `CREATE INDEX CONCURRENTLY` dans les fichiers. Le runner gère la transaction.
- Privilégier les ajouts compatibles : colonne nullable, remplissage contrôlé, puis contrainte dans une livraison ultérieure.
- Aucun `DROP`, `TRUNCATE`, réinitialisation ni remplacement de données métier sans procédure spécifique et validation de l'impact.
- Tester sur une copie restaurée de la base cible : données préservées, migration réussie, deuxième exécution sans modification, connexion et fonctions touchées.
- Les nouveaux déploiements appliquent également toutes les migrations après le schéma initial. Ne pas recopier leurs changements dans les schémas initiaux : cela les ferait appliquer deux fois.
- Une sauvegarde précède chaque mise à jour. Un retour arrière après migration demande généralement le code précédent **et** la sauvegarde correspondante ; ne pas inventer une migration inverse destructive.

Les migrations de version majeure PostgreSQL (15 vers 16, etc.) sont une opération distincte. Ne jamais simplement changer l'image tout en conservant le même répertoire de données.

Le parcours Windows natif utilise aussi ces fichiers via `scripts/windows-native/migrate-grh.ps1` et la même table `public.galaxam_schema_history`. Son script de mise à jour effectue la sauvegarde, arrête GRH, applique les migrations absentes, puis déploie le nouveau backend et le frontend. Tester la migration sur une copie de la base avant cette étape.
