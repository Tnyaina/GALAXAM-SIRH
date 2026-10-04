import { useEffect, useRef } from 'react';

const FOCUSABLE_SELECTOR = [
    'a[href]',
    'button:not([disabled])',
    'input:not([disabled])',
    'select:not([disabled])',
    'textarea:not([disabled])',
    '[tabindex]:not([tabindex="-1"])',
    'details',
    'summary',
].join(', ');

/**
 * useFocusTrap — Maintient le focus clavier à l'intérieur du conteneur actif (Modale, SlideOver, Dialogue).
 * Conforme WCAG 2.1 AA (Critères 2.1.2 Pas de piège au clavier & 2.4.3 Ordre du focus).
 * - Mémorise l'élément ayant déclenché l'ouverture pour lui restituer le focus à la fermeture.
 * - Oriente immédiatement le focus vers le premier élément interactif (ou le conteneur lui-même).
 * - Fait cycler la tabulation (Tab et Shift+Tab) strictement au sein du conteneur.
 */
export default function useFocusTrap(containerRef, isActive = true, options = {}) {
    const { initialFocusRef } = options;
    const previousFocusRef = useRef(null);

    useEffect(() => {
        if (!isActive) return;

        // Sauvegarde de l'élément actif avant ouverture
        if (typeof document !== 'undefined') {
            previousFocusRef.current = document.activeElement;
        }

        const container = containerRef.current;
        if (!container) return;

        const isVisible = (el) => {
            if (el.offsetParent !== null || el.getClientRects().length > 0) return true;
            try {
                const style = window.getComputedStyle(el);
                return style.display !== 'none' && style.visibility !== 'hidden';
            } catch {
                return true;
            }
        };

        // Définition de l'élément cible pour le focus initial
        const focusInitial = () => {
            if (initialFocusRef?.current) {
                initialFocusRef.current.focus();
                return;
            }

            const focusables = Array.from(container.querySelectorAll(FOCUSABLE_SELECTOR)).filter(isVisible);

            if (focusables.length > 0) {
                focusables[0].focus();
            } else {
                container.focus();
            }
        };

        // Délai minimal pour garantir que le DOM est complètement monté
        const timer = setTimeout(focusInitial, 25);

        const handleKeyDown = (e) => {
            if (e.key !== 'Tab') return;

            const focusables = Array.from(container.querySelectorAll(FOCUSABLE_SELECTOR)).filter(isVisible);

            if (focusables.length === 0) {
                e.preventDefault();
                container.focus();
                return;
            }

            const firstElement = focusables[0];
            const lastElement = focusables[focusables.length - 1];

            if (e.shiftKey) {
                // Tabulation arrière (Shift + Tab) : si sur le premier élément, boucler vers le dernier
                if (document.activeElement === firstElement || document.activeElement === container) {
                    e.preventDefault();
                    lastElement.focus();
                }
            } else {
                // Tabulation avant (Tab) : si sur le dernier élément, boucler vers le premier
                if (document.activeElement === lastElement) {
                    e.preventDefault();
                    firstElement.focus();
                }
            }
        };

        container.addEventListener('keydown', handleKeyDown);

        return () => {
            clearTimeout(timer);
            container.removeEventListener('keydown', handleKeyDown);
            // Restitution du focus à l'élément d'origine à la fermeture
            if (previousFocusRef.current && typeof previousFocusRef.current.focus === 'function') {
                previousFocusRef.current.focus();
            }
        };
    }, [isActive, containerRef, initialFocusRef]);
}
