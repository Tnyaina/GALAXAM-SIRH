import { FileText, Award, FileCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueContratsTable({
    displayContrats = [],
    _contratActif,
    onOpenCertificat,
    onOpenAttestation
}) {
    const today = new Date().setHours(0, 0, 0, 0);

    const getTypeContratBadgeVariant = () => 'default';

    return (
        <div className="space-y-3 pt-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2 pb-2 border-b border-ink-150 dark:border-ink-700">
                <FileText size={14} /> Historique des Contrats de Travail ({displayContrats.length})
            </h3>

            {displayContrats.length === 0 ? (
                <p className="text-xs text-ink-400 p-4 border border-ink-150 dark:border-ink-700 text-center">
                    Aucun contrat dans l'historique.
                </p>
            ) : (
                <Table>
                    <Thead>
                        <Tr>
                            <Th>Type &amp; N° Contrat</Th>
                            <Th>Statut</Th>
                            <Th>Intervenant &amp; Filiale</Th>
                            <Th>Dates (Début → Fin)</Th>
                            <Th>Date Signature</Th>
                            <Th>Effectif Légal</Th>
                            <Th align="right">Document Officiel</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {displayContrats.map((ct) => {
                            const isDebutFutur = ct.dateDebut && new Date(ct.dateDebut).getTime() > today;
                            const isAnnule = Boolean(ct.isAnnule);
                            const isClotureOuRomp = Boolean(ct.isCloture || ct.isRompu);
                            const isPlanifie = (Boolean(ct.isPlanifie) || isDebutFutur) && !isAnnule && !isClotureOuRomp;
                            const isContratEnCours = Boolean(ct.isActif) && !isPlanifie && !isAnnule;
                            const estDateFinEchue = ct.dateFin && new Date(ct.dateFin).getTime() < today;
                            const canGenerateCertificat = !isContratEnCours && !isPlanifie && !isAnnule && (isClotureOuRomp || estDateFinEchue);

                            return (
                                <Tr key={ct.idContrat}>
                                    <Td>
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
                                            {ct.description && (
                                                <p className="text-[10px] text-ink-400 italic">
                                                    {ct.description}
                                                </p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        <StatusBadge
                                            status={isPlanifie ? 'PLANIFIE' : isAnnule ? 'ANNULE' : isContratEnCours ? 'ACTIF' : (ct.codeStatutContrat || 'CLOTURE')}
                                            label={ct.libelleStatutContrat}
                                            size="sm"
                                        />
                                    </Td>
                                    <Td>
                                        <div className="space-y-0.5">
                                            <span className="font-medium text-ink-800 dark:text-ink-200">{ct.libelleTypeIntervenant || '-'}</span>
                                            {ct.libelleFiliale && (
                                                <p className="text-[10px] text-ink-400">{ct.libelleFiliale}</p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        <div className="space-y-0.5">
                                            <div className="font-mono text-xs text-ink-700 dark:text-ink-300">
                                                {fmtDate(ct.dateDebut)} → {fmtDate(ct.dateFin)}
                                            </div>
                                            {ct.ancienneteContratTexte && (
                                                <p className="text-[11px] text-ink-500 font-sans">
                                                    Durée : <strong className="text-ink-700 dark:text-ink-200">{ct.ancienneteContratTexte}</strong>
                                                </p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td className="font-mono text-ink-600 dark:text-ink-300">
                                        {fmtDate(ct.dateSignature)}
                                    </Td>
                                    <Td>
                                        <span className={`text-xs font-semibold ${ct.comptabiliseEffectif ? 'text-success-600 dark:text-success-400' : 'text-ink-400'}`}>
                                            {ct.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non'}
                                        </span>
                                    </Td>
                                    <Td align="right">
                                        {isPlanifie ? (
                                            <span className="text-[11px] text-ink-400">Non démarré</span>
                                        ) : isAnnule ? (
                                            <span className="text-[11px] text-danger-500">Annulé</span>
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
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}
