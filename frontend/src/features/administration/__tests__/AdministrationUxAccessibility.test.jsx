// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import DocumentTypeManagementTab from '../components/reference-data/DocumentTypeManagementTab';
import ModesPaiementTab from '../components/payroll/ModesPaiementTab';
import UserManagementTab from '../components/security/UserManagementTab';
import RolesAndPermissionsTab from '../components/security/RolesAndPermissionsTab';
import JoursFeriesList from '../components/payroll/feries/JoursFeriesList';
import ParametresGlobauxTab from '../components/payroll/ParametresGlobauxTab';
import documentRhService from '@/features/employees/api/documentRhService';
import paieService from '@/features/payroll/api/paieService';
import referentielService from '@/features/administration/api/referentielService';
import paieConfigService from '@/features/administration/api/paieConfigService';

vi.mock('@/context/AuthContext', () => ({
    useAuth: () => ({ hasPermission: () => true })
}));

vi.mock('@/features/administration/api/paieConfigService', () => ({
    default: {
        getParametresGlobaux: vi.fn(),
        annulerParametreGlobalProgramme: vi.fn()
    }
}));

vi.mock('@/components/ui/feedback/Toast', () => ({
    useToast: () => ({
        toast: vi.fn(),
        success: vi.fn(),
        error: vi.fn(),
        warning: vi.fn(),
        info: vi.fn()
    })
}));

vi.mock('@/hooks/useMenu', () => ({
    useMenu: () => ({ refreshMenu: vi.fn() })
}));

let currentSearchParams = new URLSearchParams();
vi.mock('react-router-dom', () => ({
    useSearchParams: () => [currentSearchParams, (updater) => {
        if (typeof updater === 'function') {
            currentSearchParams = updater(currentSearchParams);
        } else {
            currentSearchParams = updater;
        }
    }]
}));

vi.mock('@/features/employees/api/documentRhService', () => ({
    default: {
        getTypesDocument: vi.fn()
    }
}));

vi.mock('@/features/payroll/api/paieService', () => ({
    default: {
        listerTousModesPaiement: vi.fn()
    }
}));

vi.mock('@/features/administration/api/referentielService', () => ({
    default: {
        getUsers: vi.fn(),
        getRoles: vi.fn(),
        getModules: vi.fn(),
        getMenusTree: vi.fn(),
        getRolePermissions: vi.fn(),
        getRoleMenus: vi.fn()
    }
}));

