import { useState, useEffect, useCallback, useRef } from 'react';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
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
    const periodsRef = useRef([]);

    // Chargement centralisé et protection contre les réponses obsolètes.
    const loadData = useCallback(async (periodeId, { silent = false, reloadPeriods = false } = {}) => {
        // Annuler une éventuelle requête précédente en vol
        if (abortRef.current) abortRef.current = false;
        const token = {};
        abortRef.current = token;

        if (!silent) {
            setLoading(true);
            setError(null);
        }

        try {
            const [allPeriodes, ctx] = await Promise.all([
                !reloadPeriods && periodsRef.current.length > 0
                    ? Promise.resolve(periodsRef.current)
                    : periodePaieService.getPeriodOptions(),
                periodePaieService.getContext(periodeId),
            ]);

            if (abortRef.current !== token) return; // réponse obsolète

            const periodList = Array.isArray(allPeriodes) ? allPeriodes : [];

            // Résoudre l'ID sélectionné depuis le contexte si pas fourni en URL
            let resolvedId = periodeId
                || ctx?.selectedPeriod?.id
                || ctx?.currentPeriod?.id
                || null;

            let finalCtx = ctx;

            // Lorsqu'aucune période ne couvre la date métier et qu'aucun ID n'a été spécifié,
            // le backend renvoie selectedPeriod = null.
            // On résout directement la période la plus récente en une seule passe
            // pour éviter un rechargement différé en cascade (flash d'état vide).
            if (!resolvedId && periodList.length > 0) {
                const latestId = Number(periodList[0]?.idPeriodePaie || periodList[0]?.id);
                if (latestId) {
                    try {
                        finalCtx = await periodePaieService.getContext(latestId);
                        if (abortRef.current !== token) return;
                        resolvedId = latestId;
                    } catch {
                        // En cas d'erreur sur la période de fallback, conserver finalCtx
                    }
                }
            }

            periodsRef.current = periodList;
            setPeriodes(periodList);
            setContext(finalCtx);
            setSelectedPeriodeId(resolvedId);
        } catch (err) {
            if (abortRef.current !== token) return;
            if (!silent) setError(getErrorMessage(err, 'Erreur de chargement.'));
        } finally {
            if (abortRef.current === token && !silent) setLoading(false);
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
        return loadData(overrideId || selectedPeriodeId || requestedPeriodId, { reloadPeriods: true });
    }, [selectedPeriodeId, requestedPeriodId, loadData]);

    const refreshSilent = useCallback((overrideId = null, options = {}) => {
        return loadData(overrideId || selectedPeriodeId || requestedPeriodId, {
            silent: true,
            reloadPeriods: options.reloadPeriods ?? false,
        });
    }, [selectedPeriodeId, requestedPeriodId, loadData]);

    // Écoute des événements et polling d'arrière-plan pour actualisation silencieuse (comme les notifications)
    useEffect(() => {
        const handleSilentRefresh = () => {
            loadData(selectedPeriodeId || requestedPeriodId, { silent: true, reloadPeriods: true });
        };

        // Rafraîchissement automatique en arrière-plan toutes les 15 secondes si l'onglet est actif
        const timer = setInterval(() => {
            if (typeof document === 'undefined' || document.visibilityState === 'visible') {
                handleSilentRefresh();
            }
        }, 15000);

        const handleVisibilityChange = () => {
            if (document.visibilityState === 'visible') {
                handleSilentRefresh();
            }
        };

        window.addEventListener('sirh:period-updated', handleSilentRefresh);
        window.addEventListener('pointage-period-updated', handleSilentRefresh);
        window.addEventListener('refresh-notifications', handleSilentRefresh);
        window.addEventListener('focus', handleSilentRefresh);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            clearInterval(timer);
            window.removeEventListener('sirh:period-updated', handleSilentRefresh);
            window.removeEventListener('pointage-period-updated', handleSilentRefresh);
            window.removeEventListener('refresh-notifications', handleSilentRefresh);
            window.removeEventListener('focus', handleSilentRefresh);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [selectedPeriodeId, requestedPeriodId, loadData]);

    return {
        periodes,
        context,
        selectedPeriodeId,
        loading,
        error,
        refresh,
        refreshSilent,
        selectPeriode,
    };
}
