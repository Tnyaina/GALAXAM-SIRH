import { useState } from 'react';
import { Building2, Layers, Briefcase, FileCheck, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import FilialesSitesTab from '@/components/parametres/structure/FilialesSitesTab';
import DepartementsServicesTab from '@/components/parametres/structure/DepartementsServicesTab';
import FonctionsTab from '@/components/parametres/structure/FonctionsTab';
import ReferentielsRhTab from '@/components/parametres/structure/ReferentielsRhTab';

export default function StructureAdminPanel() {
    const [activeTab, setActiveTab] = useState('filiales'); // 'filiales' | 'departements' | 'fonctions' | 'rh'

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
                        <Building2 size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-ink-900 dark:text-white">Structure Organisationnelle & Référentiels RH</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Gestion des filiales, des sites d'exploitation, départements, services, fonctions et types de contrat.</p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4 overflow-x-auto">
                    <button
                        onClick={() => setActiveTab('filiales')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'filiales'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Building2 size={16} />
                        Filiales & Sites
                    </button>
                    <button
                        onClick={() => setActiveTab('departements')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'departements'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Layers size={16} />
                        Départements & Services
                    </button>
                    <button
                        onClick={() => setActiveTab('fonctions')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'fonctions'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Briefcase size={16} />
                        Postes & Fonctions
                    </button>
                    <button
                        onClick={() => setActiveTab('rh')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'rh'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <FileCheck size={16} />
                        Référentiels RH & Banques
                    </button>
                </div>

                {/* Contenu de l'onglet actif */}
                <div className="p-6">
                    {activeTab === 'filiales' && <FilialesSitesTab />}
                    {activeTab === 'departements' && <DepartementsServicesTab />}
                    {activeTab === 'fonctions' && <FonctionsTab />}
                    {activeTab === 'rh' && <ReferentielsRhTab />}
                </div>
            </div>
        </div>
    );
}
