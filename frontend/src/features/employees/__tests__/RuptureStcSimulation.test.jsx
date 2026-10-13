// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import RuptureContratModal from '../components/carriere/modals/workflow/RuptureContratModal';
import contratService from '../api/contratService';

vi.mock('../api/contratService', () => ({ default: {
    getTypesEvenementRh: vi.fn(), getMotifsSortie: vi.fn(), creerBrouillonTransition: vi.fn(),
} }));
vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ isOpen, children, footer }) => isOpen ? <div>{children}{footer}</div> : null }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
beforeEach(() => {
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    contratService.getTypesEvenementRh.mockResolvedValue([{ code: 'DEMISSION', libelle: 'Démission' }]);
    contratService.getMotifsSortie.mockResolvedValue([{ code: 'DEMISSION', libelle: 'Démission', actif: true }]);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

it('ouvre uniquement une simulation STC depuis le brouillon de rupture', async () => {
    const onOpenStc = vi.fn();
    await act(async () => root.render(<RuptureContratModal isOpen idEmploye={7}
        contratActif={{ idContrat: 42 }} affectationActive={{ salaireBase: 300000 }}
        onOpenStc={onOpenStc} onClose={vi.fn()} />));
    const button = [...host.querySelectorAll('button')].find(item => item.textContent.includes('Simuler STC prévisionnel'));
    expect(button).toBeDefined();
    await act(async () => button.click());
    expect(onOpenStc).toHaveBeenCalledWith(expect.objectContaining({ simulationOnly: true }));
});
