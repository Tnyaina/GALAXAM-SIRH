import { describe, it, expect } from 'vitest';
import {
    findActiveLeafId,
    hasActiveDescendant,
    normalizePath,
} from '@/utils/sidebarUtils';

const mockMenu = [
    {
        idMenu: 'DASHBOARD',
        libelle: 'Tableau de bord',
        href: '/dashboard',
        niveau: 1,
        sousMenus: [],
    },
    {
        idMenu: 'GESTION_PERSONNEL',
        libelle: 'Gestion du Personnel',
        href: null,
        niveau: 1,
        sousMenus: [
            { idMenu: 'PERS_EMPLOYES', libelle: 'Fiches Employés', href: '/employes', sousMenus: [] },
            { idMenu: 'PERS_CARRIERES', libelle: 'Contrats & Carrières', href: '/carrieres', sousMenus: [] },
        ],
    },
    {
        idMenu: 'POINTAGE',
        libelle: 'Pointage & Présences',
        href: '/pointages',
        niveau: 1,
        sousMenus: [
            { idMenu: 'POINTAGE_JOURNALIER', libelle: 'Saisie Quotidienne', href: '/pointages?tab=saisie', sousMenus: [] },
            { idMenu: 'POINTAGE_SUIVI', libelle: 'Suivi & Anomalies', href: '/pointages?tab=suivi', sousMenus: [] },
            { idMenu: 'POINTAGE_VALIDATION', libelle: 'Contrôle & Validation', href: '/pointages?tab=validation', sousMenus: [] },
        ],
    },
    {
        idMenu: 'PAIE_MODULE',
        libelle: 'Paie & Rémunération',
        href: null,
        niveau: 1,
        sousMenus: [
            { idMenu: 'PAIE_CENTRE', libelle: 'Centre de paie', href: '/paie', rang: 1, sousMenus: [] },
            { idMenu: 'PAIE_CAMPAGNE', libelle: 'Campagne active', href: '/paie/periodes', rang: 2, sousMenus: [] },
            { idMenu: 'PAIE_ACOMPTES', libelle: 'Acomptes & avances', href: '/paie/acomptes-avances', rang: 3, sousMenus: [] },
            { idMenu: 'PAIE_HISTORIQUE', libelle: 'Historique', href: '/paie/historique', rang: 4, sousMenus: [] },
            { idMenu: 'PAIE_DECLARATIONS', libelle: 'Déclarations & sorties', href: '/paie/declarations', rang: 5, sousMenus: [] },
            { idMenu: 'PAIE_PARAMETRES', libelle: 'Administration', href: '/admin/parametres?module=paie', rang: 6, sousMenus: [] },
        ],
    },
    {
        idMenu: 'ADMIN_MODULE',
        libelle: 'Administration & Paramétrage',
        href: null,
        niveau: 1,
        sousMenus: [
            { idMenu: 'ADMIN_SECURITE', libelle: 'Sécurité & Accès', href: '/admin/securite', sousMenus: [] },
        ],
    },
];

