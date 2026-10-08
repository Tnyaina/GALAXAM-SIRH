// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import PayrollEngineMap from '../components/PayrollEngineMap';
import paieService from '../api/paieService';

vi.mock('@/context/AuthContext', () => ({ useAuth: () => ({ hasPermission: () => true }) }));
vi.mock('../api/paieService', () => ({ default: { getParcoursConfiguration: vi.fn(), getConfigurationCatalogue: vi.fn() } }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host, root;
const rule = { id: 10, code: 'PRIME_EXCEPTIONNELLE', libelle: 'Prime exceptionnelle', phaseCalcul: 'BRUT',
    etapeLibelle: 'Dans le brut', niveauConfiguration: 'CONFIGURABLE', niveauLibelle: 'Configurable',
    methodeLibelle: 'Montant saisi', estActif: true, soumisIrsa: true, soumisCnaps: true, soumisSante: true, soumisFmfp: true };
const type = { id: 7, code: 'PRIME_EXCEPTIONNELLE', libelle: 'Prime exceptionnelle', idRubrique: 10,
    rubrique: rule, saisieManuelleAutorisee: true, niveauLibelle: 'Configurable' };

beforeEach(() => {
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    paieService.getConfigurationCatalogue.mockResolvedValue({ rubriquesCompatibles: [rule], defautsType: { sens: 'GAIN' }, options: { etapes: [] } });
    paieService.getParcoursConfiguration.mockResolvedValue({
        phases: [{ code: 'BRUT', libelle: 'Brut et gains', description: 'Gains bruts' }],
        rubriques: [rule], types: [type],
    });
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); vi.clearAllMocks(); });

it('place les données du serveur dans leur phase et relie type et rubrique selon le wireframe', async () => {
    await act(async () => root.render(<MemoryRouter initialEntries={['/paie/configuration']}><PayrollEngineMap /></MemoryRouter>));
    expect(host.querySelector('section[aria-label="Étapes du moteur"]').textContent).toContain('Brut et gains');
    expect(host.querySelector('section[aria-label="Étapes du moteur"]').textContent).toContain('PRIME_EXCEPTIONNELLE');

    // Au clic sur la ligne ou la flèche, la fiche détaillée de la rubrique s'ouvre
    const openRubriqueBtn = host.querySelector('button[aria-label="Ouvrir le détail de Prime exceptionnelle"]');
    await act(async () => openRubriqueBtn.click());
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Montant saisi');

    // Bascule vers l'onglet Types de variable
    const typeTabBtn = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Types de variable'));
    await act(async () => typeTabBtn.click());

    // Au clic sur la flèche du type, sa fiche détaillée s'ouvre avec la mention de saisie
    const openTypeBtn = host.querySelector('button[aria-label="Ouvrir le détail de Prime exceptionnelle"]');
    await act(async () => openTypeBtn.click());
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Saisie manuelle autorisée');
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Rubrique alimentée');

    // Au clic sur [Ouvrir la rubrique], on bascule vers la rubrique associée
    const openLinkedRubriqueBtn = [...host.querySelectorAll('button')].find(b => b.textContent.includes('Ouvrir la rubrique'));
    expect(openLinkedRubriqueBtn).toBeTruthy();
    await act(async () => openLinkedRubriqueBtn.click());
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('RUBRIQUE DE CALCUL');
    expect(host.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Prime exceptionnelle');
});

it('ouvre la création d’un type prélié depuis une rubrique sans type', async () => {
    paieService.getConfigurationCatalogue.mockResolvedValue({ rubriquesCompatibles: [{ ...rule, id: 42, code: 'PRIME_PROJET_TEST', libelle: 'Prime projet', categorie: 'PRIME', sens: 'GAIN', typeValeur: 'MONTANT' }], defautsType: { sens: 'GAIN' }, options: { etapes: [] } });
    paieService.getParcoursConfiguration.mockResolvedValue({
        phases: [{ code: 'BRUT', libelle: 'Brut et gains', description: 'Gains bruts' }],
        rubriques: [{ ...rule, id: 42, code: 'PRIME_PROJET_TEST', libelle: 'Prime projet', categorie: 'PRIME', sens: 'GAIN', typeValeur: 'MONTANT' }],
        types: [],
    });
    await act(async () => root.render(<MemoryRouter initialEntries={['/paie/configuration']}><PayrollEngineMap /></MemoryRouter>));
    expect(host.textContent).toContain('Type à créer');
    const openRubrique = host.querySelector('button[aria-label="Ouvrir le détail de Prime projet"]');
    await act(async () => openRubrique.click());
    const createLinked = [...document.querySelectorAll('button')].find(button => button.textContent.includes('Créer un type lié'));
    expect(createLinked).toBeTruthy();
    await act(async () => createLinked.click());
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog.textContent).toContain('Créer un type de variable');
    expect(dialog.textContent).toContain('Prime projet');
    expect(dialog.querySelector('input[placeholder="Ex. PRIME_PERFORMANCE"]').disabled).toBe(false);
});
