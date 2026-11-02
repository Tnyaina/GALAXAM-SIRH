-- Le plafond historique de 10 % sur les avances employeur est supprimé.
-- La retenue reste bornée par le solde dû et le net disponible au calcul du bulletin.
ALTER TABLE public.paie_config_legale
    DROP CONSTRAINT IF EXISTS chk_plafond_avance_pct;

ALTER TABLE public.paie_config_legale
    DROP COLUMN IF EXISTS plafond_avance_employeur_pct;
