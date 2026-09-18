import { useState, useMemo } from 'react';
import { FileText, Award, FileCheck, Eye, EyeOff, Calculator } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueContratsTable({
    displayContrats = [],
    _contratActif,
    onOpenCertificat,
    onOpenAttestation,
    onOpenStc,
}) {
    const today = new Date().setHours(0, 0, 0, 0);

    // Par défaut : NON AFFICHER les contrats annulés
    const [showAnnules, setShowAnnules] = useState(false);

    const getTypeContratBadgeVariant = () => 'default';

    const nbAnnules = useMemo(() => {
        return displayContrats.filter(ct => Boolean(ct.isAnnule) || ct.codeStatutContrat === 'ANNULE').length;
    }, [displayContrats]);

    const contratsAffiches = useMemo(() => {
        const list = showAnnules
            ? [...displayContrats]
            : displayContrats.filter(ct => !ct.isAnnule && ct.codeStatutContrat !== 'ANNULE');

        // Si les annulés sont affichés, on les relègue en fin de liste pour ne pas masquer les contrats valides
        return list.sort((a, b) => {
            const aAnnule = Boolean(a.isAnnule) || a.codeStatutContrat === 'ANNULE';
            const bAnnule = Boolean(b.isAnnule) || b.codeStatutContrat === 'ANNULE';
            if (aAnnule && !bAnnule) return 1;
            if (!aAnnule && bAnnule) return -1;
            return 0;
        });
    }, [displayContrats, showAnnules]);

    return (
        <div className="space-y-3 pt-4">
            <div className="flex items-center justify-between pb-2 border-b border-ink-150 dark:border-ink-700">
                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2">
                    <FileText size={14} /> Historique des Contrats de Travail ({contratsAffiches.length})
                </h3>

                {nbAnnules > 0 && (
                    <Button
                        type="button"
                        variant="ghost"
                        size="xs"
                        onClick={() => setShowAnnules(prev => !prev)}
                        iconLeft={showAnnules ? <EyeOff size={13} /> : <Eye size={13} />}
                        className="text-ink-500 hover:text-ink-800 dark:hover:text-ink-200 cursor-pointer"
                    >
                        {showAnnules
                            ? `Masquer les contrats annulés (${nbAnnules})`
                            : `Afficher les contrats annulés (${nbAnnules})`}
                    </Button>
                )}
            </div>

            {contratsAffiches.length === 0 ? (
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
                        {contratsAffiches.map((ct) => {
                            const isDebutFutur = ct.dateDebut && new Date(ct.dateDebut).getTime() > today;
                            const isAnnule = Boolean(ct.isAnnule) || ct.codeStatutContrat === 'ANNULE';
                            const isClotureOuRomp = Boolean(ct.isCloture || ct.isRompu);
                            const isPlanifie = (Boolean(ct.isPlanifie) || isDebutFutur) && !isAnnule && !isClotureOuRomp;
                            const isContratEnCours = Boolean(ct.isActif) && !isPlanifie && !isAnnule;
                            const estDateFinEchue = ct.dateFin && new Date(ct.dateFin).getTime() < today;
                            const canGenerateCertificat = !isContratEnCours && !isPlanifie && !isAnnule && (isClotureOuRomp || estDateFinEchue);

                            return (
                                <Tr
                                    key={ct.idContrat}
                                    className={isAnnule ? 'bg-ink-100/40 dark:bg-ink-800/30 opacity-60 hover:opacity-100  transition-opacity' : ''}
                                >
                                    <Td>
                                        <div className="space-y-0.5">
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                {ct.libelleTypeContrat && (
                                                    <Badge variant={getTypeContratBadgeVariant(ct.libelleTypeContrat)} size="xs">
                                                        {ct.libelleTypeContrat}
                                                    </Badge>
                                                )}
                                                <span className={`font-mono font-bold ${isAnnule ? 'text-ink-400 line-through' : 'text-brand-500'}`}>
                                                    {ct.numeroContrat}
                                                </span>
                                            </div>
                                            {ct.libelleTypeEvenementInitial && (
                                                <p className="text-[11px] text-ink-400 font-medium">
                                                    {ct.libelleTypeEvenementInitial}
                                                </p>
                                            )}
                                            {ct.description && (
                                                <p className="text-[10px] text-ink-400 ">
                                                    {ct.description}
                                                </p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        {isAnnule ? (
                                            <Badge variant="danger" size="xs" className="font-semibold">
                                                ANNULÉ
                                            </Badge>
                                        ) : (
                                            <StatusBadge
                                                status={isPlanifie ? 'PLANIFIE' : isContratEnCours ? 'ACTIF' : (ct.codeStatutContrat || 'CLOTURE')}
                                                label={ct.libelleStatutContrat}
                                                size="sm"
                                            />
                                        )}
                                    </Td>
                                    <Td>
                                        <div className="space-y-0.5">
                                            <span className={`font-medium ${isAnnule ? 'text-ink-500' : 'text-ink-800 dark:text-ink-200'}`}>
                                                {ct.libelleTypeIntervenant || '-'}
                                            </span>
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
                                            <span className="inline-flex items-center text-[10.5px] text-ink-400 px-2 py-0.5 bg-ink-100 dark:bg-ink-800/80 border border-ink-200 dark:border-ink-700">
                                                Sans effet juridique
                                            </span>
                                        ) : canGenerateCertificat ? (
                                            <div className="flex justify-end gap-1.5">
                                                {onOpenStc && (
                                                    <Button
                                                        onClick={() => onOpenStc(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        iconLeft={<Calculator size={13} />}
                                                        className="border-[var(--color-brand-600)] text-[var(--color-brand-600)] hover:bg-[var(--color-brand-50)]"
                                                    >
                                                        STC
                                                    </Button>
                                                )}
                                                <Button
                                                    onClick={() => onOpenCertificat(ct)}
                                                    variant="outline"
                                                    size="xs"
                                                    iconLeft={<Award size={13} />}
                                                >
                                                    Certificat
                                                </Button>
                                            </div>
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
