import { FileText, Award, FileCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueContratsTable({
    displayContrats = [],
    _contratActif,
    onOpenCertificat,
    onOpenAttestation
}) {
    const today = new Date().setHours(0, 0, 0, 0);

    const getTypeContratBadgeVariant = (type) => {
        if (!type) return 'default';
        const t = type.toUpperCase();
        if (t.includes('CDI')) return 'success';
        if (t.includes('CDD')) return 'warning';
        if (t.includes('STAGE')) return 'info';
        if (t.includes('PRESTATION') || t.includes('CONSULTANT')) return 'primary';
        return 'default';
    };

    return (
        <div className="space-y-3 pt-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2 pb-2 border-b border-ink-150 dark:border-ink-700">
                <FileText size={14} /> Historique des Contrats de Travail ({displayContrats.length})
            </h3>

            {displayContrats.length === 0 ? (
                <p className="text-xs text-ink-400">Aucun contrat dans l'historique.</p>
            ) : (
                <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-none">
                    <table className="w-full text-xs text-left">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase tracking-widest text-[10px]">
                                <th className="px-4 py-3">Type &amp; N° Contrat</th>
                                <th className="px-4 py-3">Statut</th>
                                <th className="px-4 py-3">Intervenant</th>
                                <th className="px-4 py-3">Dates (Début → Fin)</th>
                                <th className="px-4 py-3">Date Signature</th>
                                <th className="px-4 py-3">Effectif</th>
                                <th className="px-4 py-3">Description</th>
                                <th className="px-4 py-3 text-right">Document</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {displayContrats.map((ct) => {
                                const isDebutFutur = ct.dateDebut && new Date(ct.dateDebut).getTime() > today;
                                const isAnnule = Boolean(ct.isAnnule);
                                const isClotureOuRomp = Boolean(ct.isCloture || ct.isRompu);
                                const isPlanifie = (Boolean(ct.isPlanifie) || isDebutFutur) && !isAnnule && !isClotureOuRomp;
                                const isContratEnCours = Boolean(ct.isActif) && !isPlanifie && !isAnnule;
                                const estDateFinEchue = ct.dateFin && new Date(ct.dateFin).getTime() < today;
                                const canGenerateCertificat = !isContratEnCours && !isPlanifie && !isAnnule && (isClotureOuRomp || estDateFinEchue);

                                return (
                                    <tr key={ct.idContrat} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3">
                                            <div className="space-y-0.5">
                                                <div className="flex items-center gap-1.5 flex-wrap">
                                                    {ct.libelleTypeContrat && (
                                                        <Badge variant={getTypeContratBadgeVariant(ct.libelleTypeContrat)} size="xs">
                                                            {ct.libelleTypeContrat}
                                                        </Badge>
                                                    )}
                                                    <span className="font-mono font-bold text-brand-500">
                                                        {ct.numeroContrat}
                                                    </span>
                                                </div>
                                                {ct.libelleTypeEvenementInitial && (
                                                    <p className="text-[11px] text-ink-400 font-medium">
                                                        {ct.libelleTypeEvenementInitial}
                                                    </p>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <StatusBadge
                                                status={isPlanifie ? 'PLANIFIE' : isAnnule ? 'ANNULE' : isContratEnCours ? 'ACTIF' : (ct.codeStatutContrat || 'CLOTURE')}
                                                label={ct.libelleStatutContrat}
                                                size="sm"
                                            />
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="space-y-0.5">
                                                <span className="font-medium">{ct.libelleTypeIntervenant || '-'}</span>
                                                {ct.libelleFiliale && (
                                                    <p className="text-[10px] text-ink-400">{ct.libelleFiliale}</p>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="space-y-0.5">
                                                <div className="font-mono text-xs">
                                                    {fmtDate(ct.dateDebut)} → {fmtDate(ct.dateFin)}
                                                </div>
                                                {ct.ancienneteContratTexte && (
                                                    <p className="text-[11px] text-ink-500 font-sans">
                                                        Durée : <strong className="text-ink-700 dark:text-ink-200">{ct.ancienneteContratTexte}</strong>
                                                    </p>
                                                )}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 font-mono">
                                            {fmtDate(ct.dateSignature)}
                                        </td>
                                        <td className="px-4 py-3">
                                            {ct.comptabiliseEffectif ? 'Oui' : 'Non'}
                                        </td>
                                        <td className="px-4 py-3 text-ink-500">
                                            {ct.description || '-'}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            {isPlanifie ? (
                                                <span className="text-[11px] text-ink-400 ">Non démarré</span>
                                            ) : isAnnule ? (
                                                <span className="text-[11px] text-danger-500 ">Annulé</span>
                                            ) : canGenerateCertificat ? (
                                                <Button
                                                    onClick={() => onOpenCertificat(ct)}
                                                    variant="outline"
                                                    size="xs"
                                                    iconLeft={<Award size={13} />}
                                                >
                                                    Certificat
                                                </Button>
                                            ) : isContratEnCours ? (
                                                <Button
                                                    onClick={() => onOpenAttestation(ct)}
                                                    variant="outline"
                                                    size="xs"
                                                    iconLeft={<FileCheck size={13} />}
                                                >
                                                    Attestation
                                                </Button>
                                            ) : null}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
