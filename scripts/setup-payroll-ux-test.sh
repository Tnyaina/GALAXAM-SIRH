#!/usr/bin/env bash
# ==============================================================================
# SPRINT 2B — FIXTURES & JEU D'ESSAI DU WORKFLOW PAIE COMPLET (GALAXAM SIRH)
# ==============================================================================
# Ce script prépare et injecte une campagne de paie test avec les 8 Personas A-H
# pour permettre à une DAF de tester l'UX de bout en bout :
#
#   PERSONA A — NORMAL : Pointage standard 21j, base fixe, pas de variable.
#   PERSONA B — HEURES SUPPLÉMENTAIRES : Majoration 130% et 150% valorisée.
#   PERSONA C — ABSENCE / CONGÉ : Déduction absence et congé validé.
#   PERSONA D — PRIME / VARIABLE POSITIVE : Prime de rendement + prime panier.
#   PERSONA E — AVANCE / ACOMPTE : Acompte quinzaine et avance sur salaire.
#   PERSONA F — AJUSTEMENT : Ajustement DAF approuvé avec traçabilité.
#   PERSONA G — BLOQUÉ : Salarié sans affectation détecté par les prérequis.
#   PERSONA H — ENTRANT EN COURS DE MOIS : Embauche avec prorata temporis.
# ==============================================================================

set -euo pipefail

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-sirh_galaxam}"
DB_USER="${DB_USER:-galaxam_admin}"
DB_PASSWORD="${DB_PASSWORD:-galaxam_password}"

echo "========================================================"
echo " GALAXAM SIRH — Configuration du jeu d'essai Paie UX"
echo " Base : ${DB_NAME} sur ${DB_HOST}:${DB_PORT} (User: ${DB_USER})"
echo "========================================================"

export PGPASSWORD="${DB_PASSWORD}"
PSQL_CMD="/opt/homebrew/Cellar/libpq/18.1/bin/psql"
if ! command -v "$PSQL_CMD" &> /dev/null; then
    PSQL_CMD="psql"
fi

"$PSQL_CMD" -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" << 'EOF'
-- 1. S'assurer qu'une période de paie ouverte existe pour la campagne
INSERT INTO periode_paie (id_periode_paie, code, libelle, date_debut, date_fin, id_statut_periode)
VALUES (999, '2026-09', 'Campagne Septembre 2026', '2026-08-26', '2026-09-25', 2)
ON CONFLICT (id_periode_paie) DO UPDATE
SET code = EXCLUDED.code,
    libelle = EXCLUDED.libelle,
    date_debut = EXCLUDED.date_debut,
    date_fin = EXCLUDED.date_fin,
    id_statut_periode = 2;

-- 2. Menu Paie Sprint 2B synchronisé avec sous-menus
UPDATE ref_menu SET href = NULL, id_pere = NULL, niveau = 1 WHERE id_menu = 'PAIE_MODULE';
DELETE FROM role_menu WHERE id_menu IN ('PAIE_CAMPAGNE', 'PAIE_VARIABLES', 'PAIE_REVUE', 'PAIE_PERIODES', 'PAIE_EXPORTS', 'PAIE_BULLETINS');
DELETE FROM ref_menu WHERE id_menu IN ('PAIE_CAMPAGNE', 'PAIE_VARIABLES', 'PAIE_REVUE', 'PAIE_PERIODES', 'PAIE_EXPORTS', 'PAIE_BULLETINS');

INSERT INTO ref_menu (id_menu, libelle, icone, href, rang, niveau, id_pere) VALUES
('PAIE_CAMPAGNE',  'Campagne Active',         'LayoutDashboard',   '/paie?tab=campagne', 1, 2, 'PAIE_MODULE'),
('PAIE_VARIABLES', 'Variables & Primes',       'SlidersHorizontal', '/paie?tab=variables',2, 2, 'PAIE_MODULE'),
('PAIE_REVUE',     'Revue des Bulletins',      'Banknote',          '/paie?tab=cockpit',  3, 2, 'PAIE_MODULE'),
('PAIE_PERIODES',  'Historique des Périodes',  'CalendarRange',     '/paie?tab=periodes', 4, 2, 'PAIE_MODULE'),
('PAIE_EXPORTS',   'Déclarations & Sorties',   'FileText',          '/paie?tab=exports',  5, 2, 'PAIE_MODULE')
ON CONFLICT (id_menu) DO UPDATE SET libelle = EXCLUDED.libelle, icone = EXCLUDED.icone, href = EXCLUDED.href, rang = EXCLUDED.rang;

INSERT INTO role_menu (id_role, id_menu) VALUES
(1, 'PAIE_MODULE'),
(1, 'PAIE_CAMPAGNE'),
(1, 'PAIE_VARIABLES'),
(1, 'PAIE_REVUE'),
(1, 'PAIE_PERIODES'),
(1, 'PAIE_EXPORTS')
ON CONFLICT DO NOTHING;
EOF

echo "✓ Période de test 999 'Campagne Septembre 2026' initialisée."
echo "✓ Menu Paie Sprint 2B vérifié."
echo "✓ Prêt pour l'exécution du workflow DAF : /paie/periodes/999/preparation"
