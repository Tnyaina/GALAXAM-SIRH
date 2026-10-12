import { useState, useEffect, useCallback } from 'react';
import contratService from '@/features/employees/api/contratService';
import stcService from '@/features/payroll/api/stcService';

/**
 * Custom Hook React pour la gestion et le chargement centralisé du dossier carrière d'un employé.
 * Réduit le code dupliqué et gère automatiquement le rafraîchissement des données RH.
 */
export function useCarriere(idEmploye) {
    // État local : dossier de carrière complet et état de chargement.
    const [loading, setLoading] = useState(true);
    const [errorMsg, setErrorMsg] = useState('');

    const [brouillonWf, setBrouillonWf] = useState(null);
    const [contratActif, setContratActif] = useState(null);
    const [affectationActive, setAffectationActive] = useState(null);
    const [contratsList, setContratsList] = useState([]);
    const [workflowsList, setWorkflowsList] = useState([]);
    const [stcList, setStcList] = useState([]);
    const [etatEssai, setEtatEssai] = useState({
        isEssaiActif: false,
        isDernierJour: false,
        isEssaiEchu: false,
        estConfirme: false,
        estRenouvele: false,
        dateFinEssaiStr: null,
        dateFinEssai: null,        // alias de dateFinEssaiStr (cohérence frontend)
        dureeEssaiMois: null,
        canConfirm: false,
        canRenew: false,
        // Champs enrichis retournés par le backend
        aPeriodeEssai: false,      // true si une PE est EN_COURS sur ce contrat
        periodeActuelle: 0,        // numéro de la période active (1 = initiale, 2+ = renouvelée)
        nombreRenouvellements: 0,  // nombre de renouvellements déjà effectués
        maxRenouvellements: 1,     // valeur de MAX_RENOUVELLEMENTS_ESSAI (configurable)
    });

    const updateContrat = useCallback((contratMisAJour) => {
        if (!contratMisAJour?.idContrat) return;
        const mergeContrat = contrat => Number(contrat.idContrat) === Number(contratMisAJour.idContrat)
            ? { ...contrat, ...contratMisAJour }
            : contrat;

        setContratsList(previous => previous.map(mergeContrat));
        setContratActif(previous => previous ? mergeContrat(previous) : previous);
    }, []);

    // Chargement centralisé du dossier carrière.
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
                etatEssaiRes,
                stcRes
            ] = await Promise.allSettled([
                contratService.getBrouillonEnCours(idEmploye),
                contratService.getContratActif(idEmploye),
                contratService.getAffectationActive(idEmploye),
                contratService.getContratsParEmploye(idEmploye),
                contratService.getWorkflowsParEmploye(idEmploye),
                contratService.getEtatEssai(idEmploye),
                stcService.listerStcEmploye(idEmploye)
            ]);

            const brouillonData = brouillonRes.status === 'fulfilled' ? brouillonRes.value : null;
            setBrouillonWf(brouillonData && (brouillonData.isBrouillon ?? (brouillonData.codeStatutWorkflow === 'BROUILLON')) ? brouillonData : null);
            setContratActif(actifContratRes.status === 'fulfilled' ? actifContratRes.value : null);
            setAffectationActive(activeAffRes.status === 'fulfilled' ? activeAffRes.value : null);
            setContratsList(contratsRes.status === 'fulfilled' ? (contratsRes.value || []) : []);
            setWorkflowsList(workflowsRes.status === 'fulfilled' ? (workflowsRes.value || []) : []);
            setStcList(stcRes.status === 'fulfilled' ? (stcRes.value || []) : []);
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
        stcList,
        etatEssai,
        refreshData: loadCarriereData,
        updateContrat,
    };
}
export default useCarriere;
