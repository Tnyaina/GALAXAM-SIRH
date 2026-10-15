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
    expect(document.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Montant saisi');

    // Bascule vers l'onglet Types de variable
    const typeTabBtn = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Types de variable'));
    await act(async () => typeTabBtn.click());

    // Au clic sur la flèche du type, sa fiche détaillée s'ouvre avec la mention de saisie
    const openTypeBtn = host.querySelector('button[aria-label="Ouvrir le détail de Prime exceptionnelle"]');
    await act(async () => openTypeBtn.click());
    expect(document.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Saisie manuelle autorisée');
    expect(document.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Rubrique alimentée');

    // Au clic sur [Ouvrir la rubrique], on bascule vers la rubrique associée
    const openLinkedRubriqueBtn = [...document.querySelectorAll('button')].find(b => b.textContent.includes('Ouvrir la rubrique'));
    expect(openLinkedRubriqueBtn).toBeTruthy();
    await act(async () => openLinkedRubriqueBtn.click());
    expect(document.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('RUBRIQUE DE CALCUL');
    expect(document.querySelector('aside[aria-label="Détail de configuration"]').textContent).toContain('Prime exceptionnelle');
});

it('limite le catalogue de rubriques à une page et garde l’accès aux suivantes', async () => {
    const rubriques = Array.from({ length: 30 }, (_, index) => ({
        ...rule,
        id: index + 1,
        code: `RUBRIQUE_${index + 1}`,
        libelle: `Rubrique ${index + 1}`,
    }));
    paieService.getParcoursConfiguration.mockResolvedValue({
        phases: [{ code: 'BRUT', libelle: 'Brut et gains' }], rubriques, types: [],
    });
    await act(async () => root.render(<MemoryRouter initialEntries={['/paie/configuration']}><PayrollEngineMap /></MemoryRouter>));

    expect(host.querySelector('button[aria-label="Ouvrir le détail de Rubrique 25"]')).toBeTruthy();
    expect(host.querySelector('button[aria-label="Ouvrir le détail de Rubrique 26"]')).toBeNull();
    await act(async () => host.querySelector('button[aria-label="Page 2"]').click());
    expect(host.querySelector('button[aria-label="Ouvrir le détail de Rubrique 26"]')).toBeTruthy();
    expect(host.querySelector('button[aria-label="Ouvrir le détail de Rubrique 1"]')).toBeNull();
});

it('guide la création et bloque le passage si l’identification est vide', async () => {
    await act(async () => root.render(<MemoryRouter initialEntries={['/paie/configuration']}><PayrollEngineMap /></MemoryRouter>));
    const create = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Nouvelle règle'));
    await act(async () => create.click());
    const dialog = document.querySelector('[role="dialog"]');
    expect(dialog.querySelector('[aria-current="step"]').textContent).toContain('Identification');
    await act(async () => [...dialog.querySelectorAll('button')].find(button => button.textContent === 'Continuer').click());
    expect(dialog.querySelector('[aria-current="step"]').textContent).toContain('Identification');
    expect(dialog.textContent).toContain('Renseignez ce champ');
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
