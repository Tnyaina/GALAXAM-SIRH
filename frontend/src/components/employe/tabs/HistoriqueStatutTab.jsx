import { FileText, ShieldCheck, ShieldAlert } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

const InfoBlock = ({ label, value, children }) => (
    <div className="space-y-1">
        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] dark:text-[var(--color-fg-muted)]">{label}</p>
        {children || <p className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)] dark:text-white truncate">{value || '—'}</p>}
    </div>
);

const fmtDate = (d) => {
    if (!d) return '—';
    try {
        const date = new Date(d);
        if (isNaN(date.getTime())) return '—';
        return date.toLocaleDateString('fr-FR');
    } catch {
        return '—';
    }
};

export default function HistoriqueStatutTab({ emp }) {
    if (!emp) return null;

    return (
        <div className="space-y-4">
            <div className="bg-[var(--color-surface)] dark:bg-[var(--color-surface)] border border-[var(--color-border-subtle)] dark:border-[var(--color-border)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)] dark:border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] dark:text-white flex items-center gap-2">
                        <FileText size={14} className="text-brand-500" />
                        Statut du Dossier & Traçabilité
                    </h3>
                    <Badge variant={emp.estActif ? "success" : "default"} size="xs" dot className="font-bold">
                        {emp.estActif ? "Compte Collaborateur Actif" : "Compte Inactif / Archivé"}
                    </Badge>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-4 pt-1">
                    <InfoBlock label="Statut opérationnel">
                        <div className="flex items-center gap-2">
                            {emp.estActif ? (
                                <span className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-success-500)] dark:text-[var(--color-success-400)]">
                                    <ShieldCheck size={14} /> Dossier RH Actif
                                </span>
                            ) : (
                                <span className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-fg-muted)]">
                                    <ShieldAlert size={14} /> Dossier Clôturé
                                </span>
                            )}
                        </div>
                    </InfoBlock>

                    {!emp.estActif && (
                        <>
                            <InfoBlock label="Date de désactivation" value={fmtDate(emp.dateDesactivation)} />
                            <InfoBlock label="Motif de désactivation" value={emp.motifDesactivation || '—'} />
                        </>
                    )}

                    <InfoBlock label="Matricule RH unique" value={emp.matricule} />
                </div>
            </div>
        </div>
    );
}
