import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import {
  Folder, LayoutDashboard, ShoppingBag, Terminal, Users, X, Zap,
  ChevronDown, MapPin, Package, Tag, BarChart2,
  Import
} from 'lucide-react';

const navItems = [
  {
    section: 'Boutique',
    items: [
      { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
      { to: '/stats', icon: LayoutDashboard, label: 'Stats' },
  
      {
        icon: ShoppingBag,
        label: 'Catalogue',
        children: [
          //{ to: '/products', icon: Package, label: 'Produits' },
          //{ to: '/categories', icon: Folder, label: 'Catégories' },
          { to: '/stock', icon: Tag, label: 'Stock' },
        ]
      },
      // {
      //   icon: Users,
      //   label: 'Clients',
      //   children: [
      //     { to: '/customers', icon: Users, label: 'Clients' },
      //     { to: '/address', icon: MapPin, label: 'Adresses' },
      //   ]
      // },
      { to: '/orders', icon: ShoppingBag, label: 'Commandes' },
    ]
  },
  {
    section: 'Développement',
    items: [
      { to: '/reset', icon: Terminal, label: 'Reset data' },
      {to : '/import' , icon: Import, label: 'Import data'}
    ]
  },
];

function NavItem({ item, onClose }) {
  const [expanded, setExpanded] = useState(false);

  if (item.children) {
    return (
      <li>
        <button
          onClick={() => setExpanded((p) => !p)}
          className="w-full flex items-center gap-3 px-3 py-2.5 text-sm font-medium text-[#b0b0b0] hover:bg-[#3a3a3a] hover:text-white transition-colors"
        >
          <item.icon size={16} className="shrink-0" />
          <span className="flex-1 text-left">{item.label}</span>
          <ChevronDown
            size={14}
            className={`shrink-0 transition-transform duration-200 ${expanded ? 'rotate-180' : ''}`}
          />
        </button>

        {expanded && (
          <ul className="mt-0.5 border-l-2 border-[#4a4a4a] ml-[22px] space-y-0.5">
            {item.children.map((child) => (
              <li key={child.to}>
                <NavLink
                  to={child.to}
                  end
                  onClick={onClose}
                  className={({ isActive }) =>
                    `flex items-center gap-3 pl-4 pr-3 py-2 text-sm font-medium transition-colors ${
                      isActive
                        ? 'bg-[#2196f3] text-white border-l-2 border-[#64b5f6] -ml-[2px]'
                        : 'text-[#999] hover:bg-[#3a3a3a] hover:text-white'
                    }`
                  }
                >
                  <child.icon size={14} className="shrink-0" />
                  <span>{child.label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        )}
      </li>
    );
  }

  return (
    <li>
      <NavLink
        to={item.to}
        end={item.to === '/'}
        onClick={onClose}
        className={({ isActive }) =>
          `flex items-center gap-3 px-3 py-2.5 text-sm font-medium transition-colors ${
            isActive
              ? 'bg-[#2196f3] text-white'
              : 'text-[#b0b0b0] hover:bg-[#3a3a3a] hover:text-white'
          }`
        }
      >
        <item.icon size={16} className="shrink-0" />
        <span>{item.label}</span>
      </NavLink>
    </li>
  );
}

export default function Sidebar({ open, onClose }) {
  return (
    <>
      {open && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`fixed top-0 left-0 h-full w-[260px] z-50 flex flex-col lg:translate-x-0 ${
          open ? 'translate-x-0' : '-translate-x-full'
        } transition-transform duration-300`}
        style={{ backgroundColor: '#2d2d2d', borderRight: '1px solid #3a3a3a' }}
      >
        {/* Logo */}
        <div
          className="flex items-center justify-between px-5 py-4"
          style={{ borderBottom: '1px solid #3a3a3a' }}
        >
          <div className="flex items-center gap-3">
            <div
              className="w-8 h-8 flex items-center justify-center text-white"
              style={{ backgroundColor: '#2196f3' }}
            >
              <Zap size={16} />
            </div>
            <span className="text-base font-bold text-white tracking-tight">
              PrestaReact
            </span>
          </div>
          <button
            onClick={onClose}
            className="lg:hidden p-1 text-[#888] hover:text-white transition-colors"
          >
            <X size={18} />
          </button>
        </div>

        {/* Nav */}
        <nav className="flex-1 overflow-y-auto py-3">
          {navItems.map(({ section, items }) => (
            <div key={section} className="mb-4">
              <p
                className="px-4 mb-1 text-[10px] font-bold uppercase tracking-widest"
                style={{ color: '#666' }}
              >
                {section}
              </p>
              <ul className="space-y-0.5">
                {items.map((item) => (
                  <NavItem
                    key={item.to || item.label}
                    item={item}
                    onClose={onClose}
                  />
                ))}
              </ul>
            </div>
          ))}
        </nav>

        {/* Footer */}
        <div
          className="px-4 py-3 text-[11px]"
          style={{ borderTop: '1px solid #3a3a3a', color: '#555' }}
        >
          PrestaShop 8.2.6
        </div>
      </aside>
    </>
  );
}