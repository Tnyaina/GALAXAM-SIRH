// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StatCard from '../StatCard';
import { Table, Tbody, Td, Th, Thead, Tr } from '../Table';
import { SkeletonTable } from '../../feedback/Skeleton';
import { getStatutPointageBadge } from '@/features/attendance/utils/attendanceDisplayUtils';

describe('composants partagés de données', () => {
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

  it('transmet le libellé accessible à la table native et applique la densité compacte aux cellules', async () => {
    await act(async () => root.render(
      <Table aria-label="Collaborateurs" density="compact" bordered={false}>
        <Thead><Th>Nom</Th></Thead>
        <Tbody><Tr><Td>Ranaivo</Td></Tr></Tbody>
      </Table>
    ));

    expect(host.querySelector('table').getAttribute('aria-label')).toBe('Collaborateurs');
    expect(host.querySelector('th').className).toContain('px-2 py-1.5');
    expect(host.querySelector('td').className).toContain('px-2 py-1.5');
    expect(host.firstElementChild.className).not.toContain('shadow-sm');
  });

  it('rend le tri au clavier activable et annonce la colonne triée', async () => {
    const onSort = vi.fn();
    await act(async () => root.render(
      <Table><Thead><Th sortable sorted="asc" sortLabel="Nom" onSort={onSort}>Nom</Th></Thead></Table>
    ));
    const button = host.querySelector('th button');
    expect(host.querySelector('th').getAttribute('aria-sort')).toBe('ascending');
    expect(button.getAttribute('aria-label')).toBe('Trier par Nom');
    await act(async () => button.click());
    expect(onSort).toHaveBeenCalledOnce();
  });

  it('reflète l’état actif d’un KPI filtrant dans son contrôle accessible', async () => {
    await act(async () => root.render(<StatCard title="Actifs" value={3} active onClick={() => {}} />));
    expect(host.querySelector('button').getAttribute('aria-pressed')).toBe('true');
    expect(host.querySelector('button').className).toContain('border-[var(--color-brand-500)]');
  });

  it('accepte columns et l’ancien alias cols pour le squelette de tableau', async () => {
    await act(async () => root.render(<SkeletonTable rows={2} columns={6} />));
    expect(host.querySelectorAll(':scope > div > div:first-child > div')).toHaveLength(6);
    await act(async () => root.render(<SkeletonTable rows={2} cols={3} />));
    expect(host.querySelectorAll(':scope > div > div:first-child > div')).toHaveLength(3);
  });

  it('garde un code couleur cohérent pour présence, retard, absence et congé', async () => {
    await act(async () => root.render(
      <div>
        {getStatutPointageBadge('PRESENT')}
        {getStatutPointageBadge('PRESENT', 12)}
        {getStatutPointageBadge('ABSENCE_NON_JUSTIFIEE')}
        {getStatutPointageBadge('CONGE')}
      </div>
    ));
    const badges = host.querySelectorAll('span');
    expect(badges[0].className).toContain('--color-success-500');
    expect(badges[1].className).toContain('--color-warning-500');
    expect(badges[2].className).toContain('--color-danger-500');
    expect(badges[3].className).toContain('--color-sky-500');
  });
});
