-- Les badges demeurent intacts ; la correction signe le temps effectivement valorise.
ALTER TABLE public.pointage_jour
    ADD COLUMN minutes_correction_temps INTEGER NOT NULL DEFAULT 0
    CONSTRAINT ck_pointage_minutes_correction_temps
    CHECK (minutes_correction_temps BETWEEN -480 AND 480);

-- Capture du parametre de correction utilise au moment du gel de la paie.
ALTER TABLE public.pointage_snapshot_jour
    ADD COLUMN minutes_correction_temps INTEGER NOT NULL DEFAULT 0;
