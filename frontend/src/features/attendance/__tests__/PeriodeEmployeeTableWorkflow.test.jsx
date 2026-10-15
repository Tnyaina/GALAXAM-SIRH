// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import PeriodeEmployeeTable from '../components/views/PeriodeEmployeeTable';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;
afterEach(async () => {
    if (root) await act(async () => root.unmount());
    host?.remove();
});

it('ouvre les semaines par défaut, permet de les replier et garde les actions de journée dans le menu partagé', async () => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    const day = datePointage => ({ datePointage, date: datePointage.slice(8) + '/08', jour: 'Lun.',
        codeStatut: 'PRESENT', totalHeuresDecimal: 8, heuresFaites: '08h00', minutesRetard: 0,
        heureEntreeMatin: '08:00', heureSortieMatin: '12:30', heureEntreeAm: '13:30', heureSortieAm: '17:00' });
    const first = day('2026-08-03');
    const second = day('2026-08-10');
    const onStandard = vi.fn();
    const onWeekStandard = vi.fn();
    const onReset = vi.fn();
    await act(async () => root.render(<PeriodeEmployeeTable
        groupedByWeeks={[
            { weekKey: '2026-08-03', weekNumber: 2, rows: [first], summaryRows: [first], objectifHeuresSemaine: 40 },
            { weekKey: '2026-08-10', weekNumber: 3, rows: [second], summaryRows: [second], objectifHeuresSemaine: 40 }
        ]}
        canEditPeriod initialRows={[first, second]} displayedIndemnites={[]}
        isDayRowModified={() => false} handleApplyDayStandard={onStandard}
        handleApplyWeekStandard={onWeekStandard} handleResetDay={onReset}
        updateDayHours={vi.fn()} updateDayStatut={vi.fn()} toggleDayContinu={vi.fn()}
        updateRowDynamicIndemnite={vi.fn()} openRegularisation={vi.fn()}
    />));
    expect(host.querySelector('#week-2026-08-03')).not.toBeNull();
    expect(host.querySelector('#week-2026-08-10')).not.toBeNull();
    const weekButtons = [...host.querySelectorAll('button')]
        .filter(button => button.textContent.includes('Remplir semaine'));
    expect(weekButtons).toHaveLength(2);
    await act(async () => weekButtons[1].click());
    expect(onWeekStandard).toHaveBeenCalledWith([second]);
    await act(async () => host.querySelector('button[aria-controls="week-2026-08-10"]').click());
    expect(host.querySelector('#week-2026-08-10')).toBeNull();
    await act(async () => host.querySelector('button[aria-controls="week-2026-08-10"]').click());
    expect(host.querySelector('#week-2026-08-10')).not.toBeNull();
    await act(async () => host.querySelector('button[aria-label="Actions du 2026-08-10"]').click());
    expect(document.querySelector('[role="menu"]')).not.toBeNull();
    await act(async () => [...document.querySelectorAll('[role="menuitem"]')]
        .find(button => button.textContent.includes('Vider la ligne')).click());
    expect(onReset).toHaveBeenCalledWith('2026-08-10');
    expect(onStandard).not.toHaveBeenCalled();
});

it('nomme un jour férié chômé même lorsqu’il tombe le week-end', async () => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    const holiday = {
        datePointage: '2026-08-15', date: '15/08', jour: 'Sam.', weekend: true,
        ferie: true, libelleFerie: 'Assomption', codeStatut: 'FERIE_CHOME',
        totalHeuresDecimal: 0, minutesRetard: 0
    };
    await act(async () => root.render(<PeriodeEmployeeTable
        groupedByWeeks={[{ weekKey: '2026-08-10', weekNumber: 4, rows: [holiday],
            summaryRows: [holiday], objectifHeuresSemaine: 40, totalFeriesSemaine: 1 }]}
        initialRows={[holiday]} displayedIndemnites={[]} canEditPeriod={false}
    />));
    const day = host.querySelector('#pointage-day-2026-08-15');
    expect(day.textContent).toContain('Férié chômé');
    expect(day.textContent).toContain('Assomption');
    expect(day.textContent).toContain('Jour non ouvré');
    expect(day.textContent).not.toContain('Repos hebdomadaire');
});
