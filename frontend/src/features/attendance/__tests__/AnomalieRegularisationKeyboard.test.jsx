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
        idEmploye: 7, idSite: 1, idPeriodePaie: 1, datePointage: '2026-08-04',
        typeAnomalie: 'RETARD', minutesRetard: 37, version: 1
    };
    await act(async () => root.render(
        <AnomalieRegularisationModal isOpen anomalie={anomalie} onClose={vi.fn()} onSuccess={onSuccess} />
    ));
    const choice = [...host.querySelectorAll('button')]
        .find((button) => button.textContent.includes('Confirmer le pointage réel'));
    await act(async () => choice.click());
    expect(choice.dataset.regularisationSelected).toBe('true');
    choice.focus();
    await act(async () => choice.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true })));
    expect(onSuccess).toHaveBeenCalledTimes(1);
    expect(onSuccess.mock.calls[0][0].typeRegularisation).toBe('RETARD_REEL');
});
