import { Menu, Sun, Moon, LogOut } from 'lucide-react';
import { useAuth } from '@/context/authContext.jsx';
import { useNavigate } from 'react-router-dom';
import { useState } from 'react';

export default function Topbar({ onMenuClick, onThemeToggle, isDarkMode, title }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [showDropdown, setShowDropdown] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className={`h-16 border-b flex items-center gap-4 px-6 sticky top-0 z-30 ${isDarkMode ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-100'}`}>
      <button onClick={onMenuClick} className="lg:hidden p-2 -ml-2 text-slate-500">
        <Menu size={20} />
      </button>

      <div className="flex-1">
        <h1 className={`text-lg font-bold ${isDarkMode ? 'text-slate-100' : 'text-slate-900'}`}>
          {title}
        </h1>
      </div>

      <div className="flex items-center gap-4">
        <button
          onClick={onThemeToggle}
          className={`p-2 rounded-xl transition-colors ${isDarkMode ? 'bg-slate-800 text-amber-300' : 'hover:bg-slate-100 text-slate-500'}`}
        >
          {isDarkMode ? <Sun size={18} /> : <Moon size={18} />}
        </button>

        <div className="relative">
          <button
            onClick={() => setShowDropdown(!showDropdown)}
            className="flex items-center gap-2 hover:opacity-75 transition"
          >
            <div className="w-8 h-8 rounded-full bg-blue-600 flex items-center justify-center text-white text-xs font-bold">
              {user?.firstname?.charAt(0)}{user?.lastname?.charAt(0)}
            </div>
            <span className={`text-sm font-medium hidden sm:block ${isDarkMode ? 'text-slate-200' : 'text-slate-700'}`}>
              {user?.firstname} {user?.lastname}
            </span>
          </button>

          {showDropdown && (
            <div className={`absolute right-0 mt-2 w-40 rounded-md shadow-lg z-40 ${isDarkMode ? 'bg-slate-800 border border-slate-700' : 'bg-white border border-slate-200'}`}>
              <div className={`px-4 py-2 border-b ${isDarkMode ? 'border-slate-700 text-slate-300' : 'border-slate-200 text-slate-600'}`}>
                <p className="text-xs">{user?.email}</p>
              </div>
              <button
                onClick={handleLogout}
                className={`w-full text-left px-4 py-2 flex items-center gap-2 transition ${isDarkMode ? 'hover:bg-slate-700 text-slate-200' : 'hover:bg-slate-100 text-slate-700'}`}
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