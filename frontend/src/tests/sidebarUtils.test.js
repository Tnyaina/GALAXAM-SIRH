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
            { idMenu: 'PAIE_CAMPAGNE', libelle: 'Campagne Active', href: '/paie?tab=campagne', sousMenus: [] },
            { idMenu: 'PAIE_VARIABLES', libelle: 'Variables & Primes', href: '/paie?tab=variables', sousMenus: [] },
            { idMenu: 'PAIE_REVUE', libelle: 'Revue des Bulletins', href: '/paie?tab=cockpit', sousMenus: [] },
            { idMenu: 'PAIE_PERIODES', libelle: 'Historique des Périodes', href: '/paie?tab=periodes', sousMenus: [] },
            { idMenu: 'PAIE_EXPORTS', libelle: 'Déclarations & Sorties', href: '/paie?tab=exports', sousMenus: [] },
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

    it('active PAIE_CAMPAGNE pour la racine /paie et pour /paie?tab=campagne', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '?tab=campagne' })).toBe('PAIE_CAMPAGNE');
    });

    it('active les bons sous-menus de paie selon l’onglet ou l’étape spécifique', () => {
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '?tab=variables' })).toBe('PAIE_VARIABLES');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '?tab=cockpit' })).toBe('PAIE_REVUE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '?tab=periodes' })).toBe('PAIE_PERIODES');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie', search: '?tab=exports' })).toBe('PAIE_EXPORTS');

        // Étapes du stepper de campagne
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/preparation', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/population', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/variables', search: '' })).toBe('PAIE_VARIABLES');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/calcul', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/controle', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/validation', search: '' })).toBe('PAIE_CAMPAGNE');
        expect(findActiveLeafId(mockMenu, { pathname: '/paie/periodes/10/sorties', search: '' })).toBe('PAIE_EXPORTS');
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
