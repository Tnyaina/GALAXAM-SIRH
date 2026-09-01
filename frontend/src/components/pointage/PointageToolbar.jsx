import { Search, MapPin, ClockCheck, Coffee, Save } from 'lucide-react';
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
    isReadOnly = false,
    // Nouvelles props pour Save intégré
    onSave,
    saving = false,
    dirtyCount = 0,
}) {
    const siteOptions = sites.map(s => ({
        value: s.idSite,
        label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}`
    }));

    const statusTabs = [
        { id: 'ALL', label: 'Tous', count: statusCounts.ALL ?? 0 },
        { id: 'PRESENT', label: 'Présents', count: statusCounts.PRESENT ?? 0 },
        { id: 'NON_SAISI', label: 'Non saisis', count: statusCounts.NON_SAISI ?? 0 },
        { id: 'RETARD', label: 'Retards', count: statusCounts.RETARD ?? 0 },
        { id: 'CONGE', label: 'Congés', count: statusCounts.CONGE ?? 0 },
    ];

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
            {/* Barre unique : tout sur une ligne */}
            <div className="px-3 py-2 flex items-center gap-2 flex-wrap lg:flex-nowrap">

                {/* 1. Site (compact) */}
                <div className="flex items-center gap-1.5 shrink-0 min-w-0">
                    <MapPin size={13} className="text-[var(--color-fg-muted)] shrink-0" />
                    <Select
                        options={siteOptions}
                        value={selectedSite}
                        onChange={(val) => onSiteChange(val)}
                        disabled={isReadOnly}
                        className="text-xs h-[32px] min-w-[160px] max-w-[220px]"
                    />
                </div>

                {/* Séparateur */}
                <div className="hidden lg:block h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                {/* 2. Navigateur de date */}
                <div className="shrink-0">
                    <PeriodNavigator
                        viewPeriod="jour"
                        selectedDate={selectedDate}
                        onDateChange={(val) => onDateChange(val)}
                        className="h-[32px]"
                    />
                </div>

                {/* Séparateur */}
                <div className="hidden lg:block h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                {/* 3. Recherche */}
                <div className="relative flex-1 min-w-[140px] max-w-[240px]">
                    <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                    <input
                        type="text"
                        placeholder="Nom, matricule…"
                        value={searchTerm}
                        onChange={(e) => onSearchChange(e.target.value)}
                        className="w-full pl-7 pr-3 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 h-[32px]"
                    />
                </div>

                {/* 4. Filtres statut (pills) */}
                <div className="flex items-center gap-1 flex-wrap">
                    {statusTabs.map((tab) => {
                        const isActive = selectedStatutFilter === tab.id;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                onClick={() => onStatutFilterChange?.(tab.id)}
                                className={`
                                    px-2 py-1 text-[11px] transition-all cursor-pointer border h-[28px] whitespace-nowrap
                                    ${isActive
                                        ? 'bg-brand-600 border-brand-600 text-white font-bold'
                                        : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }
                                `}
                            >
                                {tab.label}
                                <span className={`ml-1 font-mono text-[10px] ${isActive ? 'opacity-80' : 'opacity-50'}`}>
                                    {tab.count}
                                </span>
                            </button>
                        );
                    })}
                </div>

                {/* Séparateur */}
                <div className="hidden lg:block h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                {/* 5. Outils auto-saisie */}
                <div className="flex items-center gap-1 shrink-0">
                    <button
                        type="button"
                        disabled={isReadOnly}
                        onClick={onAutoFillStandard}
                        title="Remplir 08h–17h pour les non saisis"
                        className="flex items-center gap-1 px-2 h-[28px] text-[11px] font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                    >
                        <ClockCheck size={11} className="text-emerald-600 shrink-0" />
                        08h–17h
                    </button>
                    <button
                        type="button"
                        disabled={isReadOnly}
                        onClick={onAutoDeducePauses}
                        title="Déduire les pauses déjeuner"
                        className="flex items-center gap-1 px-2 h-[28px] text-[11px] font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer"
                    >
                        <Coffee size={11} className="text-amber-600 shrink-0" />
                        Pauses
                    </button>
                </div>

                {/* Séparateur */}
                <div className="hidden lg:block h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                {/* 6. Bouton Enregistrer (dans la toolbar) */}
                <button
                    type="button"
                    disabled={isReadOnly || saving}
                    onClick={onSave}
                    className={`
                        flex items-center gap-1.5 px-3 h-[32px] text-[12px] font-bold shrink-0 transition-all cursor-pointer border
                        ${dirtyCount > 0
                            ? 'bg-brand-600 hover:bg-brand-700 text-white border-brand-600 shadow-sm'
                            : isReadOnly
                                ? 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] opacity-50 cursor-not-allowed'
                                : 'bg-[var(--color-surface)] border-[var(--color-border)] text-[var(--color-fg-muted)] hover:border-[var(--color-border-strong)] hover:text-[var(--color-fg)]'
                        }
                        disabled:opacity-50 disabled:cursor-not-allowed
                    `}
                    title={dirtyCount > 0 ? `${dirtyCount} modification(s) non enregistrée(s) — Ctrl+S` : 'Enregistrer les pointages (Ctrl+S)'}
                >
                    {saving ? (
                        <span className="animate-spin inline-block w-3 h-3 border-2 border-current border-t-transparent rounded-full" />
                    ) : (
                        <Save size={12} />
                    )}
                    <span>{saving ? 'Enregistrement…' : dirtyCount > 0 ? 'Enregistrer' : 'Enregistré'}</span>
                    {dirtyCount > 0 && (
                        <span className="bg-white/25 text-white text-[10px] font-mono px-1 py-0.5 rounded-sm leading-none">
                            {dirtyCount}
                        </span>
                    )}
                    <span className="hidden xl:inline text-[10px] opacity-50 font-normal">(Ctrl+S)</span>
                </button>
            </div>
        </div>
    );
}
