import {
    Circle,
    LayoutDashboard,
    Users,
    Clock,
    UserCheck,
    Truck,
    Package,
    Boxes,
    Shield,
    Settings,
    ChevronDown,
    X,
    Menu,
    Sun,
    Moon,
    LogOut,
    User,
    ShieldAlert,
    ArrowLeft,
    AlertCircle,
    CheckCircle,
    Info,
    WifiOff,
    Wrench,
    Home,
} from 'lucide-react';

// Registre de résolution dynamique des icônes depuis leur nom (string stocké en BDD dans ref_menu.icone)
// Fallback sur Circle si l'icône est inconnue ou vide.
const ICON_MAP = {
    Circle,
    LayoutDashboard,
    Users,
    Clock,
    UserCheck,
    Truck,
    Package,
    Boxes,
    Shield,
    Settings,
    ChevronDown,
    X,
    Menu,
    Sun,
    Moon,
    LogOut,
    User,
    ShieldAlert,
    ArrowLeft,
    AlertCircle,
    CheckCircle,
    Info,
    WifiOff,
    Wrench,
    Home,
};

export function getMenuIcon(name) {
    if (!name) return Circle;
    return ICON_MAP[name] || Circle;
}

