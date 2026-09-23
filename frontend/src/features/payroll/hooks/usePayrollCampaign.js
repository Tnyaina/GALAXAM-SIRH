import { useState, useEffect, useCallback, useRef } from 'react';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * usePayrollCampaign — Hook central Sprint 2B.
 *
 * Charge et maintient à jour le contexte d'une campagne de paie (périodes + workflow context).
 * Toutes les pages de paie utilisent ce hook pour accéder aux données de campagne
 * sans dupliquer la logique de chargement.
 *
 * @param {number|null} requestedPeriodId  - ID de la période demandée (depuis l'URL ou null)
 * @returns {{
 *   periodes: Array,
 *   context: PayrollPeriodContextView|null,
 *   selectedPeriodeId: number|null,
 *   loading: boolean,
 *   error: string|null,
 *   refresh: Function,
 *   selectPeriode: Function,
 * }}
 */
export function usePayrollCampaign(requestedPeriodId = null) {
    // État local : périodes, contexte de campagne et requête active.
    const [periodes, setPeriodes] = useState([]);
    const [context, setContext] = useState(null);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const abortRef = useRef(null);

    // Chargement centralisé et protection contre les réponses obsolètes.
    const loadData = useCallback(async (periodeId) => {
        // Annuler une éventuelle requête précédente en vol
        if (abortRef.current) abortRef.current = false;
        const token = {};
        abortRef.current = token;

        setLoading(true);
        setError(null);

        try {
            const [allPeriodes, ctx] = await Promise.all([
                periodePaieService.getAllPeriodes(),
                periodePaieService.getContext(periodeId),
            ]);

            if (abortRef.current !== token) return; // réponse obsolète

            setPeriodes(Array.isArray(allPeriodes) ? allPeriodes : []);
            setContext(ctx || null);

            // Résoudre l'ID sélectionné depuis le contexte si pas fourni en URL
            const resolvedId = periodeId
                || ctx?.selectedPeriod?.id
                || ctx?.currentPeriod?.id
                || null;
            setSelectedPeriodeId(resolvedId);
        } catch (err) {
            if (abortRef.current !== token) return;
            setError(getErrorMessage(err, 'Erreur de chargement.'));
        } finally {
            if (abortRef.current === token) setLoading(false);
        }
    }, []);

    // Chargement initial + rechargement si la période change en URL
    useEffect(() => {
        loadData(requestedPeriodId);
        return () => { abortRef.current = false; };
    }, [requestedPeriodId, loadData]);

    // Lorsqu'aucune période ne couvre la date métier, le Centre de paie doit
    // néanmoins ouvrir la période la plus récente. Sans cela, une campagne
    // existante mais hors de la date du jour paraît avoir disparu.
    useEffect(() => {
        if (requestedPeriodId || selectedPeriodeId || periodes.length === 0) return;
        const latestId = periodes[0]?.idPeriodePaie || periodes[0]?.id;
        if (latestId) loadData(Number(latestId));
    }, [requestedPeriodId, selectedPeriodeId, periodes, loadData]);

    /** Changer de période et recharger le contexte */
    const selectPeriode = useCallback((newPeriodeId) => {
        setSelectedPeriodeId(newPeriodeId);
        loadData(newPeriodeId);
    }, [loadData]);

    /** Rechargement manuel (bouton Actualiser ou après une action) */
    const refresh = useCallback((overrideId = null) => {
        loadData(overrideId || selectedPeriodeId || requestedPeriodId);
    }, [selectedPeriodeId, requestedPeriodId, loadData]);

    return {
        periodes,
        context,
        selectedPeriodeId,
        loading,
        error,
        refresh,
        selectPeriode,
    };
}
