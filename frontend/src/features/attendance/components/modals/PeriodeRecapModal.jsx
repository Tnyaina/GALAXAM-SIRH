import { useEffect } from 'react';
import { CheckCircle2, Clock, Calendar, Users, AlertTriangle } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatHeuresMinutes, formatHeuresDecimal } from '@/features/attendance/utils/attendanceDisplayUtils';

export default function PeriodeRecapModal({
    isOpen = false,
    onClose,
    onConfirm,
    saving = false,
    modifiedRows = [],
    employeNom = '',
    siteIndemnites = []
}) {
    // Raccourci clavier : validation immédiate avec Entrée si pas d'erreur
    useEffect(() => {
        if (!isOpen || saving || modifiedRows.some(r => r._previewPending || r.hasValidationError)) return;

        const handleKeyDown = (e) => {
            if (e.key === 'Enter') {
                e.preventDefault();
                e.stopPropagation();
                onConfirm?.();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, saving, onConfirm, modifiedRows]);

    const hasPending = modifiedRows.some(r => r._previewPending);
    const hasErrors = modifiedRows.some(r => r.hasValidationError);
    const cannotConfirm = saving || hasPending || hasErrors;

    const totalHeures = modifiedRows.reduce((acc, r) => acc + (Number(r.totalHeuresDecimal) || 0), 0);
    const totalRetard = modifiedRows.reduce((acc, r) => acc + (Number(r.minutesRetard) || 0), 0);

    return (
        <Modal
            isOpen={isOpen}
            onClose={saving ? undefined : onClose}
            title={`Récapitulatif avant enregistrement · ${employeNom || 'Collaborateur'}`}
            size="xl"
            closeOnEscape={!saving}
            closeOnOutsideClick={!saving}
        >
            <div className="space-y-4">
                {/* Bandeau d'information */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 flex items-center justify-between text-xs">
                    <div className="flex items-center gap-2">
                        <Calendar size={15} className="text-[var(--color-brand-600)]" />
                        <span className="font-semibold text-[var(--color-fg)]">
                            {modifiedRows.length} journée{modifiedRows.length > 1 ? 's' : ''} modifiée{modifiedRows.length > 1 ? 's' : ''} à sauvegarder
                        </span>
                    </div>
                    <div className="flex items-center gap-3 font-mono text-[11px]">
                        <span>
                            Cumul heures modifiées : <strong className="text-[var(--color-brand-600)]">{formatHeuresDecimal(totalHeures)}</strong>
                        </span>
                        {totalRetard > 0 && (
                            <span className="text-[var(--color-warning-600)] font-semibold">
                                Retard : +{totalRetard}m
                            </span>
                        )}
                    </div>
                </div>

                {hasErrors && (
                    <Alert
                        type="error"
                        title="Erreurs de validation détectées"
                        message="Certaines journées comportent des anomalies de saisie (horaires incohérents ou manquants). Corrigez-les avant d'enregistrer."
                    />
                )}

                {hasPending && (
                    <Alert
                        type="info"
                        title="Calcul en cours"
                        message="La prévisualisation en temps réel termine ses calculs réglementaires…"
                    />
                )}

                {/* Table des journées modifiées */}
                <div className="max-h-72 overflow-y-auto border border-[var(--color-border-subtle)]">
                    <Table density="compact" className="w-full text-xs text-left">
                        <Thead>
                            <Tr className="bg-[var(--color-surface)] border-b border-[var(--color-border-subtle)] text-[10px] uppercase font-bold text-[var(--color-fg-muted)]">
                                <Th className="px-2.5 py-1.5">Date</Th>
                                <Th className="px-2 py-1.5 text-center">Horaires</Th>
                                <Th className="px-2 py-1.5 text-center">Heures</Th>
                                <Th className="px-2 py-1.5 text-center">Retard</Th>
                                <Th className="px-2 py-1.5 text-center">Statut</Th>
                                <Th className="px-2 py-1.5">Indemnités</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {modifiedRows.map(row => {
                                const hoursSummary = row.horaireContinu
                                    ? `${row.heureEntreeMatin || '—'} → ${row.heureSortieAm || '—'} (Continu)`
                                    : [row.heureEntreeMatin, row.heureSortieMatin, row.heureEntreeAm, row.heureSortieAm]
                                        .filter(Boolean)
                                        .join(' · ') || '—';

                                const grantedAllowances = siteIndemnites.flatMap(ind => {
                                    const choice = row.dynamicIndemnites?.[String(ind.idIndemnite)];
                                    if (!choice) return [];
                                    if (ind.estFractionnable || ind.fractionnable) {
                                        if (!choice.matin && !choice.am) return [];
                                        const part = choice.matin && choice.am ? 'j' : choice.matin ? 'matin' : 'am';
                                        return [`${ind.code} (${part})`];
                                    }
                                    return choice.accordee ? [ind.code] : [];
                                });

                                return (
                                    <Tr key={row.datePointage} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/30">
                                        <Td className="px-2.5 py-1.5 font-mono font-bold whitespace-nowrap">
                                            {row.datePointage}
                                            <span className="text-[10px] text-[var(--color-fg-muted)] font-normal ml-1">
                                                ({row.jour})
                                            </span>
                                        </Td>
                                        <Td className="px-2 py-1.5 font-mono text-center text-[11px]">
                                            {hoursSummary}
                                        </Td>
                                        <Td className="px-2 py-1.5 font-mono font-bold text-center">
                                            {row.heuresFaites || formatHeuresMinutes(row.totalHeuresDecimal)}
                                        </Td>
                                        <Td className="px-2 py-1.5 text-center font-mono">
                                            {row.minutesRetard > 0 ? (
                                                <span className="text-[var(--color-warning-600)] font-semibold">
                                                    +{row.minutesRetard}m
                                                </span>
                                            ) : '—'}
                                        </Td>
                                        <Td className="px-2 py-1.5 text-center">
                                            <Badge variant="neutral" size="sm">
                                                {row.libelleStatut || row.codeStatut}
                                            </Badge>
                                        </Td>
                                        <Td className="px-2 py-1.5 text-[11px] text-[var(--color-fg-muted)]">
                                            {grantedAllowances.length > 0 ? grantedAllowances.join(', ') : '—'}
                                        </Td>
                                    </Tr>
                                );
                            })}
                        </Tbody>
                    </Table>
                </div>

                {/* Actions de pied de modale */}
                <div className="flex items-center justify-between pt-2 border-t border-[var(--color-border-subtle)]">
                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                        Appuyez sur <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">Entrée</kbd> pour confirmer ou <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">Échap</kbd> pour annuler
                    </span>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={onClose}
                            disabled={saving}
                        >
                            Annuler
                        </Button>
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={onConfirm}
                            disabled={cannotConfirm}
                            isLoading={saving}
                            className="bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-[var(--color-on-color)]"
                        >
                            Confirmer l'enregistrement
                        </Button>
                    </div>
                </div>
            </div>
        </Modal>
    );
}
