// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import EmployeTable from '../components/EmployeTable';
import HistoriqueContratsTable from '../components/carriere/timeline/HistoriqueContratsTable';
import CarriereTimeline from '../components/carriere/CarriereTimeline';
import { FamilleFormStep, UrgenceFormStep } from '../components/form/EmployeeFormSteps';

vi.mock('@/features/employees/hooks/useFiscalConfig', () => ({
    default: () => ({
        fiscalConfig: {
            ageLimiteDefaut: 21,
            ageLimiteEtudiant: 25,
            deductionParCharge: 2000
        },
        loading: false
    })
}));

vi.mock('@/features/auth/components/HasPermission', () => ({
    default: ({ children }) => children
}));

describe('Employé & Carrière UX & Accessibilité (WCAG AA & EmptyState focal)', () => {
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
    });

    it('EmployeTable : affiche l\'EmptyState focal avec bouton d\'action quand la liste est vide sans filtres', async () => {
        const handleOpenCreate = vi.fn();

        await act(async () => {
            root.render(
                <EmployeTable
                    employes={[]}
                    loading={false}
                    canWrite={true}
                    hasActiveFilters={false}
                    onOpenCreate={handleOpenCreate}
                />
            );
        });

        const buttons = Array.from(container.querySelectorAll('button'));
        const emptyStateBtn = buttons.find(b => b.textContent.includes('Nouvel Employé'));
        expect(emptyStateBtn).not.toBeUndefined();
    });

    it('EmployeTable : garantit les cibles tactiles WCAG AA (>=44x44px) et aria-labels contextualisés sur desktop et mobile', async () => {
        const dummyEmployes = [
            {
                idEmploye: 42,
                matricule: 'EMP-0042',
                prenom: 'Jean',
                nom: 'Dupont',
                emailProfessionnel: 'jean.dupont@galaxam.mg',
                telephoneProfessionnel: '+261340000001',
                nomFonction: 'Développeur Fullstack',
                nomSite: 'Siège Antananarivo',
                estActif: true
            }
        ];

        await act(async () => {
            root.render(
                <EmployeTable
                    employes={dummyEmployes}
                    canWrite={true}
                    canValidate={true}
                    onOpenEdit={() => {}}
                    onToggleStatus={() => {}}
                    onOpenDetail={() => {}}
                />
            );
        });

        // Desktop action buttons
        const desktopWrapper = container.querySelector('.hidden.md\\:block');
        expect(desktopWrapper).not.toBeNull();

        const editDesktopBtn = desktopWrapper.querySelector('button[aria-label="Modifier Jean Dupont"]');
        expect(editDesktopBtn).not.toBeNull();
        expect(editDesktopBtn.className).toContain('min-h-[44px]');
        expect(editDesktopBtn.className).toContain('min-w-[44px]');

        const statusDesktopBtn = desktopWrapper.querySelector('button[aria-label="Désactiver Jean Dupont"]');
        expect(statusDesktopBtn).not.toBeNull();
        expect(statusDesktopBtn.className).toContain('min-h-[44px]');
        expect(statusDesktopBtn.className).toContain('min-w-[44px]');

        // Mobile buttons
        const mobileArticle = container.querySelector('article');
        expect(mobileArticle).not.toBeNull();

        const editMobileBtn = mobileArticle.querySelector('button[aria-label="Modifier Jean Dupont"]');
        expect(editMobileBtn).not.toBeNull();
        expect(editMobileBtn.className).toContain('min-h-[44px]');
        expect(editMobileBtn.textContent.trim()).toBe('Modifier');

        const toggleMobileBtn = mobileArticle.querySelector('button[aria-label="Désactiver Jean Dupont"]');
        expect(toggleMobileBtn).not.toBeNull();
        expect(toggleMobileBtn.className).toContain('min-h-[44px]');
        expect(toggleMobileBtn.textContent.trim()).toBe('Désactiver');
    });

    it('HistoriqueContratsTable : garantit les cibles tactiles WCAG AA (>=44px) et aria-labels explicites', async () => {
        const dummyContrats = [
            {
                idContrat: 101,
                numeroContrat: 'CTR-2025-001',
                typeContrat: 'CDI',
                isActif: true,
                dateDebut: '2025-01-01',
                dateFin: null
            }
        ];

        await act(async () => {
            root.render(
                <HistoriqueContratsTable
                    contratsList={dummyContrats}
                    onOpenAttestation={() => {}}
                    onOpenContrat={() => {}}
                />
            );
        });

        const attestationBtn = container.querySelector('button[aria-label="Générer l\'attestation de travail pour le contrat CTR-2025-001"]');
        expect(attestationBtn).not.toBeNull();
        expect(attestationBtn.className).toContain('min-h-[44px]');

        const consulterBtn = container.querySelector('button[aria-label="Consulter le contrat CTR-2025-001"]');
        expect(consulterBtn).not.toBeNull();
        expect(consulterBtn.className).toContain('min-h-[44px]');
    });

    it('CarriereTimeline : garantit les cibles tactiles (>=44px) et aria-pressed sur le sélecteur de mode', async () => {
        await act(async () => {
            root.render(
                <CarriereTimeline
                    contratActif={null}
                    affectationActive={null}
                    workflowCplActif={null}
                    workflowsList={[]}
                    contratsList={[]}
                    canWrite={false}
                />
            );
        });

        // Open details
        const details = container.querySelector('details');
        if (details) details.open = true;

        const parcoursBtn = container.querySelector('button[aria-pressed="false"]');
        const tablesBtn = container.querySelector('button[aria-pressed="true"]');

        expect(parcoursBtn).not.toBeNull();
        expect(parcoursBtn.className).toContain('min-h-[44px]');
        expect(parcoursBtn.textContent).toContain('Parcours chronologique');

        expect(tablesBtn).not.toBeNull();
        expect(tablesBtn.className).toContain('min-h-[44px]');
        expect(tablesBtn.textContent).toContain('Tableaux détaillés');
    });

    it('EmployeeFormSteps : garantit les cibles tactiles WCAG AA (>=44x44px) sur la suppression d\'enfant et de contact d\'urgence', async () => {
        const dummyFormData = {
            situationFamiliale: 'MARIE',
            conjoint: { nom: 'Dupont', prenom: 'Marie', dateNaissance: '1992-05-15', societe: '', fonction: '' },
            enfants: [
                { nom: 'Dupont', prenom: 'Lucas', dateNaissance: '2015-06-10', sexe: 'M', aCharge: true }
            ],
            contactsUrgence: [
                { nom: 'Dupont', prenom: 'Pierre', lienParente: 'Parent', telephone: '+261320000000' },
                { nom: 'Rabe', prenom: 'Soa', lienParente: 'Ami', telephone: '+261330000000' }
            ]
        };

        // 1. Test FamilleFormStep
        await act(async () => {
            root.render(
                <FamilleFormStep
                    formData={dummyFormData}
                    setNested={() => {}}
                    addEnfant={() => {}}
                    removeEnfant={() => {}}
                    setEnfant={() => {}}
                />
            );
        });

        const deleteEnfantBtn = container.querySelector('button[aria-label="Supprimer l\'enfant 1"]');
        expect(deleteEnfantBtn).not.toBeNull();
        expect(deleteEnfantBtn.className).toContain('min-h-[44px]');
        expect(deleteEnfantBtn.className).toContain('min-w-[44px]');

        // 2. Test UrgenceFormStep
        await act(async () => {
            root.render(
                <UrgenceFormStep
                    formData={dummyFormData}
                    set={() => {}}
                    setContact={() => {}}
                    addContact={() => {}}
                    removeContact={() => {}}
                />
            );
        });

        const deleteContactBtn = container.querySelector('button[aria-label="Supprimer le contact d\'urgence 1"]');
        expect(deleteContactBtn).not.toBeNull();
        expect(deleteContactBtn.className).toContain('min-h-[44px]');
        expect(deleteContactBtn.className).toContain('min-w-[44px]');
    });
});
