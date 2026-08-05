import { useState } from 'react';
import { Wrench, ShieldCheck, Gift, Clock, Calendar, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import CotisationsIrsaTab from '@/components/admin/paie/CotisationsIrsaTab';
import IndemnitesSitesTab from '@/components/admin/paie/IndemnitesSitesTab';
import MajorationsParametresTab from '@/components/admin/paie/MajorationsParametresTab';
import CalendrierFerieTab from '@/components/admin/paie/CalendrierFerieTab';

export default function PaieConfigAdminPanel() {
    const [activeTab, setActiveTab] = useState('cotisations_irsa'); // 'cotisations_irsa' | 'indemnites_sites' | 'majorations_params' | 'calendrier'

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
                        <Wrench size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-ink-900 dark:text-white">Paramétrage Paie & Barèmes Fiscaux</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Gestion des cotisations sociales, du barème IRSA, des indemnités par site, des majorations, calendrier des jours fériés et paramètres globaux.</p>
                    </div>
                </div>
            </div>

            {/* Sélecteur d'onglets principal */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4 overflow-x-auto">
                    <button
                        onClick={() => setActiveTab('cotisations_irsa')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'cotisations_irsa'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <ShieldCheck size={16} />
                        Cotisations & Barème IRSA
                    </button>
                    <button
                        onClick={() => setActiveTab('indemnites_sites')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'indemnites_sites'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Gift size={16} />
                        Indemnités & Tarifs par Site
                    </button>
                    <button
                        onClick={() => setActiveTab('majorations_params')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'majorations_params'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Clock size={16} />
                        Majorations & Paramètres Globaux
                    </button>
                    <button
                        onClick={() => setActiveTab('calendrier')}
                        className={`
                            flex items-center gap-2 py-4 px-4 border-b-2 text-sm font-semibold transition-all cursor-pointer whitespace-nowrap
                            ${activeTab === 'calendrier'
                                ? 'border-brand-500 text-brand-500'
                                : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                            }
                        `}
                    >
                        <Calendar size={16} />
                        Calendrier Jours Fériés & Chômés
                    </button>
                </div>

                {/* Contenu de l'onglet actif */}
                <div className="p-6">
                    {activeTab === 'cotisations_irsa' && <CotisationsIrsaTab />}
                    {activeTab === 'indemnites_sites' && <IndemnitesSitesTab />}
                    {activeTab === 'majorations_params' && <MajorationsParametresTab />}
                    {activeTab === 'calendrier' && <CalendrierFerieTab />}
                </div>
            </div>
        </div>
    );
}
