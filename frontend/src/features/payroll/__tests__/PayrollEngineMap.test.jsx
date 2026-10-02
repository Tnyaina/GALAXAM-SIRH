// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import PayrollEngineMap from '../components/PayrollEngineMap';
import paieService from '../api/paieService';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock('../api/paieService', () => ({ default: { getParcoursConfiguration: vi.fn() } }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
const rule = { id: 10, code: 'PRIME_EXCEPTIONNELLE', libelle: 'Prime exceptionnelle', phaseCalcul: 'BRUT',
    etapeLibelle: 'Dans le brut', niveauConfiguration: 'CONFIGURABLE', niveauLibelle: 'Configurable',
    methodeLibelle: 'Montant saisi', estActif: true, soumisIrsa: true, soumisCnaps: true, soumisSante: true, soumisFmfp: true };
const type = { id: 7, code: 'PRIME_EXCEPTIONNELLE', libelle: 'Prime exceptionnelle', idRubrique: 10,
    rubrique: rule, saisieManuelleAutorisee: true, niveauLibelle: 'Configurable' };

beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    paieService.getParcoursConfiguration.mockResolvedValue({
        phases: [{ code: 'BRUT', libelle: 'Brut et gains', description: 'Gains bruts' }],
        rubriques: [rule], types: [type],
    });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

it('place les données du serveur dans leur phase et relie type et rubrique', async () => {
    await act(async () => root.render(<MemoryRouter initialEntries={['/paie/configuration']}><PayrollEngineMap /></MemoryRouter>));
    expect(host.querySelector('section[aria-label="Étapes du moteur"]').textContent).toContain('Brut et gains');
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Montant saisi');
    expect(host.querySelector('section[aria-label="Étapes du moteur"]').textContent).toContain('PRIME_EXCEPTIONNELLE');
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Par type de variable').click());
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Saisie manuelle autorisée');
});
