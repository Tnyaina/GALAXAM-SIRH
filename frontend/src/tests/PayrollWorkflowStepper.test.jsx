// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import PayrollPeriodStepper from '@/features/payroll/components/workflow/PayrollPeriodStepper';

const steps = [
    { code: 'PREPARATION', label: 'Préparation', state: 'COMPLETED', accessible: true, destination: '/preparation', issues: [], warnings: [] },
    { code: 'CALCULATION', label: 'Calcul', state: 'STALE', accessible: true, destination: '/calcul', issues: [], warnings: ['Recalcul requis.'] },
    { code: 'CONTROL', label: 'Contrôle', state: 'BLOCKED', accessible: false, destination: '/controle', issues: ['Le calcul est obsolète.'], warnings: [] },
    { code: 'OUTPUTS', label: 'Sorties', state: 'LOCKED', accessible: true, readOnly: true, destination: '/sorties', issues: [], warnings: [] },
];

let container;
let root;

afterEach(() => {
    if (root) act(() => root.unmount());
    container?.remove();
    root = null;
});

function renderStepper(activeCode = 'CALCULATION') {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => {
        root.render(
            <MemoryRouter>
                <PayrollPeriodStepper steps={steps} currentStep="CALCULATION" activeCode={activeCode} />
            </MemoryRouter>
        );
    });
}

describe('PayrollPeriodStepper backend-driven', () => {
    it('rend les états actif, terminé, stale et verrouillé sans les recalculer', () => {
        renderStepper();
        expect(container.querySelector('[aria-current="step"]')?.textContent).toContain('Calcul');
        expect(container.textContent).toContain('Recalcul requis.');
        expect(container.querySelector('[aria-label="Complété"]')).not.toBeNull();
        expect(container.querySelector('[aria-label="Calcul obsolète"]')).not.toBeNull();
        expect(container.querySelector('[aria-label="Verrouillé"]')).not.toBeNull();
    });

    it('explique une étape bloquée lors de la tentative d’accès', () => {
        renderStepper();
        const controlButton = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('Contrôle'));
        expect(controlButton?.getAttribute('aria-disabled')).toBe('true');
        act(() => controlButton.click());
        expect(container.textContent).toContain('Contrôle indisponible');
        expect(container.textContent).toContain('Le calcul est obsolète.');
    });

    it('la lecture seule reste accessible à la navigation', () => {
        renderStepper('OUTPUTS');
        const outputsButton = [...container.querySelectorAll('button')]
            .find(button => button.textContent.includes('Sorties'));
        expect(outputsButton?.getAttribute('aria-disabled')).toBe('false');
    });
});
