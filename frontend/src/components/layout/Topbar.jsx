import { Menu, LogOut} from 'lucide-react';
import { useAuth } from '@/context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';
import NotificationBell from './NotificationBell';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

export default function Topbar({ onMenuClick, title}) {
    const { user, logout, hasPermission } = useAuth();
    const navigate = useNavigate();
    const [showDropdown, setShowDropdown] = useState(false);

    const handleLogout = async () => {
        await logout();
        navigate('/', { replace: true });
    };

    return (
        <header className="h-12 shrink-0 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center gap-3 px-4">
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
                <div className="relative">
                    <button
                        onClick={() => setShowDropdown((v) => !v)}
                        className="h-8 px-2 flex items-center gap-2 hover:opacity-80 rounded-none hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                        aria-label="Menu profil utilisateur"
                        aria-expanded={showDropdown}
                    >
                        <Avatar name={user?.identifiant || 'Utilisateur'} size="xs" shape="square" status="online" />
                        <span className="text-xs font-semibold hidden sm:block text-[var(--color-fg)]">
                            {user?.identifiant}
                        </span>
                    </button>

                    {showDropdown && (
                        <div className="absolute right-0 mt-2 w-48 border border-[var(--color-border)] bg-[var(--color-surface)] shadow-md z-40 p-2 space-y-2">
                            <div className="px-2 py-1 border-b border-[var(--color-border-subtle)] pb-2">
                                <p className="text-xs font-bold text-[var(--color-fg)] truncate">
                                    {user?.identifiant}
                                </p>
                                {user?.role && (
                                    <Badge variant="primary" size="sm" className="mt-1">
                                        {user.role}
                                    </Badge>
                                )}
                            </div>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={handleLogout}
                                className="w-full justify-start text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10"
                                iconLeft={<LogOut size={16} />}
                            >
                                Déconnexion
                            </Button>
                        </div>
                    )}
                </div>
            </div>
        </header>
    );
}