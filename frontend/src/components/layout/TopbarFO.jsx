import { useState } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useCustomerAuth } from '@/context/customerAuthContext.jsx';
import { useCart } from '@/context/cartContext.jsx';
import { ChevronDown, Home, LogOut, Menu, Package, ShoppingCart, X } from 'lucide-react';

// ─── Config ────────────────────────────────────────────────────────────────
const topbarFONavConfig = {
    navItems: [
        { id: 'home', label: 'Catalogue', route: '/FO/products', icon: Home },
        { id: 'orders', label: 'Mes commandes', route: '/FO/orders', icon: Package },
    ],
    account: {
        label: 'Compte client',
        items: [
            { id: 'logout', type: 'action', action: 'logout', label: 'Déconnexion', icon: LogOut },
        ],
    },
};

function NavItemFO({ item, isDarkMode }) {
    const navigate = useNavigate();
    const { pathname } = useLocation();
    const isActive = pathname === item.route || pathname.startsWith(item.route + '/');
    const Icon = item.icon;

    return (
        <button
            onClick={() => navigate(item.route)}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold tracking-tight transition-all duration-200 ${isActive
                ? isDarkMode
                    ? 'bg-slate-800 text-white border border-slate-700 shadow-sm'
                    : 'bg-blue-50/80 text-blue-600 shadow-[0_2px_10px_-3px_rgba(37,99,235,0.15)] border border-blue-100/50'
                : isDarkMode
                    ? 'text-slate-400 hover:bg-slate-800 hover:text-white border border-transparent'
                    : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900 border border-transparent'
                }`}
        >
            {Icon && <Icon size={17} className={`transition-colors ${isActive ? 'text-blue-600' : 'text-slate-400'}`} />}
            {item.label}
        </button>
    );
}

function AccountDropdown({ isDarkMode, user, onAction }) {
    const [open, setOpen] = useState(false);
    const { account } = topbarFONavConfig;

    const isAnon = user?.isAnonymous || !user?.id;
    const initials = isAnon ? '?' : ((user?.firstname?.charAt(0) ?? '') + (user?.lastname?.charAt(0) ?? '')).toUpperCase();

    return (
        <div className="relative">
            <button
                onClick={() => setOpen((p) => !p)}
                className={`flex items-center gap-2.5 p-1.5 pr-3.5 rounded-xl border transition-all duration-200 shadow-sm ${
                    isDarkMode 
                        ? 'bg-slate-800 border-slate-700 text-slate-200 hover:bg-slate-750' 
                        : 'bg-white border-slate-100 text-slate-700 hover:bg-slate-50'
                }`}
            >
                <div className={`w-8 h-8 rounded-lg flex items-center justify-center text-white text-xs font-black shadow-sm tracking-wider ${
                    isAnon ? 'bg-gradient-to-br from-slate-400 to-slate-500' : 'bg-gradient-to-br from-blue-500 to-indigo-600'
                }`}>
                    {initials}
                </div>
                <div className="flex flex-col items-start leading-tight text-left hidden sm:flex pr-1">
                    <span className="text-[9px] text-slate-400 uppercase font-bold tracking-widest">
                        {isAnon ? 'Session' : 'Client'}
                    </span>
                    <span className="text-sm font-bold truncate max-w-[130px]">
                        {isAnon ? 'Anonyme' : `${user?.firstname} ${user?.lastname}`}
                    </span>
                </div>
                <ChevronDown
                    size={13}
                    className={`transition-transform duration-300 ${open ? 'rotate-180' : ''} text-slate-400 ml-0.5`}
                />
            </button>

            {open && (
                <>
                    {/* Backdrop */}
                    <div className="fixed inset-0 z-30" onClick={() => setOpen(false)} />

                    <div
                        className={`absolute right-0 mt-2 w-56 rounded-xl shadow-xl border z-40 overflow-hidden animate-in fade-in slide-in-from-top-2 duration-200 ${isDarkMode
                            ? 'bg-slate-900 border-slate-800 text-slate-200'
                            : 'bg-white border-slate-100 text-slate-700'
                            }`}
                    >
                        {/* Header */}
                        <div
                            className={`px-4 py-3 border-b ${isDarkMode
                                ? 'border-slate-800 bg-slate-900/50'
                                : 'border-slate-100 bg-slate-50/50'
                                }`}
                        >
                            <p className="text-[10px] font-black text-slate-400 uppercase tracking-wider mb-0.5">{account.label}</p>
                            <p className="text-sm font-bold truncate">
                                {isAnon ? 'Utilisateur Anonyme' : `${user?.firstname} ${user?.lastname}`}
                            </p>
                            {user?.email && <p className="text-xs text-slate-400 truncate mt-0.5">{user.email}</p>}
                        </div>

                        {/* Items */}
                        <div className="p-1.5">
                            {account.items.map((item) => {
                                if (item.type === 'separator') {
                                    return (
                                        <div
                                            key={item.id}
                                            className={`h-px my-1.5 ${isDarkMode ? 'bg-slate-800' : 'bg-slate-100'
                                                }`}
                                        />
                                    );
                                }

                                if (item.type === 'action') {
                                    const Icon = item.icon;
                                    return (
                                        <button
                                            key={item.id}
                                            onClick={() => {
                                                setOpen(false);
                                                onAction(item.action);
                                            }}
                                            className={`w-full text-left px-3 py-2 flex items-center gap-2.5 text-sm font-semibold rounded-lg transition ${isDarkMode
                                                ? 'hover:bg-slate-800 text-slate-300 hover:text-white'
                                                : 'hover:bg-rose-50 text-slate-700 hover:text-rose-600'
                                                }`}
                                        >
                                            {Icon && <Icon size={16} className="opacity-70" />}
                                            {item.label}
                                        </button>
                                    );
                                }

                                return null;
                            })}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}

export default function TopbarFO({ isDarkMode = false }) {
    const { user, logout } = useCustomerAuth();
    const { getItemCount } = useCart();
    const navigate = useNavigate();
    const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
    const { navItems } = topbarFONavConfig;
    const cartCount = getItemCount();

    const handleAction = (action) => {
        if (action === 'logout') {
            logout();
            navigate('/FO');
        }
    };

    const isRestricted = user?.isAnonymous || String(user?.id) === '1';
    const visibleNavItems = navItems.filter(item => !(isRestricted && item.id === 'orders'));

    return (
        <header
            className={`sticky top-0 z-30 backdrop-blur-md border-b transition-colors duration-300 ${isDarkMode
                ? 'bg-slate-950/90 border-slate-800'
                : 'bg-white/85 border-slate-100'
                }`}
        >
            <div className="h-16 flex items-center gap-4 px-6 max-w-7xl mx-auto">
                {/* Brand Logo */}
                <div 
                    className="flex items-center gap-2.5 shrink-0 cursor-pointer select-none group"
                    onClick={() => navigate('/FO/products')}
                >
                    <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center text-white shadow-md shadow-blue-600/20 transition-transform group-hover:scale-105 duration-300">
                        <ShoppingCart size={18} className="fill-white/10" />
                    </div>
                    <span
                        className={`text-lg font-black tracking-tight transition-colors ${isDarkMode ? 'text-slate-100' : 'text-slate-900'}`}
                    >
                        Presta<span className="text-blue-600">React</span>
                    </span>
                </div>

                {/* Separator */}
                <div className={`h-5 w-px hidden md:block mx-2 ${isDarkMode ? 'bg-slate-800' : 'bg-slate-200'}`} />

                {/* Nav desktop */}
                {visibleNavItems.length > 0 && (
                    <nav className="hidden md:flex items-center gap-1 flex-1">
                        {visibleNavItems.map((item) => (
                            <NavItemFO key={item.id} item={item} isDarkMode={isDarkMode} />
                        ))}
                    </nav>
                )}

                <div className="flex-1 md:hidden" />

                {/* Actions Right */}
                <div className="flex items-center gap-3">
                    {/* Cart button */}
                    <button
                        onClick={() => navigate('/FO/cart')}
                        className={`relative p-2.5 rounded-xl border transition-all duration-200 shadow-sm group bg-white ${isDarkMode
                            ? 'text-slate-300 bg-slate-800 border-slate-700 hover:bg-slate-750'
                            : 'text-slate-600 border-slate-100 hover:bg-slate-50 hover:border-slate-200'
                            }`}
                        title="Voir le panier"
                    >
                        <ShoppingCart size={20} className="group-hover:scale-105 transition-transform group-hover:text-blue-600 duration-200" />
                        {cartCount > 0 && (
                            <span className="absolute -top-2 -right-2 flex h-6 w-6">
                                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-rose-500 opacity-75"></span>
                                <span className="relative inline-flex rounded-full h-6 w-6 bg-gradient-to-br from-rose-500 to-red-600 text-white text-[10px] font-black items-center justify-center border-2 border-white shadow-md select-none">
                                    {cartCount > 99 ? '99+' : cartCount}
                                </span>
                            </span>
                        )}
                    </button>

                    {/* Account */}
                    <AccountDropdown
                        isDarkMode={isDarkMode}
                        user={user}
                        onAction={handleAction}
                    />

                    {/* Mobile menu toggle */}
                    {visibleNavItems.length > 0 && (
                        <button
                            className={`md:hidden p-2.5 rounded-xl border transition-all ${isDarkMode 
                                ? 'bg-slate-800 border-slate-700 text-slate-300 hover:bg-slate-750' 
                                : 'bg-white border-slate-100 text-slate-600 hover:bg-slate-50'
                            }`}
                            onClick={() => setMobileMenuOpen((p) => !p)}
                        >
                            {mobileMenuOpen ? (
                                <X size={20} />
                            ) : (
                                <Menu size={20} />
                            )}
                        </button>
                    )}
                </div>
            </div>

            {/* Mobile nav */}
            {mobileMenuOpen && visibleNavItems.length > 0 && (
                <nav
                    className={`md:hidden border-t px-4 py-3 flex flex-col gap-1.5 shadow-inner animate-in slide-in-from-top duration-200 ${isDarkMode
                        ? 'border-slate-800 bg-slate-950'
                        : 'border-slate-100 bg-slate-50/95 backdrop-blur-md'
                        }`}
                >
                    {visibleNavItems.map((item) => (
                        <NavItemFO
                            key={item.id}
                            item={item}
                            isDarkMode={isDarkMode}
                        />
                    ))}
                </nav>
            )}
        </header>
    );
}