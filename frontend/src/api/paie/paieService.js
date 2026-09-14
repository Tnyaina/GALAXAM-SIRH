import apiClient from '@/api/apiClient';

/**
 * Service API pour le module Paie & Rémunération GALAXAM SIRH
 */
export const paieService = {
    // --- Cockpit & Calculs ---
    getCockpitPeriode: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/cockpit`);
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

    cloturerPaiePeriode: async (idPeriodePaie) => {
        const response = await apiClient.post('paie/cloturer-periode', { idPeriodePaie });
        return response.data;
    },

    rouvrirPaiePeriode: async (idPeriodePaie, motif) => {
        const response = await apiClient.post('paie/rouvrir-periode', { idPeriodePaie, motif });
        return response.data;
    },

    // --- Bulletins ---
    getBulletinComplet: async (idBulletin) => {
        const response = await apiClient.get(`paie/bulletin/${idBulletin}`);
        return response.data;
    },

    listerBulletinsPeriode: async (idPeriode) => {
        const response = await apiClient.get(`paie/periode/${idPeriode}/bulletins`);
        return response.data;
    },

    // --- Variables & Ajustements (Primes, Acomptes, Retenues) ---
    listerAjustements: async (idPeriode = null, idEmploye = null) => {
        const params = {};
        if (idPeriode) params.idPeriode = idPeriode;
        if (idEmploye) params.idEmploye = idEmploye;
        const response = await apiClient.get('paie/ajustements', { params });
        return response.data;
    },

    listerTypesMouvements: async () => {
        const response = await apiClient.get('paie/ajustements/types-mouvements');
        return response.data;
    },

    creerAjustement: async (payload) => {
        const response = await apiClient.post('paie/ajustements', payload);
        return response.data;
    },

    changerStatutAjustement: async (idAjustement, statut, motif = null) => {
        const response = await apiClient.patch(`paie/ajustements/${idAjustement}/statut`, {
            statut,
            motif
        });
        return response.data;
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
    }
};

export default paieService;
