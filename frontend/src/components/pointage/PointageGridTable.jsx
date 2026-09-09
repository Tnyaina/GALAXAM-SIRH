import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Moon, ClockCheck, AlertTriangle, Car, Check, X, CheckSquare, Clock, UserX } from 'lucide-react';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import TimeInput from '@/components/ui/forms/TimeInput';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import Badge from '@/components/ui/data-display/Badge';
import Pagination from '@/components/ui/navigation/Pagination';
import { formatHeuresMinutes, formatMonnaie } from '@/utils/pointageUtils';
import StatusPickerPopover from '@/components/pointage/StatusPickerPopover';

export default function PointageGridTable({
    grilleData = [],
    _siteParams,
    siteIndemnites = [],
    selectedStatutFilter = 'ALL',
    onStatutFilterChange,
    onUpdateHours,
    onUpdateStatut,
    onUpdateIndemnite,
    onUpdateDynamicIndemnite,
    onBulkStandardHours,
    onBulkStatut,
    onBulkIndemnite,
    onRegisterInputRef,
    onKeyDown,
    onFocusNext,
    currentPage = 1,
    pageSize = 25,
    onPageChange,
    onPageSizeChange,
    isReadOnly = false
}) {
    const startIndex = (currentPage - 1) * pageSize;
    const paginatedData = grilleData.slice(startIndex, startIndex + pageSize);

    // État de sélection multiple (Bulk actions)
    const [selectedEmpIds, setSelectedEmpIds] = useState(new Set());
    const [showCollectiveAbsenceModal, setShowCollectiveAbsenceModal] = useState(false);
    const [collectiveMotif, setCollectiveMotif] = useState('');

    const toggleSelectRow = (idEmploye) => {
        setSelectedEmpIds(prev => {
            const next = new Set(prev);
            if (next.has(idEmploye)) next.delete(idEmploye);
            else next.add(idEmploye);
            return next;
        });
    };

    const isAllSelected = paginatedData.length > 0 && paginatedData.every(r => selectedEmpIds.has(r.idEmploye));
    const isSomeSelected = paginatedData.some(r => selectedEmpIds.has(r.idEmploye)) && !isAllSelected;

    const toggleSelectAll = () => {
        if (isAllSelected) {
            setSelectedEmpIds(prev => {
                const next = new Set(prev);
                paginatedData.forEach(r => next.delete(r.idEmploye));
                return next;
            });
        } else {
            setSelectedEmpIds(prev => {
                const next = new Set(prev);
                paginatedData.forEach(r => next.add(r.idEmploye));
                return next;
            });
        }
    };

    const handleClearSelection = () => {
        setSelectedEmpIds(new Set());
    };

    const handleApplyBulkStandard = () => {
        if (selectedEmpIds.size === 0) return;
        onBulkStandardHours?.(selectedEmpIds);
        handleClearSelection();
    };

    const handleOpenCollectiveAbsence = () => {
        setCollectiveMotif('');
        setShowCollectiveAbsenceModal(true);
    };

    const handleConfirmCollectiveAbsence = () => {
        if (selectedEmpIds.size === 0) return;
        onBulkStatut?.(selectedEmpIds, 'ABSENCE_JUSTIFIEE', collectiveMotif || 'Absence collective');
        setShowCollectiveAbsenceModal(false);
        handleClearSelection();
    };

    const handleApplyBulkTransport = () => {
        if (selectedEmpIds.size === 0) return;
        onBulkIndemnite?.(selectedEmpIds, 'deplacementMatin', true);
        onBulkIndemnite?.(selectedEmpIds, 'deplacementAm', true);
        handleClearSelection();
    };

    const fmtMoney = (val) => formatMonnaie(val);

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden flex flex-col">
            {/* Barre combinée : Filtres de statut à gauche + Info & Légende à droite */}
            <div className="flex flex-wrap items-center justify-between px-3 py-1.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-xs text-[var(--color-fg-muted)] gap-2">
                {/* 1. Filtres d'état (Pills au-dessus de la table) */}
                <div className="flex items-center gap-1 overflow-x-auto">
                    {[
                        { id: 'ALL', label: 'Tous', count: grilleData.length },
                        { id: 'PRESENT', label: 'Présents', count: grilleData.filter(r => r.present || r.dispensePointage || r.ferieTravaille || (r.totalHeures && r.totalHeures > 0)).length },
                        { id: 'NON_SAISI', label: 'Non saisis', count: grilleData.filter(r => Boolean(r.nonSaisi)).length },
                        { id: 'RETARD', label: 'Retards', count: grilleData.filter(r => Boolean(r.retard)).length },
                        { id: 'CONGE', label: 'Congés', count: grilleData.filter(r => Boolean(r.conge || r.absenceJustifiee)).length },
                    ].map((tab) => {
                        const isActive = selectedStatutFilter === tab.id;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                onClick={() => onStatutFilterChange?.(tab.id)}
                                className={`
                                    px-2.5 py-1 text-[11px] transition-all cursor-pointer border h-[26px] whitespace-nowrap flex items-center shrink-0
                                    ${isActive
                                        ? 'bg-brand-600 border-brand-600 text-white font-bold'
                                        : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }
                                `}
                            >
                                <span>{tab.label}</span>
                                <span className={`ml-1.5 font-mono text-[10px] ${isActive ? 'opacity-95 font-bold' : 'opacity-60'}`}>
                                    {tab.count}
                                </span>
                            </button>
                        );
                    })}
                </div>

                {/* 2. Légende & Info */}
                <div className="flex items-center gap-3 text-[11px]">
                    <span className="flex items-center gap-1.5"><span className="w-2 h-2 bg-[var(--color-warning-500)] inline-block"></span> Congé</span>
                    <span className="flex items-center gap-1.5"><span className="w-2 h-2 bg-[var(--color-ink-350)] inline-block"></span> Férié</span>
                    <span className="flex items-center gap-1.5"><span className="w-2 h-2 bg-[var(--color-info-500)] inline-block"></span> Cadre</span>
                    <span className="flex items-center gap-1.5"><span className="w-2 h-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] inline-block"></span> Non saisi</span>
                </div>
            </div>

            <div className="overflow-x-auto overflow-y-auto" style={{ maxHeight: 'calc(100vh - 260px)' }}>
                <Table className="w-full text-left border-collapse text-[13px]">
                    <Thead className="sticky top-0 z-10">
                        {/* Ligne 1 d'en-tête (Titres de Blocs) */}
                        <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            <Th className="w-10 px-3 py-2.5 text-center">
                                <input
                                    type="checkbox"
                                    checked={isAllSelected}
                                    ref={el => { if (el) el.indeterminate = isSomeSelected; }}
                                    onChange={toggleSelectAll}
                                    disabled={isReadOnly}
                                    className="w-3.5 h-3.5 rounded accent-brand-600 cursor-pointer align-middle"
                                    title={isAllSelected ? "Tout désélectionner" : "Sélectionner tous les employés de la page"}
                                />
                            </Th>
                            <Th className="px-4 py-2.5 w-[210px]">Employé</Th>
                            <Th className="px-2 py-2.5 text-center border-l border-[var(--color-border-subtle)]" colSpan={2}>
                                Matin
                            </Th>
                            <Th className="px-2 py-2.5 text-center border-l border-[var(--color-border-subtle)]" colSpan={2}>
                                Après-midi
                            </Th>
                            <Th className="px-3 py-2.5 text-center border-l border-[var(--color-border-subtle)] min-w-[85px]">
                                Total
                            </Th>
                            <Th className="px-3 py-2.5 text-center min-w-[85px]">
                                Retard
                            </Th>
                            <Th className="px-3 py-2.5 text-center min-w-[110px]">
                                Statut
                            </Th>
                            {siteIndemnites.map((ind) => (
                                <Th key={ind.idIndemnite} className="px-3 py-2.5 border-l border-[var(--color-border-subtle)] text-center min-w-[130px]" title={ind.libelle}>
                                    <div className="flex flex-col items-center">
                                        <span>{ind.libelle || ind.code}</span>
                                        <span className="text-[10px] font-normal text-[var(--color-fg-muted)] normal-case">
                                            {ind.sousTitre || (ind.estProratise ? `Prorata ${fmtMoney(ind.montantJour)}/j` : `${fmtMoney(ind.montantJour)}/j`)}
                                        </span>
                                    </div>
                                </Th>
                            ))}
                        </Tr>
                        {/* Ligne 2 d'en-tête (Sous-colonnes Entrée / Sortie) */}
                        <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider sticky top-[38px] z-10">
                            <Th className="w-10 px-3 py-1.5 text-center"></Th>
                            <Th className="px-4 py-1.5"></Th>
                            <Th className="px-2 py-1.5 text-center border-l border-[var(--color-border-subtle)]">Entrée</Th>
                            <Th className="px-2 py-1.5 text-center">Sortie</Th>
                            <Th className="px-2 py-1.5 text-center border-l border-[var(--color-border-subtle)]">Retour</Th>
                            <Th className="px-2 py-1.5 text-center">Sortie</Th>
                            <Th className="border-l border-[var(--color-border-subtle)]" colSpan={3}></Th>
                            {siteIndemnites.map((ind) => (
                                <Th key={ind.idIndemnite} className="border-l border-[var(--color-border-subtle)]"></Th>
                            ))}
                        </Tr>
                    </Thead>
                    <Tbody>
                        {paginatedData.map((row) => {
                            const isCongeFull = Boolean(row.congeFull ?? (row.typeCongeJour === 'FULL_DAY' || row.conge));
                            const isCongeMatin = Boolean(row.congeMatin ?? (row.typeCongeJour === 'MATIN'));
                            const isCongeAm = Boolean(row.congeAm ?? (row.typeCongeJour === 'APRES_MIDI'));
                            const _isFerieChome = Boolean(row.ferieChome);
                            const isReadOnlyForUser = isReadOnly;

                            const isNonSaisi = Boolean(row.nonSaisi);

                            let rowBorderCue = 'border-l-2 border-l-transparent';
                            if (isNonSaisi) rowBorderCue = 'border-l-2 border-l-amber-500';
                            else if (row.totalHeures > 0) rowBorderCue = 'border-l-2 border-l-emerald-500';

                            return (
                                <Tr key={row.idEmploye} className={`border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors ${rowBorderCue}`}>
                                    {/* 0. Case à cocher de sélection */}
                                    <Td className="w-10 px-3 py-2 text-center">
                                        <input
                                            type="checkbox"
                                            checked={selectedEmpIds.has(row.idEmploye)}
                                            onChange={() => toggleSelectRow(row.idEmploye)}
                                            disabled={isReadOnlyForUser}
                                            className="w-3.5 h-3.5 rounded accent-brand-600 cursor-pointer align-middle"
                                            aria-label={`Sélectionner ${row.nom}`}
                                        />
                                    </Td>

                                    {/* 1. Identification Salarié */}
                                    <Td className="px-3.5 py-2">
                                        <div className="flex items-center gap-2.5 min-w-[210px]">
                                            <Avatar
                                                src={row.photoUrl ? getFullPhotoUrl(row.photoUrl) : null}
                                                name={`${row.prenom || ''} ${row.nom || ''}`}
                                                size="sm"
                                                shape="square"
                                            />
                                            <div className="min-w-0 flex-1">
                                                <p className="font-semibold text-[var(--color-fg)] leading-tight truncate">
                                                    <Link
                                                        to={`/employes/${row.idEmploye}?tab=pointage`}
                                                        className="hover:text-brand-600 hover:underline"
                                                        title={`${row.nom} ${row.prenom}`}
                                                    >
                                                        {row.nom} {row.prenom}
                                                    </Link>
                                                </p>
                                                <div className="flex items-center gap-1.5 text-[11px] font-mono text-[var(--color-fg-muted)] mt-0.5">
                                                    <span>{row.matricule}</span>
                                                    {row.badgeProfilLabel && (
                                                        <span
                                                            className={`text-[9px] font-bold px-1 py-0.2 tracking-wide shrink-0 ${
                                                                row.badgeProfilVariant === 'purple'
                                                                    ? 'bg-purple-500/15 text-purple-700 dark:text-purple-300'
                                                                    : 'bg-brand-500/15 text-brand-700 dark:text-brand-300'
                                                            }`}
                                                            title={row.badgeProfilTooltip || row.badgeProfilLabel}
                                                        >
                                                            {row.badgeProfilLabel}
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                        </div>
                                    </Td>

                                    {/* 2. Entrée Matin */}
                                    <Td className="px-2 py-1.5 text-center border-l border-[var(--color-border-subtle)]">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 0, el)}
                                            value={row.heureEntreeMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 0)}
                                            onComplete={() => onFocusNext(row.idEmploye, 0)}
                                            className={row.hasValidationError ? '!border-[var(--color-danger-500)] !bg-[var(--color-danger-500)]/10' : ''}
                                        />
                                    </Td>

                                    {/* 3. Sortie Matin */}
                                    <Td className="px-2 py-1.5 text-center">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 1, el)}
                                            value={row.heureSortieMatin}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeMatin}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieMatin', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 1)}
                                            onComplete={() => onFocusNext(row.idEmploye, 1)}
                                            className={row.hasValidationError ? '!border-[var(--color-danger-500)] !bg-[var(--color-danger-500)]/10' : ''}
                                        />
                                    </Td>

                                    {/* 4. Retour Pause Midi */}
                                    <Td className="px-2 py-1.5 text-center border-l border-[var(--color-border-subtle)]">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 2, el)}
                                            value={row.heureEntreeAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureEntreeAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 2)}
                                            onComplete={() => onFocusNext(row.idEmploye, 2)}
                                            className={row.hasValidationError ? '!border-[var(--color-danger-500)] !bg-[var(--color-danger-500)]/10' : ''}
                                        />
                                    </Td>

                                    {/* 5. Sortie Soir */}
                                    <Td className="px-2 py-1.5 text-center">
                                        <TimeInput
                                            inputRef={(el) => onRegisterInputRef?.(row.idEmploye, 3, el)}
                                            value={row.heureSortieAm}
                                            disabled={isReadOnlyForUser || isCongeFull || isCongeAm}
                                            onChange={(val) => onUpdateHours(row.idEmploye, 'heureSortieAm', val)}
                                            onKeyDown={(e) => onKeyDown(e, row.idEmploye, 3)}
                                            className={row.hasValidationError ? '!border-[var(--color-danger-500)] !bg-[var(--color-danger-500)]/10' : ''}
                                        />
                                    </Td>

                                    {/* 6. Total Heures */}
                                    <Td className="px-3 py-1.5 text-center font-mono font-semibold border-l border-[var(--color-border-subtle)]">
                                        <div className="flex flex-col items-center justify-center">
                                            <span>
                                                {formatHeuresMinutes(row.totalHeures, true)}
                                            </span>
                                            {row.heuresNuit > 0 && (
                                                <span className="inline-flex items-center gap-0.5 text-[9px] font-bold text-purple-700 dark:text-purple-300 bg-purple-500/10 px-1 mt-0.5">
                                                    <Moon size={9} /> nuit
                                                </span>
                                            )}
                                        </div>
                                    </Td>

                                    {/* 7. Retard */}
                                    <Td className="px-3 py-1.5 text-center font-mono font-semibold">
                                        {row.minutesRetard > 0 ? (
                                            <span className="text-danger-600 dark:text-danger-400">
                                                +{row.minutesRetard} min
                                            </span>
                                        ) : (
                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                00 min
                                            </span>
                                        )}
                                    </Td>

                                    {/* 8. Statut */}
                                    <Td className="px-3 py-1.5 text-center">
                                        <StatusPickerPopover
                                            row={row}
                                            currentStatus={row.codeStatut}
                                            currentMotif={row.motifAjustement}
                                            onStatusChange={(newStatut, motif) => onUpdateStatut?.(row.idEmploye, newStatut, motif)}
                                            disabled={isReadOnlyForUser || Boolean(row.ferieChome) || Boolean(row.conge)}
                                        />
                                    </Td>

                                    {/* 9. Colonnes Dynamiques d'Indemnités avec Cases à cocher */}
                                    {siteIndemnites.map((ind) => {
                                        const dynObj = row.dynamicIndemnites?.[String(ind.idIndemnite)] || {};
                                        const isAccordee = dynObj.accordee !== false && !isCongeFull;
                                        const montantJour = ind.montantJour || ind.montantReference || 0;
                                        const isFractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;

                                        if (isFractionnable) {
                                            const montantTrajet = ind.montantTrajet || Math.round(montantJour / 2.0);
                                            const matVal = dynObj.matin !== undefined ? dynObj.matin : (row.deplacementMatin !== false && isAccordee);
                                            const amVal = dynObj.am !== undefined ? dynObj.am : (row.deplacementAm !== false && isAccordee);

                                            return (
                                                <Td key={ind.idIndemnite} className="px-3 py-1.5 text-center border-l border-[var(--color-border-subtle)]">
                                                    {row.estDerogeDeplacement ? (
                                                        <div className="flex items-center justify-center">
                                                            <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-1.5 py-0.5" title={row.motifDerogationDeplacement || 'Exclu deplacement (ex: vehicule de fonction)'}>
                                                                {row.labelDerogationDeplacement || 'Vehicule'}
                                                            </span>
                                                        </div>
                                                    ) : (
                                                        <div className="flex items-center justify-center gap-3">
                                                            <label className={`inline-flex items-center gap-1 text-[11px] font-semibold ${isCongeFull || isReadOnlyForUser ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Trajet aller (matin) — ${fmtMoney(montantTrajet)}`}>
                                                                <input
                                                                    type="checkbox"
                                                                    checked={matVal}
                                                                    disabled={isReadOnlyForUser || isCongeFull}
                                                                    onChange={(e) => {
                                                                        onUpdateIndemnite(row.idEmploye, 'deplacementMatin', e.target.checked);
                                                                        onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked, 'MATIN');
                                                                    }}
                                                                    className="w-3.5 h-3.5 cursor-pointer accent-brand-600"
                                                                />
                                                                <span className={matVal ? 'text-[var(--color-fg)] font-bold' : 'text-[var(--color-fg-muted)]'}>Aller</span>
                                                            </label>

                                                            <label className={`inline-flex items-center gap-1 text-[11px] font-semibold ${isCongeFull || isReadOnlyForUser ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Trajet retour (soir) — ${fmtMoney(montantTrajet)}`}>
                                                                <input
                                                                    type="checkbox"
                                                                    checked={amVal}
                                                                    disabled={isReadOnlyForUser || isCongeFull}
                                                                    onChange={(e) => {
                                                                        onUpdateIndemnite(row.idEmploye, 'deplacementAm', e.target.checked);
                                                                        onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked, 'APRES_MIDI');
                                                                    }}
                                                                    className="w-3.5 h-3.5 cursor-pointer accent-brand-600"
                                                                />
                                                                <span className={amVal ? 'text-[var(--color-fg)] font-bold' : 'text-[var(--color-fg-muted)]'}>Retour</span>
                                                            </label>
                                                        </div>
                                                    )}
                                                </Td>
                                            );
                                        }

                                        // Indemnité journalière standard (Repas, etc.)
                                        const isRepas = ind.code === 'REPAS';
                                        const useGlobalIfZero = !!ind.useGlobalIfZero;
                                        const checked = isRepas
                                            ? (row.indemniteRepasRecue !== undefined ? !!row.indemniteRepasRecue : (useGlobalIfZero ? false : isAccordee))
                                            : (useGlobalIfZero ? (dynObj.accordee === true) : isAccordee);
                                        const montantEffectif = useGlobalIfZero && checked
                                            ? (ind.montantGlobal || ind.montantJour || 0)
                                            : (ind.montantJour || ind.montantReference || 0);

                                        return (
                                            <Td key={ind.idIndemnite} className="px-3 py-1.5 text-center border-l border-[var(--color-border-subtle)]">
                                                {isRepas && row.estDerogeRepas ? (
                                                    <div className="flex items-center justify-center">
                                                        <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-1.5 py-0.5" title={row.motifDerogationRepas || 'Exclu indemnite repas (ex: cantine prise en charge)'}>
                                                            {row.labelDerogationRepas || 'Exclu repas'}
                                                        </span>
                                                    </div>
                                                ) : (
                                                    <div className="flex flex-col items-center justify-center gap-1">
                                                        <label
                                                            className={`inline-flex items-center gap-1.5 text-[11.5px] font-medium ${isCongeFull || isReadOnlyForUser ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`}
                                                            title={useGlobalIfZero
                                                                ? `Non inclus dans les indemnites du site — Si coche, applique le parametre global : ${fmtMoney(ind.montantGlobal || 0)}/j`
                                                                : `Accordee — ${fmtMoney(montantEffectif)}`
                                                            }
                                                        >
                                                            <input
                                                                type="checkbox"
                                                                checked={checked}
                                                                disabled={isReadOnlyForUser || isCongeFull}
                                                                onChange={(e) => {
                                                                    if (isRepas) {
                                                                        onUpdateIndemnite(row.idEmploye, 'indemniteRepasRecue', e.target.checked);
                                                                    }
                                                                    onUpdateDynamicIndemnite?.(row.idEmploye, ind.idIndemnite, e.target.checked);
                                                                }}
                                                                className="w-3.5 h-3.5 cursor-pointer accent-brand-600"
                                                            />
                                                            <span className={checked
                                                                ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-bold'
                                                                : useGlobalIfZero ? 'text-amber-600 dark:text-amber-400' : 'text-[var(--color-fg-muted)]'
                                                            }>
                                                                {checked && montantEffectif > 0
                                                                    ? fmtMoney(montantEffectif)
                                                                    : useGlobalIfZero ? <span className="text-[10px] opacity-70">Manuel</span> : '0 Ar'
                                                                }
                                                            </span>
                                                        </label>
                                                        {useGlobalIfZero && !checked && (
                                                            <span className="text-[9px] text-amber-600 dark:text-amber-400 bg-amber-500/10 px-1 leading-tight">
                                                                opt.
                                                            </span>
                                                        )}
                                                    </div>
                                                )}
                                            </Td>
                                        );
                                    })}
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            </div>

            {/* Pagination */}
            <Pagination
                currentPage={currentPage}
                totalItems={grilleData.length}
                pageSize={pageSize}
                onPageChange={onPageChange}
                onPageSizeChange={onPageSizeChange}
            />

            {/* Barre flottante d'actions groupées (Bulk actions) */}
            {selectedEmpIds.size > 0 && (
                <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-30 bg-[var(--color-surface)] border border-[var(--color-border)] shadow-2xl px-5 py-2.5 flex items-center gap-4 animate-in slide-in-from-bottom-3 duration-200">
                    {/* 1. Compteur & Désélection */}
                    <div className="flex items-center gap-2">
                        <span className="inline-flex items-center px-2 py-0.5 text-xs font-semibold bg-brand-500/10 text-brand-600 dark:text-brand-400">
                            {selectedEmpIds.size} sélectionné{selectedEmpIds.size > 1 ? 's' : ''}
                        </span>
                        <button
                            type="button"
                            onClick={handleClearSelection}
                            className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] underline underline-offset-2 ml-1 cursor-pointer"
                        >
                            Annuler
                        </button>
                    </div>

                    {/* Séparateur vertical */}
                    <div className="h-5 w-px bg-[var(--color-border-subtle)]" />

                    {/* 2. Actions structurées avec espacement aéré */}
                    <div className="flex items-center gap-2.5">
                        <Button
                            size="xs"
                            variant="outline"
                            onClick={handleApplyBulkStandard}
                            icon={<Clock size={13} className="text-emerald-500" />}
                            title="Remplir 8h standard pour les lignes sélectionnées"
                        >
                            Heures standard (8h)
                        </Button>

                        <Button
                            size="xs"
                            variant="outline"
                            onClick={handleApplyBulkTransport}
                            icon={<Car size={13} className="text-blue-500" />}
                            title="Cocher transport Matin + Soir pour la sélection"
                        >
                            Transport A/R
                        </Button>

                        <Button
                            size="xs"
                            variant="outline"
                            onClick={handleOpenCollectiveAbsence}
                            icon={<UserX size={13} className="text-amber-500" />}
                            title="Déclarer une absence collective pour toute la sélection"
                        >
                            Absence collective…
                        </Button>
                    </div>
                </div>
            )}

            {/* Modale de déclaration d'absence collective */}
            <Modal
                isOpen={showCollectiveAbsenceModal}
                onClose={() => setShowCollectiveAbsenceModal(false)}
                title="Absence collective"
                size="md"
                icon={<UserX size={18} className="text-amber-500" />}
                footer={
                    <div className="flex items-center justify-end gap-3 w-full">
                        <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => setShowCollectiveAbsenceModal(false)}
                        >
                            Annuler
                        </Button>
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={handleConfirmCollectiveAbsence}
                            className="bg-amber-600 hover:bg-amber-700 text-white border-none"
                        >
                            Appliquer l'absence
                        </Button>
                    </div>
                }
            >
                <div className="space-y-4 py-2">
                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                        Appliquer le statut <strong>Absence justifiée</strong> à{' '}
                        <strong className="text-[var(--color-fg)]">{selectedEmpIds.size} collaborateur{selectedEmpIds.size > 1 ? 's' : ''}</strong> sélectionnés.
                    </p>

                    <div>
                        <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1.5">
                            Motif de l'absence
                        </label>
                        <input
                            type="text"
                            value={collectiveMotif}
                            onChange={(e) => setCollectiveMotif(e.target.value)}
                            placeholder="Ex: Intempéries, coupure générale, formation..."
                            className="w-full px-3 py-2 text-xs border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:ring-1 focus:ring-brand-500"
                        />
                    </div>

                    <div>
                        <span className="block text-[11px] text-[var(--color-fg-muted)] mb-1.5 font-medium">Suggestions rapides :</span>
                        <div className="flex flex-wrap gap-1.5">
                            {['Intempéries / Cyclone', 'Coupure réseau / électricité', 'Formation interne', "Événement d'entreprise", 'Mission terrain'].map((suggestion) => (
                                <button
                                    key={suggestion}
                                    type="button"
                                    onClick={() => setCollectiveMotif(suggestion)}
                                    className="px-2 py-1 text-[11px] bg-[var(--color-bg-subtle)] hover:bg-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] border border-[var(--color-border-subtle)] transition-colors cursor-pointer"
                                >
                                    {suggestion}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>
            </Modal>
        </div>
    );
}
