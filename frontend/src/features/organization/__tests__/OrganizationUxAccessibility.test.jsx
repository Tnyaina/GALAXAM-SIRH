// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import DepartementsList from '../components/views/DepartementsList';
import ServicesList from '../components/views/ServicesList';
import TypesContratList from '../components/views/TypesContratList';
import DepartementFormModal from '../components/modals/DepartementFormModal';

describe('Organisation UX & Accessibilité (WCAG AA & EmptyState focal)', () => {
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

    it('DepartementsList : masque le bouton d\'en-tête lorsque la liste est vide (EmptyState focal)', async () => {
        await act(async () => {
            root.render(
                <DepartementsList
                    departements={[]}
                    onOpenCreate={() => {}}
                />
            );
        });

        // Le bouton "Nouveau" du header ne doit pas être rendu
        const buttons = Array.from(container.querySelectorAll('button'));
        const headerNewBtn = buttons.find(b => b.textContent.trim() === 'Nouveau');
        expect(headerNewBtn).toBeUndefined();

        // Mais l'EmptyState propose le bouton d'action focal
        const emptyStateBtn = buttons.find(b => b.textContent.includes('Créer un département'));
        expect(emptyStateBtn).not.toBeUndefined();
    });

    it('DepartementsList : affiche les cibles tactiles WCAG AA (44x44px) pour les actions', async () => {
        const dummyDepts = [
            { idDepartement: 1, codeDepartement: 'IT', libelle: 'Informatique', estActif: true }
        ];

        await act(async () => {
            root.render(
                <DepartementsList
                    departements={dummyDepts}
                    onOpenEdit={() => {}}
                    onToggleStatus={() => {}}
                />
            );
        });

        const actionButtons = Array.from(container.querySelectorAll('.space-y-1\\.5 button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('ServicesList : EmptyState focal quand aucun service rattaché', async () => {
        await act(async () => {
            root.render(
                <ServicesList
                    selectedDepartement={{ idDepartement: 1, codeDepartement: 'IT', libelle: 'Informatique' }}
                    services={[]}
                    onOpenCreateService={() => {}}
                />
            );
        });

        const buttons = Array.from(container.querySelectorAll('button'));
        const headerBtn = buttons.find(b => b.textContent.trim() === 'Nouveau Service');
        expect(headerBtn).toBeUndefined();

        const emptyAction = buttons.find(b => b.textContent.includes('Ajouter un service'));
        expect(emptyAction).not.toBeUndefined();
    });

    it('TypesContratList : vérifie les dimensions tactiles WCAG AA (44x44px)', async () => {
        const dummyTc = [
            { idTypeContrat: 1, code: 'CDI', libelle: 'Contrat à Durée Indéterminée', estActif: true }
        ];

        await act(async () => {
            root.render(
                <TypesContratList
                    typesContrat={dummyTc}
                    onOpenEdit={() => {}}
                    onToggleStatus={() => {}}
                />
            );
        });

        const actionButtons = Array.from(container.querySelectorAll('button[aria-label]'));
        expect(actionButtons.length).toBe(2);
        actionButtons.forEach(btn => {
            expect(btn.className).toContain('min-h-[44px]');
            expect(btn.className).toContain('min-w-[44px]');
        });
    });

    it('DepartementFormModal : affiche l\'erreur ciblée sur l\'input (aria-invalid et FieldError)', async () => {
        const fieldErrors = { codeDepartement: 'Le code département est obligatoire.' };

        await act(async () => {
            root.render(
                <DepartementFormModal
                    isOpen={true}
                    onClose={() => {}}
                    onSubmit={() => {}}
                    formData={{ codeDepartement: '', libelle: '' }}
                    setFormData={() => {}}
                    fieldErrors={fieldErrors}
                />
            );
        });

        // L'input en erreur doit avoir aria-invalid="true"
        const codeInput = container.querySelector('#dept-form input[placeholder="Ex: LOG, DAF, TRANSIT"]');
        expect(codeInput).not.toBeNull();
        expect(codeInput.getAttribute('aria-invalid')).toBe('true');

        // Le message d'erreur est affiché sous l'input
        expect(container.textContent).toContain('Le code département est obligatoire.');

        // Et pas de FormError global (div) au sommet quand seule une erreur de champ existe
        expect(container.querySelector('div[role="alert"]')).toBeNull();
    });

    it('DepartementFormModal : affiche FormError au sommet pour les conflits globaux de formulaire', async () => {
        await act(async () => {
            root.render(
                <DepartementFormModal
                    isOpen={true}
                    onClose={() => {}}
                    onSubmit={() => {}}
                    formData={{ codeDepartement: 'LOG', libelle: 'Logistique' }}
                    setFormData={() => {}}
                    actionError="Ce code département est déjà attribué à une autre entité."
                    fieldErrors={{}}
                />
            );
        });

        // FormError global présent
        expect(container.querySelector('[role="alert"]')).not.toBeNull();
        expect(container.textContent).toContain('Ce code département est déjà attribué à une autre entité.');

        // Mais les inputs sont valides (pas d'aria-invalid)
        const inputs = Array.from(container.querySelectorAll('#dept-form input'));
        inputs.forEach(input => {
            expect(input.getAttribute('aria-invalid')).not.toBe('true');
        });
    });

    it('DepartementFormModal : consomme les variables et tokens sémantiques de index.css', async () => {
        await act(async () => {
            root.render(
                <DepartementFormModal
                    isOpen={true}
                    onClose={() => {}}
                    onSubmit={() => {}}
                    formData={{ codeDepartement: 'LOG', libelle: 'Logistique' }}
                    setFormData={() => {}}
                    actionError="Conflit détecté"
                    fieldErrors={{ codeDepartement: 'Erreur code' }}
                />
            );
        });

        // Vérification de la présence des variables CSS index.css dans les composants rendus
        const alertBox = container.querySelector('[role="alert"]');
        expect(alertBox).not.toBeNull();
        // Vérifie qu'on n'utilise pas de styles bruts non tokenisés
        expect(container.innerHTML).toContain('var(--color-danger-500)');
    });
});

