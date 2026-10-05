import { useState, useEffect, useCallback } from 'react';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { getErrorMessage } from '@/api/apiClient';

export function usePeriodePaie() {
    // État local : périodes et retours d'action.
    const [periodes, setPeriodes] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [successMsg, setSuccessMsg] = useState(null);

    // Chargement et actions de workflow de période.
    const loadPeriodes = useCallback(async (options = {}) => {
        const { silent = false } = options;
        if (!silent) {
            setLoading(true);
            setError(null);
        }
        try {
            const data = await periodePaieService.getAllPeriodes();
            setPeriodes(data);
        } catch (err) {
            console.error('Erreur chargement des périodes:', err);
            if (!silent) setError("Impossible de charger les périodes de paie.");
        } finally {
            if (!silent) setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPeriodes();
    }, [loadPeriodes]);

    // Revalidation silencieuse automatique au focus de l'écran et sur événement applicatif
    useEffect(() => {
        const handleSilentRefresh = () => {
            loadPeriodes({ silent: true });
        };
        window.addEventListener('focus', handleSilentRefresh);
        window.addEventListener('sirh:period-updated', handleSilentRefresh);
        return () => {
            window.removeEventListener('focus', handleSilentRefresh);
            window.removeEventListener('sirh:period-updated', handleSilentRefresh);
        };
    }, [loadPeriodes]);

    const createPeriode = async (periodeData) => {
        setLoading(true);
        setError(null);
        setSuccessMsg(null);
        try {
            const resp = await periodePaieService.createPeriode(periodeData);
            if (resp.success) {
                setSuccessMsg(resp.message);
                await loadPeriodes();
                window.dispatchEvent(new CustomEvent('sirh:period-updated'));
                return true;
            } else {
                setError(resp.message);
                return false;
            }
        } catch (err) {
            const msg = getErrorMessage(err, "Impossible de créer la période de paie.");
            setError(msg);
            return false;
        } finally {
            setLoading(false);
        }
    };

    const executeCommand = async (periode, command, extra = {}) => {
        setLoading(true);
        setError(null);
        setSuccessMsg(null);
        try {
            const resp = await periodePaieService.executeCommand(periode.idPeriodePaie, command, {
                version: periode.version,
                ...extra
            });
            if (resp.decision === 'ALLOWED') {
                setSuccessMsg(resp.message);
                await loadPeriodes();
                window.dispatchEvent(new CustomEvent('sirh:period-updated', { detail: { periodId: periode.idPeriodePaie } }));
                return true;
            } else {
                setError(resp.message);
                return false;
            }
        } catch (err) {
            const msg = getErrorMessage(err, "Impossible de mettre à jour l'état de la période.");
            setError(msg);
            return false;
        } finally {
            setLoading(false);
        }
    };

    return {
        periodes,
        loading,
        error,
        successMsg,
        setError,
        setSuccessMsg,
        loadPeriodes,
        createPeriode,
        executeCommand
    };
}
