import { FileText } from 'lucide-react';

const InfoBlock = ({ label, value }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400 mb-0.5">{label}</p>
        <p className="text-sm font-semibold text-ink-800 dark:text-ink-100">{value || '-'}</p>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
};

export default function HistoriqueStatutTab({ emp }) {
    if (!emp) return null;

    return (
        <div className="space-y-6">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                <FileText size={14} /> Historique du Dossier
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-6 bg-ink-50 dark:bg-ink-950 p-4 border border-ink-150 dark:border-ink-700">
                <InfoBlock label="Statut du Compte" value={emp.estActif ? "Actif" : "Inactif"} />
                {!emp.estActif && (
                    <>
                        <InfoBlock label="Date de Désactivation" value={fmtDate(emp.dateDesactivation)} />
                        <InfoBlock label="Motif de Désactivation" value={emp.motifDesactivation || '-'} />
                    </>
                )}
            </div>
        </div>
    );
}
