import apiClient from '@/api/apiClient';

/**
 * Service API pour le module Paie & Rémunération GALAXAM SIRH
 */
export const paieService = {
    // --- Périodes ---
    getAllPeriodes: async () => {
        const response = await apiClient.get('/periodes');
        return response.data;
    },

    // --- Cockpit & Calculs ---
    getCockpitPeriode: async (idPeriode, params = {}) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/cockpit`, {
            params: { includeContext: false, ...params }
        });
        return response.data;
    },

    /**
     * Sprint 2B : Récupère le contexte enrichi d'une période, incluant :
     * - campaignSteps : liste ordonnée des étapes avec leur état (CURRENT, COMPLETED, BLOCKED...)
     * - currentStep : code de l'étape courante calculée par le backend
     * - recalculationRequired : indique si un recalcul est nécessaire
     * - availableActions : map des actions autorisées
     * Le frontend utilise ces données directement pour afficher le stepper sans logique métier.
     */
    getWorkflowContext: async (idPeriode) => {
        const response = await apiClient.get('/periodes/context', { params: { periodId: idPeriode } });
        return response.data;
    },

    calculerBulletinEmploye: async (idEmploye, idPeriodePaie, simulation = false) => {
        const response = await apiClient.post('paie/calculer-employe', {
            idEmploye,
            idPeriodePaie,
            simulation
        });
        return response.data;
    },

    calculerPaiePeriode: async (idPeriodePaie, simulation = false) => {
        const response = await apiClient.post('paie/calculer-periode', {
            idPeriodePaie,
            simulation
        });
        return response.data;
    },

    validerPaiePeriode: async (idPeriodePaie) => {
        const response = await apiClient.post('paie/valider-periode', { idPeriodePaie });
        return response.data;
    },

    // --- Bulletins ---
    getBulletinComplet: async (idBulletin) => {
        const response = await apiClient.get(`paie/bulletin/${idBulletin}`);
        return response.data;
    },

    getBulletinDocument: async (idBulletin, format) => {
        const response = await apiClient.get(`paie/bulletin/${idBulletin}/document/${format}`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        const filename = disposition.match(/filename="([^"]+)"/)?.[1] || `Bulletin_${idBulletin}.${format}`;
        return { blob: response.data, filename };
    },

    getGrandLivre: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/grand-livre`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        return {
            blob: response.data,
            filename: disposition.match(/filename="([^"]+)"/)?.[1] || `Grand_Livre_Paie_${idPeriode}.xlsx`,
        };
    },

    getIrsaExcel: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/irsa-excel`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        return {
            blob: response.data,
            filename: disposition.match(/filename="([^"]+)"/)?.[1] || `Etat_IRSA_${idPeriode}.xls`,
        };
    },

    getEtatSocialMensuel: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/etat-social`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        return {
            blob: response.data,
            filename: disposition.match(/filename="([^"]+)"/)?.[1] || `Etat_Social_Mensuel_${idPeriode}.xlsx`,
        };
    },

    getQuarterlyExportStatus: async (year, quarter) =>
        (await apiClient.get(`paie/trimestre/${year}/${quarter}/exports/status`)).data,

    getQuarterlyExport: async (year, quarter, type) => {
        const response = await apiClient.get(`paie/trimestre/${year}/${quarter}/export/${type}`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        const extension = type === 'fmfp' ? 'xls' : 'xlsx';
        return {
            blob: response.data,
            filename: disposition.match(/filename="([^"]+)"/)?.[1] || `${type.toUpperCase()}_T${quarter}_${year}.${extension}`,
        };
    },

    getAnnualPayrollRecap: async (year) => {
        const response = await apiClient.get(`paie/annee/${year}/export/recap-paie`, { responseType: 'blob' });
        const disposition = response.headers?.['content-disposition'] || '';
        return {
            blob: response.data,
            filename: disposition.match(/filename="([^"]+)"/)?.[1] || `Recap_Annuel_Paie_${year}.xlsx`,
        };
    },

    getBulletinCorrectionStatus: async (idBulletin) => {
        const response = await apiClient.get(`paie/bulletin/${idBulletin}/correction-status`);
        return response.data;
    },

    listerBulletinsPeriode: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/bulletins`);
        return response.data;
    },

    listerBulletinsEmploye: async (idEmploye) => {
        const response = await apiClient.get(`paie/employe/${idEmploye}/bulletins`);
        return response.data;
    },

    getSynthesePaieEmploye: async (idEmploye) => {
        const response = await apiClient.get(`paie/employe/${idEmploye}/synthese`);
        return response.data;
    },

    listerPretsEmploye: async (idEmploye) => {
        const response = await apiClient.get(`paie/employe/${idEmploye}/prets`);
        return response.data;
    },

    // --- Variables & Ajustements (Primes, Acomptes, Retenues) ---
    listerAjustements: async (idPeriode = null, idEmploye = null, domaine = null) => {
        const params = {};
        if (idPeriode) params.idPeriode = idPeriode;
        if (idEmploye) params.idEmploye = idEmploye;
        if (domaine) params.domaine = domaine;
        const response = await apiClient.get('paie/ajustements', { params });
        return response.data;
    },

    listerTypesMouvements: async (idPeriode = null) => {
        const response = await apiClient.get('paie/ajustements/types-mouvements', {
            params: idPeriode ? { idPeriode } : {}
        });
        return response.data;
    },

    getConfigurationCatalogue: async (params, signal) => (await apiClient.get('referentiel/types-mouvement-paie/configuration', { params, signal })).data,
    getParcoursConfiguration: async (signal) => (await apiClient.get('referentiel/types-mouvement-paie/configuration/parcours', { signal })).data,
    getCatalogueVariables: async () => (await apiClient.get('referentiel/types-mouvement-paie/catalogue')).data,
    creerVersionRubrique: async (id, payload) => (await apiClient.post(`paie/rubriques/${id}/versions`, payload)).data,
    creerRubriqueVariable: async (payload) => (await apiClient.post('paie/rubriques', payload)).data,
    modifierRubriqueVariable: async (id, payload) => (await apiClient.put(`paie/rubriques/${id}`, payload)).data,
    creerTypeVariable: async (payload) => (await apiClient.post('referentiel/types-mouvement-paie', payload)).data,
    modifierTypeVariable: async (id, payload) => (await apiClient.put(`referentiel/types-mouvement-paie/${id}`, payload)).data,

    creerAjustement: async (payload) => {
        const response = await apiClient.post('paie/ajustements', payload);
        return response.data;
    },
    deposerJustificatifFrais: async (id, file) => {
        const data = new FormData();
        data.append('file', file);
        return (await apiClient.post(`paie/ajustements/${id}/justificatif-frais`, data, {
            headers: { 'Content-Type': 'multipart/form-data' }
        })).data;
    },
    telechargerJustificatifFrais: async (id) => {
        const response = await apiClient.get(`paie/ajustements/${id}/justificatif-frais`, { responseType: 'blob' });
        return response.data;
    },
    syntheseAcomptesAvances: async () => (await apiClient.get('paie/ajustements/acomptes-avances/synthese')).data,
    listerImputationsAcompteAvance: async (id) => (await apiClient.get(`paie/ajustements/${id}/imputations`)).data,
    listerModesReglement: async () => (await apiClient.get('paie/ajustements/modes-reglement')).data,
    listerTousModesPaiement: async () => (await apiClient.get('paie/ajustements/modes-paiement')).data,
    modifierModePaiement: async (id, payload) => (await apiClient.put(`paie/ajustements/modes-paiement/${id}`, payload)).data,
    confirmerVersementAcompteAvance: async (id, payload) => (await apiClient.post(`paie/ajustements/${id}/versement`, payload)).data,

    /**
     * P1-01 : Exécute une commande de workflow sur un ajustement (APPROVE, REJECT, CANCEL).
     * Le backend décide de la transition autorisée et applique l'optimistic locking via expectedVersion.
     */
    executerCommandeAjustement: async (idAjustement, command, reason = null, expectedVersion = 1) => {
        const response = await apiClient.post(`paie/ajustements/${idAjustement}/commands`, {
            command,
            reason,
            expectedVersion
        });
        return response.data;
    },

    /**
     * @deprecated P1-01 : Utiliser executerCommandeAjustement avec une commande explicite.
     */
    changerStatutAjustement: async (idAjustement, statut, motif = null, expectedVersion = 1) => {
        const commandMap = {
            APPROUVE: 'APPROVE',
            APPROUVER: 'APPROVE',
            REJETE: 'REJECT',
            REJETER: 'REJECT',
            ANNULE: 'CANCEL',
            ANNULER: 'CANCEL'
        };
        const cmd = commandMap[statut?.toUpperCase()] || statut;
        return paieService.executerCommandeAjustement(idAjustement, cmd, motif, expectedVersion);
    },

    supprimerAjustement: async (idAjustement) => {
        const response = await apiClient.delete(`paie/ajustements/${idAjustement}`);
        return response.data;
    },

    // --- Exports Réglementaires & Bancaires ---
    obtenirDonneesSafi: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/safi-data`);
        return response.data;
    },

    obtenirDonneesDnts: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/dnts-data`);
        return response.data;
    },

    obtenirDonneesVirement: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/export/virement-data`);
        return response.data;
    },

    telechargerFichierExport: async (idPeriode, type) => {
        let endpoint;
        let defaultFilename;

        switch (type) {
            case 'safi-csv':
                endpoint = `paie/periode/${idPeriode}/export/safi`;
                defaultFilename = `etat_301_safi_periode_${idPeriode}.csv`;
                break;
            case 'safi-ods':
                endpoint = `paie/periode/${idPeriode}/export/safi-ods`;
                defaultFilename = `modele_declaration_irsa_safi_periode_${idPeriode}.ods`;
                break;
            case 'edi-isi':
                endpoint = `paie/periode/${idPeriode}/export/edi-isi`;
                defaultFilename = `annexe_edi_isi_periode_${idPeriode}.xml`;
                break;
            case 'dnts-csv':
                endpoint = `paie/periode/${idPeriode}/export/dnts`;
                defaultFilename = `dnts_cnaps_fmfp_periode_${idPeriode}.csv`;
                break;
            case 'virement-csv':
                endpoint = `paie/periode/${idPeriode}/export/virement`;
                defaultFilename = `ordre_virement_periode_${idPeriode}.csv`;
                break;
            default:
                throw new Error(`Type d'export non supporté : ${type}`);
        }

        const response = await apiClient.get(endpoint, {
            responseType: 'blob'
        });

        // Détection du nom de fichier depuis Content-Disposition
        let filename = defaultFilename;
        const disposition = response.headers['content-disposition'];
        if (disposition && disposition.includes('filename=')) {
            const matches = disposition.match(/filename="?([^"]+)"?/);
            if (matches && matches[1]) {
                filename = matches[1];
            }
        }

        // Déclenchement du téléchargement navigateur
        const blob = new Blob([response.data], { type: response.headers['content-type'] });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(downloadUrl);

        return true;
    },

    /**
     * Lister toutes les périodes de paie (Historique des campagnes, Déclarations, etc.)
     * Route backend canonique : GET /api/periodes
     */
    listerPeriodes: async () => {
        const response = await apiClient.get('/periodes');
        return response.data;
    },
};

export default paieService;
