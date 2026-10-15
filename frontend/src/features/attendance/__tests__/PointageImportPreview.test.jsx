// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import PeriodeEmployeeImportPreview from '../components/views/PeriodeEmployeeImportPreview';

vi.mock('@/components/ui/overlays/Modal', () => ({ default: ({ children }) => <div>{children}</div> }));
vi.mock('../api/attendanceApi', () => ({ attendanceService: { daily: { previewBatch: vi.fn() } } }));
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

let host;
let root;
afterEach(async () => {
    if (root) await act(async () => root.unmount());
    host?.remove();
    root = null;
});

const line = (ligne, date, decision, extras = {}) => ({ ligne, date, decision, errors: [], warnings: [], allowances: [], ...extras });
const show = async (lines, selections = {}) => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    await act(async () => root.render(<PeriodeEmployeeImportPreview
        importPreview={{ lines }} importSelections={selections}
        setImportSelections={vi.fn()} setImportPreview={vi.fn()}
        grilleRows={lines.map(item => ({ datePointage: item.date, canCorrect: true }))}
        onClose={vi.fn()} onApply={vi.fn()} onSave={vi.fn()}
    />));
};

it('compte les lignes ignorées et distingue signalement et pointage enregistrable', async () => {
    await show([
        line(1, '2026-08-01', 'IGNORE'),
        line(2, '2026-08-03', 'IMPORT', { proposed: { codeStatut: 'PRESENT' } }),
        line(3, '2026-08-04', 'SIGNAL', { eventCode: 'C' }),
        line(-4, '2026-08-05', 'MISSING')
    ], { 2: true, 3: true });
    expect(host.textContent).toContain('1 ignorées');
    expect(host.textContent).toContain('1 absentes du fichier');
    expect(host.textContent).toContain('1 pointage(s) · 1 signalement(s)');
    expect([...host.querySelectorAll('button')].find(button => button.textContent.includes('Enregistrer 1 pointage')).disabled).toBe(true);
});

it('exige un accord explicite avant de remplacer un pointage existant', async () => {
    await show([line(5, '2026-08-06', 'IMPORT', {
        conflict: true, proposed: { codeStatut: 'PRESENT', heureEntreeMatin: '08:00', heureSortieMatin: '12:00', heureEntreeAm: '13:00', heureSortieAm: '17:00' },
        existing: { statut: 'PRESENT', heureEntreeMatin: '09:00', heureSortieMatin: '12:00', heureEntreeAm: '13:00', heureSortieAm: '17:00' }
    })], { 5: true });
    const save = [...host.querySelectorAll('button')].find(button => button.textContent.includes('Enregistrer 1 pointage'));
    expect(save.disabled).toBe(true);
    await act(async () => [...host.querySelectorAll('button')].find(button => button.textContent === 'Détails').click());
    const approval = [...host.querySelectorAll('input[type="checkbox"]')].find(input => input.parentElement?.textContent.includes('Remplacer'));
    expect(approval).toBeDefined();
    await act(async () => { approval.click(); });
    expect(save.disabled).toBe(false);
});