describe('Administration UX & Accessibilité (WCAG AA & Hiérarchie Erreurs)', () => {
    let container;
    let root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
        vi.clearAllMocks();
    });

    it('DocumentTypeManagementTab : EmptyState focal quand aucun type n\'est présent', async () => {
        documentRhService.getTypesDocument.mockResolvedValue([]);

        await act(async () => {
            root.render(<DocumentTypeManagementTab />);
        });

        const buttons = Array.from(container.querySelectorAll('button'));
        const headerNewBtn = buttons.find(b => b.textContent.trim() === 'Nouveau Type');
        expect(headerNewBtn).toBeUndefined();

        const emptyAction = buttons.find(b => b.textContent.includes('Créer un type de document'));
        expect(emptyAction).not.toBeUndefined();
    });

    it('DocumentTypeManagementTab : boutons d\'actions conformes aux cibles tactiles WCAG AA (44x44px)', async () => {
        documentRhService.getTypesDocument.mockResolvedValue([
            { idTypeDocument: 1, libelle: 'CIN', categorie: 'Identité', obligatoire: true, actif: true }
        ]);

        await act(async () => {
            root.render(<DocumentTypeManagementTab />);
        });

        const actionButtons = Array.from(container.querySelectorAll('td button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('ModesPaiementTab : boutons d\'actions conformes aux cibles tactiles WCAG AA (44x44px)', async () => {
        paieService.listerTousModesPaiement.mockResolvedValue([
            { idModePaiement: 1, code: 'VIREMENT', libelle: 'Virement bancaire', actif: true, exigeRib: true }
        ]);

        await act(async () => {
            root.render(<ModesPaiementTab />);
        });

        const actionButtons = Array.from(container.querySelectorAll('td button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('UserManagementTab : boutons d\'actions conformes aux cibles tactiles WCAG AA (44x44px)', async () => {
        referentielService.getUsers.mockResolvedValue([
            { idUtilisateur: 1, identifiant: 'jean.dupont', roleLibelle: 'Admin', estActif: true }
        ]);
        referentielService.getRoles.mockResolvedValue([
            { idRole: 1, codeRole: 'ADMIN', libelle: 'Administrateur', estActif: true }
        ]);

        await act(async () => {
            root.render(<UserManagementTab />);
        });

        const actionButtons = Array.from(container.querySelectorAll('td button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('UserManagementTab : utilise SearchSelect avec recherche pour la sélection de Rôle et Habilitations', async () => {
        referentielService.getUsers.mockResolvedValue([]);
        referentielService.getRoles.mockResolvedValue([
            { idRole: 1, codeRole: 'ROLE_ADMIN', libelle: 'Administrateur', estActif: true },
            { idRole: 2, codeRole: 'ROLE_AGENT_POINTAGE', libelle: 'Agent de Pointage', estActif: true }
        ]);

        await act(async () => {
            root.render(<UserManagementTab />);
        });

        // Ouvrir la modale nouvel utilisateur
        const newBtn = Array.from(container.querySelectorAll('button')).find(b => b.textContent.includes('Nouvel Utilisateur'));
        expect(newBtn).toBeTruthy();
        await act(async () => {
            newBtn.click();
        });

        // Vérifier la présence du SearchSelect pour Rôle et Habilitations
        const searchSelectBtn = document.body.querySelector('button[aria-required="true"]');
        expect(searchSelectBtn).toBeTruthy();
        expect(searchSelectBtn.textContent).toContain('-- Sélectionner un rôle --');

        // Ouvrir le menu déroulant
        await act(async () => {
            searchSelectBtn.click();
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));
        });

        // Vérifier l'input de filtre
        const filterInput = document.body.querySelector('input[placeholder="Filtrer..."]');
        expect(filterInput).toBeTruthy();

        // Sélectionner le rôle Agent de Pointage
        const roleOption = Array.from(document.body.querySelectorAll('button')).find(b => b.textContent.includes('Agent de Pointage'));
        expect(roleOption).toBeTruthy();
        await act(async () => {
            roleOption.click();
        });

        // Vérifier l'affichage du libellé et du code rôle
        expect(searchSelectBtn.textContent).toContain('Agent de Pointage');
        expect(searchSelectBtn.textContent).toContain('ROLE_AGENT_POINTAGE');
    });

    it('RolesAndPermissionsTab : boutons d\'actions conformes aux cibles tactiles WCAG AA (44x44px)', async () => {
        referentielService.getRoles.mockResolvedValue([
            { idRole: 1, codeRole: 'ADMIN', libelle: 'Administrateur', estActif: true }
        ]);
        referentielService.getModules.mockResolvedValue([]);
        referentielService.getMenusTree.mockResolvedValue([]);
        referentielService.getRolePermissions.mockResolvedValue([]);
        referentielService.getRoleMenus.mockResolvedValue([]);

        await act(async () => {
            root.render(<RolesAndPermissionsTab />);
        });

        const actionButtons = Array.from(container.querySelectorAll('button[aria-label]'));
        expect(actionButtons.length).toBeGreaterThanOrEqual(1);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('JoursFeriesList : masque le bouton d\'en-tête quand vide (EmptyState focal) et cibles tactiles WCAG AA', async () => {
        await act(async () => {
            root.render(
                <JoursFeriesList
                    feries={[]}
                    selectedYear="2026"
                    onOpenCreate={() => {}}
                />
            );
        });

        // En-tête : pas de bouton "Ajouter un Jour Férié"
        const headerButtons = Array.from(container.querySelectorAll('.flex.justify-between button'));
        expect(headerButtons.length).toBe(0);

        // EmptyState affiche l'action
        const emptyStateBtn = container.querySelector('button');
        expect(emptyStateBtn).not.toBeNull();
        expect(emptyStateBtn.textContent).toContain('Ajouter un Jour Férié');

        // Avec données : boutons d'action conformes
        await act(async () => {
            root.render(
                <JoursFeriesList
                    feries={[{ idFerie: 1, dateFerie: '2026-06-26', libelle: 'Indépendance', estChome: true, estActif: true }]}
                    selectedYear="2026"
                    onOpenCreate={() => {}}
                    onOpenEdit={() => {}}
                    onToggleStatus={() => {}}
                />
            );
        });

        const actionButtons = Array.from(container.querySelectorAll('td button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('ParametresGlobauxTab : affiche le bouton Annuler et déclenche la confirmation pour un paramètre PROGRAMME', async () => {
        currentSearchParams = new URLSearchParams({ pg_all_statut: 'PROGRAMME' });
        const programmedParam = {
            idParametre: 42,
            codeParametre: 'MAX_RENOUVELLEMENTS_ESSAI',
            valeur: '3',
            categorie: 'CONTRAT',
            dateDebut: '2028-01-01',
            dateFin: null,
            statutTemporel: 'PROGRAMME',
            estActif: true,
            estDesactivable: true,
            peutModifier: true,
            peutVersionner: false
        };

        paieConfigService.getParametresGlobaux.mockResolvedValue([programmedParam]);
        paieConfigService.annulerParametreGlobalProgramme.mockResolvedValue({ success: true });

        await act(async () => {
            root.render(<ParametresGlobauxTab />);
        });

        // Le bouton Annuler doit être présent pour ce paramètre programmé
        const buttons = Array.from(container.querySelectorAll('button'));
        const cancelBtn = buttons.find(b => b.textContent.trim() === 'Annuler');
        expect(cancelBtn).not.toBeUndefined();

        // Clic sur Annuler ouvre la boîte de confirmation
        await act(async () => {
            cancelBtn.click();
        });

        const dialogConfirmBtn = Array.from(document.body.querySelectorAll('button')).find(
            b => b.textContent.trim() === 'Annuler la version'
        );
        expect(dialogConfirmBtn).not.toBeUndefined();

        // Confirmer l'annulation
        await act(async () => {
            dialogConfirmBtn.click();
        });

        expect(paieConfigService.annulerParametreGlobalProgramme).toHaveBeenCalledWith(42);
    });
});
