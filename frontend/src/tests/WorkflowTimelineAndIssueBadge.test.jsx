// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, describe, expect, it } from 'vitest';
import WorkflowTimeline from '@/components/workflow/WorkflowTimeline';
import PayrollIssueBadge from '@/features/payroll/components/feedback/PayrollIssueBadge';

let container;
let root;

afterEach(() => {
    if (root) act(() => root.unmount());
    container?.remove();
    root = null;
});

function renderComponent(component) {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => {
        root.render(component);
    });
}

describe('WorkflowTimeline — Composant générique d’historique de dossier', () => {
    it('affiche correctement une séquence d’événements avec statuts et métadonnées', () => {
        const events = [
            { id: 1, label: 'Demande créée', date: '2026-09-01', status: 'COMPLETED', author: 'Employé RAKOTO' },
            { id: 2, label: 'Approuvée DAF', date: '2026-09-02', status: 'COMPLETED', author: 'DAF' },
            { id: 3, label: 'En cours de versement', date: '2026-09-03', status: 'CURRENT' },
            { id: 4, label: 'Prochaine imputation paie', status: 'PENDING' },
        ];

        renderComponent(<WorkflowTimeline events={events} />);

        expect(container.textContent).toContain('Demande créée');
        expect(container.textContent).toContain('Approuvée DAF');
        expect(container.textContent).toContain('En cours de versement');
        expect(container.textContent).toContain('Prochaine imputation paie');
        expect(container.textContent).toContain('Employé RAKOTO');
    });

    it('retourne null si aucun événement n’est fourni', () => {
        renderComponent(<WorkflowTimeline events={[]} />);
        expect(container.innerHTML).toBe('');
    });
});

describe('PayrollIssueBadge — Badge unifié d’anomalies et bloqueurs', () => {
    it('affiche le badge BLOCKER avec le compte et libellé approprié', () => {
        renderComponent(<PayrollIssueBadge severity="BLOCKER" count={3} label="bloquants" />);
        expect(container.textContent).toContain('3');
        expect(container.textContent).toContain('bloquants');
        expect(container.firstElementChild?.className).toContain('text-[var(--color-danger-700)]');
    });

    it('affiche le badge WARNING avec style attention', () => {
        renderComponent(<PayrollIssueBadge severity="WARNING" label="À vérifier" />);
        expect(container.textContent).toContain('À vérifier');
        expect(container.firstElementChild?.className).toContain('border-[var(--color-warning-500)]/30');
    });

    it('affiche le badge INFO par défaut', () => {
        renderComponent(<PayrollIssueBadge label="Info note" />);
        expect(container.textContent).toContain('Info note');
    });
});
