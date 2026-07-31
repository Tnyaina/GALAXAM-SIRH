import { Menu, LogOut } from 'lucide-react';
import { useAuth } from '@/context/AuthContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';

export default function Topbar({ onMenuClick, title }) {
    const { user, logout } = useAuth();
    const navigate = useNavigate();
    const [showDropdown, setShowDropdown] = useState(false);

    const handleLogout = () => {
        logout();
        navigate('/');
    };

    return (
        <header className="h-16 shrink-0 border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 flex items-center gap-4 px-6">
            <button onClick={onMenuClick} className="lg:hidden p-2 -ml-2 text-ink-400">
                <Menu size={20} />
            </button>

            <div className="flex-1 min-w-0">
                <h1 className="text-lg font-bold text-ink-800 dark:text-ink-50 truncate">
                    {title}
                </h1>
            </div>

            <div className="flex items-center gap-4">

                <div className="relative">
                    <button
                        onClick={() => setShowDropdown((v) => !v)}
                        className="flex items-center gap-2 hover:opacity-75 transition"
                    >
                        <div className="w-8 h-8 flex items-center justify-center bg-brand-500 text-white text-xs font-bold shrink-0">
                            {user?.identifiant?.substring(0, 2).toUpperCase()}
                        </div>
                        <span className="text-sm font-medium hidden sm:block text-ink-700 dark:text-ink-200">
                            {user?.identifiant}
                        </span>
                    </button>

                    {showDropdown && (
                        <div className="absolute right-0 mt-2 w-44 border border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 shadow-md z-40">
                            <div className="px-4 py-2 border-b border-ink-150 dark:border-ink-700">
                                <p className="text-xs text-ink-500 dark:text-ink-300 truncate">
                                    {user?.role}
                                </p>
                            </div>
                            <button
                                onClick={handleLogout}
                                className="w-full text-left px-4 py-2 flex items-center gap-2 text-ink-700 dark:text-ink-200 hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors"
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
