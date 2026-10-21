import { useState, useMemo } from 'react';
import { FileText, Award, FileCheck, Eye, EyeOff, Calculator } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';
import { getTransitionedContractIds } from '@/features/employees/utils/carriereWorkflowUtils';
import HasPermission from '@/features/auth/components/HasPermission';

export default function HistoriqueContratsTable({
    displayContrats: propDisplayContrats,
    contratsList,
    contratActif = null,
    workflowsList = [],
    stcList = [],
    onOpenCertificat,
    onOpenAttestation,
    onOpenContrat,
    onOpenStc,
}) {
    const displayContrats = useMemo(
        () => propDisplayContrats || contratsList || [],
        [propDisplayContrats, contratsList]
    );
    // Données dérivées stables
    const today = new Date().setHours(0, 0, 0, 0);
    const hasContratActif = Boolean(contratActif) || displayContrats.some(ct => Boolean(ct.isActif) && !ct.isAnnule);
    const transitionedContractIds = useMemo(() => getTransitionedContractIds(workflowsList), [workflowsList]);

    // État local : les contrats annulés restent masqués par défaut.
    const [showAnnules, setShowAnnules] = useState(false);

    // Fonctions et données dérivées de présentation.
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

    // Rendu
    return (
        <div className="space-y-3 pt-4">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex items-center gap-2">
                    <FileText size={14} /> Historique des Contrats de Travail ({contratsAffiches.length})
                </h3>

                {nbAnnules > 0 && (
                    <Button
                        type="button"
                        variant="ghost"
                        size="xs"
                        onClick={() => setShowAnnules(prev => !prev)}
                        iconLeft={showAnnules ? <EyeOff size={13} /> : <Eye size={13} />}
                        className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                    >
                        {showAnnules
                            ? `Masquer les contrats annulés (${nbAnnules})`
                            : `Afficher les contrats annulés (${nbAnnules})`}
                    </Button>
                )}
            </div>

            {contratsAffiches.length === 0 ? (
                <p className="text-xs text-[var(--color-fg-muted)] p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-center">
                    Aucun contrat dans l'historique.
                </p>
            ) : (
                <Table tableClassName="min-w-[1100px]">
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
                            const isTransitionne = transitionedContractIds.has(Number(ct.idContrat));
                            const isPlanifie = (Boolean(ct.isPlanifie) || isDebutFutur) && !isAnnule && !isClotureOuRomp;
                            const isContratEnCours = Boolean(ct.isActif) && !isPlanifie && !isAnnule;
                            const estDateFinEchue = ct.dateFin && new Date(ct.dateFin).getTime() < today;
                            const canGenerateCertificat = !isContratEnCours && !isPlanifie && !isAnnule && (isClotureOuRomp || estDateFinEchue);
                            // Le modèle STC est rattaché au salarié (pas à idContrat) : la date
                            // de départ permet d'identifier le contrat soldé dans l'historique.
                            const dateFinContrat = ct.dateFin ? String(ct.dateFin).slice(0, 10) : null;
                            const stcExiste = stcList.some(stc =>
                                stc.statut !== 'ANNULE' && dateFinContrat &&
                                String(stc.dateDepart || '').slice(0, 10) === dateFinContrat
                            );

                            return (
                                <Tr
                                    key={ct.idContrat}
                                    className={isAnnule ? 'bg-[var(--color-bg-subtle)] opacity-60 hover:opacity-100 transition-opacity' : ''}
                                >
                                    <Td>
                                        <div className="space-y-0.5">
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                {ct.libelleTypeContrat && (
                                                    <Badge variant={getTypeContratBadgeVariant(ct.libelleTypeContrat)} size="xs">
                                                        {ct.libelleTypeContrat}
                                                    </Badge>
                                                )}
                                                <span className={`font-mono font-bold ${isAnnule ? 'text-[var(--color-fg-muted)] line-through' : 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]'}`}>
                                                    {ct.numeroContrat}
                                                </span>
                                            </div>
                                            {ct.libelleTypeEvenementInitial && (
                                                <p className="text-[11px] text-[var(--color-fg-muted)] font-medium">
                                                    {ct.libelleTypeEvenementInitial}
                                                </p>
                                            )}
                                            {ct.description && (
                                                <p className="text-[10px] text-[var(--color-fg-subtle)]">
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
                                                label={isTransitionne ? 'Terminé · transition' : ct.libelleStatutContrat}
                                                size="sm"
                                            />
                                        )}
                                    </Td>
                                    <Td>
                                        <div className="space-y-0.5">
                                            <span className={`font-medium ${isAnnule ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'}`}>
                                                {ct.libelleTypeIntervenant || <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                                            </span>
                                            {ct.libelleFiliale && (
                                                <p className="text-[10px] text-[var(--color-fg-subtle)]">{ct.libelleFiliale}</p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        <div className="space-y-0.5">
                                            <div className="font-mono text-xs text-[var(--color-fg)]">
                                                {fmtDate(ct.dateDebut)} → {ct.dateFin ? fmtDate(ct.dateFin) : (isContratEnCours ? 'En cours' : 'Indéterminée')}
                                            </div>
                                            {ct.ancienneteContratTexte && (
                                                <p className="text-[11px] text-[var(--color-fg-muted)] font-sans">
                                                    Durée : <strong className="text-[var(--color-fg)]">{ct.ancienneteContratTexte}</strong>
                                                </p>
                                            )}
                                        </div>
                                    </Td>
                                    <Td className="min-w-[150px]">
                                        <div className="flex flex-col items-start gap-1">
                                            <span className="font-mono text-xs whitespace-nowrap text-[var(--color-fg-muted)]">
                                                {ct.dateSignature ? fmtDate(ct.dateSignature) : <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseignée</span>}
                                            </span>
                                            {!isAnnule && ct.codeStatutContrat !== 'BROUILLON' && onOpenContrat && (
                                                <HasPermission authority="EMPLOYE_WRITE">
                                                    <Button
                                                        type="button"
                                                        onClick={() => onOpenContrat(ct)}
                                                        variant="ghost"
                                                        size="xs"
                                                        className="min-h-8 px-1.5"
                                                    >
                                                        {ct.dateSignature ? 'Modifier' : 'Renseigner'}
                                                    </Button>
                                                </HasPermission>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        <span className={`text-xs font-semibold ${ct.comptabiliseEffectif ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {ct.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non'}
                                        </span>
                                    </Td>
                                    <Td align="right">
                                        {ct.codeStatutContrat === 'BROUILLON' ? (
                                            <Button onClick={() => onOpenContrat?.(ct)} variant="outline" size="xs">Générer / signer</Button>
                                        ) : isPlanifie ? (
                                            <div className="flex justify-end gap-1.5 items-center">
                                                <span className="text-[11px] text-[var(--color-fg-muted)]">Non démarré</span>
                                                {onOpenContrat && (
                                                    <Button
                                                        onClick={() => onOpenContrat(ct)}
                                                        variant="ghost"
                                                        size="xs"
                                                        iconLeft={<FileText size={13} />}
                                                    >
                                                        Consulter
                                                    </Button>
                                                )}
                                            </div>
                                        ) : isAnnule ? (
                                            <span className="inline-flex items-center text-[10.5px] text-[var(--color-fg-muted)] px-2 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                                Sans effet juridique
                                            </span>
                                        ) : isContratEnCours ? (
                                            <div className="flex justify-end gap-1.5 items-center">
                                                {onOpenAttestation && (
                                                    <Button
                                                        onClick={() => onOpenAttestation(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        iconLeft={<FileCheck size={14} />}
                                                        aria-label={`Générer l'attestation de travail pour le contrat ${ct.numeroContrat || ''}`}
                                                    >
                                                        Attestation
                                                    </Button>
                                                )}
                                                {onOpenContrat && (
                                                    <Button
                                                        onClick={() => onOpenContrat(ct)}
                                                        variant="ghost"
                                                        size="xs"
                                                        iconLeft={<FileText size={14} />}
                                                        aria-label={`Consulter le contrat ${ct.numeroContrat || ''}`}
                                                    >
                                                        Consulter
                                                    </Button>
                                                )}
                                            </div>
                                        ) : isTransitionne ? (
                                            <div className="flex justify-end items-center gap-1.5">
                                                <span className="text-[10.5px] text-[var(--color-fg-muted)] px-2 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] font-sans">
                                                    Contrat remplacé
                                                </span>
                                                {onOpenContrat && (
                                                    <Button
                                                        onClick={() => onOpenContrat(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        className="inline-flex items-center justify-center"
                                                        iconLeft={<FileText size={14} />}
                                                        aria-label={`Consulter l'ancien contrat ${ct.numeroContrat || ''}`}
                                                    >
                                                        Consulter
                                                    </Button>
                                                )}
                                            </div>
                                        ) : hasContratActif ? (
                                            /* Salarié en poste : Ancien contrat de transition (ni STC ni certificat de sortie) */
                                            <div className="flex justify-end gap-1.5 items-center">
                                                <span className="text-[10.5px] text-[var(--color-fg-muted)] px-2 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] font-sans">
                                                    Transitionné
                                                </span>
                                                {onOpenContrat && (
                                                    <Button
                                                        onClick={() => onOpenContrat(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        className="inline-flex items-center justify-center"
                                                        iconLeft={<FileText size={14} />}
                                                        aria-label={`Consulter le contrat transitionné ${ct.numeroContrat || ''}`}
                                                    >
                                                        Consulter
                                                    </Button>
                                                )}
                                            </div>
                                        ) : canGenerateCertificat ? (
                                            /* Salarié ayant quitté l'entreprise : STC et Certificat de travail légal */
                                            <div className="flex justify-end gap-1.5 items-center">
                                                {onOpenStc && !stcExiste && (
                                                    <HasPermission all={['PAIE_READ', 'PAIE_WRITE']}>
                                                    <Button
                                                        onClick={() => onOpenStc(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        iconLeft={<Calculator size={14} />}
                                                        className="inline-flex items-center justify-center border-[var(--color-brand-600)] text-[var(--color-brand-600)] hover:bg-[var(--color-brand-100)]/40"
                                                        aria-label={`Calculer le solde de tout compte (STC) pour le contrat ${ct.numeroContrat || ''}`}
                                                    >
                                                        STC
                                                    </Button>
                                                    </HasPermission>
                                                )}
                                                {stcExiste && (
                                                    <span className="text-[10.5px] text-[var(--color-success-700)] px-2 py-1 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/30 font-sans">
                                                        STC établi
                                                    </span>
                                                )}
                                                {onOpenCertificat && (
                                                    <Button
                                                        onClick={() => onOpenCertificat(ct)}
                                                        variant="outline"
                                                        size="xs"
                                                        className="inline-flex items-center justify-center"
                                                        iconLeft={<Award size={14} />}
                                                        aria-label={`Générer le certificat de travail pour le contrat ${ct.numeroContrat || ''}`}
                                                    >
                                                        Certificat
                                                    </Button>
                                                )}
                                            </div>
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
