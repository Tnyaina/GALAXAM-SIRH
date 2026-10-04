// @vitest-environment jsdom
globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as apiClientModule from '@/api/apiClient';
import { NetworkStatusProvider } from '@/context/NetworkStatusContext';
import { useNetworkStatus } from '@/context/networkStatus';
import NetworkStatusBanner from '@/components/system/NetworkStatusBanner';
import NetworkOfflineAlert from '@/components/system/NetworkOfflineAlert';

describe('Résilience réseau et détection de connectivité', () => {
    let host;
    let root;

    beforeEach(() => {
        Object.defineProperty(window.navigator, 'onLine', {
            value: true,
            writable: true,
            configurable: true,
        });

        vi.spyOn(apiClientModule, 'probeBackendConnectivity').mockResolvedValue(true);

        host = document.createElement('div');
        document.body.appendChild(host);
        root = createRoot(host);
    });

    afterEach(async () => {
        vi.restoreAllMocks();
        await act(async () => root.unmount());
        host.remove();
    });

    it('identifie correctement les erreurs réseau transitoires vs erreurs applicatives', () => {
        const { isTransientNetworkError } = apiClientModule;
        // Erreur réseau pure (pas de réponse)
        expect(isTransientNetworkError({ request: {} })).toBe(true);

        // Erreur timeout
        expect(isTransientNetworkError({ code: 'ECONNABORTED' })).toBe(true);
        expect(isTransientNetworkError({ message: 'timeout of 25000ms exceeded' })).toBe(true);

        // Erreur 502 / 503 / 504
        expect(isTransientNetworkError({ response: { status: 502 } })).toBe(true);
        expect(isTransientNetworkError({ response: { status: 503 } })).toBe(true);
        expect(isTransientNetworkError({ response: { status: 504 } })).toBe(true);

        // Erreurs applicatives normales (non transitoires)
        expect(isTransientNetworkError({ response: { status: 400 } })).toBe(false);
        expect(isTransientNetworkError({ response: { status: 401 } })).toBe(false);
        expect(isTransientNetworkError({ response: { status: 404 } })).toBe(false);
        expect(isTransientNetworkError({ response: { status: 409 } })).toBe(false);
        expect(isTransientNetworkError({ response: { status: 500 } })).toBe(false);
    });

    it('affiche le composant NetworkOfflineAlert avec bouton de retry', async () => {
        const retryFn = vi.fn();

        await act(async () => root.render(
            <NetworkStatusProvider>
                <NetworkOfflineAlert
                    title="Erreur de test"
                    message="Impossible de joindre le serveur."
                    onRetry={retryFn}
                />
            </NetworkStatusProvider>
        ));

        expect(host.textContent).toContain('Erreur de test');
        expect(host.textContent).toContain('Impossible de joindre le serveur.');

        const button = host.querySelector('button');
        expect(button).not.toBeNull();
        expect(button.textContent).toContain('Réessayer');

        await act(async () => {
            button.click();
        });

        expect(retryFn).toHaveBeenCalledTimes(1);
    });

    it('met à jour serverReachable via les événements globaux api-network-status', async () => {
        let currentStatus = null;

        function Consumer() {
            currentStatus = useNetworkStatus();
            return (
                <div data-testid="status">
                    {currentStatus.serverReachable ? 'REACHABLE' : 'UNREACHABLE'}
                </div>
            );
        }

        await act(async () => root.render(
            <NetworkStatusProvider>
                <Consumer />
                <NetworkStatusBanner />
            </NetworkStatusProvider>
        ));

        expect(host.querySelector('[data-testid="status"]').textContent).toBe('REACHABLE');

        // Simule un échec réseau signalé par apiClient
        await act(async () => {
            window.dispatchEvent(new CustomEvent('api-network-status', { detail: { reachable: false } }));
        });

        expect(host.querySelector('[data-testid="status"]').textContent).toBe('UNREACHABLE');
        // La bannière d'alerte globale doit maintenant être affichée
        expect(host.querySelector('[role="alert"]')).not.toBeNull();
        expect(host.textContent).toContain('Serveur Galaxam inaccessible');

        // Simule le rétablissement de la liaison
        await act(async () => {
            window.dispatchEvent(new CustomEvent('api-network-status', { detail: { reachable: true } }));
        });

        expect(host.querySelector('[data-testid="status"]').textContent).toBe('REACHABLE');
    });
});

