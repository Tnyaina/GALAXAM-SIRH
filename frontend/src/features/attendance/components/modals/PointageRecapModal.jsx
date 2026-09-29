import { useEffect } from 'react';
import { CheckCircle2, AlertTriangle, Clock, Calendar, Users, Gift } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatHeuresMinutes, formatHeuresDecimal } from '@/features/attendance/utils/attendanceDisplayUtils';

export default function PointageRecapModal({
    isOpen = false,
    onClose,
    onConfirm,
    saving = false,
    grilleData = [],
    siteIndemnites = [],
    modifiedEmpIds = new Set(),
    selectedDate = '',
    siteLabel = ''
}) {
    // Effet : raccourci clavier de validation immédiate avec Entrée.
    useEffect(() => {
        if (!isOpen || saving || grilleData.some(r => r._previewPending || r._previewError)) return;

        const handleKeyDown = (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                onConfirm?.();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, saving, onConfirm, grilleData]);
    // Données dérivées pour le récapitulatif journalier.
    const safeData = Array.isArray(grilleData) ? grilleData : [];
    const pending = safeData.filter(r => r._previewPending);
    const previewErrors = safeData.filter(r => r._previewError);
    const cannotConfirm = saving || pending.length > 0 || previewErrors.length > 0;

    const totalInscrits = safeData.length;
    const presents = safeData.filter(r => r.present || r.dispensePointage || r.ferieTravaille || r.samediTravaille || r.dimancheTravaille);
    const retards = safeData.filter(r => r.retard);
    const cumulRetard = safeData.reduce((acc, r) => acc + (Number(r.minutesRetard) || 0), 0);
    const anomalies = safeData.filter(r => r.hasValidationError || r.anomalie);
    const autonomes = safeData.filter(r => r.dispensePointage);
    const allowanceChoices = (row) => siteIndemnites.flatMap((allowance) => {
        const choice = row.dynamicIndemnites?.[String(allowance.idIndemnite)];
        if (!choice) return [];
        if (allowance.fractionnable) {
            if (!choice.matin && !choice.am) return [];
            const part = choice.matin && choice.am ? 'journée' : choice.matin ? 'matin' : 'après-midi';
            return [`${allowance.code} (${part})`];
        }
        return choice.accordee ? [allowance.code] : [];
    });
    const allowanceCounts = siteIndemnites.map((allowance) => ({
        ...allowance,
        count: safeData.filter((row) => {
            const choice = row.dynamicIndemnites?.[String(allowance.idIndemnite)];
            return allowance.fractionnable ? Boolean(choice?.matin || choice?.am) : Boolean(choice?.accordee);
        }).length
    }));

    // Rendu
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Récapitulatif & Validation des Pointages du Jour"
            size="4xl"
            footer={
                <div className="flex flex-col sm:flex-row items-center justify-between w-full gap-3">
                    <p className="text-xs text-[var(--color-fg-muted)] text-center sm:text-left">
                        Vérifiez l'exactitude des horaires et indemnités avant confirmation.
                    </p>
                    <div className="flex items-center gap-2.5 shrink-0">
                        <Button
                            type="button"
                            variant="outline"
                            size="md"
                            onClick={onClose}
                            disabled={saving}
                            className="font-medium inline-flex items-center gap-2"
                        >
                            <span>Corriger la saisie</span>
                            <kbd className="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-mono bg-[var(--color-bg-subtle)] border border-[var(--color-border)] text-[var(--color-fg-muted)] leading-none">
                                Échap
                            </kbd>
                        </Button>
                        <Button
                            type="button"
                            variant="primary"
                            size="md"
                            loading={saving}
                            disabled={cannotConfirm}
                            onClick={onConfirm}
                            className="font-bold flex items-center gap-2 shadow-xs px-5"
                        >
                            <CheckCircle2 size={16} />
                            <span>Confirmer &amp; Enregistrer</span>
                            <kbd className="hidden sm:inline-block px-1.5 py-0.5 text-[10px] font-mono bg-[var(--color-surface)]/20 border border-[var(--color-on-color)]/30 text-[var(--color-on-color)] leading-none">
                                Entrée ↵
                            </kbd>
                        </Button>
                    </div>
                </div>
            }
        >
            <div className="space-y-4">
                {/* 1. En-tête Contextuel */}
                <div className="flex flex-wrap items-center justify-between gap-3 p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                    <div className="flex items-center gap-2 text-xs font-bold text-[var(--color-fg)]">
                        <Calendar size={15} className="text-brand-500" />
                        <span>Date de pointage : <strong className="font-mono text-brand-700 dark:text-brand-300">{selectedDate}</strong></span>
                    </div>
                    <div className="text-xs text-[var(--color-fg-muted)]">
                        Site ciblé : <span className="font-bold text-[var(--color-fg)]">{siteLabel}</span>
                    </div>
                </div>

                {/* 2. Indicateurs Synthétiques Grands & Lisibles */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3.5">
                    <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xs">
                        <p className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] flex items-center gap-1.5 tracking-wider">
                            <Users size={14} className="text-brand-500" /> Présence
                        </p>
                        <p className="text-xl font-bold font-mono text-[var(--color-fg)] mt-1.5">
                            {presents.length} <span className="text-xs font-normal text-[var(--color-fg-muted)] font-sans">/ {totalInscrits}</span>
                        </p>
                    </div>
                    <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xs">
                        <p className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] flex items-center gap-1.5 tracking-wider">
                            <Clock size={14} className="text-[var(--color-warning-600)]" /> Retards
                        </p>
                        <p className={`text-xl font-bold font-mono mt-1.5 ${retards.length > 0 ? 'text-[var(--color-warning-700)]' : 'text-[var(--color-fg)]'}`}>
                            {retards.length} <span className="text-xs font-normal text-[var(--color-fg-muted)] font-sans">({cumulRetard} min)</span>
                        </p>
                    </div>
                    {allowanceCounts.map((allowance) => (
                        <div key={allowance.idIndemnite} className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xs">
                            <p className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] flex items-center gap-1.5 tracking-wider">
                                <Gift size={14} className="text-[var(--color-info-500)]" /> {allowance.code}
                            </p>
                            <p className="text-xl font-bold font-mono text-[var(--color-info-600)] mt-1.5">{allowance.count}</p>
                        </div>
                    ))}
                </div>
                <p className="text-xs text-[var(--color-fg-muted)]">
                    {modifiedEmpIds.size} ligne(s) modifiée(s) à enregistrer · {autonomes.length} autonome(s) inclus dans les présences
                    {pending.length > 0 && ` · ${pending.length} calcul(s) en cours`}
                    {previewErrors.length > 0 && ` · ${previewErrors.length} calcul(s) échoué(s)`}
                </p>
                {previewErrors.length > 0 && (
                    <div role="alert" className="p-3 text-xs text-danger-600 border border-danger-500">
                        Corrigez ou relancez la saisie des lignes dont la prévisualisation a échoué avant de confirmer.
                        {previewErrors.map(row => <div key={row.idEmploye}>{row.nom} {row.prenom} : {row._previewError}</div>)}
                    </div>
                )}

                {/* 3. Alerte Anomalies si présentes */}
                {anomalies.length > 0 && (
                    <div className="p-3.5 bg-[var(--color-warning-500)]/10 border-l-4 border-l-amber-500 flex items-start gap-2.5 text-xs text-[var(--color-warning-900)] dark:text-[var(--color-warning-200)]">
                        <AlertTriangle size={17} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                        <div>
                            <p className="font-bold">{anomalies.length} anomalie(s) de saisie constatée(s) :</p>
                            {anomalies.map(row => (
                                <p key={row.idEmploye} className="text-[11.5px] mt-1">
                                    {row.nom} {row.prenom} : {row.validationMessages?.length
                                        ? row.validationMessages.join(' · ')
                                        : 'Statut d’anomalie enregistré ; modifiez la ligne pour relancer le calcul.'}
                                </p>
                            ))}
                        </div>
                    </div>
                )}

                {/* 4. Tableau Synthétique Complet et Confortable */}
                <div className="max-h-[460px] overflow-y-auto border border-[var(--color-border-subtle)]">
                    <Table className="w-full text-xs">
                        <Thead>
                            <Tr className="bg-[var(--color-bg-subtle)] text-[11px] font-bold">
                                <Th className="py-2.5 px-4 w-[240px]">Collaborateur</Th>
                                <Th className="py-2.5 px-3 text-center">Matin (Entrée → Sortie)</Th>
                                <Th className="py-2.5 px-3 text-center">Après-midi (Retour → Sortie)</Th>
                                <Th className="py-2.5 px-3 text-center">Heures</Th>
                                <Th className="py-2.5 px-3 text-center">Retard</Th>
                                <Th className="py-2.5 px-3 text-center">Statut</Th>
                                <Th className="py-2.5 px-3 text-center">Indemnités</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {safeData.map((row) => {
                                const matinStr = (row.heureEntreeMatin || row.heureSortieMatin)
                                    ? `${row.heureEntreeMatin || '--:--'} → ${row.heureSortieMatin || '--:--'}`
                                    : '—';
                                const amStr = (row.heureEntreeAm || row.heureSortieAm)
                                    ? `${row.heureEntreeAm || '--:--'} → ${row.heureSortieAm || '--:--'}`
                                    : '—';

                                const choices = allowanceChoices(row);

                                return (
                                    <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50">
                                        <Td className="py-2.5 px-4 font-semibold">
                                            {row.nom} {row.prenom}
                                            <span className="block text-[10.5px] font-mono text-[var(--color-fg-muted)]">
                                                Matricule {row.matricule}
                                            </span>
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center font-mono text-[12px]">{matinStr}</Td>
                                        <Td className="py-2.5 px-3 text-center font-mono text-[12px]">{amStr}</Td>
                                        <Td className="py-2.5 px-3 text-center font-mono font-bold text-[12.5px]">
                                            <div>{row._previewPending ? 'Calcul…' : row._previewError ? '—' : formatHeuresMinutes(row.totalHeures, true)}</div>
                                            {!row._previewPending && !row._previewError && <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-mono">({formatHeuresDecimal(row.totalHeures)})</div>}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center font-mono">
                                            {row.minutesRetard > 0 ? (
                                                <span className="text-danger-600 font-bold">+{row.minutesRetard}m</span>
                                            ) : (
                                                <span className="text-[var(--color-success-600)] font-medium">0m</span>
                                            )}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center">
                                            <Badge size="xs" variant={row.codeStatut === 'PRESENT' || row.codeStatut === 'FERIE_TRAVAILLE' ? 'success' : row.codeStatut === 'CONGE' ? 'warning' : 'neutral'}>
                                                {row.libelleStatut || row.codeStatut || 'Non saisi'}
                                            </Badge>
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center text-[11px]">
                                            <div className="flex flex-wrap items-center justify-center gap-1.5">
                                                {choices.map((choice) => (
                                                    <span key={choice} className="inline-flex items-center gap-1 px-2 py-0.5 bg-[var(--color-info-500)]/10 text-[var(--color-info-700)] dark:text-[var(--color-info-300)] font-semibold text-[11px] border border-[var(--color-info-500)]/20">
                                                        <Gift size={12} /> {choice}
                                                    </span>
                                                ))}
                                                {choices.length === 0 && (
                                                    <span className="text-[var(--color-fg-muted)] font-mono">—</span>
                                                )}
                                            </div>
                                        </Td>
                                    </Tr>
                                );
                            })}
                        </Tbody>
                    </Table>
                </div>
            </div>
        </Modal>
    );
}
