import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { useAuth } from '@/context/authContext.jsx';
import { useCustomerAuth } from '@/context/customerAuthContext.jsx';
import { CartProvider } from '@/context/cartContext.jsx';
import Sidebar from './Sidebar';
import Topbar from './Topbar';
import TopbarFO from './TopbarFO';

const THEME_STORAGE_KEY = 'prestashop-theme';

const getInitialTheme = () => {
  if (typeof window === 'undefined') return false;
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY);
  return savedTheme === 'dark' ? true : (savedTheme === 'light' ? false : window.matchMedia('(prefers-color-scheme: dark)').matches);
};

// On ne garde que les titres liés à ton projet
const pageTitles = {
  '/': 'Tableau de Bord',
  '/products': 'Gestion des Produits',
  '/categories': 'Gestion des Categories',
  '/customers': 'Clients',
  '/address': 'Address',
  '/orderes': 'Commandes',
  '/stock': 'Stock',
  '/reset': 'Reset Data',
  '/import': 'Import Data',
  '/test': 'Zone de Test API',
};

export default function Layout({ children }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(getInitialTheme);
  const { pathname } = useLocation();
  const { token } = useAuth();
  const customerAuth = useCustomerAuth();
  const isCustomerAuthenticated = customerAuth?.isAuthenticated;

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', isDarkMode);
    window.localStorage.setItem(THEME_STORAGE_KEY, isDarkMode ? 'dark' : 'light');
  }, [isDarkMode]);

  // Si pas authentifié, afficher juste les enfants (page de login)
  if (!token && !isCustomerAuthenticated) {
    return <div>{children}</div>;
  }

  // Si client FO authentifié, afficher avec TopbarFO simple
  if (isCustomerAuthenticated && pathname.startsWith('/FO')) {
    return (
      <CartProvider>
        <div className={`min-h-screen flex flex-col ${isDarkMode ? 'bg-slate-950' : 'bg-slate-50'}`}>
          <TopbarFO isDarkMode={isDarkMode} />
          <main className={`flex-1 p-6 ${isDarkMode ? 'text-slate-100' : 'text-slate-900'}`}>
            {children}
          </main>
        </div>
      </CartProvider>
    );
  }

  // Si admin authentifié, afficher avec sidebar et topbar
  if (token) {
    return (
      <div className={`min-h-screen flex ${isDarkMode ? 'bg-slate-950' : 'bg-slate-50'}`}>
        <Sidebar open={sidebarOpen} onClose={() => setSidebarOpen(false)} />

        <div className="flex-1 flex flex-col min-h-screen lg:ml-[260px]">
          <Topbar
            onMenuClick={() => setSidebarOpen(true)}
            onThemeToggle={() => setIsDarkMode((prev) => !prev)}
            isDarkMode={isDarkMode}
            title={pageTitles[pathname] || 'PrestaShop Admin'}
          />
          <main className={`flex-1 p-6 ${isDarkMode ? 'text-slate-100' : 'text-slate-900'}`}>
            {children}
          </main>
        </div>
      </div>
    );
  }

  return <div>{children}</div>;
}