import { Search, MapPin, Calendar, ClockCheck, Coffee } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';

export default function PointageToolbar({
    sites = [],
    selectedSite,
    onSiteChange,
    selectedDate,
    onDateChange,
    searchTerm,
    onSearchChange,
    selectedStatutFilter = 'ALL',
    onStatutFilterChange,
    statusCounts = { ALL: 0, PRESENT: 0, CONGE: 0, NON_SAISI: 0, RETARD: 0 },
    onAutoFillStandard,
    onAutoDeducePauses,
    isReadOnly = false
}) {
    const siteOptions = sites.map(s => ({
        value: s.idSite,
        label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}`
    }));

    const statusTabs = [
        { id: 'ALL', label: 'Tous', count: statusCounts.ALL ?? 0 },
        { id: 'PRESENT', label: 'Présents', count: statusCounts.PRESENT ?? 0 },
        { id: 'CONGE', label: 'En congé', count: statusCounts.CONGE ?? 0 },
        { id: 'NON_SAISI', label: 'Non saisis', count: statusCounts.NON_SAISI ?? 0 },
        { id: 'RETARD', label: 'Retards', count: statusCounts.RETARD ?? 0 }
    ];

    return (
        <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs divide-y divide-[var(--color-border-subtle)]">
            {/* Bloc 1 : Contexte de Saisie (Site, Date & Outils) */}
            <div className="p-3 sm:p-3.5">
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-3 items-end">
                    {/* 1.1 Site d'affectation (5 cols) */}
                    <div className="lg:col-span-5 space-y-1">
                        <label className="text-[10.5px] font-bold text-[var(--color-fg-muted)] flex items-center gap-1.5 uppercase tracking-wider h-4">
                            <MapPin size={12} className="text-brand-500" />
                            <span>Site d'affectation</span>
                        </label>
                        <Select
                            options={siteOptions}
                            value={selectedSite}
                            onChange={(val) => onSiteChange(val)}
                            disabled={isReadOnly}
                            className="w-full bg-[var(--color-surface)] text-xs h-[32px]"
                        />
                    </div>

                    {/* 1.2 Date de pointage & Bouton Aujourd'hui (4 cols) */}
                    <div className="lg:col-span-4 space-y-1">
                        <label className="text-[10.5px] font-bold text-[var(--color-fg-muted)] flex items-center gap-1.5 uppercase tracking-wider h-4">
                            <Calendar size={12} className="text-brand-500" />
                            <span>Date de pointage</span>
                        </label>
                        <div className="flex items-center gap-2">
                            <PeriodNavigator
                                viewPeriod="jour"
                                selectedDate={selectedDate}
                                onDateChange={(val) => onDateChange(val)}
                                className="flex-1 h-[32px]"
                            />
                        </div>
                    </div>

                    {/* 1.3 Outils d'aide rapide (3 cols) */}
                    <div className="lg:col-span-3 space-y-1">
                        <label className="text-[10.5px] font-bold text-[var(--color-fg-muted)] flex items-center gap-1.5 uppercase tracking-wider h-4">
                            <ClockCheck size={12} className="text-brand-500" />
                            <span>Aide à la saisie</span>
                        </label>
                        <div className="flex items-center gap-1.5 h-[32px]">
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                disabled={isReadOnly}
                                onClick={onAutoFillStandard}
                                className="flex-1 text-[11px] font-semibold h-[32px] px-2"
                                title="Remplir automatiquement 08h-12h et 13h-17h pour les salariés non saisis"
                            >
                                <ClockCheck size={12} className="mr-1 text-emerald-600 shrink-0" />
                                08h-17h
                            </Button>
                            <Button
                                type="button"
                                variant="outline"
                                size="xs"
                                disabled={isReadOnly}
                                onClick={onAutoDeducePauses}
                                className="flex-1 text-[11px] font-semibold h-[32px] px-2"
                                title="Déduire automatiquement la pause déjeuner pour les Saisies 2 bornes"
                            >
                                <Coffee size={12} className="mr-1 text-amber-600 shrink-0" />
                                Pauses
                            </Button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Bloc 2 : Recherche & Filtres par Statut */}
            <div className="p-3 sm:p-3.5 bg-[var(--color-bg-subtle)]/50">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                    {/* 2.1 Champ de recherche employé */}
                    <div className="w-full lg:max-w-md">
                        <div className="relative">
                            <Search size={13} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                            <input
                                type="text"
                                placeholder="Rechercher par nom, prénom ou matricule…"
                                value={searchTerm}
                                onChange={(e) => onSearchChange(e.target.value)}
                                className="w-full pl-8 pr-3 py-1 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 shadow-2xs h-[32px]"
                            />
                        </div>
                    </div>

                    {/* 2.2 Onglets de statut avec compteurs */}
                    <div className="flex flex-wrap items-center gap-1">
                        {statusTabs.map((tab) => {
                            const isActive = selectedStatutFilter === tab.id;
                            return (
                                <button
                                    key={tab.id}
                                    type="button"
                                    onClick={() => onStatutFilterChange?.(tab.id)}
                                    className={`
                                        px-2.5 py-1 text-[11px] transition-all cursor-pointer border h-[32px]
                                        ${isActive
                                            ? 'bg-[var(--color-surface)] border-[var(--color-border)] text-brand-700 dark:text-brand-300 font-bold shadow-2xs'
                                            : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                        }
                                    `}
                                >
                                    <span>{tab.label}</span>
                                    <span className="opacity-60 text-[10.5px] font-mono ml-1">· {tab.count}</span>
                                </button>
                            );
                        })}
                    </div>
                </div>
            </div>
        </section>
    );
}
