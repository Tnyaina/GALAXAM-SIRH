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
import documentRhService from '@/features/employees/api/documentRhService';
import paieService from '@/features/payroll/api/paieService';
import referentielService from '@/features/administration/api/referentielService';

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

vi.mock('react-router-dom', () => ({
    useSearchParams: () => [new URLSearchParams(), vi.fn()]
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
});
