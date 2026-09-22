-- Le rôle Agent Pointage consulte le contrôle et la validation de sa période.
-- Cette habilitation n'accorde aucun droit de validation : les actions restent
-- contrôlées par role_permission.droit_validation et le backend.
BEGIN;

INSERT INTO role_menu (id_role, id_menu)
SELECT r.id_role, 'POINTAGE_VALIDATION'
FROM ref_role r
WHERE r.code_role = 'ROLE_AGENT_POINTAGE'
ON CONFLICT DO NOTHING;

COMMIT;
