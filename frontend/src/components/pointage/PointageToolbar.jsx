import { Search, ClockCheck, Coffee, Save, CalendarOff } from 'lucide-react';
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
    onAutoFillStandard,
    onAutoDeducePauses,
    onAddConge,
    isReadOnly = false,
    // Props pour Save intégré
    onSave,
    saving = false,
    dirtyCount = 0,
}) {
    const siteOptions = sites
        .filter(s => s.estActif !== false && s.actif !== false || String(s.idSite) === String(selectedSite))
        .map(s => ({
            value: s.idSite,
            label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}`
        }));

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
            <div className="px-3 py-2 flex items-center justify-between gap-3 flex-wrap">

                {/* Bloc 1 : Sélection Site, Date & Recherche */}
                <div className="flex items-center gap-2.5">
                    {/* Site */}
                    <div className="flex items-center gap-1 shrink-0">
                        <Select
                            options={siteOptions}
                            value={selectedSite}
                            onChange={(val) => onSiteChange(val)}
                            disabled={isReadOnly}
                            className="text-xs h-[32px] w-[180px]"
                        />
                    </div>

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Date */}
                    <div className="shrink-0">
                        <PeriodNavigator
                            viewPeriod="jour"
                            selectedDate={selectedDate}
                            onDateChange={(val) => onDateChange(val)}
                            allowFutureDates={false}
                            className="h-[32px]"
                        />
                    </div>

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Recherche */}
                    <div className="relative w-[180px] shrink-0">
                        <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Nom, matricule…"
                            value={searchTerm}
                            onChange={(e) => onSearchChange(e.target.value)}
                            className="w-full pl-7 pr-3 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 h-[32px]"
                        />
                    </div>
                </div>

                {/* Bloc 2 : Outils d'automatisation, Congés & Enregistrement */}
                <div className="flex items-center gap-2">
                    <button
                        type="button"
                        disabled={isReadOnly}
                        onClick={onAutoFillStandard}
                        title="Remplir 08h–17h pour les non saisis"
                        className="flex items-center gap-1.5 px-3 h-[32px] text-xs font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap"
                    >
                        <ClockCheck size={13} className="text-emerald-600 shrink-0" />
                        <span>08h–17h</span>
                    </button>
                    <button
                        type="button"
                        disabled={isReadOnly}
                        onClick={onAutoDeducePauses}
                        title="Déduire les pauses déjeuner"
                        className="flex items-center gap-1.5 px-3 h-[32px] text-xs font-semibold border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer whitespace-nowrap"
                    >
                        <Coffee size={13} className="text-amber-600 shrink-0" />
                        <span>Pauses</span>
                    </button>
                    <button
                        type="button"
                        onClick={onAddConge}
                        title="Déclarer un congé ou une absence pour un collaborateur"
                        className="flex items-center gap-1.5 px-3 h-[32px] text-xs font-semibold border border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-300 hover:bg-amber-500/20 transition-colors cursor-pointer whitespace-nowrap"
                    >
                        <CalendarOff size={13} className="shrink-0" />
                        <span>Absence / Congé</span>
                    </button>

                    <div className="h-5 w-px bg-[var(--color-border-subtle)] shrink-0" />

                    {/* Bouton Enregistrer */}
                    <button
                        type="button"
                        disabled={isReadOnly || saving}
                        onClick={onSave}
                        className={`
                            flex items-center gap-1.5 px-4 h-[32px] text-xs font-bold shrink-0 transition-all cursor-pointer border whitespace-nowrap
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
                            <Save size={13} />
                        )}
                        <span>{saving ? 'Enregistrement…' : dirtyCount > 0 ? 'Enregistrer' : 'Enregistré'}</span>
                        {dirtyCount > 0 && (
                            <span className="bg-white/25 text-white text-[10px] font-mono px-1.5 py-0.5 rounded-sm leading-none">
                                {dirtyCount}
                            </span>
                        )}
                        <span className="hidden xl:inline text-[10px] opacity-60 font-normal">(Ctrl+S)</span>
                    </button>
                </div>
            </div>
        </div>
    );
}
