import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext.jsx';
import { useMenu } from '@/hooks/useMenu';
import { findMenuTitleByHref } from '@/utils/menu';
import Sidebar from './Sidebar';
import Topbar from './Topbar';
import Footer from './Footer';

const THEME_STORAGE_KEY = 'galaxam-theme';

const getInitialTheme = () => {
    if (typeof window === 'undefined') return false;
    const saved = window.localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'dark') return true;
    if (saved === 'light') return false;
    return window.matchMedia('(prefers-color-scheme: dark)').matches;
};

/**
 * Layout — back-office uniquement (admin + agent pointage), plus de mode
 * Front Office / panier (ancien projet e-commerce PrestaShop).
 *
 * 4 zones : Topbar (fixe en haut) / Sidebar (scroll indépendant) /
 * Main (scroll indépendant) / Footer (fixe en bas).
 */
export default function Layout({ children }) {
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const [isDarkMode, setIsDarkMode] = useState(getInitialTheme);
    const { pathname } = useLocation();
    const { isAuthenticated } = useAuth();
    const { menu, loading: menuLoading, error: menuError } = useMenu();

    useEffect(() => {
        document.documentElement.classList.toggle('dark', isDarkMode);
        window.localStorage.setItem(THEME_STORAGE_KEY, isDarkMode ? 'dark' : 'light');
    }, [isDarkMode]);

    // Pas authentifié -> juste les enfants (page de login), sans le shell
    if (!isAuthenticated) {
        return <div>{children}</div>;
    }

    const title = findMenuTitleByHref(menu, pathname) || 'Galaxam SIRH';

    return (
        <div className="h-screen flex flex-col bg-ink-50 dark:bg-ink-950">
            <Topbar
                onMenuClick={() => setSidebarOpen(true)}
                onThemeToggle={() => setIsDarkMode((prev) => !prev)}
                isDarkMode={isDarkMode}
                title={title}
            />

            <div className="flex-1 flex overflow-hidden">
                <Sidebar
                    open={sidebarOpen}
                    onClose={() => setSidebarOpen(false)}
                    menu={menu}
                    loading={menuLoading}
                    error={menuError}
                />

                <main className="flex-1 overflow-y-auto p-6 text-ink-800 dark:text-ink-50">
                    {children}
                </main>
            </div>

            <Footer />
        </div>
    );
}
