import { useAuth } from '@/context/AuthContext';
import { ArrowLeft, Shield } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function AdminPanel() {
    const { user } = useAuth();

    return (
        <div className="space-y-6">
            <Link to="/dashboard" className="inline-flex items-center gap-2 text-sm text-brand-500 hover:underline font-semibold">
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            <div className="bg-white dark:bg-ink-900 rounded-xl shadow-sm border border-ink-150 dark:border-ink-700 p-8">
                <div className="flex items-center gap-3 mb-6">
                    <div className="w-12 h-12 bg-ink-800 dark:bg-ink-100 text-white dark:text-ink-950 rounded-lg flex items-center justify-center">
                        <Shield size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-ink-900 dark:text-white">Espace Administrateur</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Zone de haute sécurité (Accès réservé)</p>
                    </div>
                </div>

                <div className="border-t border-ink-150 dark:border-ink-700 pt-6">
                    <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-lg p-4 mb-6 text-sm">
                        Félicitations ! Vous êtes connecté en tant que <strong>{user?.identifiant}</strong> avec l'autorité <strong>{user?.role}</strong>. L'accès vous a été accordé.
                    </div>

                    <h2 className="text-lg font-bold text-ink-900 dark:text-white mb-3">Logs et Contrôle DAF / RH</h2>
                    <p className="text-ink-500 dark:text-ink-400 text-sm leading-relaxed">
                        Cet espace permettra prochainement de gérer les comptes utilisateurs, configurer les matrices de permissions et visualiser les journaux de connexion et d'audit.
                    </p>
                </div>
            </div>
        </div>
    );
}
