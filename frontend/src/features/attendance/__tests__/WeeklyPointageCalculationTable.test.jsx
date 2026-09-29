// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it } from 'vitest';
import WeeklyPointageCalculationTable from '../components/WeeklyPointageCalculationTable';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const host = document.createElement('div');
document.body.appendChild(host);
const root = createRoot(host);
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