describe('sidebarUtils — Résolution d’état actif unique Sprint 2B', () => {
    it('normalise correctement les chemins avec ou sans trailing slash', () => {
        expect(normalizePath('/paie/')).toBe('/paie');
        expect(normalizePath('/paie')).toBe('/paie');
        expect(normalizePath('/paie?tab=periodes')).toBe('/paie');
        expect(normalizePath('')).toBe('/');
    });

    it('active Dashboard pour /dashboard', () => {
        const activeId = findActiveLeafId(mockMenu, { pathname: '/dashboard', search: '' });
        expect(activeId).toBe('DASHBOARD');
    });

    it('active PAIE_CENTRE pour la racine /paie', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '' })).toBe('PAIE_CENTRE');
    });

    it('active les bons sous-menus de paie selon l’espace ou l’étape spécifique', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/acomptes-avances', search: '' })).toBe('PAIE_ACOMPTES');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/historique', search: '' })).toBe('PAIE_HISTORIQUE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/declarations', search: '' })).toBe('PAIE_DECLARATIONS');
        expect(findActiveLeafId(mockMenu, { pathname: '/admin/parametres', search: '?module=paie' })).toBe('PAIE_PARAMETRES');

        // Étapes du stepper de campagne -> PAIE_CAMPAGNE via sous-chemin hiérarchique /paie/periodes
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/preparation', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/population', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/variables', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/calcul', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/controle', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/validation', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/sorties', search: '' })).toBe('PAIE_CAMPAGNE');
    });

    it('active PERS_EMPLOYES pour /employes et pour /employes/42 (sous-fiche)', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/employes', search: '' })).toBe('PERS_EMPLOYES');
        expect(findActiveLeafId(mockMenu, { pathname: '/employes/42', search: '' })).toBe('PERS_EMPLOYES');
    });

    it('distingue les onglets de pointage par query param sans faux doublons', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/pointages', search: '?tab=saisie' }))
            .toBe('POINTAGE_JOURNALIER');
        expect(findActiveLeafId(mockMenu, { pathname: '/pointages', search: '?tab=suivi' }))
            .toBe('POINTAGE_SUIVI');
        expect(findActiveLeafId(mockMenu, { pathname: '/pointages', search: '?tab=validation' }))
            .toBe('POINTAGE_VALIDATION');
    });

    it('hasActiveDescendant retourne vrai uniquement si la feuille active est dans la section', () => {
        const personnelNode = mockMenu.find((m) => m.idMenu === 'GESTION_PERSONNEL');
        expect(hasActiveDescendant(personnelNode, 'PERS_EMPLOYES')).toBe(true);
        expect(hasActiveDescendant(personnelNode, 'PAIE_MODULE')).toBe(false);
        expect(hasActiveDescendant(personnelNode, null)).toBe(false);
    });

    it('résout de manière générique le premier sous-menu par défaut (rang 1) pour les modules avec onglets sans query string', () => {
        // Module Congés : sur /conges pur, le premier sous-menu de rang 1 est sélectionné
        const congesMenu = [
            {
                idMenu: 'CONGES_MODULE',
                libelle: 'Congés & Absences',
                href: null,
                sousMenus: [
                    { idMenu: 'CONGES_SOLDES', libelle: 'Vue Soldes', href: '/conges?tab=soldes', rang: 1, sousMenus: [] },
                    { idMenu: 'CONGES_DEMANDES', libelle: 'Demandes', href: '/conges?tab=demandes', rang: 2, sousMenus: [] },
                ],
            },
        ];

        expect(findActiveLeafId(congesMenu, { pathname: '/conges', search: '' })).toBe('CONGES_SOLDES');
        expect(findActiveLeafId(congesMenu, { pathname: '/conges', search: '?tab=demandes' })).toBe('CONGES_DEMANDES');
    });

    it('résout fidèlement les hiérarchies profondes du module Administration', () => {
        const adminTree = [
            {
                idMenu: 'ADMIN_MODULE',
                libelle: 'Administration',
                href: null,
                sousMenus: [
                    { idMenu: 'ADMIN_ORGA', libelle: 'Organisation', href: '/admin/organisation', rang: 1, sousMenus: [] },
                    { idMenu: 'ADMIN_PARAMS', libelle: 'Paramètres Modules', href: '/admin/parametres', rang: 2, sousMenus: [] },
                    { idMenu: 'ADMIN_SEC', libelle: 'Sécurité', href: '/admin/securite', rang: 3, sousMenus: [] },
                ],
            },
        ];

        // Route exacte
        expect(findActiveLeafId(adminTree, { pathname: '/admin/organisation', search: '' })).toBe('ADMIN_ORGA');
        // Sous-route hiérarchique profonde (parent pathname le plus spécifique)
        expect(findActiveLeafId(adminTree, { pathname: '/admin/organisation/sites/3/editer', search: '' })).toBe('ADMIN_ORGA');
        expect(findActiveLeafId(adminTree, { pathname: '/admin/parametres/modules/pointage', search: '' })).toBe('ADMIN_PARAMS');
    });

    it('respecte strictement la priorité conceptuelle (href exact > pathname exact > parent path > fallback rang)', () => {
        const customMenu = [
            { idMenu: 'BASE_ROOT', libelle: 'Racine', href: '/espace', rang: 1, sousMenus: [] },
            { idMenu: 'BASE_TAB_1', libelle: 'Onglet 1', href: '/espace?vue=detail', rang: 2, sousMenus: [] },
            { idMenu: 'BASE_TAB_2', libelle: 'Onglet 2', href: '/espace?vue=stats', rang: 3, sousMenus: [] },
            { idMenu: 'BASE_SUB', libelle: 'Sous-page', href: '/espace/dossiers', rang: 4, sousMenus: [] },
        ];

        // 1. href exact + query exacte
        expect(findActiveLeafId(customMenu, { pathname: '/espace', search: '?vue=stats' })).toBe('BASE_TAB_2');
        // 2. pathname exact sans query
        expect(findActiveLeafId(customMenu, { pathname: '/espace', search: '' })).toBe('BASE_ROOT');
        // 3. parent pathname le plus spécifique
        expect(findActiveLeafId(customMenu, { pathname: '/espace/dossiers/123', search: '' })).toBe('BASE_SUB');
    });

    it('garantit qu’une seule feuille est active à un instant donné', () => {
        const urls = [
            { pathname: '/dashboard', search: '' },
            { pathname: '/paie/periodes/5/variables', search: '' },
            { pathname: '/pointages', search: '?tab=suivi' },
            { pathname: '/admin/securite', search: '' },
        ];

        for (const loc of urls) {
            const activeId = findActiveLeafId(mockMenu, loc);
            expect(activeId).toBeTruthy();
            expect(typeof activeId).toBe('string');
        }
    });
});
