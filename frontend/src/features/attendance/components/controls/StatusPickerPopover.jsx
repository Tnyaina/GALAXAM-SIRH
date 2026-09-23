import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { 
    ChevronDown, 
    Check, 
    X, 
    CheckCircle2, 
    AlertTriangle, 
    XCircle
} from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

const STATUT_OPTIONS = [
    {
        code: 'PRESENT',
        label: 'Présent (Journée normale)',
        description: 'Journée travaillée avec calcul standard des heures',
        icon: CheckCircle2,
        dotColor: 'bg-emerald-500',
        textColor: 'text-emerald-700 dark:text-emerald-400',
        bgHover: 'hover:bg-emerald-500/10'
    },
    {
        code: 'ABSENCE_JUSTIFIEE',
        label: 'Absence justifiée',
        description: 'Motif légitime (certificat médical, mission, urgence)',
        icon: AlertTriangle,
        dotColor: 'bg-amber-500',
        textColor: 'text-amber-700 dark:text-amber-400',
        bgHover: 'hover:bg-amber-500/10',
        requiresMotif: true
    },
    {
        code: 'ABSENCE_NON_JUSTIFIEE',
        label: 'Absence non justifiée',
        description: 'Absence non autorisée (décomptée de la paie)',
        icon: XCircle,
        dotColor: 'bg-rose-500',
        textColor: 'text-rose-700 dark:text-rose-400',
        bgHover: 'hover:bg-rose-500/10'
    }
];

const SUGGESTIONS_MOTIF = [
    'Certificat médical',
    'Urgence familiale',
    'Mission terrain',
    'Intempéries / Cyclone',
    'Arrêt maladie',
    'Permission exceptionnelle'
];

/**
 * Popover compact de modification de statut de pointage en ligne dans la grille.
 * Garantit l'étanchéité des règles métier (pas d'autonomie arbitraire, pas de faux congés sans workflow, motif requis pour absence justifiée).
 */
