import { useState, useEffect, useCallback, useMemo } from 'react';
import paieConfigService from '@/features/administration/api/paieConfigService';
import { REGLES_FISCALES_IRSA_DEFAUT } from '@/constants/employeConstants';
import { calculateAge } from '@/utils/dateUtils';

// Cache mémoire pour éviter les requêtes répétées lors des changements d'onglets ou de formulaires
let memoryCachedConfig = null;
let memoryCacheTimestamp = 0;
const CACHE_TTL_MS = 5 * 60 * 1000; // 5 minutes

/**
 * Calcule l'âge révolu d'un individu à partir de sa date de naissance.
 */

/**
 * Évalue l'éligibilité fiscale d'un enfant aux déductions IRSA selon la Loi de Finances (LF).
 * 
 * Règles légales malgaches :
 * 1. Enfant avec handicap attesté : éligible sans condition d'âge.
 * 2. Enfant < âge limite par défaut (21 ans) : éligible au titre d'enfant à charge mineur.
 * 3. Enfant < âge limite étudiant (25 ans) avec statut étudiant/certificat : éligible.
 * 
 * @param {Object} enfant Données de l'enfant
 * @param {Object} config Configuration fiscale effective (LF)
 * @returns {{ eligible: boolean, motif: string, deductionMontant: number, age: number|null }}
 */
export function evaluateEnfantIrsaEligibility(enfant, config = {}) {
    const ageLimiteDefaut = config.ageLimiteDefaut ?? REGLES_FISCALES_IRSA_DEFAUT.AGE_LIMITE_DEFAUT;
    const ageLimiteEtudiant = config.ageLimiteEtudiant ?? REGLES_FISCALES_IRSA_DEFAUT.AGE_LIMITE_ETUDIANT;
    const deductionMontant = Number(config.deductionParCharge ?? REGLES_FISCALES_IRSA_DEFAUT.DEDUCTION_PAR_CHARGE_AR);

    if (!enfant?.dateNaissance) {
        return { eligible: false, motif: 'Date de naissance requise', deductionMontant: 0, age: null };
    }

    const age = calculateAge(enfant.dateNaissance);
    if (age === null) {
        return { eligible: false, motif: 'Date de naissance invalide', deductionMontant: 0, age: null };
    }

    // 1. Handicap attesté
    if (enfant.estHandicape) {
        return {
            eligible: true,
            motif: `Handicap attesté (${age} ans) — Déductible sans limite d'âge`,
            deductionMontant,
            age
        };
    }

    // 2. Âge < limite légale standard (21 ans)
    if (age < ageLimiteDefaut) {
        return {
            eligible: true,
            motif: `Enfant à charge mineur (${age} ans < ${ageLimiteDefaut} ans)`,
            deductionMontant,
            age
        };
    }

    // 3. Statut étudiant avec certificat (< 25 ans)
    if (enfant.estEtudiant && age < ageLimiteEtudiant) {
        const aCertificat = Boolean(enfant.certificatFourni);
        return {
            eligible: true,
            motif: aCertificat
                ? `Étudiant scolarisé certifié (${age} ans < ${ageLimiteEtudiant} ans)`
                : `Étudiant déclaré (${age} ans < ${ageLimiteEtudiant} ans) — Certificat requis`,
            deductionMontant,
            age
        };
    }

    // 4. Hors critères
    const limiteAppliquee = enfant.estEtudiant ? ageLimiteEtudiant : ageLimiteDefaut;
    return {
        eligible: false,
        motif: `Non éligible (${age} ans) — Âge limite : < ${limiteAppliquee} ans`,
        deductionMontant: 0,
        age
    };
}

/**
 * Hook personnalisé chargeant les règles fiscales dynamiques issues de la BDD (table paie_config_legale).
 * Intègre la Loi de Finances (LF) en vigueur avec fallback sécurisé.
 */
export function useFiscalConfig() {
    const [configData, setConfigData] = useState(() => memoryCachedConfig);
    const [loading, setLoading] = useState(!memoryCachedConfig);
    const [error, setError] = useState(null);

    const fetchConfig = useCallback(async (forceRefresh = false) => {
        const now = Date.now();
        if (!forceRefresh && memoryCachedConfig && now - memoryCacheTimestamp < CACHE_TTL_MS) {
            setConfigData(memoryCachedConfig);
            setLoading(false);
            return;
        }

        try {
            setLoading(true);
            const res = await paieConfigService.getConfigLegaleActive();
            memoryCachedConfig = res;
            memoryCacheTimestamp = now;
            setConfigData(res);
            setError(null);
        } catch (err) {
            // En cas d'erreur ou d'absence de réponse, on conserve le fallback résilient
            console.warn('[useFiscalConfig] Impossible de charger la Loi de Finances active, repli sur constantes de secours:', err);
            setError(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        fetchConfig();
    }, [fetchConfig]);

    const fiscalConfig = useMemo(() => {
        const deduction = configData?.deductionChargeFamille != null
            ? Number(configData.deductionChargeFamille)
            : REGLES_FISCALES_IRSA_DEFAUT.DEDUCTION_PAR_CHARGE_AR;

        const codeVersion = configData?.codeVersion || 'LF_2026';
        const libelle = configData?.libelle || `Loi de Finances (${codeVersion})`;

        return {
            deductionParCharge: deduction,
            codeVersion,
            libelle,
            ageLimiteDefaut: REGLES_FISCALES_IRSA_DEFAUT.AGE_LIMITE_DEFAUT,
            ageLimiteEtudiant: REGLES_FISCALES_IRSA_DEFAUT.AGE_LIMITE_ETUDIANT,
            ageMinLegalEmploye: REGLES_FISCALES_IRSA_DEFAUT.AGE_MIN_LEGAL_EMPLOYE,
            seuilExonerationIrsa: configData?.seuilExonerationIrsa ? Number(configData.seuilExonerationIrsa) : 350000,
            minimumPerceptionIrsa: configData?.minimumPerceptionIrsa ? Number(configData.minimumPerceptionIrsa) : 3000,
            isFromDatabase: Boolean(configData),
            rawConfig: configData,
            loading,
            error
        };
    }, [configData, loading, error]);

    const evaluate = useCallback((enfant) => {
        return evaluateEnfantIrsaEligibility(enfant, fiscalConfig);
    }, [fiscalConfig]);

    return {
        fiscalConfig,
        loading,
        error,
        refresh: () => fetchConfig(true),
        evaluateEligibility: evaluate
    };
}

export default useFiscalConfig;
