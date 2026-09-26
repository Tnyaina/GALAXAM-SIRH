-- Corrige les doubles insertions historiques du même millésime. Un doublon
-- de borne +0,01 ou identique est supprimé uniquement si le reste de la
-- tranche correspond exactement à la tranche contiguë conservée.
DELETE FROM bareme_irsa doublon
USING bareme_irsa canonique
WHERE doublon.id_tranche > canonique.id_tranche
  AND doublon.id_config_legale = canonique.id_config_legale
  AND doublon.numero_tranche = canonique.numero_tranche
  AND doublon.date_debut = canonique.date_debut
  AND doublon.date_fin IS NOT DISTINCT FROM canonique.date_fin
  AND doublon.taux = canonique.taux
  AND doublon.tranche_max IS NOT DISTINCT FROM canonique.tranche_max
  AND doublon.tranche_min IN (canonique.tranche_min, canonique.tranche_min + 0.01)
  AND (
      canonique.numero_tranche = 1 AND canonique.tranche_min = 0
      OR EXISTS (
          SELECT 1 FROM bareme_irsa precedente
          WHERE precedente.id_config_legale = canonique.id_config_legale
            AND precedente.numero_tranche = canonique.numero_tranche - 1
            AND precedente.date_debut = canonique.date_debut
            AND precedente.tranche_max = canonique.tranche_min
      )
  );

-- Une variante de barème non reconnue doit rester visible et bloquer cette
-- contrainte : elle nécessite une décision explicite sur la bonne version.
CREATE UNIQUE INDEX IF NOT EXISTS uk_bareme_irsa_config_numero_debut
    ON bareme_irsa(id_config_legale, numero_tranche, date_debut);
