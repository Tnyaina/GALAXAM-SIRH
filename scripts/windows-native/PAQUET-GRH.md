# Paquet GRH pour le PC Windows configure

Ce paquet contient le backend et le frontend compiles, les six DDL et les migrations versionnees. Il ne contient ni code source, ni outils de developpement. Le PC cible doit deja posseder le service PostgreSQL GALAXAM, Java 17 et Node 22 installes globalement, ainsi que les variables utilisateur `SIRH_PROD_*` et `SIRH_ADMIN_*`. Si la base `sirh_galaxam` est absente ou vide, l'installateur demande le mot de passe PostgreSQL `postgres`, cree la base si besoin, charge les six DDL et applique les migrations. Le premier compte administrateur exige `SIRH_PROD_BOOTSTRAP_ADMIN_ENABLED=true`.

Un administrateur double-clique sur `Installer-GRH.bat` une seule fois. L'installation copie l'application dans `D:\GALAXAM-GRH`, cree les raccourcis **GRH** et **Arreter GRH** sur le Bureau, et planifie la sauvegarde quotidienne a 19 h dans `D:\GALAXAM-Sauvegardes\GRH-native`.

L'utilisateur utilise uniquement les deux raccourcis du Bureau. Les scripts techniques restent dans `D:\GALAXAM-GRH\scripts`. Les donnees televersees sont dans `D:\GALAXAM-GRH\uploads`. Les sauvegardes ne doivent pas etre effacees lors d'une mise a jour.

Ce paquet cible le PC actuellement configure. L'installation autonome sur un Windows vierge necessitera un autre installateur qui provisionne aussi Java, Node et PostgreSQL.
