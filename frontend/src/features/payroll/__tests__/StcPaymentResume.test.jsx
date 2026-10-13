// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import StcRecuModal from '../components/modals/stc/StcRecuModal';
import stcService from '../api/stcService';

vi.mock('../api/stcService', () => ({ default: {
    obtenirRecuStc: vi.fn(), enregistrerReglement: vi.fn(),
} }));
vi.mock('@/api/documentService', () => ({ default: { downloadCertificatDocx: vi.fn() } }));
vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ isOpen, children, footer }) => isOpen ? <div>{children}{footer}</div> : null }));

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
const setInput = async (input, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    await act(async () => { setter.call(input, value); input.dispatchEvent(new Event('input', { bubbles: true })); });
};
beforeEach(() => {
    host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host);
    stcService.obtenirRecuStc.mockResolvedValueOnce({ idStc: 15, numeroRecu: 'STC-2026-0015', statut: 'VALIDE' })
        .mockResolvedValueOnce({ idStc: 15, numeroRecu: 'STC-2026-0015', statut: 'REGLE' });
    stcService.enregistrerReglement.mockResolvedValue();
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

it('reprend un STC validé et constate le paiement séparément', async () => {
    const onPaymentRegistered = vi.fn();
    await act(async () => root.render(<StcRecuModal isOpen idStc={15} canRegisterPayment
        onPaymentRegistered={onPaymentRegistered} onClose={vi.fn()} />));
    expect(host.textContent).toContain('Constater le paiement du STC');
    const inputs = [...host.querySelectorAll('input')];
    await setInput(inputs.find(input => input.type === 'date'), '2026-10-10');
    await setInput(inputs.find(input => input.type !== 'date'), 'VIR-15');
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent.includes('Enregistrer le règlement')).click());
    expect(stcService.enregistrerReglement).toHaveBeenCalledWith(15, {
        dateReglement: '2026-10-10', referenceReglement: 'VIR-15',
    });
    expect(onPaymentRegistered).toHaveBeenCalledOnce();
    expect(host.textContent).toContain('Paiement constaté');
});
