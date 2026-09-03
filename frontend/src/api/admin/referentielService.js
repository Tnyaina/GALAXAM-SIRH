// referentielService.js
import apiClient from '@/api/apiClient';
import { notifyMenuChanged } from '@/utils/menuEvents';

// Cache mémoire pour les référentiels peu susceptibles de changer fréquemment
const cache = {
    modules: null,
    roles: null,
    menusFlat: null,
    menusTree: null,
};

/**
 * Invalide manuellement une ou plusieurs clés de cache.
 */
export const invalidateReferentielCache = (keys = ['modules', 'roles', 'menusFlat', 'menusTree']) => {
    keys.forEach(k => {
        cache[k] = null;
    });
};

const referentielService = {
    // ----------------------------------------------------
    // RÔLES
    // ----------------------------------------------------
    getRoles: async (forceRefresh = false) => {
        if (!forceRefresh && cache.roles) {
            return cache.roles;
        }
        const response = await apiClient.get('/referentiel/roles');
        cache.roles = response.data;
        return response.data;
    },

    getRoleById: async (id) => {
        const response = await apiClient.get(`/referentiel/roles/${id}`);
        return response.data;
    },

    createRole: async (data) => {
        const response = await apiClient.post('/referentiel/roles', data);
        invalidateReferentielCache(['roles']);
        notifyMenuChanged();
        return response.data;
    },

    updateRole: async (id, data) => {
        const response = await apiClient.put(`/referentiel/roles/${id}`, data);
        invalidateReferentielCache(['roles']);
        notifyMenuChanged();
        return response.data;
    },

    deactivateRole: async (id) => {
        const response = await apiClient.put(`/referentiel/roles/${id}/desactiver`);
        invalidateReferentielCache(['roles']);
        notifyMenuChanged();
        return response.data;
    },

    activateRole: async (id) => {
        const response = await apiClient.put(`/referentiel/roles/${id}/activer`);
        invalidateReferentielCache(['roles']);
        notifyMenuChanged();
        return response.data;
    },

    // ----------------------------------------------------
    // MODULES
    // ----------------------------------------------------
    getModules: async (forceRefresh = false) => {
        if (!forceRefresh && cache.modules) {
            return cache.modules;
        }
        const response = await apiClient.get('/referentiel/modules');
        cache.modules = response.data;
        return response.data;
    },

    // ----------------------------------------------------
    // MENUS
    // ----------------------------------------------------
    getMenusTree: async (forceRefresh = false) => {
        if (!forceRefresh && cache.menusTree) {
            return cache.menusTree;
        }
        const response = await apiClient.get('/referentiel/menus');
        cache.menusTree = response.data;
        return response.data;
    },

    getMenusFlat: async (forceRefresh = false) => {
        if (!forceRefresh && cache.menusFlat) {
            return cache.menusFlat;
        }
        const response = await apiClient.get('/referentiel/menus/flat');
        cache.menusFlat = response.data;
        return response.data;
    },

    createMenu: async (data) => {
        const response = await apiClient.post('/referentiel/menus', data);
        invalidateReferentielCache(['menusFlat', 'menusTree']);
        notifyMenuChanged();
        return response.data;
    },

    updateMenu: async (id, data) => {
        const response = await apiClient.put(`/referentiel/menus/${id}`, data);
        invalidateReferentielCache(['menusFlat', 'menusTree']);
        notifyMenuChanged();
        return response.data;
    },

    deleteMenu: async (id) => {
        const response = await apiClient.delete(`/referentiel/menus/${id}`);
        invalidateReferentielCache(['menusFlat', 'menusTree']);
        notifyMenuChanged();
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
        notifyMenuChanged();
        return response.data;
    },

    getRoleMenus: async (idRole) => {
        const response = await apiClient.get(`/referentiel/roles/${idRole}/menus`);
        return response.data;
    },

    updateRoleMenus: async (idRole, idMenus) => {
        const response = await apiClient.post(`/referentiel/roles/${idRole}/menus`, { idMenus });
        invalidateReferentielCache(['menusTree', 'menusFlat']);
        notifyMenuChanged();
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
        notifyMenuChanged();
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
