import { useState, useEffect, useCallback } from 'react';
import organisationService from '@/features/organization/api/organisationService';
import contratService from '@/features/employees/api/contratService';

// Cache en mémoire partagé avec horodatage
let cacheData = null;
let cacheTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // Expiration après 5 minutes

/**
 * Invalide le cache local des référentiels contrat/carrière
 * (À appeler par exemple après modification des référentiels dans l'admin)
 */
export function invalidateContractReferentiels() {
    cacheData = null;
    cacheTimestamp = 0;
}

/**
 * Hook d'accès aux référentiels RH pour les modales et formulaires de contrat/carrière
 * @param {boolean} enabled - Si false, ne déclenche pas le chargement tant que le composant est masqué
 * @param {number|string|null} excludeEmployeId - ID employé à exclure de la liste des responsables N+1
 */
export function useContractReferentiels(enabled = true, _excludeEmployeId = null) {
    const [referentiels, setReferentiels] = useState(cacheData || {
        filiales: [],
        sites: [],
        departements: [],
        services: [],
        fonctions: [],
        typesContrat: [],
        typesIntervenant: [],
        categoriesPro: [],
        modesPaie: [],
        typesEvenementRh: [],
    });

    const [loading, setLoading] = useState(!cacheData);
    const [error, setError] = useState(null);

    const fetchAll = useCallback(async (force = false) => {
        const isExpired = Date.now() - cacheTimestamp > CACHE_TTL_MS;

        // Si le cache est valide et qu'on ne force pas, on utilise la version mémoire
        if (cacheData && !isExpired && !force) {
            setReferentiels(cacheData);
            setLoading(false);
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const [
                filRes, siteRes, depRes, srvRes, fnRes,
                tcRes, tiRes, catRes, paieRes, tevRes
            ] = await Promise.all([
                organisationService.getFiliales(true),
                organisationService.getSites(true),
                organisationService.getDepartements(true),
                organisationService.getServices(true),
                organisationService.getFonctions(true),
                organisationService.getTypesContrat(true),
                organisationService.getTypesIntervenant(true),
                contratService.getCategoriesPro(true),
                contratService.getModesPaie(),
                contratService.getTypesEvenementRh(),
            ]);

            const isItemActive = (item) => item && item.estActif !== false && item.actif !== false;

            const newData = {
                filiales: (filRes || []).filter(isItemActive),
                sites: (siteRes || []).filter(isItemActive),
                departements: (depRes || []).filter(isItemActive),
                services: (srvRes || []).filter(isItemActive),
                fonctions: (fnRes || []).filter(isItemActive),
                typesContrat: (tcRes || []).filter(isItemActive),
                typesIntervenant: (tiRes || []).filter(isItemActive),
                categoriesPro: (catRes || []).filter(isItemActive),
                modesPaie: (paieRes || []).filter(isItemActive),
                typesEvenementRh: (tevRes || []).filter(isItemActive),
            };


            cacheData = newData;
            cacheTimestamp = Date.now();
            setReferentiels(newData);
        } catch (err) {
            console.error('Erreur lors du chargement des référentiels RH:', err);
            setError('Erreur lors du chargement des tables de référence.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (enabled) {
            fetchAll();
        }
    }, [enabled, fetchAll]);

    return {
        ...referentiels,
        responsables: [],
        loading,
        error,
        refresh: () => fetchAll(true),
    };
}
