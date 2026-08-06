import { useAuth } from '@/context/AuthContext';
import { User, ShieldAlert } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function Dashboard() {
    const { user } = useAuth();

    return (
        <div className="space-y-6">
            {/* Bannière de Bienvenue */}
            <div className="bg-white dark:bg-ink-900 rounded-xl shadow-sm border border-ink-150 dark:border-ink-700 p-8">
                <h2 className="text-2xl font-bold text-ink-900 dark:text-white mb-2">
                    Bienvenue, {user?.identifiant} !
                </h2>
                <p className="text-ink-500 dark:text-ink-400 max-w-2xl leading-relaxed">
                    Votre session est active. Votre Access Token est stocké de manière sécurisée en mémoire vive (RAM), 
                    tandis que votre Refresh Token est stocké dans un cookie HTTP-Only pour prévenir les failles XSS/CSRF.
                </p>
            </div>

            {/* Grille d'Informations */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Carte Session */}
                <div className="bg-white dark:bg-ink-900 rounded-xl shadow-sm border border-ink-150 dark:border-ink-700 p-6">
                    <h3 className="text-md font-bold text-ink-900 dark:text-white mb-4 flex items-center gap-2">
                        <User className="text-brand-500" size={18} />
                        Informations Session
                    </h3>
                    <div className="space-y-3 text-sm">
                        <div className="flex justify-between py-2 border-b border-ink-100 dark:border-ink-800">
                            <span className="text-ink-400">Identifiant</span>
                            <span className="font-semibold text-ink-800 dark:text-ink-100">{user?.identifiant}</span>
                        </div>
                        <div className="flex justify-between py-2 border-b border-ink-100 dark:border-ink-800">
                            <span className="text-ink-400">Rôle Assigné</span>
                            <span className="font-semibold text-brand-500">{user?.role}</span>
                        </div>
                        <div className="flex justify-between py-2">
                            <span className="text-ink-400">Statut Compte</span>
                            <span className="text-success-500 font-bold flex items-center gap-1.5">
                                <span className="w-2 h-2 rounded-full bg-success-500 inline-block animate-pulse"></span>
                                Actif (Stateless)
                            </span>
                        </div>
                    </div>
                </div>

                {/* Carte Fiches Employés */}
                <div className="bg-white dark:bg-ink-900 rounded-xl shadow-sm border border-ink-150 dark:border-ink-700 p-6">
                    <h3 className="text-md font-bold text-ink-900 dark:text-white mb-4 flex items-center gap-2">
                        <User className="text-blue-500" size={18} />
                        Gestion du Personnel
                    </h3>
                    <p className="text-ink-500 dark:text-ink-400 text-sm mb-6 leading-relaxed">
                        Accédez au répertoire complet des employés, consultez leurs fiches individuelles (état civil, CIN, conjoint, enfants, banques) et gérez leurs statuts.
                    </p>
                    <div className="flex">
                        <Link
                            to="/employes"
                            className="inline-flex items-center justify-center px-4 py-2.5 text-sm font-semibold bg-blue-600 hover:bg-blue-500 text-white rounded-lg transition-colors shadow-sm"
                        >
                            Accéder aux Fiches Employés
                        </Link>
                    </div>
                </div>

                {/* Carte Test Autorisation */}
                <div className="bg-white dark:bg-ink-900 rounded-xl shadow-sm border border-ink-150 dark:border-ink-700 p-6">
                    <h3 className="text-md font-bold text-ink-900 dark:text-white mb-4 flex items-center gap-2">
                        <ShieldAlert className="text-amber-500" size={18} />
                        Test d'Autorisation
                    </h3>
                    <p className="text-ink-500 dark:text-ink-400 text-sm mb-6 leading-relaxed">
                        Si vous possédez le rôle <strong>ROLE_ADMIN</strong>, vous pouvez accéder aux espaces d'administration. 
                        Sinon, la garde de route détectera l'insuffisance de droits et vous redirigera vers l'écran d'accès refusé.
                    </p>
                    <div className="flex">
                        <Link
                            to="/admin"
                            className="inline-flex items-center justify-center px-4 py-2.5 text-sm font-semibold bg-ink-800 dark:bg-ink-100 text-white dark:text-ink-950 rounded-lg hover:bg-ink-700 dark:hover:bg-white transition-colors"
                        >
                            Accéder à l'Espace Admin
                        </Link>
                    </div>
                </div>
            </div>
        </div>
    );
}
