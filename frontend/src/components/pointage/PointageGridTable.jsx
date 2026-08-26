import { Link } from 'react-router-dom';
import { Clock } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import TimeInput from '@/components/ui/forms/TimeInput';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';
import { getStatutPointageBadge, isPointageRowGrayed } from '@/utils/pointageUtils';

export default function PointageGridTable({
    grilleData = [],
    siteParams,
    currentPage = 1,
    pageSize = 15,
    onPageChange,
    onPageSizeChange,
    isReadOnlyForUser = false,
    onRegisterInputRef,
    onUpdateHours,
    onUpdateIndemnite,
    onKeyDown,
    onFocusNext
}) {
    const { hasPermission } = useAuth();
    const canViewEmploye = hasPermission('EMPLOYE_READ');
    if (!grilleData || grilleData.length === 0) {
        return (
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 shadow-xs">
                <EmptyState
                    icon={Clock}
                    title="Aucun employé trouvé"
                    description="Aucun employé ne correspond aux filtres ou n'est affecté à ce site."
                />
            </div>
        );
    }

    return (
        <div className="space-y-3">
            {/* Légende subtile */}
            <div className="flex flex-wrap items-center gap-x-5 gap-y-2 px-1 text-[11px] text-[var(--color-fg-muted)]">
                <span className="font-bold uppercase tracking-wider text-[var(--color-fg)]">Légende :</span>
                <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 bg-orange-500 inline-block" /> Congé
                </span>
                <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 bg-blue-500 inline-block" /> Cadre autonome (pré-saisi)
                </span>
                <span className="flex items-center gap-1.5">
                    <span className="w-2.5 h-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] inline-block" /> Absent / non actif
                </span>
            </div>

            {/* Tableau Principal */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-x-auto">
                <Table>
                    <Thead>
                        <Tr className="border-b border-[var(--color-border-subtle)]">
                            <Th className="text-xs font-bold uppercase sticky top-0 z-10 bg-[var(--color-surface)] min-w-[200px]">
                                Employé
                            </Th>
                            <Th
                                title="Heure d'entrée du matin"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Entrée M.
                            </Th>
                            <Th
                                title="Heure de sortie pour la pause du midi"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Sortie M.
                            </Th>
                            <Th
                                title="Heure de retour de la pause du midi"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)] border-l border-l-[var(--color-border-subtle)]"
                            >
                                Retour P.
                            </Th>
                            <Th
                                title="Heure de sortie du soir"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Sortie S.
                            </Th>
                            <Th
                                title="Total des heures travaillées sur la journée"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Total
                            </Th>
                            <Th
                                title="Somme des retards constatés"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Retard
                            </Th>
                            <Th className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]">
                                Statut
                            </Th>
                            <Th
                                title="Indemnité de déplacement versée ce jour"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Déplacement
                            </Th>
                            <Th
                                title="Indemnité de repas versée ce jour"
                                className="text-xs font-bold uppercase text-center sticky top-0 z-10 bg-[var(--color-surface)]"
                            >
                                Repas
                            </Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {grilleData.map((row) => {
                            const isCongeFull = row.codeStatut === 'CONGE' || row.typeCongeJour === 'FULL_DAY';
                            const isCongeMatin = row.typeCongeJour === 'MATIN';
                            const isCongeAm = row.typeCongeJour === 'APRES_MIDI';
                            
                            let rowClassName;
                            if (isCongeFull) {
                                rowClassName = '!bg-orange-50/80 dark:!bg-orange-950/20 border-l-4 !border-l-orange-500';
                            } else if (row.estAutonome) {
                                rowClassName = '!bg-blue-50/80 dark:!bg-blue-950/20 border-l-4 !border-l-blue-500';
                            } else if (isPointageRowGrayed(row.codeStatut)) {
                                rowClassName = 'bg-[var(--color-bg-subtle)]/60 opacity-80 border-l-4 border-l-transparent';
                            } else {
                                rowClassName = 'odd:bg-[var(--color-bg-subtle)]/20 hover:bg-[var(--color-brand-500)]/5 border-l-4 border-l-transparent transition-colors';
                            }

                            return (
                                <Tr
                                    key={row.idEmploye}
                                    className={`${rowClassName} py-2.5`}
                                >
                                    {/* Employé */}
                                    <Td className="py-2.5">
                                        {canViewEmploye ? (
                                            <Link
                                                to={`/employes/${row.idEmploye}?tab=pointage`}
                                                className="group flex items-center gap-3 hover:opacity-90 transition-opacity"
                                                title="Ouvrir la fiche employé (onglet Pointage)"
                                            >
                                                <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)] text-white flex items-center justify-center font-bold text-xs shrink-0 group-hover:ring-2 group-hover:ring-[var(--color-brand-500)]/40">
                                                    {row.nom?.substring(0, 1)}{row.prenom?.substring(0, 1)}
                                                </div>
                                                <div>
                                                    <div className="flex items-center gap-2">
                                                        <p className="text-xs font-bold text-[var(--color-fg)] group-hover:text-[var(--color-brand-600)] dark:group-hover:text-[var(--color-brand-400)] transition-colors underline-offset-2 group-hover:underline">
                                                            {row.nom} {row.prenom}
                                                        </p>
                                                        {row.estAutonome && (
                                                            <Badge variant="info" className="text-[10px]">Autonome</Badge>
                                                        )}
                                                    </div>
                                                    <p className="text-[11px] text-[var(--color-fg-muted)] font-mono">{row.matricule}</p>
                                                </div>
                                            </Link>
                                        ) : (
                                            <div className="flex items-center gap-3">
                                                <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)] text-white flex items-center justify-center font-bold text-xs shrink-0">
                                                    {row.nom?.substring(0, 1)}{row.prenom?.substring(0, 1)}
                                                </div>
                                                <div>
                                                    <div className="flex items-center gap-2">
                                                        <p className="text-xs font-bold text-[var(--color-fg)]">
                                                            {row.nom} {row.prenom}
                                                        </p>
                                                        {row.estAutonome && (
                                                            <Badge variant="info" className="text-[10px]">Autonome</Badge>
                                                        )}
                                                    </div>
                                                    <p className="text-[11px] text-[var(--color-fg-muted)] font-mono">{row.matricule}</p>
                                                </div>
                                            </div>
                                        )}
                                    </Td>

                                    {/* 1. Entrée Matin */}
                                    <Td className={`text-center py-2.5 ${isCongeMatin ? 'bg-[var(--color-bg-subtle)]/70 opacity-80' : ''}`}>
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 0, el)}
                                            value={row.heureEntreeMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 0)}
                                            onComplete={() => onFocusNext(row.idEmploye, 0)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50 dark:!bg-red-900/20' : ''}
                                        />
                                    </Td>

                                    {/* 2. Sortie Matin */}
                                    <Td className={`text-center py-2.5 ${isCongeMatin ? 'bg-[var(--color-bg-subtle)]/70 opacity-80' : ''}`}>
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 1, el)}
                                            value={row.heureSortieMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 1)}
                                            onComplete={() => onFocusNext(row.idEmploye, 1)}
                                            placeholder={row.isPauseAuto ? (siteParams?.heureFinMatin || '12:00') : '--:--'}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50 dark:!bg-red-900/20' : ''}
                                        />
                                    </Td>

                                    {/* 3. Retour Pause */}
                                    <Td className={`text-center py-2.5 border-l border-l-[var(--color-border-subtle)] ${isCongeAm ? 'bg-[var(--color-bg-subtle)]/70 opacity-80' : ''}`}>
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 2, el)}
                                            value={row.heureEntreeAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 2)}
                                            onComplete={() => onFocusNext(row.idEmploye, 2)}
                                            placeholder={row.isPauseAuto ? (siteParams?.heureDebutAm || (siteParams?.horaireContinu ? '13:00' : '14:00')) : '--:--'}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50 dark:!bg-red-900/20' : ''}
                                        />
                                    </Td>

                                    {/* 4. Sortie Soir */}
                                    <Td className={`text-center py-2.5 ${isCongeAm ? 'bg-[var(--color-bg-subtle)]/70 opacity-80' : ''}`}>
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 3, el)}
                                            value={row.heureSortieAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 3)}
                                            onComplete={() => onFocusNext(row.idEmploye, 3)}
                                            className={row.hasValidationError ? '!border-red-500 !bg-red-50 dark:!bg-red-900/20' : ''}
                                        />
                                    </Td>

                                    {/* Totaux & Heures de Nuit */}
                                    <Td className="text-center py-2.5">
                                        <div className="flex flex-col items-center justify-center">
                                            <span className="font-mono text-xs font-bold text-[var(--color-fg)]">
                                                {(() => {
                                                    const th = row.totalHeures || 0;
                                                    const h = Math.floor(th);
                                                    const m = Math.round((th - h) * 60);
                                                    return `${String(h).padStart(2, '0')}h${String(m).padStart(2, '0')}`;
                                                })()}
                                            </span>
                                            {(() => {
                                                if (row.heureSortieAm && row.heureSortieAm > '22:00') {
                                                    const parts = row.heureSortieAm.split(':').map(Number);
                                                    const h = parts[0] || 0;
                                                    const m = parts[1] || 0;
                                                    const nightMins = (h * 60 + m) - (22 * 60);
                                                    if (nightMins > 0) {
                                                        const nightHours = (nightMins / 60).toFixed(1);
                                                        return (
                                                            <span 
                                                                className="inline-flex items-center gap-0.5 text-[9px] font-semibold text-indigo-600 dark:text-indigo-400 bg-indigo-500/10 px-1 py-0.2 mt-0.5" 
                                                                title={`Heures de nuit majorées (+30%) : ${nightHours}h au-delà de 22h00`}
                                                            >
                                                                🌙 {nightHours}h nuit
                                                            </span>
                                                        );
                                                    }
                                                }
                                                return null;
                                            })()}
                                        </div>
                                    </Td>

                                    <Td className={`font-mono text-xs font-semibold text-center py-2.5 ${(row.minutesRetard || 0) > 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-600)]'}`}>
                                        {(row.minutesRetard || 0) > 0 ? `-${row.minutesRetard} min` : '00 min'}
                                    </Td>

                                    {/* Statut */}
                                    <Td className="text-center py-2.5">
                                        <div className="flex flex-col items-center gap-0.5">
                                            {getStatutPointageBadge(row.codeStatut, row.minutesRetard)}
                                            {row.isPauseAuto && (
                                                <span className="text-[10px] text-[var(--color-brand-600)] font-bold">
                                                    (pause déduite)
                                                </span>
                                            )}
                                        </div>
                                    </Td>

                                    {/* Indemnité Déplacement (Matin & Après-midi / 2 trajets par jour) */}
                                    <Td className="text-center py-2">
                                        <div className="flex flex-col items-center justify-center gap-1">
                                            <div className="flex items-center gap-2">
                                                <label className={`inline-flex items-center gap-1 ${isCongeFull || isReadOnlyForUser ? '' : 'cursor-pointer'} select-none`} title="Trajet Matin (Aller)">
                                                    <input
                                                        type="checkbox"
                                                        checked={row.deplacementMatin !== false}
                                                        disabled={isReadOnlyForUser || isCongeFull}
                                                        onChange={(e) => onUpdateIndemnite(row.idEmploye, 'deplacementMatin', e.target.checked)}
                                                        className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-500)] rounded-none"
                                                    />
                                                    <span className="text-[10px] text-[var(--color-fg-subtle)] font-medium">M</span>
                                                </label>

                                                <label className={`inline-flex items-center gap-1 ${isCongeFull || isReadOnlyForUser ? '' : 'cursor-pointer'} select-none`} title="Trajet Soir (Retour)">
                                                    <input
                                                        type="checkbox"
                                                        checked={row.deplacementAm !== false}
                                                        disabled={isReadOnlyForUser || isCongeFull}
                                                        onChange={(e) => onUpdateIndemnite(row.idEmploye, 'deplacementAm', e.target.checked)}
                                                        className="w-3.5 h-3.5 cursor-pointer accent-[var(--color-brand-500)] rounded-none"
                                                    />
                                                    <span className="text-[10px] text-[var(--color-fg-subtle)] font-medium">S</span>
                                                </label>
                                            </div>

                                            {/* Badge compte de trajets */}
                                            {(() => {
                                                const nbTr = (row.deplacementMatin !== false ? 1 : 0) + (row.deplacementAm !== false ? 1 : 0);
                                                if (nbTr === 2) return <span className="text-[9px] font-bold text-emerald-700 dark:text-emerald-400 bg-emerald-500/10 px-1 py-0.2">2 tr (1j)</span>;
                                                if (nbTr === 1) return <span className="text-[9px] font-bold text-sky-700 dark:text-sky-400 bg-sky-500/10 px-1 py-0.2">1 tr (½j)</span>;
                                                return <span className="text-[9px] text-[var(--color-fg-subtle)]">0 tr</span>;
                                            })()}
                                        </div>
                                    </Td>

                                    {/* Indemnité Repas */}
                                    <Td className="text-center py-2.5">
                                        <div className="flex flex-col items-center justify-center gap-1">
                                            <label className={`inline-flex items-center gap-1.5 ${isCongeFull || isReadOnlyForUser ? '' : 'cursor-pointer'} select-none`}>
                                                <input
                                                    type="checkbox"
                                                    checked={!!row.indemniteRepasRecue}
                                                    disabled={isReadOnlyForUser || isCongeFull}
                                                    onChange={(e) => onUpdateIndemnite(row.idEmploye, 'indemniteRepasRecue', e.target.checked)}
                                                    className="w-4 h-4 cursor-pointer accent-[var(--color-brand-500)]"
                                                />
                                                <span className={`text-[10px] font-semibold ${row.indemniteRepasRecue ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                    {row.indemniteRepasRecue ? '2 000 Ar' : '0 Ar'}
                                                </span>
                                            </label>

                                            {/* Déclenchement automatique 2ème Repas Soir */}
                                            {(() => {
                                                if (row.heureSortieAm && row.heureSortieAm >= '21:00') {
                                                    return (
                                                        <span 
                                                            className="inline-flex items-center text-[9px] font-bold text-amber-700 dark:text-amber-400 bg-amber-500/15 border border-amber-500/30 px-1 py-0.2"
                                                            title="Sortie après 21h00 : 2ème repas majoré (4 000 Ar)"
                                                        >
                                                            +4 000 Ar (Soir)
                                                        </span>
                                                    );
                                                }
                                                if (row.heureSortieAm && row.heureSortieAm >= '19:30') {
                                                    return (
                                                        <span 
                                                            className="inline-flex items-center text-[9px] font-bold text-amber-600 dark:text-amber-300 bg-amber-500/10 border border-amber-500/20 px-1 py-0.2"
                                                            title="Sortie entre 19h30 et 21h00 : 2ème repas (2 000 Ar)"
                                                        >
                                                            +2 000 Ar (Soir)
                                                        </span>
                                                    );
                                                }
                                                return null;
                                            })()}
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>

                {/* Pagination intégrée au tableau */}
                <Pagination
                    currentPage={currentPage}
                    totalItems={grilleData.length}
                    pageSize={pageSize}
                    onPageChange={onPageChange}
                    onPageSizeChange={onPageSizeChange}
                />
            </div>
        </div>
    );
}
