// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it } from 'vitest';
import WeeklyPointageCalculationTable from '../components/WeeklyPointageCalculationTable';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;
beforeEach(() => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
});
afterEach(async () => { await act(async () => root.unmount()); host.remove(); });

it('présente la semaine incomplète sans afficher le déficit calculé comme une retenue', async () => {
    await act(async () => root.render(
        <WeeklyPointageCalculationTable
            weeks={[{ debutSemaine: '2026-08-03', finSemaine: '2026-08-09', numeroSemaine: 1,
                nbJoursTroncon: 5, seuilHeuresTroncon: 40, totalHeuresTravaillees: 0, heuresDeficit: 40 }]}
            pointages={[{ dateComplete: '2026-08-03', statut: 'ABSENT' }]}
        />
    ));
    expect(host.textContent).toContain('Saisie incomplète · 1 j à saisir');
    expect(host.textContent).toContain('Voir les jours à saisir');
    expect(host.textContent).not.toContain('40h00 de déficit');
});

it('affiche les heures qualifiées de la semaine avec heures et décimales, même à zéro', async () => {
    await act(async () => root.render(
        <WeeklyPointageCalculationTable
            weeks={[{ debutSemaine: '2026-08-03', finSemaine: '2026-08-09', numeroSemaine: 1,
                nbJoursTroncon: 5, seuilHeuresTroncon: 40, totalHeuresTravaillees: 40,
                heuresNuit: 1.5, heuresDimanche: 0, heuresFerie: 2.25 }]}
        />
    ));
    expect(host.textContent).toContain('Heures qualifiées');
    expect(host.textContent).toContain('Nuit01h30 (1.50h)');
    expect(host.textContent).toContain('Dimanche00h00 (0.00h)');
    expect(host.textContent).toContain('Jour férié02h15 (2.25h)');
});
