import { useState, useEffect, useCallback, useMemo } from 'react';
import organisationService from '@/api/admin/organisationService';
import contratService from '@/api/employe/contratService';
import employeService from '@/api/employe/employeService';

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
export function useContractReferentiels(enabled = true, excludeEmployeId = null) {
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
        employes: [],
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
                tcRes, tiRes, catRes, paieRes, tevRes, empRes
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
                employeService.getEmployes({ size: 10000, actif: true }),
            ]);

            const empArray = Array.isArray(empRes) ? empRes : (empRes?.content || []);

            const newData = {
                filiales: filRes || [],
                sites: siteRes || [],
                departements: depRes || [],
                services: srvRes || [],
                fonctions: fnRes || [],
                typesContrat: tcRes || [],
                typesIntervenant: tiRes || [],
                categoriesPro: catRes || [],
                modesPaie: paieRes || [],
                typesEvenementRh: tevRes || [],
                employes: empArray,
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

    // Filtrer la liste des responsables (N+1 potentiel) en excluant l'employé concerné (mémoïsé)
    const responsables = useMemo(() => {
        return excludeEmployeId
            ? referentiels.employes.filter(e => Number(e.idEmploye || e.id_employe) !== Number(excludeEmployeId))
            : referentiels.employes;
    }, [referentiels.employes, excludeEmployeId]);

    return {
        ...referentiels,
        responsables,
        loading,
        error,
        refresh: () => fetchAll(true),
    };
}
