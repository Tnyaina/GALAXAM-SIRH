import { Menu, LogOut, Sun, Moon } from 'lucide-react';
import { useAuth } from '@/context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import NotificationBell from './NotificationBell';

export default function Topbar({ onMenuClick, title, onThemeToggle, isDarkMode }) {
    const { user, logout, hasPermission } = useAuth();
    const navigate = useNavigate();
    const [showDropdown, setShowDropdown] = useState(false);

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <header className="h-16 shrink-0 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center gap-4 px-6">
            <button
                onClick={onMenuClick}
                className="lg:hidden min-w-[44px] min-h-[44px] flex items-center justify-center -ml-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                aria-label="Ouvrir le menu"
            >
                <Menu size={20} />
            </button>

            <div className="flex-1 min-w-0">
                <h1 className="text-lg font-bold text-[var(--color-fg)] truncate">
                    {title}
                </h1>
            </div>

            <div className="flex items-center gap-2">
                {/* Cloche notifications */}
                {hasPermission('EMPLOYE_READ') && <NotificationBell />}

                {/* Toggle thème */}
                {onThemeToggle && (
                    <button
                        onClick={onThemeToggle}
                        className="min-w-[44px] min-h-[44px] flex items-center justify-center text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] transition-colors rounded-sm hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                        aria-label={isDarkMode ? 'Passer en mode clair' : 'Passer en mode sombre'}
                    >
                        {isDarkMode ? <Sun size={18} /> : <Moon size={18} />}
                    </button>
                )}

                {/* Profil utilisateur */}
                <div className="relative">
                    <button
                        onClick={() => setShowDropdown((v) => !v)}
                        className="min-h-[44px] px-2 flex items-center gap-2 hover:opacity-80 transition-opacity cursor-pointer"
                        aria-label="Menu profil utilisateur"
                        aria-expanded={showDropdown}
                    >
                        <div className="w-8 h-8 flex items-center justify-center bg-[var(--color-brand-500)] text-white text-xs font-bold shrink-0">
                            {user?.identifiant?.substring(0, 2).toUpperCase()}
                        </div>
                        <span className="text-sm font-medium hidden sm:block text-[var(--color-fg)]">
                            {user?.identifiant}
                        </span>
                    </button>

                    {showDropdown && (
                        <div className="absolute right-0 mt-2 w-44 border border-[var(--color-border)] bg-[var(--color-surface)] shadow-md z-40">
                            <div className="px-4 py-2 border-b border-[var(--color-border-subtle)]">
                                <p className="text-xs text-[var(--color-fg-muted)] truncate">
                                    {user?.role}
                                </p>
                            </div>
                            <button
                                onClick={handleLogout}
                                className="w-full text-left px-4 py-2 flex items-center gap-2 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors text-sm"
                            >
                                <LogOut size={16} />
                                Déconnexion
                            </button>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}