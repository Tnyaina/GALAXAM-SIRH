import { CheckCircle2, AlertTriangle, Clock, Calendar, Users, Coffee, Car } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatHeuresMinutes, formatMonnaie } from '@/utils/pointageUtils';

export default function PointageRecapModal({
    isOpen = false,
    onClose,
    onConfirm,
    saving = false,
    grilleData = [],
    siteIndemnites = [],
    selectedDate = '',
    siteLabel = ''
}) {
    const safeData = Array.isArray(grilleData) ? grilleData : [];

    const fmtMoney = (val) => formatMonnaie(val);

    const totalInscrits = safeData.length;
    const presents = safeData.filter(r => r.present || r.dispensePointage || r.ferieTravaille || (r.totalHeures && r.totalHeures > 0));
    const retards = safeData.filter(r => r.retard || (r.minutesRetard && r.minutesRetard > 0));
    const cumulRetard = safeData.reduce((acc, r) => acc + (Number(r.minutesRetard) || 0), 0);
    const anomalies = safeData.filter(r => r.anomalie || r.absenceNonJustifiee);
    const repasCount = safeData.filter(r => r.indemniteRepasRecue).length;
    const totalTrajets = safeData.reduce((acc, r) => {
        let cnt = 0;
        if (r.deplacementMatin) cnt += 1;
        if (r.deplacementAm) cnt += 1;
        return acc + cnt;
    }, 0);
    const totalJoursDeplacement = totalTrajets / 2.0;

    // Configuration dynamique des montants du site (fournis par le backend)
    const deplacementConfig = siteIndemnites.find(i => i.code === 'DEPLACEMENT' || i.code === 'TRANSPORT');
    const montantDeplacementJour = deplacementConfig?.montantJour || deplacementConfig?.montantReference || 0;
    const montantTrajetSimple = deplacementConfig?.montantTrajet || Math.round(montantDeplacementJour / 2.0);

    const repasConfig = siteIndemnites.find(i => i.code === 'REPAS');
    const montantRepasBase = repasConfig?.montantJour || repasConfig?.montantReference || 0;

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
                            className="font-medium"
                        >
                            Corriger la saisie
                        </Button>
                        <Button
                            type="button"
                            variant="primary"
                            size="md"
                            loading={saving}
                            onClick={onConfirm}
                            className="font-bold flex items-center gap-2 shadow-xs px-5"
                        >
                            <CheckCircle2 size={16} />
                            <span>Confirmer &amp; Enregistrer</span>
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
                            <Clock size={14} className="text-danger-500" /> Retards
                        </p>
                        <p className={`text-xl font-bold font-mono mt-1.5 ${retards.length > 0 ? 'text-danger-600' : 'text-[var(--color-fg)]'}`}>
                            {retards.length} <span className="text-xs font-normal text-[var(--color-fg-muted)] font-sans">({cumulRetard} min)</span>
                        </p>
                    </div>
                    <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xs">
                        <p className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] flex items-center gap-1.5 tracking-wider">
                            <Coffee size={14} className="text-amber-500" /> Repas accordés
                        </p>
                        <p className="text-xl font-bold font-mono text-amber-600 mt-1.5">
                            {repasCount}
                        </p>
                    </div>
                    <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-2xs">
                        <p className="text-[11px] font-bold uppercase text-[var(--color-fg-muted)] flex items-center gap-1.5 tracking-wider">
                            <Car size={14} className="text-indigo-500" /> Déplacements
                        </p>
                        <p className="text-xl font-bold font-mono text-indigo-600 mt-1.5">
                            {totalTrajets} <span className="text-xs font-normal text-[var(--color-fg-muted)] font-sans">trajet{totalTrajets > 1 ? 's' : ''} ({totalJoursDeplacement}j)</span>
                        </p>
                    </div>
                </div>

                {/* 3. Alerte Anomalies si présentes */}
                {anomalies.length > 0 && (
                    <div className="p-3.5 bg-amber-500/10 border-l-4 border-l-amber-500 flex items-start gap-2.5 text-xs text-amber-900 dark:text-amber-200">
                        <AlertTriangle size={17} className="text-amber-600 shrink-0 mt-0.5" />
                        <div>
                            <p className="font-bold">{anomalies.length} anomalie(s) de saisie constatée(s) :</p>
                            <p className="text-[11.5px] opacity-90 mt-0.5 leading-relaxed">
                                Certains salariés ont des Saisies incomplets. Vous pouvez confirmer pour figer l'état actuel ou cliquer sur « Corriger la saisie » pour rectifier.
                            </p>
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

                                const montantRepasLigne = row.montantRepas || montantRepasBase;

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
                                            {formatHeuresMinutes(row.totalHeures, true)}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center font-mono">
                                            {row.minutesRetard > 0 ? (
                                                <span className="text-danger-600 font-bold">+{row.minutesRetard}m</span>
                                            ) : (
                                                <span className="text-emerald-600 font-medium">0m</span>
                                            )}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center">
                                            <Badge size="xs" variant={row.codeStatut === 'PRESENT' || row.codeStatut === 'FERIE_TRAVAILLE' ? 'success' : row.codeStatut === 'CONGE' ? 'warning' : 'neutral'}>
                                                {row.libelleStatut || row.codeStatut || 'Non saisi'}
                                            </Badge>
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center text-[11px]">
                                            <div className="flex flex-wrap items-center justify-center gap-1.5">
                                                {row.deplacementMatin && row.deplacementAm && (
                                                    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-500/10 text-blue-700 dark:text-blue-300 font-semibold text-[11px] border border-blue-500/20" title="Indemnité déplacement aller + retour (jour entier)">
                                                        <Car size={12} />
                                                        <span>Déplacement ({fmtMoney(montantDeplacementJour)})</span>
                                                    </span>
                                                )}
                                                {row.deplacementMatin && !row.deplacementAm && (
                                                    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-500/10 text-blue-700 dark:text-blue-300 font-semibold text-[11px] border border-blue-500/20" title="Trajet aller du matin">
                                                        <Car size={12} />
                                                        <span>Aller matin ({fmtMoney(montantTrajetSimple)})</span>
                                                    </span>
                                                )}
                                                {!row.deplacementMatin && row.deplacementAm && (
                                                    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-500/10 text-blue-700 dark:text-blue-300 font-semibold text-[11px] border border-blue-500/20" title="Trajet retour du soir">
                                                        <Car size={12} />
                                                        <span>Retour soir ({fmtMoney(montantTrajetSimple)})</span>
                                                    </span>
                                                )}
                                                {row.indemniteRepasRecue && (
                                                    <span className="inline-flex items-center gap-1 px-2 py-0.5 bg-amber-500/10 text-amber-700 dark:text-amber-300 font-semibold text-[11px] border border-amber-500/20" title="Indemnité de repas journalière">
                                                        <Coffee size={12} />
                                                        <span>Repas ({fmtMoney(montantRepasLigne)})</span>
                                                    </span>
                                                )}
                                                {!row.deplacementMatin && !row.deplacementAm && !row.indemniteRepasRecue && (
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
