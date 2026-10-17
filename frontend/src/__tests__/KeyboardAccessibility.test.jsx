// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Tabs from '@/components/ui/navigation/Tabs';
import SideTabs from '@/components/ui/navigation/SideTabs';

describe('Accessibilité au clavier et navigation (WCAG 2.1 AA)', () => {
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

    describe('Onglets WAI-ARIA et navigation fléchée (Tabs & SideTabs)', () => {
        it('gère le roving tabindex et la sélection par flèches droite/gauche sur Tabs', async () => {
            const handleChange = vi.fn();
            const tabsList = [
                { id: 'tab1', label: 'Premier' },
                { id: 'tab2', label: 'Deuxième' },
                { id: 'tab3', label: 'Troisième' },
            ];

            await act(async () => root.render(
                <Tabs
                    tabs={tabsList}
                    activeTab="tab1"
                    onChange={handleChange}
                />
            ));

            const tablist = host.querySelector('[role="tablist"]');
            expect(tablist).not.toBeNull();
            expect(tablist.getAttribute('aria-orientation')).toBe('horizontal');

            const tabButtons = host.querySelectorAll('[role="tab"]');
            expect(tabButtons).toHaveLength(3);

            // Tab 1 est actif : tabindex 0, aria-selected true
            expect(tabButtons[0].getAttribute('tabindex')).toBe('0');
            expect(tabButtons[0].getAttribute('aria-selected')).toBe('true');

            // Tab 2 et 3 sont inactifs : tabindex -1, aria-selected false
            expect(tabButtons[1].getAttribute('tabindex')).toBe('-1');
            expect(tabButtons[1].getAttribute('aria-selected')).toBe('false');

            // Touche Flèche Droite
            await act(async () => {
                tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
            });

            expect(handleChange).toHaveBeenCalledWith('tab2');

            // Touche Flèche Gauche depuis premier -> boucle vers dernier
            await act(async () => {
                tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', bubbles: true }));
            });

            expect(handleChange).toHaveBeenCalledWith('tab3');
        });

        it('gère la navigation verticale (ArrowDown/ArrowUp) sur SideTabs', async () => {
            const handleChange = vi.fn();
            const sideTabsList = [
                { id: 'sec1', label: 'Section 1' },
                { id: 'sec2', label: 'Section 2' },
            ];

            await act(async () => root.render(
                <SideTabs
                    tabs={sideTabsList}
                    activeTab="sec1"
                    onChange={handleChange}
                >
                    <p>Contenu</p>
                </SideTabs>
            ));

            const tablist = host.querySelector('[role="tablist"]');
            expect(tablist.getAttribute('aria-orientation')).toBe('vertical');

            // Touche Flèche Bas
            await act(async () => {
                tablist.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
            });

            expect(handleChange).toHaveBeenCalledWith('sec2');
        });
    });

    describe('Modales et Piège du Focus (Modal & ConfirmDialog)', () => {
        it('inclut role="dialog" et aria-modal="true" sur Modal et répond à Échap', async () => {
            const handleClose = vi.fn();

            await act(async () => root.render(
                <Modal
                    isOpen={true}
                    onClose={handleClose}
                    title="Dialogue d'accessibilité"
                >
                    <button type="button">Action 1</button>
                    <button type="button">Action 2</button>
                </Modal>
            ));

            const dialog = document.body.querySelector('[role="dialog"]');
            expect(dialog).not.toBeNull();
            expect(dialog.getAttribute('aria-modal')).toBe('true');
            expect(dialog.getAttribute('aria-labelledby')).toBe('modal-title');

            // Pression touche Échap
            await act(async () => {
                window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            });

            expect(handleClose).toHaveBeenCalledTimes(1);
        });

        it('boucle le focus avec Tab et Shift+Tab à l\'intérieur du ConfirmDialog', async () => {
            const handleClose = vi.fn();

            await act(async () => root.render(
                <ConfirmDialog
                    isOpen={true}
                    onClose={handleClose}
                    onConfirm={vi.fn()}
                    title="Confirmation WCAG"
                />
            ));

            const dialog = document.body.querySelector('[role="dialog"]');
            expect(dialog).not.toBeNull();

            const buttons = dialog.querySelectorAll('button');
            expect(buttons.length).toBeGreaterThanOrEqual(2);

            const firstBtn = buttons[0];
            const lastBtn = buttons[buttons.length - 1];

            // Tabulation arrière (Shift+Tab) depuis le premier bouton -> cible le dernier bouton
            firstBtn.focus();
            const shiftTabEvent = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
            const preventedShift = !dialog.dispatchEvent(shiftTabEvent);

            expect(preventedShift).toBe(true);
            expect(document.activeElement).toBe(lastBtn);

            // Tabulation avant (Tab) depuis le dernier bouton -> cible le premier bouton
            lastBtn.focus();
            const tabEvent = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: false, bubbles: true, cancelable: true });
            const preventedTab = !dialog.dispatchEvent(tabEvent);

            expect(preventedTab).toBe(true);
            expect(document.activeElement).toBe(firstBtn);
        });
    });
});
