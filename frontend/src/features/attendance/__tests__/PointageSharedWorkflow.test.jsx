// @vitest-environment jsdom
import { act, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import { attendanceService } from '../api/attendanceApi';
import { usePointageBatchPreview } from '../hooks/usePointageBatchPreview';
import StatusPickerPopover from '../components/controls/StatusPickerPopover';
import { changePointageAllowance, changePointageHours, changePointageStatus, isPointageRowModified } from '../utils/pointageRow';

vi.mock('../api/attendanceApi', () => ({ attendanceService: { daily: { previewBatch: vi.fn(), preview: vi.fn() } } }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;
const keyOf = row => row.idEmploye;
let host;
let root;
afterEach(async () => {
    if (root) await act(async () => root.unmount());
    host?.remove();
    root = null;
    vi.useRealTimers();
    vi.clearAllMocks();
});

it('applique les mêmes transitions de statut, d’heures et d’indemnité aux deux grilles', () => {
    const initial = { codeStatut: 'ABSENCE_NON_JUSTIFIEE', heureEntreeMatin: '', dynamicIndemnites: { 2: { accordee: true, fractionJour: 1, matin: true, am: true } } };
    const worked = changePointageHours(initial, { heureEntreeMatin: '08:00' });
    expect(worked.codeStatut).toBe('NON_SAISI');
    expect(worked._previewPending).toBe(true);
    expect(isPointageRowModified(worked, initial)).toBe(true);
    const absent = changePointageStatus(worked, 'ABSENCE_JUSTIFIEE', 'Certificat');
    expect(absent.dynamicIndemnites[2]).toMatchObject({ accordee: false, fractionJour: 0, matin: false, am: false });
    expect(changePointageAllowance({ matin: true, am: true }, false, 'APRES_MIDI'))
        .toMatchObject({ accordee: true, matin: true, am: false, fractionJour: 0.5 });
});

it('ignore une réponse serveur ancienne après une nouvelle édition', async () => {
    vi.useFakeTimers();
    const pending = [];
    attendanceService.daily.previewBatch.mockImplementation(() => new Promise(resolve => pending.push(resolve)));
    let changeRows;
    function Harness() {
        const [rows, setRows] = useState([{ idEmploye: 7, datePointage: '2026-08-06', idSite: 1,
            heureEntreeMatin: '08:00', codeStatut: 'NON_SAISI', _previewPending: true }]);
        changeRows = setRows;
        usePointageBatchPreview(rows, setRows, keyOf);
        return <output>{JSON.stringify(rows[0])}</output>;
    }
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    await act(async () => root.render(<Harness />));
    await act(async () => vi.advanceTimersByTimeAsync(250));
    expect(pending).toHaveLength(1);
    await act(async () => changeRows(rows => rows.map(row => changePointageHours(row, { heureEntreeMatin: '09:00' }))));
    await act(async () => vi.advanceTimersByTimeAsync(250));
    expect(pending).toHaveLength(2);
    await act(async () => pending[0]([{ idEmploye: 7, preview: { codeStatut: 'RETARD', totalHeures: 8 } }]));
    expect(JSON.parse(host.querySelector('output').textContent).heureEntreeMatin).toBe('09:00');
    expect(JSON.parse(host.querySelector('output').textContent)._previewPending).toBe(true);
    await act(async () => pending[1]([{ idEmploye: 7, preview: { codeStatut: 'PRESENT', totalHeures: 7 } }]));
    const final = JSON.parse(host.querySelector('output').textContent);
    expect(final.codeStatut).toBe('PRESENT');
    expect(final.totalHeures).toBe(7);
    expect(final._previewPending).toBe(false);
});

it('obtient les actions de statut du serveur et respecte une liste vide', async () => {
    attendanceService.daily.preview.mockResolvedValue({ statusActions: [] });
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    await act(async () => root.render(<StatusPickerPopover row={{ idEmploye: 7, idSite: 1, datePointage: '2026-08-06',
        codeStatut: 'NON_SAISI', dynamicIndemnites: {} }} onStatusChange={vi.fn()} />));
    await act(async () => host.querySelector('button[title]').click());
    expect(attendanceService.daily.preview).toHaveBeenCalledOnce();
    expect(document.body.textContent).toContain('Aucune action autorisée pour cette journée.');
    expect(document.body.textContent).not.toContain('Demi-journée');
});
