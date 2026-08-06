// referentielService.js
import apiClient from './apiClient';

const referentielService = {
    // ----------------------------------------------------
    // RÔLES
    // ----------------------------------------------------
    getRoles: async () => {
        const response = await apiClient.get('/referentiel/roles');
        return response.data;
    },

    getRoleById: async (id) => {
        const response = await apiClient.get(`/referentiel/roles/${id}`);
        return response.data;
    },

    createRole: async (data) => {
        const response = await apiClient.post('/referentiel/roles', data);
        return response.data;
    },

    updateRole: async (id, data) => {
        const response = await apiClient.put(`/referentiel/roles/${id}`, data);
        return response.data;
    },

    deactivateRole: async (id) => {
        const response = await apiClient.put(`/referentiel/roles/${id}/desactiver`);
        return response.data;
    },

    activateRole: async (id) => {
        const response = await apiClient.put(`/referentiel/roles/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // MODULES
    // ----------------------------------------------------
    getModules: async () => {
        const response = await apiClient.get('/referentiel/modules');
        return response.data;
    },

    // ----------------------------------------------------
    // BANQUES (REF_BANQUE)
    // ----------------------------------------------------
    getBanques: async () => {
        const response = await apiClient.get('/referentiel/banques');
        return response.data;
    },

    // ----------------------------------------------------
    // MENUS
    // ----------------------------------------------------
    getMenusTree: async () => {
        const response = await apiClient.get('/referentiel/menus');
        return response.data;
    },

    getMenusFlat: async () => {
        const response = await apiClient.get('/referentiel/menus/flat');
        return response.data;
    },

    createMenu: async (data) => {
        const response = await apiClient.post('/referentiel/menus', data);
        return response.data;
    },

    updateMenu: async (id, data) => {
        const response = await apiClient.put(`/referentiel/menus/${id}`, data);
        return response.data;
    },

    deleteMenu: async (id) => {
        const response = await apiClient.delete(`/referentiel/menus/${id}`);
        return response.data;
    },

    // ----------------------------------------------------
    // HABILITATIONS (PERMISSIONS & MENUS PAR RÔLE)
    // ----------------------------------------------------
    getRolePermissions: async (idRole) => {
        const response = await apiClient.get(`/referentiel/roles/${idRole}/permissions`);
        return response.data;
    },

    updateRolePermissions: async (idRole, permissions) => {
        const response = await apiClient.post(`/referentiel/roles/${idRole}/permissions`, { permissions });
        return response.data;
    },

    getRoleMenus: async (idRole) => {
        const response = await apiClient.get(`/referentiel/roles/${idRole}/menus`);
        return response.data;
    },

    updateRoleMenus: async (idRole, idMenus) => {
        const response = await apiClient.post(`/referentiel/roles/${idRole}/menus`, { idMenus });
        return response.data;
    },

    // ----------------------------------------------------
    // UTILISATEURS
    // ----------------------------------------------------
    getUsers: async () => {
        const response = await apiClient.get('/securite/utilisateurs');
        return response.data;
    },

    getUserById: async (id) => {
        const response = await apiClient.get(`/securite/utilisateurs/${id}`);
        return response.data;
    },

    createUser: async (data) => {
        const response = await apiClient.post('/securite/utilisateurs', data);
        return response.data;
    },

    updateUser: async (id, data) => {
        const response = await apiClient.put(`/securite/utilisateurs/${id}`, data);
        return response.data;
    },

    deactivateUser: async (id, motif) => {
        const response = await apiClient.put(`/securite/utilisateurs/${id}/desactiver`, { motifDesactivation: motif });
        return response.data;
    },

    activateUser: async (id) => {
        const response = await apiClient.put(`/securite/utilisateurs/${id}/activer`);
        return response.data;
    }
};

export default referentielService;
