import { Menu, LogOut, Settings } from 'lucide-react';
import { useAuth } from '@/context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';
import NotificationBell from './NotificationBell';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

export default function Topbar({ onMenuClick, title}) {
    // Contexte et navigation
    const { user, logout, hasPermission } = useAuth();
    const navigate = useNavigate();
    // État local
    const [showDropdown, setShowDropdown] = useState(false);
    const profileRef = useRef(null);

    useEffect(() => {
        if (!showDropdown) return undefined;
        const closeOnOutsideClick = (event) => {
            if (!profileRef.current?.contains(event.target)) setShowDropdown(false);
        };
        const closeOnEscape = (event) => {
            if (event.key === 'Escape') setShowDropdown(false);
        };
        document.addEventListener('pointerdown', closeOnOutsideClick);
        document.addEventListener('keydown', closeOnEscape);
        return () => {
            document.removeEventListener('pointerdown', closeOnOutsideClick);
            document.removeEventListener('keydown', closeOnEscape);
        };
    }, [showDropdown]);

    // Action utilisateur
    const handleLogout = async () => {
        await logout();
        navigate('/', { replace: true });
    };

    // Rendu
    return (
        <header className="h-12 shrink-0 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center gap-3 px-5 sm:px-6 lg:px-8">
            <Button
                variant="ghost"
                size="xs"
                onClick={onMenuClick}
                className="lg:hidden -ml-1.5 !p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                aria-label="Ouvrir le menu"
                iconLeft={<Menu size={18} />}
            />

            <div className="flex-1 min-w-0">
                <h1 className="text-sm sm:text-base font-bold text-[var(--color-fg)] truncate">
                    {title}
                </h1>
            </div>

            <div className="flex items-center gap-1.5">
                {/* Cloche notifications */}
                {hasPermission('EMPLOYE_READ') && <NotificationBell />}

                {/* Profil utilisateur */}
                <div ref={profileRef} className="relative">
                    <button
                        onClick={() => setShowDropdown((v) => !v)}
                        className="min-h-[40px] min-w-[40px] px-2 flex items-center gap-2 hover:opacity-80 rounded-none hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                        aria-label={`Menu profil de ${user?.identifiant || 'l’utilisateur'}`}
                        aria-expanded={showDropdown}
                        aria-haspopup="menu"
                    >
                        <Avatar name={user?.identifiant || 'Utilisateur'} size="xs" shape="square" status="online" />
                        <span className="text-xs font-semibold hidden sm:block text-[var(--color-fg)]">
                            {user?.identifiant}
                        </span>
                    </button>

                    {showDropdown && (
                        <div role="menu" aria-label="Options du profil" className="absolute right-0 mt-2 z-40 w-64 overflow-hidden rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] shadow-xl">
                            <div className="px-4 py-3">
                                <p className="truncate text-sm font-semibold text-[var(--color-fg)]">
                                    {user?.identifiant}
                                </p>
                                {user?.role && (
                                    <Badge variant="primary" size="sm" className="mt-2">
                                        {user.role}
                                    </Badge>
                                )}
                            </div>
                            <div className="border-t border-[var(--color-border-subtle)] p-2">
                                {typeof window !== 'undefined' && window.__TAURI_INTERNALS__ && <Button
                                    plain
                                    role="menuitem"
                                    onClick={() => { setShowDropdown(false); navigate('/settings/connection'); }}
                                    className="flex min-h-0 w-full items-center gap-3 rounded px-3 py-2.5 text-left text-sm text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]"
                                ><Settings size={17} className="shrink-0 text-[var(--color-fg-muted)]"/><span>Connexion et données</span></Button>}
                                <Button
                                    plain
                                    role="menuitem"
                                    onClick={handleLogout}
                                    className="flex min-h-0 w-full items-center gap-3 rounded px-3 py-2.5 text-left text-sm text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]"
                                ><LogOut size={17} className="shrink-0"/><span>Déconnexion</span></Button>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}
