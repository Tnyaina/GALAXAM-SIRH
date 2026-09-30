// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import SoldesGantt from '../components/soldes/SoldesGantt';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({ hasPermission: () => false }),
}));

let host;
let root;

beforeEach(() => {
  host = document.createElement('div');
  document.body.appendChild(host);
  root = createRoot(host);
});

afterEach(async () => {
  await act(async () => root.unmount());
  host.remove();
});

describe('Sélecteur de semaine du calendrier des absences', () => {
  it('permet de choisir une date depuis la barre de navigation hebdomadaire', async () => {
    const onWeekDateChange = vi.fn();

    await act(async () => root.render(<SoldesGantt
      viewMode="SEMAINE"
      setViewMode={vi.fn()}
      filterMode="TOUS"
      setFilterMode={vi.fn()}
      ganttRows={[]}
      filteredGanttRows={[]}
      paginatedGanttRows={[]}
      weekStartStr="2026-08-03"
      weekEndStr="2026-08-09"
      weekDaysArray={[]}
      isSemaineActuelle
      handlePrevWeek={vi.fn()}
      handleNextWeek={vi.fn()}
      handleTodayWeek={vi.fn()}
      onWeekDateChange={onWeekDateChange}
    />));

    expect(host.querySelector('[aria-label="Choisir une date pour accéder à sa semaine"]')).not.toBeNull();
    const input = host.querySelector('input[type="date"]');
    expect(input.value).toBe('2026-08-03');

    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(input, '2026-08-18');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(onWeekDateChange).toHaveBeenCalledWith('2026-08-18');
  });

  it('permet de choisir directement un mois en vue mensuelle', async () => {
    const onMonthChange = vi.fn();

    await act(async () => root.render(<SoldesGantt
      viewMode="MOIS"
      setViewMode={vi.fn()}
      filterMode="TOUS"
      setFilterMode={vi.fn()}
      ganttRows={[]}
      filteredGanttRows={[]}
      paginatedGanttRows={[]}
      year={2026}
      month={8}
      daysArray={[]}
      isMoisActuel
      handlePrevMonth={vi.fn()}
      handleNextMonth={vi.fn()}
      handleToday={vi.fn()}
      onMonthChange={onMonthChange}
    />));

    expect(host.querySelector('[aria-label="Choisir un mois"]')).not.toBeNull();
    const input = host.querySelector('input[type="month"]');
    expect(input.value).toBe('2026-08');

    await act(async () => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
      setter.call(input, '2026-09');
      input.dispatchEvent(new Event('input', { bubbles: true }));
      input.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(onMonthChange).toHaveBeenCalledWith('2026-09');
  });
});
