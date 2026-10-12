import { useRef, useCallback } from 'react';
import {
    Calendar,
    ClockCheck,
    FastForward,
    Palmtree,
    RotateCcw
} from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import TimeInput from '@/components/ui/forms/TimeInput';
import StatusPickerPopover from '@/features/attendance/components/controls/StatusPickerPopover';
import { formatHeuresDecimal, formatMonnaie } from '@/features/attendance/utils/attendanceDisplayUtils';

export default function PeriodeEmployeeTable({
    groupedByWeeks = [],
    canEditPeriod = false,
    isAutonome = false,
    displayedIndemnites = [],
    initialRows = [],
    isDayRowModified,
    toggleDayContinu,
    handleApplyDayStandard,
    handleApplyWeekStandard,
    updateDayHours,
    updateDayStatut,
    updateRowDynamicIndemnite,
    handleResetDay,
    openRegularisation
}) {
    // ── Navigation clavier rapide et saut automatique ────────────────────────
    const inputRefs = useRef({});

    const registerInputRef = (datePointage, fieldIndex, el) => {
        if (!inputRefs.current[datePointage]) {
            inputRefs.current[datePointage] = [];
        }
        inputRefs.current[datePointage][fieldIndex] = el;
    };

    // Obtenir la liste à plat de toutes les dates visibles dans le tableau
    const allFilteredDates = useRef([]);
    allFilteredDates.current = groupedByWeeks.flatMap(w => w.rows.map(r => r.datePointage));

    const focusNext = useCallback((datePointage, fieldIndex) => {
        // 1. Chercher le prochain champ éditable du même jour
        for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
            const nextEl = inputRefs.current[datePointage]?.[nextIndex];
            if (nextEl && !nextEl.disabled && document.body.contains(nextEl)) {
                nextEl.focus();
                nextEl.select?.();
                return;
            }
        }
        // 2. Sauter au premier champ éditable du jour suivant
        const allDates = allFilteredDates.current;
        const currIdx = allDates.indexOf(datePointage);
        if (currIdx !== -1 && currIdx < allDates.length - 1) {
            for (let d = currIdx + 1; d < allDates.length; d++) {
                const nextDate = allDates[d];
                const dayRefs = inputRefs.current[nextDate];
                if (!dayRefs) continue;
                for (let f = 0; f <= 3; f++) {
                    const targetEl = dayRefs[f];
                    if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
        }
    }, []);

    const handleKeyDown = useCallback((e, datePointage, fieldIndex) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            focusNext(datePointage, fieldIndex);
            return;
        }

        if (e.key === 'ArrowRight') {
            for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
                const nextEl = inputRefs.current[datePointage]?.[nextIndex];
                if (nextEl && !nextEl.disabled && document.body.contains(nextEl)) {
                    e.preventDefault();
                    nextEl.focus();
                    nextEl.select?.();
                    return;
                }
            }
            const allDates = allFilteredDates.current;
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx !== -1 && currIdx < allDates.length - 1) {
                for (let d = currIdx + 1; d < allDates.length; d++) {
                    const nextDate = allDates[d];
                    const dayRefs = inputRefs.current[nextDate];
                    if (!dayRefs) continue;
                    for (let f = 0; f <= 3; f++) {
                        const targetEl = dayRefs[f];
                        if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                            e.preventDefault();
                            targetEl.focus();
                            targetEl.select?.();
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowLeft') {
            for (let prevIndex = fieldIndex - 1; prevIndex >= 0; prevIndex--) {
                const prevEl = inputRefs.current[datePointage]?.[prevIndex];
                if (prevEl && !prevEl.disabled && document.body.contains(prevEl)) {
                    e.preventDefault();
                    prevEl.focus();
                    prevEl.select?.();
                    return;
                }
            }
            const allDates = allFilteredDates.current;
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx > 0) {
                for (let d = currIdx - 1; d >= 0; d--) {
                    const prevDate = allDates[d];
                    const dayRefs = inputRefs.current[prevDate];
                    if (!dayRefs) continue;
                    for (let f = 3; f >= 0; f--) {
                        const targetEl = dayRefs[f];
                        if (targetEl && !targetEl.disabled && document.body.contains(targetEl)) {
                            e.preventDefault();
                            targetEl.focus();
                            targetEl.select?.();
                            return;
                        }
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            const allDates = allFilteredDates.current;
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx !== -1 && currIdx < allDates.length - 1) {
                for (let d = currIdx + 1; d < allDates.length; d++) {
                    const nextDate = allDates[d];
                    const dayRefs = inputRefs.current[nextDate];
                    if (!dayRefs) continue;
                    let targetEl = dayRefs[fieldIndex];
                    if (!targetEl || targetEl.disabled || !document.body.contains(targetEl)) {
                        targetEl = dayRefs.find(el => el && !el.disabled && document.body.contains(el));
                    }
                    if (targetEl) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
            return;
        }

        if (e.key === 'ArrowUp') {
            e.preventDefault();
            const allDates = allFilteredDates.current;
            const currIdx = allDates.indexOf(datePointage);
            if (currIdx > 0) {
                for (let d = currIdx - 1; d >= 0; d--) {
                    const prevDate = allDates[d];
                    const dayRefs = inputRefs.current[prevDate];
                    if (!dayRefs) continue;
                    let targetEl = dayRefs[fieldIndex];
                    if (!targetEl || targetEl.disabled || !document.body.contains(targetEl)) {
                        targetEl = dayRefs.find(el => el && !el.disabled && document.body.contains(el));
                    }
                    if (targetEl) {
                        targetEl.focus();
                        targetEl.select?.();
                        return;
                    }
                }
            }
        }
    }, [focusNext]);

    return (
        <div className="space-y-4">
            {/* Bandeau d'assistance & Raccourcis Clavier */}
            <div className="flex items-center justify-between text-[11px] text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-3 py-1.5 border border-[var(--color-border-subtle)]">
                <div className="flex items-center gap-2 flex-wrap">
                    <span className="font-semibold text-[var(--color-fg)]">Aide à la saisie :</span>
                    <span>Tapez 4 chiffres (ex: <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">0800</kbd> → 08:00) avec saut automatique</span>
                    <span>·</span>
                    <span>Flèches <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">↑</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">↓</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">←</kbd> <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">→</kbd> et <kbd className="px-1 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">Entrée</kbd> pour naviguer</span>
                </div>
                <div className="hidden lg:flex items-center gap-2 shrink-0">
                    <kbd className="px-1.5 py-0.2 font-mono text-[10px] bg-[var(--color-surface)] border border-[var(--color-border-subtle)] font-bold text-[var(--color-brand-600)]">Ctrl+S</kbd>
                    <span>enregistrer</span>
                </div>
            </div>

            {/* Liste des Semaines */}
            {groupedByWeeks.map((week, wIdx) => {
                const weekTotalHeures = week.rows.reduce((acc, r) => acc + (Number(r.totalHeuresDecimal) || 0), 0);
                const weekTotalRetard = week.rows.reduce((acc, r) => acc + (r.minutesRetard || 0), 0);

                return (
                    <div key={week.weekKey || wIdx} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                        {/* En-tête de la Semaine */}
                        <div className="px-3.5 py-2 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex items-center justify-between text-xs font-bold text-[var(--color-fg)]">
                            <div className="flex items-center gap-2">
                                <Calendar size={13} className="text-[var(--color-brand-600)]" />
                                <span>Semaine {wIdx + 1}</span>
                                <span className="font-normal text-[var(--color-fg-muted)]">
                                    ({week.rows[0]?.date} au {week.rows[week.rows.length - 1]?.date})
                                </span>
                            </div>
                            <div className="flex items-center gap-3 text-[11px] font-mono">
                                <span>
                                    Total réalisé : <strong className="text-[var(--color-fg)]">{formatHeuresDecimal(weekTotalHeures)}</strong>
                                    <span className="text-[var(--color-fg-muted)] font-normal ml-1">
                                        / {formatHeuresDecimal(week.objectifHeuresSemaine)} prévu
                                    </span>
                                    {week.totalFeriesSemaine > 0 && (
                                        <span className="text-[var(--color-purple-600)] font-sans font-medium text-[10px] ml-1.5">
                                            ({week.totalFeriesSemaine} férié chômé payé)
                                        </span>
                                    )}
                                </span>
                                {weekTotalRetard > 0 && !isAutonome && (
                                    <span className="text-[var(--color-warning-600)] font-semibold">
                                        Retard : {weekTotalRetard} min
                                    </span>
                                )}
                                {canEditPeriod && (
                                    <Button
                                        variant="outline"
                                        size="xs"
                                        iconLeft={<ClockCheck size={11} />}
                                        onClick={() => handleApplyWeekStandard(week.rows)}
                                        title="Appliquer l'horaire standard aux jours ouvrés de cette semaine"
                                        className="font-sans font-semibold text-[10px] border-[var(--color-brand-500)]/30 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] hover:bg-[var(--color-brand-500)]/10"
                                    >
                                        Remplir semaine
                                    </Button>
                                )}
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <Table bordered={false} density="compact" className="w-full text-left text-xs">
                                <Thead>
                                    <Tr className="border-b border-[var(--color-border-subtle)] text-[10px] uppercase font-bold text-[var(--color-fg-muted)] bg-[var(--color-surface)]">
                                        <Th className="w-20 px-3 py-2">Date</Th>
                                        <Th className="w-24 px-2 py-2">Jour</Th>
                                        <Th className="w-24 px-2 py-2 text-center">Entrée Matin</Th>
                                        <Th className="w-24 px-2 py-2 text-center">Pause / Sortie</Th>
                                        <Th className="w-24 px-2 py-2 text-center">Retour AM</Th>
                                        <Th className="w-24 px-2 py-2 text-center">Sortie Fin</Th>
                                        <Th className="w-16 px-2 py-2 text-center">Retard</Th>
                                        <Th className="w-24 px-2 py-2 text-center">Heures</Th>
                                        <Th className="w-32 px-2 py-2 text-center">Statut</Th>
                                        {displayedIndemnites.length > 0 && (
                                            <Th className="w-44 px-2 py-2 text-center border-l border-[var(--color-border-subtle)]" title="Indemnités applicables à la journée">
                                                Indemnités
                                            </Th>
                                        )}
                                        <Th className="w-24 px-1.5 py-2 text-center">Actions</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {week.rows.map((row) => {
                                        const isWeekend = row.weekend;
                                        const isFerie = row.ferie;
                                        const isConge = row.conge;
                                        const hasPunches = Boolean(row.heureEntreeMatin || row.heureSortieAm);

                                        const initialRow = initialRows.find(i => i.datePointage === row.datePointage);
                                        const isModified = isDayRowModified ? isDayRowModified(row, initialRow) : false;
                                        const isNonSaisi = (row.codeStatut === 'NON_SAISI' || !row.codeStatut) && !isWeekend && !isFerie && !isConge;

                                        let rowBorderCue = 'border-l-2 border-l-transparent';
                                        if (isModified) {
                                            rowBorderCue = 'border-l-2 border-l-[var(--color-brand-500)] bg-[var(--color-brand-500)]/[0.03]';
                                        } else if (isNonSaisi) {
                                            rowBorderCue = 'border-l-2 border-l-[var(--color-warning-500)]';
                                        } else if (row.totalHeuresDecimal > 0) {
                                            rowBorderCue = 'border-l-2 border-l-[var(--color-success-500)]';
                                        }

                                        const canEditRow = canEditPeriod && row.canCorrect !== false;
                                        const chomeColSpan = 7;

                                        return (
                                            <Tr
                                                key={row.datePointage}
                                                id={`pointage-day-${row.datePointage}`}
                                                className={`border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors ${rowBorderCue} ${
                                                    isWeekend
                                                        ? 'bg-[var(--color-bg-subtle)]/40 text-[var(--color-fg-muted)]'
                                                        : isFerie
                                                            ? 'bg-[var(--color-purple-500)]/[0.04]'
                                                            : isConge
                                                                ? 'bg-[var(--color-sky-500)]/[0.04]'
                                                                : ''
                                                }`}
                                            >
                                                {/* 1. Date */}
                                                <Td className="font-mono font-bold text-xs px-3 py-2 whitespace-nowrap">
                                                    {row.date}
                                                </Td>

                                                {/* 2. Jour avec bouton continu / pause inline */}
                                                <Td className="text-xs text-[var(--color-fg-muted)] px-2 py-2 whitespace-nowrap">
                                                    <div className="flex items-center gap-1.5">
                                                        <span>{row.jour}</span>
                                                        {canEditRow && !isConge && (
                                                            <button
                                                                type="button"
                                                                onClick={() => toggleDayContinu(row.datePointage)}
                                                                className={`text-[9px] px-1 py-0.2 font-sans font-semibold rounded border transition-colors cursor-pointer shrink-0 ${
                                                                    row.horaireContinu
                                                                        ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/40 hover:bg-[var(--color-brand-500)]/20'
                                                                        : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                                                }`}
                                                                title={row.horaireContinu ? "Journée continue active (sans pause déjeuner). Cliquer pour rétablir la pause." : "Activer la journée continue (sans pause déjeuner 1h)"}
                                                            >
                                                                {row.horaireContinu ? (
                                                                    <span className="inline-flex items-center gap-0.5">
                                                                        <FastForward size={8} className="shrink-0" /> Continu
                                                                    </span>
                                                                ) : 'Pause'}
                                                            </button>
                                                        )}
                                                    </div>
                                                </Td>

                                                {/* Ligne fusionnée pour Repos / Férié chômé / Congé sans badge de travail */}
                                                {isWeekend && !hasPunches ? (
                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-fg-muted)]">
                                                        <div className="flex items-center justify-center gap-2">
                                                            <span>Repos hebdomadaire</span>
                                                            {canEditRow && (
                                                                <Button
                                                                    variant="ghost"
                                                                    size="xs"
                                                                    onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                    className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)]"
                                                                >
                                                                    + Déclarer travaillé
                                                                </Button>
                                                            )}
                                                        </div>
                                                    </Td>
                                                ) : isFerie && !hasPunches ? (
                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)] font-medium">
                                                        <div className="flex items-center justify-center gap-2">
                                                            <Badge variant="purple" size="sm">Férié chômé payé</Badge>
                                                            <span className="text-[10px] text-[var(--color-purple-600)] dark:text-[var(--color-purple-400)]">
                                                                Jour chômé légal (-8h d'obligation de présence)
                                                            </span>
                                                            {canEditRow && (
                                                                <Button
                                                                    variant="ghost"
                                                                    size="xs"
                                                                    onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                    className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)]"
                                                                >
                                                                    + Travaillé (+100%)
                                                                </Button>
                                                            )}
                                                        </div>
                                                    </Td>
                                                ) : isConge && !hasPunches ? (
                                                    <Td colSpan={chomeColSpan} className="px-2 py-1.5 text-center text-[11px] text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)] font-medium">
                                                        <div className="flex items-center justify-center gap-2">
                                                            <Badge variant="info" size="sm">Congé payé validé</Badge>
                                                            <span className="text-[10px] text-[var(--color-sky-600)] dark:text-[var(--color-sky-400)]">
                                                                Piloté par le module Congés
                                                            </span>
                                                        </div>
                                                    </Td>
                                                ) : (
                                                    <>
                                                        {/* 3. Entrée Matin */}
                                                        <Td className="px-1.5 py-2 text-center">
                                                            <TimeInput
                                                                inputRef={(el) => registerInputRef(row.datePointage, 0, el)}
                                                                value={row.heureEntreeMatin}
                                                                onChange={(val) => updateDayHours(row.datePointage, 'heureEntreeMatin', val)}
                                                                onKeyDown={(e) => handleKeyDown(e, row.datePointage, 0)}
                                                                onComplete={() => focusNext(row.datePointage, 0)}
                                                                disabled={!canEditRow}
                                                                placeholder="--:--"
                                                                className="h-7 text-xs text-center"
                                                            />
                                                        </Td>

                                                        {/* 4 & 5. Pause ou Continu */}
                                                        {row.horaireContinu ? (
                                                            <Td colSpan={2} className="px-1.5 py-1 text-center bg-[var(--color-brand-500)]/5 dark:bg-[var(--color-brand-500)]/10">
                                                                <div className="flex items-center justify-center gap-1.5">
                                                                    <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                                                                        <FastForward size={10} className="shrink-0 text-[var(--color-brand-600)]" />
                                                                        Journée continue (sans pause)
                                                                    </span>
                                                                    <button
                                                                        type="button"
                                                                        onClick={() => toggleDayContinu(row.datePointage)}
                                                                        className="text-[10px] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-600)] underline cursor-pointer ml-1"
                                                                    >
                                                                        Modifier
                                                                    </button>
                                                                </div>
                                                            </Td>
                                                        ) : (
                                                            <>
                                                                <Td className="px-1.5 py-2 text-center">
                                                                    <TimeInput
                                                                        inputRef={(el) => registerInputRef(row.datePointage, 1, el)}
                                                                        value={row.heureSortieMatin}
                                                                        onChange={(val) => updateDayHours(row.datePointage, 'heureSortieMatin', val)}
                                                                        onKeyDown={(e) => handleKeyDown(e, row.datePointage, 1)}
                                                                        onComplete={() => focusNext(row.datePointage, 1)}
                                                                        disabled={!canEditRow}
                                                                        placeholder="--:--"
                                                                        className="h-7 text-xs text-center"
                                                                    />
                                                                </Td>
                                                                <Td className="px-1.5 py-2 text-center">
                                                                    <TimeInput
                                                                        inputRef={(el) => registerInputRef(row.datePointage, 2, el)}
                                                                        value={row.heureEntreeAm}
                                                                        onChange={(val) => updateDayHours(row.datePointage, 'heureEntreeAm', val)}
                                                                        onKeyDown={(e) => handleKeyDown(e, row.datePointage, 2)}
                                                                        onComplete={() => focusNext(row.datePointage, 2)}
                                                                        disabled={!canEditRow}
                                                                        placeholder="--:--"
                                                                        className="h-7 text-xs text-center"
                                                                    />
                                                                </Td>
                                                            </>
                                                        )}

                                                        {/* 6. Sortie Fin */}
                                                        <Td className="px-1.5 py-2 text-center">
                                                            <TimeInput
                                                                inputRef={(el) => registerInputRef(row.datePointage, 3, el)}
                                                                value={row.heureSortieAm}
                                                                onChange={(val) => updateDayHours(row.datePointage, 'heureSortieAm', val)}
                                                                onKeyDown={(e) => handleKeyDown(e, row.datePointage, 3)}
                                                                onComplete={() => focusNext(row.datePointage, 3)}
                                                                disabled={!canEditRow}
                                                                placeholder="--:--"
                                                                className="h-7 text-xs text-center"
                                                            />
                                                        </Td>

                                                        {/* 7. Retard */}
                                                        <Td className="px-1 py-2 text-center font-mono text-xs">
                                                            {isAutonome ? (
                                                                <span className="text-[var(--color-fg-muted)] opacity-50">—</span>
                                                            ) : row.minutesRetard > 0 ? (
                                                                <span className="font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-50)] dark:bg-[var(--color-warning-950)] px-1.5 py-0.5 rounded-none border border-[var(--color-warning-200)]">
                                                                    +{row.minutesRetard}m
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-muted)] opacity-40">—</span>
                                                            )}
                                                        </Td>

                                                        {/* 8. Heures Faites */}
                                                        <Td className="px-2 py-2 text-center font-mono font-bold text-xs text-[var(--color-fg)]">
                                                            <div>{row.heuresFaites}</div>
                                                            {row.totalHeuresDecimal > 0 && (
                                                                <div className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                                                                    ({formatHeuresDecimal(row.totalHeuresDecimal)})
                                                                </div>
                                                            )}
                                                        </Td>

                                                        {/* 9. Statut */}
                                                        <Td className="px-2 py-2 text-center">
                                                            <StatusPickerPopover
                                                                row={row}
                                                                currentStatus={row.codeStatut}
                                                                onStatusChange={(newSt, motif) => updateDayStatut(row.datePointage, newSt, motif)}
                                                                onOpenRegularisation={(r) => openRegularisation(r)}
                                                                disabled={!canEditRow || Boolean(row.congeApprouve)}
                                                            />
                                                        </Td>
                                                    </>
                                                )}

                                                {/* 10. Indemnités regroupées */}
                                                {displayedIndemnites.length > 0 && (
                                                    <Td className="px-1.5 py-2 border-l border-[var(--color-border-subtle)]">
                                                        {isWeekend && !hasPunches ? (
                                                            <div className="text-center text-[var(--color-fg-muted)] opacity-40">—</div>
                                                        ) : (
                                                            <div className="flex flex-wrap items-center justify-center gap-1">
                                                                {displayedIndemnites.map((ind) => {
                                                                    if (row._dateAllowances && !row._dateAllowances.some(item => item.idIndemnite === ind.idIndemnite)) return null;
                                                                    const dayInd = row._dateAllowances?.find(item => item.idIndemnite === ind.idIndemnite) || ind;
                                                                    const dynObj = row.dynamicIndemnites?.[String(ind.idIndemnite)] || {};
                                                                    const isFractionnable = dayInd.estFractionnable !== undefined ? dayInd.estFractionnable : dayInd.fractionnable;
                                                                    const defaultGranted = dayInd.cocheParDefaut !== false && !isConge;
                                                                    const name = dayInd.code === 'DEPLACEMENT' || dayInd.code === 'DPLCT' || dayInd.code === 'TRANSPORT'
                                                                        ? 'Dépl.'
                                                                        : dayInd.code === 'REPAS' ? 'Repas' : (dayInd.libelle || dayInd.code);
                                                                    const details = [
                                                                        dayInd.libelle || dayInd.code,
                                                                        isFractionnable
                                                                            ? `Matin ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)} · après-midi ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)}`
                                                                            : `${formatMonnaie(dayInd.montantJour)}/j`,
                                                                        dynObj.motifSuggestion,
                                                                    ].filter(Boolean).join(' — ');

                                                                    return (
                                                                        <div key={ind.idIndemnite} title={details} className="inline-flex items-center gap-1 rounded border border-[var(--color-border-subtle)] bg-[var(--color-bg)] px-1.5 py-0.5">
                                                                            <span className="max-w-16 truncate text-[9px] font-medium" title={details}>{name}</span>
                                                                            {isFractionnable ? (
                                                                                <>
                                                                                    <label className={`inline-flex items-center gap-0.5 text-[9px] font-semibold ${!canEditRow ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Matin — ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)}`}>
                                                                                        <input type="checkbox" checked={dynObj.matin === undefined ? defaultGranted : Boolean(dynObj.matin)} disabled={!canEditRow} onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'MATIN')} className="h-3 w-3 cursor-pointer accent-[var(--color-brand-600)]" />
                                                                                        M
                                                                                    </label>
                                                                                    <label className={`inline-flex items-center gap-0.5 text-[9px] font-semibold ${!canEditRow ? 'cursor-not-allowed opacity-40' : 'cursor-pointer'} select-none`} title={`Après-midi — ${formatMonnaie(dayInd.montantTrajet || dayInd.montantJour / 2)}`}>
                                                                                        <input type="checkbox" checked={dynObj.am === undefined ? defaultGranted : Boolean(dynObj.am)} disabled={!canEditRow} onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'APRES_MIDI')} className="h-3 w-3 cursor-pointer accent-[var(--color-brand-600)]" />
                                                                                        A
                                                                                    </label>
                                                                                </>
                                                                            ) : (
                                                                            <input type="checkbox" checked={dynObj.accordee === undefined ? defaultGranted : Boolean(dynObj.accordee)} disabled={!canEditRow} onChange={(e) => updateRowDynamicIndemnite(row.datePointage, ind.idIndemnite, e.target.checked, 'ALL')} className="h-3 w-3 cursor-pointer accent-[var(--color-brand-600)]" aria-label={`Accorder ${dayInd.libelle || dayInd.code}`} />
                                                                            )}
                                                                        </div>
                                                                    );
                                                                })}
                                                            </div>
                                                        )}
                                                    </Td>
                                                )}

                                                {/* 11. Actions rapides */}
                                                        <Td className="px-1.5 py-2 text-center whitespace-nowrap">
                                                        <div className="flex items-center justify-center gap-1">
                                                        {/* Action Régularisation Congé / Absence */}
                                                        <Button
                                                            variant="outline"
                                                            size="xs"
                                                            iconLeft={<Palmtree size={13} />}
                                                            onClick={() => openRegularisation(row)}
                                                            disabled={!canEditRow}
                                                            title="Signaler un congé annuel, une permission exceptionnelle ou une absence déduite"
                                                            className="!px-1 !py-0.5 !text-[9px] [&_svg]:!w-2 [&_svg]:!h-2 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/40 hover:bg-[var(--color-brand-500)]/10"
                                                        >
                                                            Absence / congé
                                                        </Button>

                                                        {/* Action Heures : Vider ou Standard */}
                                                        {(isWeekend && !hasPunches) || (isFerie && !hasPunches) || (isConge && !hasPunches) ? (
                                                            <span className="opacity-30 text-[10px]">—</span>
                                                        ) : hasPunches ? (
                                                            <Button
                                                                variant="ghost"
                                                                size="xs"
                                                                iconOnly
                                                                icon={<RotateCcw size={12} />}
                                                                onClick={() => handleResetDay(row.datePointage)}
                                                                disabled={!canEditRow}
                                                                title="Vider les heures de cette journée"
                                                                aria-label="Vider les heures de cette journée"
                                                                className="hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 text-[var(--color-fg-muted)]"
                                                            />
                                                        ) : (
                                                            <Button
                                                                variant="outline"
                                                                size="xs"
                                                                onClick={() => handleApplyDayStandard(row.datePointage)}
                                                                disabled={!canEditRow}
                                                                title="Appliquer l'horaire standard (08h-17h)"
                                                                className="!px-1.5 !py-0.5 !text-[9px] text-[var(--color-brand-600)] border-[var(--color-brand-500)]/30 hover:bg-[var(--color-brand-500)]/10"
                                                            >
                                                                Standard
                                                            </Button>
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
                );
            })}
        </div>
    );
}
