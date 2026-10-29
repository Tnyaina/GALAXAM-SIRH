-- Repair UTF-8 seed labels that were decoded as Windows-1252 during a Windows import.
-- Correct UTF-8 labels are left unchanged. Only seeded reference/configuration fields are targeted.
DO $$
DECLARE field record;
BEGIN
    FOR field IN
        SELECT * FROM (VALUES
            ('paie_config_legale', 'libelle'),
            ('paie_config_legale', 'source_reference'),
            ('paie_rubrique', 'description'),
            ('paie_rubrique', 'libelle'),
            ('parametre_global', 'description'),
            ('periode_paie', 'libelle'),
            ('ref_calendrier_ferie', 'libelle'),
            ('ref_categorie_professionnelle', 'description'),
            ('ref_filiale', 'adresse_siege'),
            ('ref_fonction', 'libelle'),
            ('ref_indemnite', 'libelle'),
            ('ref_menu', 'libelle'),
            ('ref_mode_calcul_paie', 'description'),
            ('ref_mode_paiement', 'libelle'),
            ('ref_module', 'libelle'),
            ('ref_motif_evenement_familial', 'libelle'),
            ('ref_motif_mouvement', 'libelle'),
            ('ref_motif_sortie', 'libelle'),
            ('ref_organisme_social', 'libelle'),
            ('ref_pool_conge_annuel', 'libelle'),
            ('ref_service', 'libelle'),
            ('ref_site', 'libelle'),
            ('ref_statut_demande_conge', 'libelle'),
            ('ref_statut_pointage_jour', 'libelle'),
            ('ref_statut_rh', 'libelle'),
            ('ref_taux_majoration', 'description'),
            ('ref_type_conge', 'libelle'),
            ('ref_type_contrat', 'libelle'),
            ('ref_type_cotisation', 'libelle'),
            ('ref_type_document', 'categorie'),
            ('ref_type_document', 'description'),
            ('ref_type_document', 'libelle'),
            ('ref_type_evenement_rh', 'libelle'),
            ('ref_type_intervenant', 'libelle'),
            ('ref_type_mouvement_paie', 'description'),
            ('ref_type_mouvement_paie', 'libelle')
        ) AS fields(table_name, column_name)
    LOOP
        EXECUTE format(
            'UPDATE public.%I SET %I = convert_from(convert_to(%I, ''WIN1252''), ''UTF8'') WHERE %I LIKE ''%%'' || chr(195) || ''%%''',
            field.table_name, field.column_name, field.column_name, field.column_name
        );
    END LOOP;
END $$;
