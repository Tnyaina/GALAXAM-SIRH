import { useState, useEffect, useCallback } from 'react';
import periodePaieService from '@/services/periodePaieService';
import { getErrorMessage } from '@/api/apiClient';

export function usePeriodePaie() {
    const [periodes, setPeriodes] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [successMsg, setSuccessMsg] = useState(null);

    const loadPeriodes = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await periodePaieService.getAllPeriodes();
            setPeriodes(data);
        } catch (err) {
            console.error('Erreur chargement des périodes:', err);
            setError("Impossible de charger les périodes de paie.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadPeriodes();
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
