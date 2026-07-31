import { useAuth } from '@/context/AuthContext';
import { ArrowLeft, Shield } from 'lucide-react';

export default function AdminPanel() {
    const { user } = useAuth();

    return (
        <div className="min-h-screen bg-[#f3f4f6] text-neutral-800 font-sans p-8">
            <div className="max-w-4xl mx-auto">
                <a href="/dashboard" className="inline-flex items-center gap-2 text-sm text-[#1A82B1] hover:underline mb-6 font-semibold">
                    <ArrowLeft size={16} />
                    Retour au Tableau de bord
                </a>

                <div className="bg-white rounded-xl shadow-sm border border-neutral-200 p-8">
                    <div className="flex items-center gap-3 mb-6">
                        <div className="w-12 h-12 bg-neutral-900 text-white rounded-lg flex items-center justify-center">
                            <Shield size={24} />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-neutral-900">Espace Administrateur</h1>
                            <p className="text-xs text-neutral-500">Zone de haute sécurité (Accès réservé)</p>
                        </div>
                    </div>

                    <div className="border-t border-neutral-100 pt-6">
                        <div className="bg-green-50 text-green-800 border border-green-200 rounded-lg p-4 mb-6 text-sm">
                            Félicitations ! Vous êtes connecté en tant que <strong>{user?.identifiant}</strong> avec l'autorité <strong>{user?.role}</strong>. L'accès vous a été accordé.
                        </div>

                        <h2 className="text-lg font-bold mb-3">Logs et Contrôle DAF / RH</h2>
                        <p className="text-neutral-600 text-sm">
                            Cet espace permettrait de gérer les comptes utilisateurs, configurer les matrices de permissions et visualiser le journal de connexion.
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
