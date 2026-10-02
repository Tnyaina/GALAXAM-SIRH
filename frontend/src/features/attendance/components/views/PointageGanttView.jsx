import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, User, ChevronLeft, ChevronRight, AlertTriangle, CheckCircle2, Calendar } from 'lucide-react';
import { isDateInFuture, isDateToday } from '@/utils/dateUtils';
import { getAttendanceBadgeClass } from '@/features/attendance/utils/attendanceDisplayUtils';

/**
 * PointageGanttView — Vue Gantt hebdomadaire du pointage.
 *
 * Props :
 *   semaines[]      : liste des semaines de la periode (id, title, dates, dateDebut, dateFin, estValidee, nbAnomalies)
 *   selectedSemaine : objet semaine selectionnee { dateDebut, dateFin, title }
 *   onSelectSemaine : (semaine) => void
 *   ganttData       : Map<string(YYYY-MM-DD), SaisiePointageJourDTO[]> — donnees saisie par jour
 *   loadingGantt    : boolean
 *   isReadOnly      : boolean
 */
export default function PointageGanttView({
    semaines = [],
    selectedSemaine = null,
    onSelectSemaine,
    ganttData = {},
    loadingGantt = false,
    _isReadOnly = false,
    onCellClick = null
}) {
    // Contexte et navigation
    const navigate = useNavigate();

    // État local et référence DOM du détail au survol.
    const [tooltip, setTooltip] = useState(null);
    const tooltipRef = useRef(null);

    // Effet : ferme le détail au clic extérieur.
    useEffect(() => {
        const close = () => setTooltip(null);
        window.addEventListener('click', close);
        return () => window.removeEventListener('click', close);
    }, []);

    // Données dérivées : jours, collaborateurs et possibilité de navigation.
    const weekDays = selectedSemaine ? buildWeekDays(selectedSemaine.dateDebut, selectedSemaine.dateFin) : [];
    const employees = buildEmployeeList(ganttData, weekDays);

    const currentIdx = semaines.findIndex(s => s.dateDebut === selectedSemaine?.dateDebut);
    const hasPrev = currentIdx > 0;
    const hasNext = currentIdx < semaines.length - 1;

    // Actions utilisateur
    const handlePrev = () => { if (hasPrev) onSelectSemaine(semaines[currentIdx - 1]); };
    const handleNext = () => { if (hasNext) onSelectSemaine(semaines[currentIdx + 1]); };

    const handleCellHover = (e, emp, day, cellData) => {
        const effectiveData = cellData || {
            idEmploye: emp.idEmploye,
            datePointage: day.date,
            codeStatut: 'NON_SAISI',
            totalHeures: 0
        };
        const rect = e.currentTarget.getBoundingClientRect();
        setTooltip({ emp, day, cellData: effectiveData, x: rect.left, y: rect.bottom + 6 });
    };

    // Rendu
    return (
        <div className="space-y-2.5">
            {/* Navigation semaine */}
            <div className="flex items-center justify-between bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-3 py-1.5">
                <div className="flex items-center gap-1">
                    <button
                        type="button"
                        onClick={handlePrev}
                        disabled={!hasPrev || loadingGantt}
                        className="p-1.5 border border-transparent hover:border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-30 disabled:cursor-not-allowed transition-all cursor-pointer"
                        title="Semaine precedente"
                    >
                        <ChevronLeft size={14} />
                    </button>
                    <div className="flex items-center gap-1 overflow-x-auto px-1">
                        {semaines.length === 0 ? (
                            <span className="text-[11px] text-[var(--color-fg-muted)]  px-2 py-0.5 select-none">
                                {loadingGantt ? 'Chargement des semaines…' : 'Aucune semaine disponible pour cette période'}
                            </span>
                        ) : (
                            semaines.map((sem) => {
                                const isSelected = sem.dateDebut === selectedSemaine?.dateDebut;
                                return (
                                    <button
                                        key={sem.dateDebut || sem.id}
                                        type="button"
                                        onClick={() => onSelectSemaine(sem)}
                                        disabled={loadingGantt}
                                        className={`flex items-center gap-1.5 px-2.5 py-1 text-[10.5px] font-semibold whitespace-nowrap border transition-all cursor-pointer disabled:opacity-50 ${isSelected
                                            ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-[var(--color-brand-500)]/40 font-bold'
                                            : 'text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)] hover:border-[var(--color-border)]'
                                            }`}
                                    >
                                        <Calendar size={11} />
                                        <span>{sem.title}</span>
                                        {sem.estValidee && <CheckCircle2 size={10} className="text-[var(--color-success-600)]" />}
                                        {sem.nbAnomalies > 0 && !sem.estValidee && (
                                            <span className="px-1 py-0.5 text-[8px] font-bold bg-[var(--color-warning-500)]/20 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-500)]/40 leading-none">
                                                {sem.nbAnomalies}
                                            </span>
                                        )}
                                    </button>
                                );
                            })
                        )}
                    </div>
                    <button
                        type="button"
                        onClick={handleNext}
                        disabled={!hasNext || loadingGantt}
                        className="p-1.5 border border-transparent hover:border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-30 disabled:cursor-not-allowed transition-all cursor-pointer"
                        title="Semaine suivante"
                    >
                        <ChevronRight size={14} />
                    </button>
                </div>

                {selectedSemaine && (
                    <div className="flex items-center gap-2 shrink-0">
                        {selectedSemaine.estValidee ? (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-[var(--color-success-600)] border border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/8 px-2 py-0.5">
                                <CheckCircle2 size={10} /> Semaine calculee
                            </span>
                        ) : selectedSemaine.nbAnomalies > 0 ? (
                            <span className="flex items-center gap-1 text-[10px] font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10 px-2 py-0.5">
                                <AlertTriangle size={10} /> {selectedSemaine.nbAnomalies} anomalie(s)
                            </span>
                        ) : (
                            <span className="text-[10px] font-semibold text-[var(--color-fg-muted)]">En saisie</span>
                        )}
                    </div>
                )}
            </div>

            {/* Grille Gantt */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                {loadingGantt ? (
                    <div className="flex items-center justify-center gap-2 py-16 text-xs text-[var(--color-fg-muted)]">
                        <Clock size={14} className="animate-spin text-[var(--color-brand-500)]" />
                        Chargement de la semaine en cours…
                    </div>
                ) : !selectedSemaine ? (
                    <div className="flex items-center justify-center py-16 text-xs text-[var(--color-fg-muted)]">
                        Selectionnez une semaine ci-dessus pour afficher le Gantt.
                    </div>
                ) : employees.length === 0 ? (
                    <div className="flex items-center justify-center py-16 text-xs text-[var(--color-fg-muted)]">
                        Aucune donnee de pointage pour cette semaine. Lancez un calcul depuis la vue Semaines.
                    </div>
                ) : (
                    <table className="w-full border-collapse text-xs" style={{ minWidth: '720px' }}>
                        <thead>
                            <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)]">
                                <th className="sticky left-0 z-10 bg-[var(--color-bg-subtle)] text-left px-3 py-2 font-bold text-[11px] text-[var(--color-fg)] border-r border-[var(--color-border-subtle)] min-w-[180px] w-[180px]">
                                    Employe
                                </th>
                                {weekDays.map(day => {
                                    const isFuture = isDateInFuture(day.date);
                                    const isToday = isDateToday(day.date);
                                    return (
                                        <th
                                            key={day.date}
                                            className={`px-1 py-2 text-center font-semibold text-[10px] border-r border-[var(--color-border-subtle)] min-w-[90px] ${isToday
                                                    ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold border-[var(--color-brand-500)]/30'
                                                    : isFuture
                                                        ? 'bg-[var(--color-bg-subtle)]/30 text-[var(--color-fg-muted)] opacity-60'
                                                        : day.isWeekend
                                                            ? 'bg-[var(--color-warning-500)]/5 text-[var(--color-fg-muted)]'
                                                            : 'text-[var(--color-fg)]'
                                                }`}
                                        >
                                            <div className="font-bold flex items-center justify-center gap-1">
                                                {day.label}
                                                {isToday && <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-500)] inline-block" title="Aujourd'hui" />}
                                            </div>
                                            <div className="font-mono text-[9px] text-[var(--color-fg-muted)] mt-0.5">{day.dateShort}</div>
                                            {isFuture && <div className="text-[8px] font-normal uppercase tracking-wider text-[var(--color-fg-muted)]/70">Non échu</div>}
                                        </th>
                                    );
                                })}
                                <th className="px-2 py-2 text-center font-bold text-[10px] text-[var(--color-fg)] min-w-[64px]">
                                    Total
                                </th>
                            </tr>
                        </thead>
                        <tbody>
                            {employees.map((emp, empIdx) => {
                                let totalHeures = 0;
                                return (
                                    <tr
                                        key={emp.idEmploye}
                                        className={`border-b border-[var(--color-border-subtle)] ${empIdx % 2 === 0 ? 'bg-[var(--color-surface)]' : 'bg-[var(--color-bg-subtle)]/40'} hover:bg-[var(--color-brand-500)]/4 transition-colors`}
                                    >
                                        <td className="sticky left-0 z-10 px-3 py-1.5 border-r border-[var(--color-border-subtle)] bg-inherit">
                                            <button
                                                type="button"
                                                className="text-left cursor-pointer hover:text-[var(--color-brand-600)] transition-colors"
                                                onClick={() => navigate(`/employes/${emp.idEmploye}?tab=pointage`)}
                                                title="Voir la fiche employe"
                                            >
                                                <div className="font-semibold text-[11.5px] text-[var(--color-fg)] truncate max-w-[160px]">
                                                    {emp.nom} {emp.prenom}
                                                </div>
                                                <div className="text-[9.5px] font-mono text-[var(--color-fg-muted)] flex items-center gap-1 mt-0.5">
                                                    <User size={9} />
                                                    {emp.matricule}
                                                    {emp.badgeProfilLabel && (
                                                        <span className={`ml-1 px-1 py-0 text-[8px] font-bold border ${getAttendanceBadgeClass(emp.badgeProfilVariant)}`}>
                                                            {emp.badgeProfilLabel}
                                                        </span>
                                                    )}
                                                </div>
                                            </button>
                                        </td>

                                        {weekDays.map(day => {
                                            const cellData = getCellData(ganttData, day.date, emp.idEmploye);
                                            if (cellData?.totalHeures) {
                                                totalHeures += Number(cellData.totalHeures) || 0;
                                            }
                                            const isFuture = isDateInFuture(day.date);
                                            return (
                                                <td
                                                    key={day.date}
                                                    className={`px-1 py-1.5 border-r border-[var(--color-border-subtle)] text-center select-none ${isFuture
                                                            ? 'cursor-not-allowed bg-[var(--color-bg-subtle)]/20 opacity-55'
                                                            : 'cursor-pointer hover:opacity-85'
                                                        }`}
                                                    onMouseEnter={(e) => handleCellHover(e, emp, day, cellData)}
                                                    onMouseLeave={() => setTooltip(null)}
                                                    onClick={(e) => {
                                                        e.stopPropagation();
                                                        if (isFuture) return;
                                                        if (onCellClick) {
                                                            onCellClick(emp, day, cellData);
                                                        } else if (cellData) {
                                                            setTooltip({ emp, day, cellData, x: e.clientX, y: e.clientY + 10 });
                                                        }
                                                    }}
                                                >
                                                    <GanttCell cellData={cellData} day={day} />
                                                </td>
                                            );
                                        })}

                                        <td className="px-2 py-1.5 text-center">
                                            <span className={`font-mono text-[11px] font-bold ${totalHeures > 0 ? 'text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                {totalHeures > 0 ? `${totalHeures.toFixed(1)}h` : '—'}
                                            </span>
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                )}
            </div>

            {/* Legende */}
            {!loadingGantt && employees.length > 0 && (
                <div className="flex flex-wrap items-center gap-3 px-1 text-[10px] text-[var(--color-fg-muted)]">
                    <span className="font-semibold text-[var(--color-fg)]">Legende :</span>
                    {GANTT_STATUTS.map(s => (
                        <div key={s.code} className="flex items-center gap-1.5">
                            <div className={`h-3 w-5 border ${s.bg} ${s.border}`} />
                            <span>{s.label}</span>
                        </div>
                    ))}
                </div>
            )}

            {/* Tooltip flottant */}
            {tooltip && (
                <GanttTooltip tooltip={tooltip} tooltipRef={tooltipRef} />
            )}
        </div>
    );
}

function GanttCell({ cellData, day }) {
    const style = getStatutStyle(cellData, day);
    const label = getStatutLabel(cellData, day);
    const timeStr = getTimeString(cellData, day);

    return (
        <div
            className={`flex flex-col items-center justify-center gap-0.5 h-10 w-full border transition-all hover:shadow-xs ${style.bg} ${style.border} ${style.text}`}
        >
            <span className="text-[9px] font-bold leading-tight truncate px-1 max-w-full">{label}</span>
            {timeStr && (
                <span className="text-[8px] font-mono leading-tight opacity-80">{timeStr}</span>
            )}
            {cellData?.retard && cellData?.minutesRetard > 0 && (
                <span className="text-[7px] font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] leading-tight">
                    -{cellData.minutesRetard}min
                </span>
            )}
        </div>
    );
}

function GanttTooltip({ tooltip, tooltipRef }) {
    const { emp, day, cellData } = tooltip;
    const safeX = Math.min(tooltip.x, window.innerWidth - 300);
    const safeY = Math.min(tooltip.y, window.innerHeight - 340);

    const totalH = Number(cellData?.totalHeures || 0);
    const hasWorked = totalH > 0;
    const isDimancheTravaille = cellData?.codeStatut === 'DIMANCHE_TRAVAILLE';
    const label = getStatutLabel(cellData, day);

    // Heures précises matin / après-midi
    const em = cellData?.heureEntreeMatin || cellData?.heureEntree;
    const sm = cellData?.heureSortieMatin || cellData?.heureSortie;
    const matinTime = (em && sm) ? `${em} — ${sm}` : (em ? em : null);

    const ea = cellData?.heureEntreeAm;
    const sa = cellData?.heureSortieAm;
    const amTime = (ea && sa) ? `${ea} — ${sa}` : null;

    const allowances = Object.values(cellData?.indemnites || {}).filter((allowance) => allowance?.accordee);

    return (
        <div
            ref={tooltipRef}
            className="fixed z-50 bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xl p-3 min-w-[240px] max-w-[290px] text-xs"
            style={{ top: safeY, left: safeX }}
            onClick={(e) => e.stopPropagation()}
        >
            <div className="flex items-center justify-between font-bold text-[var(--color-fg)] mb-1.5 pb-1.5 border-b border-[var(--color-border-subtle)]">
                <div className="truncate">
                    <div className="truncate">{emp.nom} {emp.prenom}</div>
                    <div className="font-mono font-normal text-[9.5px] text-[var(--color-fg-muted)]">{emp.matricule}</div>
                </div>
                <span className="font-mono text-[10px] px-1.5 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] shrink-0">
                    {day.label} {day.dateShort}
                </span>
            </div>

            <div className="space-y-1 text-[11px]">
                <TooltipRow label="Statut" value={<span className="font-bold">{label}</span>} />

                {isDimancheTravaille && (
                    <div className="px-1.5 py-0.5 bg-[var(--color-info-500)]/10 border border-[var(--color-info-500)]/30 text-[var(--color-info-700)] dark:text-[var(--color-info-300)] font-bold text-[9.5px]">
                        Dimanche travaillé
                    </div>
                )}

                {(cellData?.ferieJour || cellData?.codeStatut === 'FERIE_TRAVAILLE') && hasWorked && (
                    <div className="px-1.5 py-0.5 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)] font-bold text-[9.5px]">
                        Jour férié travaillé
                    </div>
                )}

                {matinTime && <TooltipRow label="Matin" value={matinTime} />}
                {amTime && <TooltipRow label="Après-midi" value={amTime} />}

                {totalH > 0 && (
                    <TooltipRow label="Total heures" value={<span className="font-mono font-bold">{totalH.toFixed(2)}h</span>} />
                )}

                {Number(cellData?.heuresNuit || 0) > 0 && (
                    <TooltipRow label="Heures de nuit" value={<span className="font-mono text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)] font-bold">{Number(cellData.heuresNuit).toFixed(2)}h</span>} />
                )}

                {cellData?.retard && cellData?.minutesRetard > 0 && (
                    <TooltipRow label="Retard" value={`-${cellData.minutesRetard} min`} className="text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-bold" />
                )}

                <div className="pt-1 mt-1 border-t border-[var(--color-border-subtle)] space-y-1">
                    {allowances.length > 0
                        ? allowances.map((allowance) => (
                            <TooltipRow key={allowance.code} label={allowance.code}
                                value={`${Math.round(Number(allowance.montant || 0)).toLocaleString('fr-FR')} Ar`} />
                        ))
                        : <TooltipRow label="Indemnités" value="Aucune" />}
                </div>

                {cellData?.motifAbsence && (
                    <TooltipRow label="Motif" value={cellData.motifAbsence} className="text-[var(--color-danger-600)]" />
                )}
            </div>

            <div className="mt-2 pt-1 border-t border-[var(--color-border-subtle)] text-[9px] text-[var(--color-fg-muted)] flex items-center justify-between">
                <span>Cliquer sur la cellule pour modifier</span>
            </div>
        </div>
    );
}

function TooltipRow({ label, value, className = '' }) {
    return (
        <div className={`flex items-start justify-between gap-2 ${className}`}>
            <span className="text-[var(--color-fg-muted)] shrink-0">{label} :</span>
            <span className="font-semibold text-[var(--color-fg)] text-right">{value}</span>
        </div>
    );
}

/* Utilitaires */

function buildWeekDays(dateDebut, dateFin) {
    if (!dateDebut || !dateFin) return [];
    const days = [];
    const DAY_LABELS = ['Dim', 'Lun', 'Mar', 'Mer', 'Jeu', 'Ven', 'Sam'];
    let curr = new Date(dateDebut + 'T00:00:00');
    const end = new Date(dateFin + 'T00:00:00');
    while (curr <= end) {
        const dow = curr.getDay();
        const yyyy = curr.getFullYear();
        const mm = String(curr.getMonth() + 1).padStart(2, '0');
        const dd = String(curr.getDate()).padStart(2, '0');
        days.push({
            date: `${yyyy}-${mm}-${dd}`,
            label: DAY_LABELS[dow],
            dateShort: `${dd}/${mm}`,
            isWeekend: dow === 0 || dow === 6,
            isSunday: dow === 0,
            isSaturday: dow === 6
        });
        curr.setDate(curr.getDate() + 1);
    }
    return days;
}

function buildEmployeeList(ganttData, weekDays) {
    const empMap = new Map();
    weekDays.forEach(day => {
        const dayRows = ganttData[day.date] || [];
        dayRows.forEach(row => {
            if (!empMap.has(row.idEmploye)) {
                empMap.set(row.idEmploye, {
                    idEmploye: row.idEmploye,
                    nom: row.nom,
                    prenom: row.prenom,
                    matricule: row.matricule,
                    badgeProfilLabel: row.badgeProfilLabel,
                    badgeProfilVariant: row.badgeProfilVariant
                });
            }
        });
    });
    return Array.from(empMap.values()).sort((a, b) => {
        const na = `${a.nom} ${a.prenom}`.toLowerCase();
        const nb = `${b.nom} ${b.prenom}`.toLowerCase();
        return na < nb ? -1 : na > nb ? 1 : 0;
    });
}

function getCellData(ganttData, date, idEmploye) {
    const dayRows = ganttData[date];
    if (!dayRows) return null;
    return dayRows.find(r => r.idEmploye === idEmploye) || null;
}

function isSunday(day) {
    if (day?.isSunday !== undefined) return day.isSunday;
    if (day?.date) return new Date(day.date + 'T00:00:00').getDay() === 0;
    return false;
}

function isSaturday(day) {
    if (day?.isSaturday !== undefined) return day.isSaturday;
    if (day?.date) return new Date(day.date + 'T00:00:00').getDay() === 6;
    return false;
}

function isWeekend(day) {
    if (day?.isWeekend !== undefined) return day.isWeekend;
    if (day?.date) {
        const d = new Date(day.date + 'T00:00:00').getDay();
        return d === 0 || d === 6;
    }
    return false;
}

function getStatutStyle(cellData, day) {
    if (day?.date && isDateInFuture(day.date)) {
        return { bg: 'bg-[var(--color-bg-subtle)]/25', border: 'border-dashed border-[var(--color-border-subtle)]/70', text: 'text-[var(--color-fg-muted)] opacity-40 ' };
    }

    const weekend = isWeekend(day);
    const hasWorked = Number(cellData?.totalHeures || 0) > 0;

    // Dimanche travaillé (avec majoration)
    if (isSunday(day) && hasWorked) {
        return { bg: 'bg-[var(--color-info-500)]/15', border: 'border-[var(--color-info-500)]/50', text: 'text-[var(--color-info-800)] dark:text-[var(--color-info-200)] font-bold' };
    }

    // Samedi travaillé
    if (isSaturday(day) && hasWorked) {
        return { bg: 'bg-[var(--color-success-500)]/15', border: 'border-[var(--color-success-500)]/50', text: 'text-[var(--color-success-800)] dark:text-[var(--color-success-200)] font-bold' };
    }

    // Weekend sans travail = Repos légal (y compris cadres autonomes)
    if (weekend && (!cellData || !hasWorked || cellData.codeStatut === 'REPOS' || cellData.repos)) {
        return { bg: 'bg-[var(--color-bg-subtle)]/40', border: 'border-[var(--color-border-subtle)]', text: 'text-[var(--color-fg-muted)] opacity-55' };
    }

    if (!cellData) return { bg: 'bg-[var(--color-bg-subtle)]/30', border: 'border-[var(--color-border-subtle)]', text: 'text-[var(--color-fg-muted)]' };
    if (cellData.codeStatut === 'REPOS' || cellData.repos) return { bg: 'bg-[var(--color-bg-subtle)]/40', border: 'border-[var(--color-border-subtle)]', text: 'text-[var(--color-fg-muted)] opacity-55' };
    if (cellData.ferieJour) return { bg: 'bg-[var(--color-bg-subtle)]', border: 'border-[var(--color-border)]', text: 'text-[var(--color-fg-muted)]' };
    if (cellData.absenceNonJustifiee || cellData.codeStatut === 'ABSENCE_NON_JUSTIFIEE') return { bg: 'bg-[var(--color-danger-500)]/10', border: 'border-[var(--color-danger-500)]/40', text: 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' };
    if (cellData.conge || cellData.absenceJustifiee || cellData.codeStatut === 'CONGE') return { bg: 'bg-[var(--color-sky-500)]/10', border: 'border-[var(--color-sky-500)]/40', text: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]' };

    if (cellData.dispensePointage && !weekend) {
        if (cellData.planifie) return { bg: 'bg-[var(--color-bg-subtle)]', border: 'border-dashed border-[var(--color-border-subtle)]', text: 'text-[var(--color-fg-muted)]' };
                return { bg: 'bg-[var(--color-info-500)]/10', border: 'border-[var(--color-info-500)]/40', text: 'text-[var(--color-info-700)] dark:text-[var(--color-info-300)]' };
    }

    if (cellData.retard && cellData.minutesRetard > 0) return { bg: 'bg-[var(--color-warning-500)]/10', border: 'border-[var(--color-warning-500)]/40', text: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]' };
    if (cellData.present || hasWorked) return { bg: 'bg-[var(--color-success-500)]/10', border: 'border-[var(--color-success-500)]/40', text: 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' };

    return { bg: 'bg-[var(--color-bg-subtle)]', border: 'border-[var(--color-border-subtle)]', text: 'text-[var(--color-fg-muted)]' };
}

function getStatutLabel(cellData, day) {
    if (day?.date && isDateInFuture(day.date)) {
        return 'Non échu';
    }

    const weekend = isWeekend(day);
    const hasWorked = Number(cellData?.totalHeures || 0) > 0;

    // Dimanche travaillé
    if (isSunday(day) && hasWorked) {
        return `Dim. ${Number(cellData.totalHeures).toFixed(0)}h`;
    }

    // Samedi travaillé
    if (isSaturday(day) && hasWorked) {
        return `Sam. ${Number(cellData.totalHeures).toFixed(0)}h`;
    }

    // Repos légal sur les weekends sans saisie de travail
    if (weekend && (!cellData || !hasWorked || cellData.codeStatut === 'REPOS' || cellData.repos)) {
        return 'Repos';
    }

    if (!cellData) return 'Non saisi';
    if (cellData.codeStatut === 'REPOS' || cellData.repos) return 'Repos';
    if (cellData.ferieJour) return 'Férié';
    if (cellData.absenceNonJustifiee || cellData.codeStatut === 'ABSENCE_NON_JUSTIFIEE') return 'Abs. N.J.';
    if (cellData.conge || cellData.codeStatut === 'CONGE') return 'Congé';
    if (cellData.absenceJustifiee) return 'Abs. just.';
    if (cellData.dispensePointage && !weekend) {
        if (cellData.planifie) return 'Planifié';
        return cellData.estCadre ? 'Cadre Auto' : 'Autonome';
    }
    if (cellData.retard && cellData.minutesRetard > 0) return 'Retard';
    if (cellData.present || hasWorked) return 'Présent';
    if (cellData.nonSaisi) return 'Non saisi';
    return '—';
}

function getTimeString(cellData, day) {
    if (!cellData) return '';
    if (isWeekend(day) && Number(cellData.totalHeures || 0) === 0) return '';
    if (cellData.codeStatut === 'REPOS' || cellData.repos) return '';
    if (cellData.heureEntree && cellData.heureSortie) return `${cellData.heureEntree} - ${cellData.heureSortie}`;
    if (cellData.heureEntreeMatin && cellData.heureSortieAm) return `${cellData.heureEntreeMatin} - ${cellData.heureSortieAm}`;
    if (cellData.totalHeures > 0) return `${Number(cellData.totalHeures).toFixed(1)}h`;
    return '';
}

const GANTT_STATUTS = [
    { code: 'PRESENT', label: 'Présent', bg: 'bg-[var(--color-success-500)]/10', border: 'border-[var(--color-success-500)]/40' },
    { code: 'DIMANCHE', label: 'Dimanche (Majoré)', bg: 'bg-[var(--color-info-500)]/15', border: 'border-[var(--color-info-500)]/50' },
    { code: 'AUTONOME', label: 'Autonome', bg: 'bg-[var(--color-info-500)]/10', border: 'border-[var(--color-info-500)]/40' },
    { code: 'RETARD', label: 'Retard', bg: 'bg-[var(--color-warning-500)]/10', border: 'border-[var(--color-warning-500)]/40' },
    { code: 'CONGE', label: 'Congé', bg: 'bg-[var(--color-sky-500)]/10', border: 'border-[var(--color-sky-500)]/40' },
    { code: 'ABSENT_NJ', label: 'Abs. N.J.', bg: 'bg-[var(--color-danger-500)]/10', border: 'border-[var(--color-danger-500)]/40' },
    { code: 'FERIE', label: 'Férié', bg: 'bg-[var(--color-bg-subtle)]', border: 'border-[var(--color-border)]' },
    { code: 'REPOS', label: 'Repos', bg: 'bg-[var(--color-bg-subtle)]/40', border: 'border-[var(--color-border-subtle)]' },
];
