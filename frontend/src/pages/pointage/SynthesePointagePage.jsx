import { useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { 
    Clock, 
    RefreshCw, 
    ArrowLeft, 
    Search, 
    Building2, 
    Calendar,
    Filter,
    CalendarRange,
    FileSpreadsheet
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { usePointageSynthese } from '@/hooks/pointage/usePointageSynthese';

// Composants Design System Galaxam
import Card, { CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SEO from '@/components/system/SEO';
import { formatDate } from '@/utils/pointageUtils';

// Onglets
import SyntheseMensuelleTab from '@/components/pointage/SyntheseMensuelleTab';
import SyntheseHebdomadaireTab from '@/components/pointage/SyntheseHebdomadaireTab';

export default function SynthesePointagePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeTab = searchParams.get('tab') || 'mensuelle';

    const handleTabChange = (tabId) => {
        setSearchParams({ tab: tabId });
    };

    const { hasPermission } = useAuth();
    const canViewEmploye = hasPermission('EMPLOYE_READ') || hasPermission('POINTAGE_READ');

    const {
        periodes,
        sites,
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        searchTerm,
        setSearchTerm,
        filterCategorie,
        setFilterCategorie,
        currentPeriode,

        // Mensuel
        syntheseList,
        loading,
        error,
        stats,
        paginatedList,
        currentPage,
        setCurrentPage,
        pageSize,
        setPageSize,
        sortBy,
        sortOrder,
        toggleSort,
        loadSynthese,

        // Hebdomadaire
        syntheseHebdoList,
        selectedSemaine,
        setSelectedSemaine,
        loadingHebdo,
        statsHebdo,
        paginatedHebdoList,
        currentHebdoPage,
        setCurrentHebdoPage,
        hebdoPageSize,
        setHebdoPageSize,
        hebdoSortBy,
        hebdoSortOrder,
        toggleHebdoSort,
        loadSyntheseHebdo,

        // Détail modale
        selectedEmploye,
        setSelectedEmploye,
        troncons,
        loadingTroncons,
        loadTroncons
    } = usePointageSynthese();

    const handleRefreshAll = () => {
        loadSynthese();
        loadSyntheseHebdo();
    };

    const tabsList = [
        {
            id: 'mensuelle',
            label: 'Synthèse Mensuelle (Globale)',
            icon: FileSpreadsheet,
            badge: syntheseList.length > 0 ? syntheseList.length : undefined
        },
        {
            id: 'hebdomadaire',
            label: 'Synthèse Hebdomadaire (Compensation & HS)',
            icon: CalendarRange,
            badge: syntheseHebdoList.length > 0 ? syntheseHebdoList.length : undefined
        }
    ];

    return (
        <div className="space-y-6 font-sans">
            <SEO title="Synthèse des Pointages" description="Synthèse mensuelle et hebdomadaire, compensation des retards et calcul des indemnités." />

            {/* Lien retour */}
            <Link
                to="/dashboard"
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold"
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête de la page */}
            <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-6 sm:p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-none blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />

                <div className="relative flex items-center gap-4">
                    <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
                        <Clock size={28} strokeWidth={2.2} />
                    </div>
                    <div>
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">
                                Synthèse des Pointages
                            </h1>
                            {currentPeriode && (
                                <Badge variant={currentPeriode.statutCode === 'VALIDEE' ? 'success' : 'brand'} size="md">
                                    {currentPeriode.code} ({currentPeriode.statutCode})
                                </Badge>
                            )}
                        </div>
                        <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
                            Calcul légal, compensation hebdomadaire des retards, ventilation HS et barèmes d'indemnités.
                        </p>
                    </div>
                </div>

                <div className="relative flex items-center gap-3 shrink-0">
                    <Button
                        onClick={handleRefreshAll}
                        variant="primary"
                        size="sm"
                        icon={<RefreshCw size={14} className={loading || loadingHebdo ? 'animate-spin' : ''} />}
                    >
                        {loading || loadingHebdo ? 'Calcul en cours...' : 'Recalculer la synthèse'}
                    </Button>
                </div>
            </div>

            {/* Barre d'outils et de filtres */}
            <Card className="rounded-none shadow-sm border border-[var(--color-border-subtle)]">
                <CardBody className="p-4 flex flex-wrap items-center justify-between gap-4">
                    <div className="flex flex-wrap items-center gap-3 flex-1">
                        {/* Sélecteur de période de paie */}
                        <div className="flex items-center gap-2 min-w-[240px]">
                            <Calendar size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                            <select
                                value={selectedPeriode}
                                onChange={(e) => setSelectedPeriode(e.target.value)}
                                className="w-full bg-[var(--color-surface)] border border-[var(--color-border)] px-3 py-1.5 text-xs font-semibold rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                            >
                                {periodes.map((p) => (
                                    <option key={p.idPeriodePaie} value={p.idPeriodePaie}>
                                        {p.code} ({formatDate(p.dateDebut)} au {formatDate(p.dateFin)}) - {p.statutCode}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {/* Sélecteur de site */}
                        <div className="flex items-center gap-2 min-w-[180px]">
                            <Building2 size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                            <select
                                value={selectedSite}
                                onChange={(e) => setSelectedSite(e.target.value)}
                                className="w-full bg-[var(--color-surface)] border border-[var(--color-border)] px-3 py-1.5 text-xs font-semibold rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                            >
                                <option value="">Tous les sites</option>
                                {sites.map((s) => (
                                    <option key={s.idSite} value={s.idSite}>
                                        {s.libelle}
                                    </option>
                                ))}
                            </select>
                        </div>

                        {/* Filtre Catégorie */}
                        <div className="flex items-center gap-2 min-w-[170px]">
                            <Filter size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                            <select
                                value={filterCategorie}
                                onChange={(e) => setFilterCategorie(e.target.value)}
                                className="w-full bg-[var(--color-surface)] border border-[var(--color-border)] px-3 py-1.5 text-xs font-semibold rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                            >
                                <option value="ALL">Toutes catégories</option>
                                <option value="NON_CADRE">Non-Cadres (HS payées)</option>
                                <option value="CADRE">Cadres (Forfait 8h)</option>
                            </select>
                        </div>

                        {/* Recherche Salarié */}
                        <div className="relative min-w-[220px]">
                            <Input
                                placeholder="Rechercher par nom, matricule..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                size="sm"
                                prefix={<Search size={14} className="text-[var(--color-fg-muted)]" />}
                                className="w-full"
                            />
                        </div>
                    </div>
                </CardBody>
            </Card>

            {/* Système d'Onglets Galaxam */}
            <div className="space-y-4">
                <Tabs
                    tabs={tabsList}
                    activeTab={activeTab}
                    onChange={handleTabChange}
                />

                {/* Vue Onglet 1 : Synthèse Mensuelle Globale */}
                {activeTab === 'mensuelle' && (
                    <SyntheseMensuelleTab
                        syntheseList={syntheseList}
                        paginatedList={paginatedList}
                        stats={stats}
                        loading={loading}
                        canViewEmploye={canViewEmploye}
                        sortBy={sortBy}
                        sortOrder={sortOrder}
                        toggleSort={toggleSort}
                        currentPage={currentPage}
                        setCurrentPage={setCurrentPage}
                        pageSize={pageSize}
                        setPageSize={setPageSize}
                        onOpenTroncons={loadTroncons}
                    />
                )}

                {/* Vue Onglet 2 : Synthèse Hebdomadaire (Compensation & Tronçons) */}
                {activeTab === 'hebdomadaire' && (
                    <SyntheseHebdomadaireTab
                        syntheseHebdoList={syntheseHebdoList}
                        paginatedHebdoList={paginatedHebdoList}
                        statsHebdo={statsHebdo}
                        loadingHebdo={loadingHebdo}
                        canViewEmploye={canViewEmploye}
                        selectedSemaine={selectedSemaine}
                        setSelectedSemaine={setSelectedSemaine}
                        hebdoSortBy={hebdoSortBy}
                        hebdoSortOrder={hebdoSortOrder}
                        toggleHebdoSort={toggleHebdoSort}
                        currentHebdoPage={currentHebdoPage}
                        setCurrentHebdoPage={setCurrentHebdoPage}
                        hebdoPageSize={hebdoPageSize}
                        setHebdoPageSize={setHebdoPageSize}
                    />
                )}
            </div>

            {/* Modale Décomposition Hebdomadaire pour un employé */}
            <Modal
                isOpen={!!selectedEmploye}
                onClose={() => setSelectedEmploye(null)}
                title={selectedEmploye ? `Décomposition par Semaine • ${selectedEmploye.nom} ${selectedEmploye.prenom} (#${selectedEmploye.matricule})` : 'Détail des Semaines'}
                size="xl"
            >
                <div className="space-y-6">
                    {/* Alerte explicative des règles légales */}
                    <div className="bg-[var(--color-bg-subtle)] border-l-4 border-l-[var(--color-brand-500)] p-4 text-xs space-y-1">
                        <div className="font-bold text-[var(--color-fg)]">
                            Mécanisme de compensation légale ({selectedEmploye?.estCadre ? 'Statut Cadre' : 'Statut Non-Cadre'}) :
                        </div>
                        <div className="text-[var(--color-fg-muted)]">
                            {selectedEmploye?.estCadre
                                ? 'Pour les cadres : les heures travaillées au-delà du seuil compensent les retards constatés dans la semaine, sans donner lieu à une majoration d’heures supplémentaires.'
                                : 'Pour les non-cadres : le surplus hebdomadaire absorbe d’abord les retards. Le reliquat net est ensuite ventilé en HS 30% (jusqu’à 8h) puis HS 50% au-delà.'}
                        </div>
                    </div>

                    {loadingTroncons ? (
                        <div className="p-8">
                            <SkeletonTable rows={4} columns={8} />
                        </div>
                    ) : troncons.length === 0 ? (
                        <div className="p-8">
                            <p className="text-center text-sm text-[var(--color-fg-muted)]">
                                Aucun enregistrement hebdomadaire trouvé pour cette période.
                            </p>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            <div className="overflow-x-auto border border-[var(--color-border-subtle)]">
                                <table className="w-full text-xs text-left border-collapse">
                                    <thead className="bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)] font-bold">
                                        <tr>
                                            <th className="p-3">Semaine</th>
                                            <th className="p-3 text-right">Seuil Proratisé</th>
                                            <th className="p-3 text-right">Heures Travaillées</th>
                                            <th className="p-3 text-right">Retards Bruts</th>
                                            <th className="p-3 text-right">Compensé</th>
                                            <th className="p-3 text-right">HS 30%</th>
                                            <th className="p-3 text-right">HS 50%</th>
                                            <th className="p-3 text-right">Déficit (Retenue)</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-[var(--color-border-subtle)] font-mono">
                                        {troncons.map((t) => (
                                            <tr key={t.numeroSemaine} className="hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                <td className="p-3">
                                                    <div className="font-bold text-[var(--color-fg)]">Semaine {t.numeroSemaine}</div>
                                                    <div className="text-[11px] text-[var(--color-fg-muted)] font-sans">
                                                        {formatDate(t.debutSemaine)} au {formatDate(t.finSemaine)}
                                                    </div>
                                                </td>
                                                <td className="p-3 text-right text-[var(--color-fg-muted)]">
                                                    {t.seuilHeuresTroncon.toFixed(1)}h ({t.nbJoursTroncon}j)
                                                </td>
                                                <td className="p-3 text-right font-bold text-[var(--color-fg)]">
                                                    {t.totalHeuresTravaillees.toFixed(1)}h
                                                </td>
                                                <td className="p-3 text-right">
                                                    {t.heuresRetard > 0 ? (
                                                        <span className="text-amber-700 dark:text-amber-400 font-semibold">
                                                            {t.heuresRetard.toFixed(1)}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">-</span>
                                                    )}
                                                </td>
                                                <td className="p-3 text-right">
                                                    {t.heuresCompensees > 0 ? (
                                                        <span className="text-emerald-700 dark:text-emerald-400 font-bold">
                                                            +{t.heuresCompensees.toFixed(1)}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">-</span>
                                                    )}
                                                </td>
                                                <td className="p-3 text-right">
                                                    {t.heuresHs30 > 0 ? (
                                                        <span className="text-sky-700 dark:text-sky-400 font-bold">
                                                            +{t.heuresHs30.toFixed(1)}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">-</span>
                                                    )}
                                                </td>
                                                <td className="p-3 text-right">
                                                    {t.heuresHs50 > 0 ? (
                                                        <span className="text-purple-700 dark:text-purple-400 font-bold">
                                                            +{t.heuresHs50.toFixed(1)}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">-</span>
                                                    )}
                                                </td>
                                                <td className="p-3 text-right font-bold">
                                                    {t.heuresDeficit > 0 ? (
                                                        <span className="text-rose-700 dark:text-rose-400">
                                                            -{t.heuresDeficit.toFixed(1)}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-emerald-700 dark:text-emerald-400">0h</span>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    <div className="flex justify-end pt-2">
                        <Button variant="outline" size="sm" onClick={() => setSelectedEmploye(null)}>
                            Fermer
                        </Button>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
