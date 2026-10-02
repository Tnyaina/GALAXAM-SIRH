// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import ReferentielsRhTab from '../components/tabs/ReferentielsRhTab';
import organisationService from '../api/organisationService';

vi.mock('../api/organisationService', () => ({
    default: {
        getTypesContrat: vi.fn(),
        getTypesIntervenant: vi.fn(),
        getBanques: vi.fn(),
        createBanque: vi.fn(),
    },
}));
vi.mock('@/components/ui/feedback/Toast', () => ({ useToast: () => vi.fn() }));

describe('Référentiel banques', () => {
    let container;
    let root;

    beforeEach(() => {
        container = document.createElement('div');
        document.body.appendChild(container);
        root = createRoot(container);
        vi.clearAllMocks();
        organisationService.getTypesContrat.mockResolvedValue([]);
        organisationService.getTypesIntervenant.mockResolvedValue([]);
        organisationService.getBanques.mockResolvedValue([]);
    });

    afterEach(async () => {
        await act(async () => root.unmount());
        container.remove();
    });

    it('garde le formulaire ouvert et affiche le message métier après un code déjà utilisé', async () => {
        organisationService.createBanque.mockRejectedValue({
            response: { status: 400, data: { code: 'VALIDATION_ERROR', message: 'Ce code banque est déjà utilisé.' } },
        });
        await act(async () => root.render(
            <MemoryRouter initialEntries={['/?tab=rh&subtab=banques']}>
                <ReferentielsRhTab />
            </MemoryRouter>
        ));
        const newBank = [...container.querySelectorAll('button')]
            .find((button) => button.textContent.includes('Nouvelle Banque'));
        await act(async () => newBank.click());
        const inputs = container.querySelectorAll('#banque-form input');
        const setInput = (input, value) => {
            Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
            input.dispatchEvent(new Event('input', { bubbles: true }));
        };
        await act(async () => {
            setInput(inputs[0], '00004');
        });
        await act(async () => setInput(inputs[1], 'Banque test'));
        await act(async () => container.querySelector('#banque-form').dispatchEvent(
            new Event('submit', { bubbles: true, cancelable: true })
        ));
        expect(container.textContent).toContain('Ce code banque est déjà utilisé.');
        expect(container.querySelector('[role="dialog"]')).not.toBeNull();
    });
});