export default function StatusPickerPopover({
    row,
    currentStatus = 'PRESENT',
    currentMotif = '',
    onStatusChange,
    disabled = false
}) {
    const [isOpen, setIsOpen] = useState(false);
    const [selectedStatut, setSelectedStatut] = useState(currentStatus || 'PRESENT');
    const [motif, setMotif] = useState(currentMotif || '');
    const [position, setPosition] = useState({ top: 0, left: 0, openAbove: false });

    const triggerRef = useRef(null);
    const popoverRef = useRef(null);

    const isAutonome = Boolean(row?.estAutonome || row?.dispensePointage);

    // Synchronisation avec les props
    useEffect(() => {
        setSelectedStatut(currentStatus || 'PRESENT');
    }, [currentStatus]);

    useEffect(() => {
        setMotif(currentMotif || '');
    }, [currentMotif]);

    const updatePosition = useCallback(() => {
        if (!triggerRef.current) return;
        const rect = triggerRef.current.getBoundingClientRect();
        const popoverWidth = 320;
        const popoverHeight = 360;

        const spaceBelow = window.innerHeight - rect.bottom;
        const openAbove = spaceBelow < popoverHeight && rect.top > popoverHeight;

        let left = rect.left + rect.width / 2 - popoverWidth / 2;
        if (left < 10) left = 10;
        if (left + popoverWidth > window.innerWidth - 10) {
            left = window.innerWidth - popoverWidth - 10;
        }

        const top = openAbove ? rect.top - 8 : rect.bottom + 6;

        setPosition({
            top,
            left,
            openAbove
        });
    }, []);

    const handleToggle = () => {
        if (disabled) return;
        if (!isOpen) {
            setSelectedStatut(currentStatus || 'PRESENT');
            setMotif(currentMotif || '');
            updatePosition();
        }
        setIsOpen(prev => !prev);
    };

    // Gestion du clic extérieur et de la touche Échap
    useEffect(() => {
        if (!isOpen) return;

        const handleClickOutside = (e) => {
            if (
                popoverRef.current && !popoverRef.current.contains(e.target) &&
                triggerRef.current && !triggerRef.current.contains(e.target)
            ) {
                setIsOpen(false);
            }
        };

        const handleKeyDown = (e) => {
            if (e.key === 'Escape') {
                setIsOpen(false);
            }
        };

        const handleScrollOrResize = () => {
            updatePosition();
        };

        document.addEventListener('mousedown', handleClickOutside);
        document.addEventListener('keydown', handleKeyDown);
        window.addEventListener('resize', handleScrollOrResize);
        window.addEventListener('scroll', handleScrollOrResize, true);

        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
            document.removeEventListener('keydown', handleKeyDown);
            window.removeEventListener('resize', handleScrollOrResize);
            window.removeEventListener('scroll', handleScrollOrResize, true);
        };
    }, [isOpen, updatePosition]);

    const isMotifValid = selectedStatut !== 'ABSENCE_JUSTIFIEE' || Boolean(motif.trim());

    const handleApply = (statutToApply = selectedStatut) => {
        if (statutToApply === 'ABSENCE_JUSTIFIEE' && !motif.trim()) {
            return; // Motif obligatoire
        }
        if (onStatusChange) {
            onStatusChange(statutToApply, statutToApply === 'ABSENCE_JUSTIFIEE' ? motif.trim() : '');
        }
        setIsOpen(false);
    };

    // Badge affiché dans la cellule
    const variant = row?.statutBadgeVariant || (
        row?.ferieTravaille ? 'error' :
        row?.ferieChome ? 'neutral' :
        row?.absenceNonJustifiee || row?.retard ? 'danger' :
        row?.conge || row?.absenceJustifiee ? 'warning' :
        row?.present ? 'success' :
        row?.dispensePointage ? 'brand' : 'neutral'
    );

    const label = row?.statutBadgeLabel || row?.libelleStatut || (
        currentStatus === 'PRESENT' ? 'Présent' :
        currentStatus === 'ABSENCE_JUSTIFIEE' ? 'Absence justifiée' :
        currentStatus === 'ABSENCE_NON_JUSTIFIEE' ? 'Absent' :
        currentStatus === 'CONGE' ? 'Congé' :
        currentStatus === 'DISPENSE_POINTAGE' ? 'Autonome' :
        'Non saisi'
    );

    const tooltipTitle = disabled ? (
        row?.conge ? `En congé approuvé (${row?.libelleConge || 'Congé payé'}) — Piloté par le module Congés` :
        row?.ferieChome ? 'Férié chômé non modifiable' :
        'Période verrouillée'
    ) : 'Cliquer pour modifier le statut du jour';

    return (
        <>
            <button
                ref={triggerRef}
                type="button"
                disabled={disabled}
                onClick={handleToggle}
                title={tooltipTitle}
                className={`inline-flex items-center gap-1.5 px-2 py-0.5 rounded transition-all select-none group border border-transparent ${
                    disabled
                        ? 'cursor-default opacity-85'
                        : 'cursor-pointer hover:border-[var(--color-border)] hover:bg-[var(--color-surface-hover)]'
                }`}
            >
                <Badge variant={variant} size="sm" className="font-bold pointer-events-none">
                    {label}
                </Badge>
                {!disabled && (
                    <ChevronDown
                        size={12}
                        className={`text-[var(--color-fg-muted)] group-hover:text-[var(--color-fg)] transition-transform duration-150 ${
                            isOpen ? 'rotate-180' : ''
                        }`}
                    />
                )}
            </button>

            {isOpen && createPortal(
                <div
                    ref={popoverRef}
                    style={{
                        position: 'fixed',
                        top: position.openAbove ? 'auto' : `${position.top}px`,
                        bottom: position.openAbove ? `${window.innerHeight - position.top}px` : 'auto',
                        left: `${position.left}px`,
                        zIndex: 99999,
                        width: '320px'
                    }}
                    className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xl p-3 text-xs text-[var(--color-fg)] animate-in fade-in zoom-in-95"
                >
                    {/* Header */}
                    <div className="flex items-center justify-between pb-2 mb-2 border-b border-[var(--color-border-subtle)]">
                        <div className="flex flex-col">
                            <span className="font-bold text-[12px]">Modifier le statut</span>
                            {row?.nom && (
                                <span className="text-[10px] text-[var(--color-fg-muted)] truncate max-w-[220px]">
                                    {row.nom} {row.prenom} {isAutonome ? (row.estCadre ? '· Cadre autonome' : '· Autonome') : (row.estCadre ? '· Cadre' : '')}
                                </span>
                            )}
                        </div>
                        <button
                            type="button"
                            onClick={() => setIsOpen(false)}
                            className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] rounded hover:bg-[var(--color-surface-hover)] cursor-pointer"
                        >
                            <X size={14} />
                        </button>
                    </div>

                    {/* Liste des statuts d'absence / présence */}
                    <div className="space-y-1 pr-0.5">
                        {STATUT_OPTIONS.map((opt) => {
                            const isSelected = selectedStatut === opt.code;

                            return (
                                <button
                                    key={opt.code}
                                    type="button"
                                    onClick={() => {
                                        setSelectedStatut(opt.code);
                                        if (!opt.requiresMotif) {
                                            handleApply(opt.code);
                                        }
                                    }}
                                    className={`w-full flex items-start gap-2.5 p-2 rounded text-left transition-colors cursor-pointer border ${
                                        isSelected
                                            ? 'border-brand-500/40 bg-brand-500/10'
                                            : `border-transparent ${opt.bgHover}`
                                    }`}
                                >
                                    <span className={`w-2.5 h-2.5 rounded-full mt-1 shrink-0 ${opt.dotColor}`} />
                                    <div className="flex-1 min-w-0">
                                        <div className="flex items-center justify-between">
                                            <span className={`font-bold text-[11px] ${opt.textColor}`}>
                                                {opt.label}
                                            </span>
                                            {isSelected && (
                                                <Check size={13} className="text-brand-600 shrink-0" />
                                            )}
                                        </div>
                                        <p className="text-[10px] text-[var(--color-fg-muted)] leading-tight mt-0.5">
                                            {opt.description}
                                        </p>
                                    </div>
                                </button>
                            );
                        })}
                    </div>

                    {/* Zone de motif si Absence justifiée (obligatoire) */}
                    {selectedStatut === 'ABSENCE_JUSTIFIEE' && (
                        <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)] space-y-2">
                            <label className="block text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Motif ou Justification <span className="text-[var(--color-danger-500)]">*</span>
                            </label>
                            <input
                                type="text"
                                value={motif}
                                onChange={(e) => setMotif(e.target.value)}
                                placeholder="Ex: Certificat médical, mission, urgence..."
                                autoFocus
                                className="w-full px-2.5 py-1.5 text-xs bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded focus:outline-none focus:border-brand-500 text-[var(--color-fg)]"
                            />
                            {/* Suggestions rapides */}
                            <div className="flex flex-wrap gap-1">
                                {SUGGESTIONS_MOTIF.map((sug) => (
                                    <button
                                        key={sug}
                                        type="button"
                                        onClick={() => setMotif(sug)}
                                        className="text-[9px] px-1.5 py-0.5 rounded bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-brand-400 cursor-pointer"
                                    >
                                        + {sug}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}



                    {/* Boutons d'action */}
                    <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)] flex items-center justify-end gap-2">
                        <button
                            type="button"
                            onClick={() => setIsOpen(false)}
                            className="px-2.5 py-1 text-[11px] font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)] rounded cursor-pointer"
                        >
                            Annuler
                        </button>
                        <button
                            type="button"
                            disabled={!isMotifValid}
                            onClick={() => handleApply(selectedStatut)}
                            className={`inline-flex items-center gap-1 px-3 py-1 text-[11px] font-bold rounded shadow-xs cursor-pointer ${
                                !isMotifValid
                                    ? 'bg-gray-400 dark:bg-gray-600 text-white cursor-not-allowed opacity-60'
                                    : 'bg-brand-600 hover:bg-brand-700 text-white'
                            }`}
                        >
                            <Check size={12} />
                            Appliquer
                        </button>
                    </div>
                </div>,
                document.body
            )}
        </>
    );
}
