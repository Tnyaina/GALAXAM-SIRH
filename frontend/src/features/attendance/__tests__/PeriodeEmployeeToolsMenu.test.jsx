// @vitest-environment jsdom
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, expect, it, vi } from 'vitest';
import PeriodeEmployeeHeader from '../components/views/PeriodeEmployeeHeader';

globalThis.IS_REACT_ACT_ENVIRONMENT = true;
let host;
let root;
afterEach(async () => {
    if (root) await act(async () => root.unmount());
    host?.remove();
});

it('sépare les fichiers Excel des actions horaires dans le menu Outils', async () => {
    host = document.createElement('div');
    document.body.appendChild(host);
    root = createRoot(host);
    const download = vi.fn();
    const importFile = vi.fn();
    await act(async () => root.render(<PeriodeEmployeeHeader
        ficheData={{ nomComplet: 'Agent Test', matricule: '0041' }}
        canEditPeriod setFilterJour={vi.fn()} grilleRows={[]}
        onDownloadTemplate={download} onImportClick={importFile}
        onApplyStandard={vi.fn()} onDeducePauses={vi.fn()}
    />));
    await act(async () => host.querySelector('button[aria-label="Outils de la période"]').click());
    const menu = document.querySelector('[role="menu"][aria-label="Outils de la période"]');
    expect(menu.textContent).toContain('Fichiers Excel');
    expect(menu.textContent).toContain('Saisie des horaires');
    await act(async () => [...menu.querySelectorAll('[role="menuitem"]')]
        .find(item => item.textContent.includes('Télécharger le modèle Excel')).click());
    expect(download).toHaveBeenCalledOnce();
    expect(importFile).not.toHaveBeenCalled();
});
