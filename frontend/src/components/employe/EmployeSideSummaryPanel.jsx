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
        <div className="flex items-start justify-between gap-2.5 py-1.5 text-xs border-b border-[var(--color-border-subtle)] last:border-0">
            <span className="text-[var(--color-fg-muted)] text-[11px] shrink-0 font-medium">{label}</span>
            <span className={`text-right text-[11px] sm:text-xs font-bold text-[var(--color-fg)] break-words leading-snug ${isMono ? 'font-mono' : ''}`}>
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
        <div className="space-y-3.5">
            {/* 1. Affectation & Hiérarchie */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-3.5 sm:p-4 space-y-2.5">
                <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Affectation &amp; Hiérarchie
                </h3>
                <div className="space-y-0.5">
                    <MiniRow label="Site" value={emp.nomSite || emp.site || emp.libelleSite} />
                    <MiniRow label="Département" value={emp.nomDepartement || emp.departement || emp.libelleDepartement} />
                    <MiniRow label="Fonction" value={emp.nomFonction || emp.fonction || emp.libelleFonction} />
                </div>
            </div>

            {/* 2. Statut Contractuel */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-3.5 sm:p-4 space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                    <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)] truncate">
                        Statut Contractuel
                    </h3>
                    <Badge variant={hasContract ? "success" : "default"} size="sm" className="font-bold shrink-0 text-[10px]">
                        {hasContract ? "Sous contrat" : "Sans contrat"}
                    </Badge>
                </div>
                <div className="space-y-0.5">
                    <MiniRow label="N° contrat" value={emp.numeroContratActif || emp.numeroContrat} isMono />
                    <MiniRow label="Type" value={emp.libelleTypeContrat || emp.typeContrat} />
                    <MiniRow label="Ancienneté" value={emp.ancienneteEffectiveTexte || emp.ancienneteContratTexte} />
                    <MiniRow label="Date d'entrée" value={fmtDate(emp.dateEmbauche || emp.dateEntree)} />
                </div>
            </div>

            {/* 3. Raccourcis RH */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs p-3.5 sm:p-4 space-y-2.5">
                <h3 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Raccourcis Dossier
                </h3>
                <div className="flex flex-col gap-1.5 pt-0.5">
                    <button
                        type="button"
                        onClick={() => onNavigateTab('carriere')}
                        className={`w-full py-2 px-2.5 text-[11px] sm:text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
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
                        className={`w-full py-2 px-2.5 text-[11px] sm:text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
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
                        className={`w-full py-2 px-2.5 text-[11px] sm:text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
                            activeTab === 'pointage'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        Pointages &amp; présence
                    </button>

                    <button
                        type="button"
                        onClick={() => onNavigateTab('paie')}
                        className={`w-full py-2 px-2.5 text-[11px] sm:text-xs text-center font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
                            activeTab === 'paie'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        Paie &amp; Rémunération
                    </button>

                    <button
                        type="button"
                        onClick={() => onNavigateTab('documents')}
                        className={`w-full py-2 px-2.5 text-[11px] sm:text-xs flex items-center justify-between font-semibold border transition-colors rounded-none cursor-pointer leading-tight ${
                            activeTab === 'documents'
                                ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        <span className="flex-1 text-center pl-3">Documents RH</span>
                        <span className="text-[10px] font-bold px-1.5 py-0.5 bg-[var(--color-bg)] text-[var(--color-fg-muted)] shrink-0 border border-[var(--color-border-subtle)]">
                            {documentsCount}
                        </span>
                    </button>
                </div>
            </div>
        </div>
    );
}
