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

    if (!isAuthenticated) {
        return (
            <main id="main-content" tabIndex={-1} className="min-h-screen">
                {children}
            </main>
        );
    }

    const title = findMenuTitleByHref(menu, pathname) || 'Galaxam SIRH';

    return (
        <div className="h-screen flex flex-col bg-[var(--color-bg)] text-[var(--color-fg)]">
            <a href="#main-content" className="skip-link">
                Aller au contenu principal
            </a>
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

                <main id="main-content" tabIndex={-1} className="flex-1 overflow-y-auto p-6 bg-[var(--color-bg)] text-[var(--color-fg)] focus:outline-none">
                    {children}
                </main>
            </div>

            <Footer />
        </div>
    );
}