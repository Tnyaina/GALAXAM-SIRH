import { useAuth } from '@/context/AuthContext';
import { LogOut, User, ShieldAlert, Award } from 'lucide-react';

export default function Dashboard() {
    const { user, logout } = useAuth();

    return (
        <div className="min-h-screen bg-[#f3f4f6] text-neutral-800 font-sans">
            <header className="bg-white border-b border-neutral-200 px-6 py-4 flex items-center justify-between shadow-sm">
                <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-[#1A82B1]/10 rounded-lg flex items-center justify-center text-[#1A82B1]">
                        <Award size={22} />
                    </div>
                    <div>
                        <h1 className="text-lg font-bold text-neutral-900">GALAXAM SIRH</h1>
                        <p className="text-xs text-neutral-500">Logistique & Authentification</p>
                    </div>
                </div>
                
                <div className="flex items-center gap-4">
                    <div className="flex items-center gap-2 bg-neutral-50 px-3 py-1.5 rounded-lg border border-neutral-200">
                        <User size={16} className="text-neutral-500" />
                        <span className="text-sm font-semibold text-neutral-700">{user?.identifiant}</span>
                        <span className="text-xs bg-[#1A82B1] text-white px-2 py-0.5 rounded-full font-bold">
                            {user?.role?.replace('ROLE_', '')}
                        </span>
                    </div>
                    
                    <button 
                        onClick={logout}
                        className="flex items-center gap-2 text-sm font-semibold text-red-600 hover:text-red-700 hover:bg-red-50 px-3 py-1.5 rounded-lg border border-transparent hover:border-red-100 transition-all cursor-pointer"
                    >
                        <LogOut size={16} />
                        Déconnexion
                    </button>
                </div>
            </header>

            <main className="max-w-6xl mx-auto px-6 py-10">
                <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-8 mb-8">
                    <h2 className="text-2xl font-bold text-neutral-900 mb-2">Bienvenue, {user?.identifiant} !</h2>
                    <p className="text-neutral-600">
                        Votre session est active. Votre Access Token est stocké de manière sécurisée en mémoire vive, 
                        tandis que votre Refresh Token est stocké dans un cookie HTTP-Only.
                    </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6">
                        <h3 className="text-md font-bold text-neutral-900 mb-4 flex items-center gap-2">
                            <User className="text-[#1A82B1]" size={18} />
                            Informations Session
                        </h3>
                        <div className="space-y-3 text-sm">
                            <div className="flex justify-between py-2 border-b border-neutral-100">
                                <span className="text-neutral-500">Identifiant</span>
                                <span className="font-semibold">{user?.identifiant}</span>
                            </div>
                            <div className="flex justify-between py-2 border-b border-neutral-100">
                                <span className="text-neutral-500">Rôle Assigné</span>
                                <span className="font-semibold text-[#1A82B1]">{user?.role}</span>
                            </div>
                            <div className="flex justify-between py-2">
                                <span className="text-neutral-500">Statut Compte</span>
                                <span className="text-green-600 font-bold flex items-center gap-1">
                                    <span className="w-2 h-2 rounded-full bg-green-500 inline-block animate-pulse"></span>
                                    Actif (Stateless)
                                </span>
                            </div>
                        </div>
                    </div>

                    <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-6">
                        <h3 className="text-md font-bold text-neutral-900 mb-4 flex items-center gap-2">
                            <ShieldAlert className="text-amber-500" size={18} />
                            Test d'Autorisation
                        </h3>
                        <p className="text-neutral-600 text-sm mb-4">
                            Si vous possédez le rôle <strong>ROLE_ADMIN</strong>, vous pouvez accéder aux espaces d'administration. 
                            Sinon, vous serez redirigé vers l'écran d'accès refusé.
                        </p>
                        <div className="flex gap-3">
                            <a
                                href="/admin"
                                className="inline-flex items-center justify-center px-4 py-2 text-sm font-semibold bg-neutral-900 text-white rounded-lg hover:bg-neutral-800 transition-colors"
                            >
                                Accéder à l'Espace Admin
                            </a>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
}
