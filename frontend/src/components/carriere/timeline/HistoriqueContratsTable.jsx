import { FileText, Award, FileCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueContratsTable({
    displayContrats = [],
    contratActif,
    onOpenCertificat,
    onOpenAttestation
}) {
    const today = new Date().setHours(0, 0, 0, 0);

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
                                <th className="px-4 py-3">N° Contrat</th>
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
                                const isAnnule = ct.codeStatutContrat === 'ANNULE';
                                const isClotureOuRomp = ct.codeStatutContrat === 'CLOTURE' || ct.codeStatutContrat === 'ROMPU' || ct.codeStatutContrat === 'HISTORISE';
                                const isPlanifie = isDebutFutur && !isAnnule && !isClotureOuRomp;
                                const isContratEnCours = !isPlanifie && !isAnnule && ((contratActif && Number(ct.idContrat) === Number(contratActif.idContrat)) || ct.codeStatutContrat === 'ACTIF');
                                const estDateFinEchue = ct.dateFin && new Date(ct.dateFin).getTime() < today;
                                const canGenerateCertificat = !isContratEnCours && !isPlanifie && !isAnnule && (isClotureOuRomp || estDateFinEchue);

                                return (
                                    <tr key={ct.idContrat} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-bold text-brand-500">
                                            {ct.numeroContrat}
                                        </td>
                                        <td className="px-4 py-3">
                                            {isPlanifie ? (
                                                <Badge variant="info" size="sm">PLANIFIÉ</Badge>
                                            ) : isAnnule ? (
                                                <Badge variant="danger" size="sm">ANNULÉ</Badge>
                                            ) : isContratEnCours ? (
                                                <Badge variant="success" size="sm">ACTIF</Badge>
                                            ) : (
                                                <Badge variant="default" size="sm">CLÔTURÉ</Badge>
                                            )}
                                        </td>
                                        <td className="px-4 py-3">
                                            {ct.libelleTypeIntervenant || '-'}
                                        </td>
                                        <td className="px-4 py-3 font-mono">
                                            {fmtDate(ct.dateDebut)} → {fmtDate(ct.dateFin)}
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
                                                <span className="text-[11px] text-ink-400 italic">Non démarré</span>
                                            ) : isAnnule ? (
                                                <span className="text-[11px] text-danger-500 italic">Annulé</span>
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
