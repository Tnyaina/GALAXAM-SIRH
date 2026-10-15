// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AnomalieRegularisationModal from '../components/modals/AnomalieRegularisationModal';
import { attendanceService } from '../api/attendanceApi';

vi.mock('../api/attendanceApi', () => ({
    attendanceService: { getGrilleSaisie: vi.fn(), previewDay: vi.fn() }
}));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let root;
let host;

beforeEach(() => {
    vi.clearAllMocks();
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    attendanceService.getGrilleSaisie.mockResolvedValue({ grilleData: [{ idEmploye: 7 }], siteIndemnites: [] });
});

afterEach(async () => {
    await act(async () => root.unmount());
    host.remove();
});

it('Entrée après le choix du traitement confirme la régularisation une seule fois', async () => {
    const onSuccess = vi.fn().mockResolvedValue(true);
    const anomalie = {
        idPointage: 17, idEmploye: 7, idSite: 1, idPeriodePaie: 1, datePointage: '2026-08-04',
        typeAnomalie: 'RETARD', minutesRetard: 37, version: 1
    };
    await act(async () => root.render(
        <AnomalieRegularisationModal isOpen anomalie={anomalie} onClose={vi.fn()} onSuccess={onSuccess} />
    ));
    const choice = [...document.querySelectorAll('[role="dialog"] button')]
        .find((button) => button.textContent.includes('Confirmer le pointage réel'));
    await act(async () => choice.click());
    expect(choice.dataset.regularisationSelected).toBe('true');
    choice.focus();
    await act(async () => choice.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })));
    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(onSuccess.mock.calls[0][0].typeRegularisation).toBe('RETARD_REEL');
});

it('propose de confirmer un retard réel depuis la correction directe d’une journée', async () => {
    const anomalie = {
        idPointage: 18, idEmploye: 7, idSite: 1, idPeriodePaie: 1,
        datePointage: '2026-08-06', typeAnomalie: 'RETARD', minutesRetard: 30,
        statutCode: 'PRESENT', heureEntreeMatin: '08:30', heureSortieMatin: '12:30',
        heureEntreeAm: '13:30', heureSortieAm: '17:00', version: 1
    };
    await act(async () => root.render(
        <AnomalieRegularisationModal isOpen directDayFlow anomalie={anomalie}
            onClose={vi.fn()} onSuccess={vi.fn()} />
    ));
    expect(document.querySelector('[role="dialog"]').textContent).toContain('Confirmer le pointage réel');
    expect(document.querySelector('[role="dialog"]').textContent).toContain('Signaler absence / congé');
});

it('ouvre le bon traitement depuis le menu de la journée', async () => {
    const anomalie = {
        idEmploye: 7, idSite: 1, idPeriodePaie: 1, datePointage: '2026-08-07',
        statutCode: 'PRESENT', heureEntreeMatin: '08:00', heureSortieMatin: '12:00',
        heureEntreeAm: '13:00', heureSortieAm: '17:00',
        regularisationIntent: 'ABSENCE_DEDUITE'
    };
    await act(async () => root.render(<AnomalieRegularisationModal isOpen directDayFlow
        anomalie={anomalie} onClose={vi.fn()} onSuccess={vi.fn()} />));
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog.textContent).toContain("Modalité de traitement de l'absence");
    expect([...dialog.querySelectorAll('[data-regularisation-selected="true"]')]
        .some(button => button.textContent.includes('Absence'))).toBe(true);
});

it('sépare les indemnités dynamiques de présence et les exceptions d’absence', async () => {
    attendanceService.getGrilleSaisie.mockResolvedValue({
        grilleData: [{ idEmploye: 7, dynamicIndemnites: {
            12: { accordee: true, matin: true, am: true },
            13: { accordee: true }
        } }],
        siteIndemnites: [
            { idIndemnite: 12, code: 'DPLCT', libelle: 'Indemnité de Déplacement', estFractionnable: true },
            { idIndemnite: 13, code: 'REPAS', libelle: 'Indemnité de Repas', estFractionnable: false }
        ]
    });
    const anomalie = { idEmploye: 7, idSite: 1, datePointage: '2026-08-07', regularisationIntent: 'ABSENCE_DEDUITE' };
    await act(async () => root.render(<AnomalieRegularisationModal isOpen directDayFlow
        anomalie={anomalie} onClose={vi.fn()} onSuccess={vi.fn()} />));
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog.textContent).toContain('Indemnités lors de l’absence');
    expect(dialog.textContent).toContain('Indemnité de Déplacement');
    expect(dialog.textContent).toContain('Indemnité de Repas');
    expect([...dialog.querySelectorAll('input[type="checkbox"]')].every(input => !input.checked)).toBe(true);
    const presenceTab = [...dialog.querySelectorAll('button')].find(button => button.textContent.includes('Corriger les heures'));
    await act(async () => presenceTab.click());
    expect(dialog.textContent).toContain('Indemnités liées à la présence');
    expect([...dialog.querySelectorAll('input[type="checkbox"]')].filter(input => input.checked)).toHaveLength(3);
});
