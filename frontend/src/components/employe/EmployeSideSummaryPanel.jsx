import Badge from '@/components/ui/data-display/Badge';

const fmtDate = (d) => {
    if (!d) return '—';
    try {
        const date = new Date(d);
        if (isNaN(date.getTime())) return d;
        return date.toLocaleDateString('fr-FR');
    } catch {
        return d;
    }
};

function MiniRow({ label, value, isMono = false }) {
    if (!value || value === '-') return null;
    return (
        <div className="flex items-baseline justify-between gap-4 py-1.5 text-xs border-b border-slate-100 dark:border-ink-800/60 last:border-0">
            <span className="text-slate-500 dark:text-ink-400 text-xs whitespace-nowrap">{label}</span>
            <span className={`text-right truncate font-bold text-slate-800 dark:text-white ${isMono ? 'font-mono' : ''}`}>
                {value}
            </span>
        </div>
    );
}

export default function EmployeSideSummaryPanel({
    emp,
    documentsCount = 0,
    activeTab,
    onNavigateTab,
    canWrite: _canWrite = false
}) {
    if (!emp) return null;

    const hasContract = Boolean(emp.idContratActif || (emp.statutContrat && emp.statutContrat !== 'Aucun contrat'));

    return (
        <div className="space-y-4">
            {/* 1. Affectation & Hiérarchie */}
            <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 space-y-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white">
                    Affectation & Hiérarchie
                </h3>
                <div className="space-y-1">
                    <MiniRow label="Site" value={emp.nomSite || emp.site || emp.libelleSite} />
                    <MiniRow label="Département" value={emp.nomDepartement || emp.departement || emp.libelleDepartement} />
                    <MiniRow label="Fonction" value={emp.nomFonction || emp.fonction || emp.libelleFonction} />
                </div>
            </div>

            {/* 2. Statut Contractuel */}
            <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 space-y-3">
                <div className="flex items-center justify-between">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white">
                        Statut Contractuel
                    </h3>
                    <Badge variant={hasContract ? "success" : "default"} size="sm" className="font-bold">
                        {hasContract ? "Sous contrat" : "Sans contrat"}
                    </Badge>
                </div>
                <div className="space-y-1">
                    <MiniRow label="N° contrat" value={emp.numeroContratActif || emp.numeroContrat} isMono />
                    <MiniRow label="Type" value={emp.libelleTypeContrat || emp.typeContrat} />
                    <MiniRow label="Ancienneté" value={emp.ancienneteEffectiveTexte || emp.ancienneteContratTexte} />
                    <MiniRow label="Date d'entrée" value={fmtDate(emp.dateEmbauche || emp.dateEntree)} />
                </div>
            </div>

            {/* 3. Raccourcis RH */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs p-5 space-y-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Raccourcis Dossier
                </h3>
                <div className="flex flex-col gap-2 pt-0.5">
                    <button
                        type="button"
                        onClick={() => onNavigateTab('carriere')}
                        className={`w-full py-2 px-3 text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer ${
                            activeTab === 'carriere'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        Dossier carrière &amp; contrats
                    </button>

                    <button
                        type="button"
                        onClick={() => onNavigateTab('conges')}
                        className={`w-full py-2 px-3 text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer ${
                            activeTab === 'conges'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        Congés &amp; solde
                    </button>

                    <button
                        type="button"
                        onClick={() => onNavigateTab('pointage')}
                        className={`w-full py-2 px-3 text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer ${
                            activeTab === 'pointage'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        Pointages &amp; présence
                    </button>

                    <button
                        type="button"
                        onClick={() => onNavigateTab('documents')}
                        className={`w-full py-2 px-3 text-xs flex items-center justify-center relative font-semibold border transition-colors rounded-none cursor-pointer ${
                            activeTab === 'documents'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        <span>Documents RH</span>
                        <span className="absolute right-3 text-[10px] font-bold px-1.5 py-0.5 bg-[var(--color-bg)] text-[var(--color-fg-muted)]">
                            {documentsCount}
                        </span>
                    </button>
                </div>
            </div>
        </div>
    );
}
