import { useState } from 'react';
import { Shield, Users, ArrowLeft, Menu, FileText, Award } from 'lucide-react';
import { Link } from 'react-router-dom';
import UserManagementTab from '@/components/parametres/securite/UserManagementTab';
import RolesAndPermissionsTab from '@/components/parametres/securite/RolesAndPermissionsTab';
import MenuManagementTab from '@/components/parametres/securite/MenuManagementTab';
import DocumentTypeManagementTab from '@/components/parametres/referentiels/DocumentTypeManagementTab';
import RefCarriereManagementTab from '@/components/parametres/referentiels/RefCarriereManagementTab';

export default function SecuriteAdminPanel() {
    const [activeTab, setActiveTab] = useState('users'); // 'users' | 'roles' | 'menus' | 'docTypes' | 'carriere'

    return (
        <div className="space-y-6">
            {/* Lien retour */}
            <Link to="/dashboard" className="inline-flex items-center gap-2 text-sm text-brand-500 hover:underline font-semibold transition-all">
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête de la page */}
            <div className="bg-white dark:bg-ink-900 rounded-sm shadow-sm border border-ink-150 dark:border-ink-700 p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-brand-500 text-white rounded-sm flex items-center justify-center shadow-md shadow-brand-500/10">
                        <Shield size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-ink-900 dark:text-white">Sécurité & Administration RH</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Gestion des utilisateurs, des rôles, des menus et des référentiels de documents et de carrière.</p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4 overflow-x-auto">
                    <button
                        onClick={() => setActiveTab('users')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'users'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Users size={16} />
                        Gestion des Utilisateurs
                    </button>
                    <button
                        onClick={() => setActiveTab('roles')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'roles'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Shield size={16} />
                        Rôles & Permissions
                    </button>
                    <button
                        onClick={() => setActiveTab('menus')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'menus'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Menu size={16} />
                        Gestion des Menus
                    </button>
                    <button
                        onClick={() => setActiveTab('docTypes')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'docTypes'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <FileText size={16} />
                        Types de Documents RH
                    </button>
                    <button
                        onClick={() => setActiveTab('carriere')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'carriere'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Award size={16} />
                        Référentiels Carrière & Paie
                    </button>
                </div>

                {/* Contenu de l'onglet actif */}
                <div className="p-6">
                    {activeTab === 'users' && <UserManagementTab />}
                    {activeTab === 'roles' && <RolesAndPermissionsTab />}
                    {activeTab === 'menus' && <MenuManagementTab />}
                    {activeTab === 'docTypes' && <DocumentTypeManagementTab />}
                    {activeTab === 'carriere' && <RefCarriereManagementTab />}
                </div>
            </div>
        </div>
    );
}
