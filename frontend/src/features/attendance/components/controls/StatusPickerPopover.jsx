import { useState, useRef, useEffect, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { 
    ChevronDown, 
    Check, 
    X, 
    CheckCircle2, 
    AlertTriangle, 
    XCircle,
    Palmtree
} from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

const STATUS_PRESENTATION = {
    PRESENT: {
        label: 'Présent',
        variant: 'success',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-success-500)]',
        textColor: 'text-[var(--color-success-700)] dark:text-[var(--color-success-400)]',
        bgHover: 'hover:bg-[var(--color-success-500)]/10'
    },
    DISPENSE_POINTAGE: {
        label: 'Autonome',
        variant: 'purple',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-purple-500)]',
        textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]',
        bgHover: 'hover:bg-[var(--color-purple-500)]/10'
    },
    AUTONOME: {
        label: 'Autonome',
        variant: 'purple',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-purple-500)]',
        textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]',
        bgHover: 'hover:bg-[var(--color-purple-500)]/10'
    },
    ABSENCE_JUSTIFIEE: {
        label: 'Absence justifiée',
        variant: 'warning',
        icon: AlertTriangle,
        dotColor: 'bg-[var(--color-warning-500)]',
        textColor: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]',
        bgHover: 'hover:bg-[var(--color-warning-500)]/10',
        requiresMotif: true
    },
    ABSENCE_NON_JUSTIFIEE: {
        label: 'Absent',
        variant: 'danger',
        icon: XCircle,
        dotColor: 'bg-[var(--color-danger-500)]',
        textColor: 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]',
        bgHover: 'hover:bg-[var(--color-danger-500)]/10'
    },
    CONGE: {
        label: 'Congé',
        variant: 'info',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-sky-500)]',
        textColor: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]',
        bgHover: 'hover:bg-[var(--color-sky-500)]/10'
    },
    CONGE_PAYE: {
        label: 'Congé',
        variant: 'info',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-sky-500)]',
        textColor: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]',
        bgHover: 'hover:bg-[var(--color-sky-500)]/10'
    },
    FERIE_CHOME: {
        label: 'Férié chômé',
        variant: 'neutral',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-purple-500)]',
        textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]',
        bgHover: 'hover:bg-[var(--color-purple-500)]/10'
    },
    FERIE_TRAVAILLE: {
        label: 'Férié +100%',
        variant: 'purple',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-purple-500)]',
        textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]',
        bgHover: 'hover:bg-[var(--color-purple-500)]/10'
    },
    REPOS: {
        label: 'Repos',
        variant: 'neutral',
        icon: CheckCircle2,
        dotColor: 'bg-[var(--color-fg-subtle)]',
        textColor: 'text-[var(--color-fg-muted)]',
        bgHover: 'hover:bg-[var(--color-bg-subtle)]'
    },
    NON_SAISI: {
        label: 'Non saisi',
        variant: 'neutral',
        icon: AlertTriangle,
        dotColor: 'bg-[var(--color-fg-subtle)]',
        textColor: 'text-[var(--color-fg-muted)]',
        bgHover: 'hover:bg-[var(--color-bg-subtle)]'
    }
};

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
    onOpenRegularisation,
    disabled = false
}) {
    // État local
    const [isOpen, setIsOpen] = useState(false);
    const [selectedStatut, setSelectedStatut] = useState(currentStatus || 'PRESENT');
    const [motif, setMotif] = useState(currentMotif || '');
    const [position, setPosition] = useState({ top: 0, left: 0, openAbove: false });

    // Références DOM et données dérivées
    const triggerRef = useRef(null);
    const popoverRef = useRef(null);

    const isAutonome = Boolean(row?.estAutonome);
    const defaultStatusActions = isAutonome ? [
        { code: 'DISPENSE_POINTAGE', label: 'Autonome (8h présumé)', description: 'Présomption légale de travail 8h' },
        { code: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée', description: 'Absence autorisée avec motif', requiresMotif: true },
        { code: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée', description: 'Absence non justifiée constatée' },
        { code: 'CONGE', label: 'Congé payé', description: 'Imputer sur solde congé payé' }
    ] : [
        { code: 'PRESENT', label: 'Présent', description: 'Collaborateur présent sur le site' },
        { code: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée', description: 'Absence autorisée avec motif', requiresMotif: true },
        { code: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée', description: 'Absence non justifiée constatée' },
        { code: 'CONGE', label: 'Congé payé', description: 'Imputer sur solde congé payé' }
    ];

    const statusOptions = (row?.statusActions && row.statusActions.length > 0) ? row.statusActions : defaultStatusActions;

    // Effets : synchronise les props, la position et la fermeture du popover.
    useEffect(() => {
        setSelectedStatut(currentStatus || 'PRESENT');
    }, [currentStatus]);

    useEffect(() => {
        setMotif(currentMotif || '');
    }, [currentMotif]);

    // Actions utilisateur et géométrie de l'interface.
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

    // Données dérivées pour l'affichage du statut courant.
    const selectedAction = statusOptions.find((option) => option.code === selectedStatut);
    const isMotifValid = !selectedAction?.requiresMotif || Boolean(motif.trim());

    const handleApply = (statutToApply = selectedStatut) => {
        const action = statusOptions.find((option) => option.code === statutToApply);
        if (action?.requiresMotif && !motif.trim()) {
            return; // Motif obligatoire
        }
        if (onStatusChange) {
            onStatusChange(statutToApply, action?.requiresMotif ? motif.trim() : '');
        }
        setIsOpen(false);
    };

    // Résolution robuste du statut actif, de sa présentation et de son libellé
    const activeStatutCode = (currentStatus || row?.codeStatut || (isAutonome ? 'DISPENSE_POINTAGE' : 'PRESENT')).toUpperCase();
    const configPres = STATUS_PRESENTATION[activeStatutCode] || {};

    const variant = row?.statutBadgeVariant || configPres.variant || (
        activeStatutCode === 'PRESENT' ? 'success' :
        activeStatutCode === 'DISPENSE_POINTAGE' || activeStatutCode === 'AUTONOME' ? 'purple' :
        activeStatutCode === 'ABSENCE_JUSTIFIEE' || activeStatutCode === 'RETARD' ? 'warning' :
        activeStatutCode === 'ABSENCE_NON_JUSTIFIEE' ? 'danger' :
        activeStatutCode === 'CONGE' || activeStatutCode === 'CONGE_PAYE' ? 'info' :
        activeStatutCode === 'FERIE_TRAVAILLE' ? 'purple' :
        'neutral'
    );
    const label = row?.statutBadgeLabel || row?.libelleStatut || configPres.label || (
        activeStatutCode === 'PRESENT' ? 'Présent' :
        activeStatutCode === 'DISPENSE_POINTAGE' || activeStatutCode === 'AUTONOME' ? 'Autonome' :
        activeStatutCode === 'ABSENCE_JUSTIFIEE' ? 'Absence justifiée' :
        activeStatutCode === 'ABSENCE_NON_JUSTIFIEE' ? 'Absent' :
        activeStatutCode === 'CONGE' || activeStatutCode === 'CONGE_PAYE' ? 'Congé' :
        activeStatutCode === 'FERIE_CHOME' ? 'Férié chômé' :
        activeStatutCode === 'FERIE_TRAVAILLE' ? 'Férié +100%' :
        activeStatutCode === 'REPOS' ? 'Repos' :
        activeStatutCode === 'NON_SAISI' ? 'Non saisi' :
        activeStatutCode
    );

    const isCongeApprouve = Boolean(row?.congeApprouve || (row?.conge && row?.libelleConge && !row?._isDraftConge));

    const tooltipTitle = disabled ? (
        isCongeApprouve ? `En congé approuvé (${row?.libelleConge || 'Congé payé'}) — Piloté par le module Congés` :
        row?.ferieChome ? 'Férié chômé non modifiable' :
        'Période verrouillée'
    ) : 'Cliquer pour modifier le statut du jour';

    // Rendu
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
                        {statusOptions.map((option) => {
                            const opt = { ...option, ...(STATUS_PRESENTATION[option.code] || {}) };
                            if (!opt.icon) return null;
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
                                            ? 'border-[var(--color-brand-500)]/40 bg-[var(--color-brand-500)]/10'
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
                                                <Check size={13} className="text-[var(--color-brand-600)] shrink-0" />
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
                    {selectedAction?.requiresMotif && (
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
                                className="w-full px-2.5 py-1.5 text-xs bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)]"
                            />
                            {/* Suggestions rapides */}
                            <div className="flex flex-wrap gap-1">
                                {SUGGESTIONS_MOTIF.map((sug) => (
                                    <button
                                        key={sug}
                                        type="button"
                                        onClick={() => setMotif(sug)}
                                        className="text-[9px] px-1.5 py-0.5 rounded bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-brand-400)] cursor-pointer"
                                    >
                                        + {sug}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}



                    {/* Bouton de régularisation intelligente directe */}
                    {onOpenRegularisation && (
                        <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)]">
                            <button
                                type="button"
                                onClick={() => {
                                    setIsOpen(false);
                                    onOpenRegularisation(row);
                                }}
                                className="w-full flex items-center justify-center gap-1.5 py-1.5 px-2 text-[11px] font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 border border-[var(--color-brand-500)]/30 rounded transition-colors cursor-pointer"
                            >
                                <Palmtree size={13} className="shrink-0 text-[var(--color-brand-600)]" />
                                <span>Régulariser par congé / absence…</span>
                            </button>
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
                                    ? 'bg-[var(--color-fg-subtle)] dark:bg-[var(--color-bg-subtle)] text-[var(--color-on-color)] cursor-not-allowed opacity-60'
                                    : 'bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] text-[var(--color-on-color)]'
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
