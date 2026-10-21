import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext.jsx';
import { useMenu } from '@/hooks/useMenu';
import { findMenuTitleByHref } from '@/utils/menu';
import { getErrorMessage } from '@/api/apiClient';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

const THEME_STORAGE_KEY = 'galaxam-theme';

const getInitialTheme = () => {
    if (typeof window === 'undefined') return false;
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'dark') return true;
    if (saved === 'light') return false;
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

export default function Layout({ children }) {
    // Contexte, état local et navigation courante.
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(getInitialTheme);
    const { pathname } = useLocation();
    const { isAuthenticated } = useAuth();
    const { menu, loading: menuLoading, error: menuError, refreshMenu } = useMenu();

    // Effet : applique et mémorise le thème.
    useEffect(() => {
        document.documentElement.classList.toggle('dark', isDarkMode);
        window.localStorage.setItem(THEME_STORAGE_KEY, isDarkMode ? 'dark' : 'light');
    }, [isDarkMode]);

    // Rendu : garde non authentifiée.
    if (!isAuthenticated) {
        return (
            <main id="main-content" tabIndex={-1} className="min-h-screen">
                {children}
            </main>
        );
    }

    // Donnée dérivée et rendu authentifié.
    const title = findMenuTitleByHref(menu, pathname) || 'Galaxam GRH';

    return (
        <div className="h-screen flex overflow-hidden bg-[var(--color-bg)] text-[var(--color-fg)]">
            <a href="#main-content" className="skip-link">
                Aller au contenu principal
            </a>
            <Sidebar
                open={sidebarOpen}
                onClose={() => setSidebarOpen(false)}
                menu={menu}
                loading={menuLoading}
                error={menuError ? getErrorMessage(menuError, 'Impossible de charger le menu depuis cette connexion.') : null}
                onRetry={refreshMenu}
            />

            {/* La topbar, le contenu et le footer vivent dans la colonne de droite :
                la sidebar conserve ainsi sa pleine hauteur, du haut au bas de l'app. */}
            <div className="flex-1 min-w-0 flex flex-col overflow-hidden">
                <Topbar
                    onMenuClick={() => setSidebarOpen(true)}
                    onThemeToggle={() => setIsDarkMode((prev) => !prev)}
                    isDarkMode={isDarkMode}
                    title={title}
                />
                <main
                    id="main-content"
                    tabIndex={-1}
                    className="flex-1 min-w-0 min-h-0 flex flex-col overflow-y-auto overflow-x-hidden p-3.5 sm:p-4.5 bg-[var(--color-bg)] text-[var(--color-fg)] focus:outline-none"
                >
                    {children}
                </main>
                <footer className="h-7 shrink-0 border-t border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-center px-4">
                    <p className="text-[11px] text-[var(--color-fg-subtle)]">
                        © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
                    </p>
                </footer>
            </div>
        </div>
    );
}
