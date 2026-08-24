import { useState, useEffect, useCallback } from 'react';
import contratService from '@/api/employe/contratService';

/**
 * Custom Hook React pour la gestion et le chargement centralisé du dossier carrière d'un employé.
 * Réduit le code dupliqué et gère automatiquement le rafraîchissement des données RH.
 */
export function useCarriere(idEmploye) {
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState('');

    const [brouillonWf, setBrouillonWf] = useState(null);
    const [contratActif, setContratActif] = useState(null);
    const [affectationActive, setAffectationActive] = useState(null);
    const [contratsList, setContratsList] = useState([]);
    const [workflowsList, setWorkflowsList] = useState([]);
    const [etatEssai, setEtatEssai] = useState({
        isEssaiActif: false,
        isDernierJour: false,
        isEssaiEchu: false,
        estConfirme: false,
        estRenouvele: false,
        dateFinEssaiStr: null,
        dureeEssaiMois: null,
        canConfirm: false,
        canRenew: false
    });

    const loadCarriereData = useCallback(async (opts = {}) => {
        if (!idEmploye) return;
        if (!opts?.silent) setLoading(true);
        setErrorMsg('');

        try {
            const [
                brouillonRes,
                actifContratRes,
                activeAffRes,
                contratsRes,
                workflowsRes,
                etatEssaiRes
            ] = await Promise.allSettled([
                contratService.getBrouillonEnCours(idEmploye),
                contratService.getContratActif(idEmploye),
                contratService.getAffectationActive(idEmploye),
                contratService.getContratsParEmploye(idEmploye),
                contratService.getWorkflowsParEmploye(idEmploye),
                contratService.getEtatEssai(idEmploye)
            ]);

            const brouillonData = brouillonRes.status === 'fulfilled' ? brouillonRes.value : null;
            setBrouillonWf(brouillonData && brouillonData.codeStatutWorkflow === 'BROUILLON' ? brouillonData : null);
            setContratActif(actifContratRes.status === 'fulfilled' ? actifContratRes.value : null);
            setAffectationActive(activeAffRes.status === 'fulfilled' ? activeAffRes.value : null);
            setContratsList(contratsRes.status === 'fulfilled' ? (contratsRes.value || []) : []);
            setWorkflowsList(workflowsRes.status === 'fulfilled' ? (workflowsRes.value || []) : []);
            if (etatEssaiRes.status === 'fulfilled' && etatEssaiRes.value) {
                setEtatEssai(etatEssaiRes.value);
            }
        } catch (err) {
            console.error('Erreur chargement dossier carrière:', err);
            setErrorMsg('Impossible de charger les données du dossier carrière.');
        } finally {
            if (!opts?.silent) setLoading(false);
        }
    }, [idEmploye]);

    useEffect(() => {
        loadCarriereData();
    }, [loadCarriereData]);

    return {
        loading,
        errorMsg,
        brouillonWf,
        contratActif,
        affectationActive,
        contratsList,
        workflowsList,
        etatEssai,
        refreshData: loadCarriereData
    };
}
export default useCarriere;
