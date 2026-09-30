import { useEffect, useRef, useState, useId } from 'react';
import { Briefcase, Clock, FileCheck, RefreshCw, UserMinus, Plus, Building, Landmark, CheckCircle, ChevronDown, MoreHorizontal, FileText } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import EmptyState from '@/components/ui/data-display/EmptyState';
import HasPermission from '@/features/auth/components/HasPermission';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { isDateInFuture, isDateInPast } from '@/utils/dateUtils';

/**
 * InfoRow — Ligne label/valeur sans séparateur systématique.
 * La hiérarchie est assurée par l'espacement (space-y-2.5) et la typographie,
 * non par des lignes horizontales répétées.
 */
const InfoRow = ({ label, children }) => {
    const rawText = typeof children === 'string' ? children.trim() : children;
    const hasValue = rawText !== null && rawText !== undefined && rawText !== '' && rawText !== '—';

    return (
        <div className="flex justify-between items-baseline gap-2 min-w-0 py-0.5">
            <span className="text-xs text-[var(--color-fg-muted)] shrink-0">{label}</span>
            <span className={`text-xs text-right truncate ${hasValue ? 'font-bold text-[var(--color-fg)]' : 'text-[var(--color-fg-subtle)] font-normal'}`}>
                {hasValue ? children : 'Non renseigné'}
            </span>
        </div>
    );
};

// Menus en superposition : la carte garde sa hauteur à l’ouverture.
function ContractActionMenu({ label, icon, children }) {
    const [open, setOpen] = useState(false);
    const [menuLeft, setMenuLeft] = useState(0);
    const wrapper = useRef(null);
    const id = useId();
    const isIconOnly = label !== 'Documents';
    const close = (restoreFocus = false) => {
        setOpen(false);
        if (restoreFocus) wrapper.current?.querySelector('button')?.focus();
    };
    useEffect(() => {
        if (!open) return;
        const position = () => {
            const left = wrapper.current?.getBoundingClientRect().left || 0;
            const width = Math.min(256, window.innerWidth - 48);
            setMenuLeft(Math.max(16 - left, Math.min(0, window.innerWidth - 16 - left - width)));
        };
        position();
        window.addEventListener('resize', position);
        const outside = event => { if (!wrapper.current?.contains(event.target)) setOpen(false); };
        document.addEventListener('pointerdown', outside);
        return () => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', position); };
    }, [open]);
    return <div ref={wrapper} className="relative" onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
    }} onKeyDown={event => {
        if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); }
        if (event.key === 'ArrowDown' && open) {
            event.preventDefault();
            const buttons = [...wrapper.current.querySelectorAll('[data-actions] button')];
            buttons[(buttons.indexOf(document.activeElement) + 1) % buttons.length]?.focus();
        }
    }}>
        <Button
            variant="outline"
            size="sm"
            aria-label={label}
            title={label}
            aria-expanded={open}
            aria-controls={id}
            iconOnly={isIconOnly}
            iconLeft={icon}
            iconRight={!isIconOnly ? <ChevronDown size={14} /> : undefined}
            onClick={() => setOpen(value => !value)}
        >
            {!isIconOnly ? label : null}
        </Button>
        {open && <div id={id} data-actions style={{ left: menuLeft }} className="absolute top-full mt-2 z-20 w-64 max-w-[calc(100vw-3rem)] border border-[var(--color-border)] bg-[var(--color-surface)] shadow-lg p-1 flex flex-col gap-1"
            onClick={event => { if (event.target.closest('button')) close(); }}>{children}</div>}
    </div>;
}

export default function ContratActifSection({
    contratActif,
    affectationActive,
    workflowCplActif,
    contratsList = [],
    brouillonWf,
    workflowPlanifieFutur,
    etatEssai,
    _estEssaiRenouveleFlag,
    canWrite,
    onOpenCreateEmbauche,
    onOpenTransition,
    onOpenRupture,
    onOpenConfirmation,
    onOpenRenouvellement,
    onOpenAttestation,
    onOpenContrat,
}) {
    if (!contratActif) {
        if (brouillonWf) return null;

        const hasHistory = Array.isArray(contratsList) && contratsList.length > 0;

        return (
            <EmptyState
                icon={Briefcase}
                title={hasHistory ? 'Collaborateur inactif' : 'Aucun contrat actif'}
                description={hasHistory
                    ? "Historique de contrats clos. Vous pouvez réintégrer ou réembaucher ce collaborateur."
                    : "Aucun contrat de travail actif pour ce collaborateur."}
                actionLabel={canWrite ? (hasHistory ? 'Contractualiser (Réembauche)' : "Initier l'embauche") : undefined}
                onAction={canWrite ? onOpenCreateEmbauche : undefined}
                actionIcon={<Plus size={16} />}
            />
        );
    }

    const isContratFutur = contratActif.dateDebut && isDateInFuture(contratActif.dateDebut);
    const isContratEchu = contratActif.dateFin && isDateInPast(contratActif.dateFin);

    return (
        <Card padding bordered className="space-y-5">
            <div className="space-y-3">
                <div className="flex items-start justify-between gap-3">
                    <h3 className="text-base font-bold text-[var(--color-fg)] break-words min-w-0">
                        {contratActif.libelleTypeContrat || workflowCplActif?.libelleTypeContrat || 'Contrat'} · {contratActif.numeroContrat}
                    </h3>
                    <Badge variant={isContratFutur ? 'info' : isContratEchu ? 'danger' : 'success'} size="sm">
                        {isContratFutur ? 'Planifié' : isContratEchu ? 'Échu' : 'Actif'}
                    </Badge>
                </div>
                <p className="text-sm text-[var(--color-fg)]">{affectationActive?.libelleFonction || 'Poste non renseigné'} · {affectationActive?.libelleSite || 'Site non renseigné'}</p>
                <div className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-[var(--color-fg-muted)]">
                    <span>{isContratFutur ? 'À compter du' : 'Depuis le'} {fmtDate(contratActif.dateDebut)}{contratActif.dateFin ? ` · Fin le ${fmtDate(contratActif.dateFin)}` : ''}</span>
                    <HasPermission authority="PAIE_READ">
                        <span>{fmtMoney(affectationActive?.salaireBase)} · {affectationActive?.libelleModeCalculPaie || workflowCplActif?.libelleModeCalculPaie || 'Mode de paie non renseigné'}</span>
                    </HasPermission>
                    {etatEssai?.isEssaiActif && <Badge variant="warning" size="xs" icon={<Clock size={11} />}>Essai jusqu’au {fmtDate(etatEssai.dateFinEssaiStr)}</Badge>}
                </div>
                <div className="flex flex-wrap items-center gap-2 pt-1">
                    {canWrite && !brouillonWf && !workflowPlanifieFutur && <Button onClick={onOpenTransition} variant="primary" size="sm" iconLeft={<RefreshCw size={14} />}>Faire évoluer la situation</Button>}
                    {(onOpenContrat || onOpenAttestation) && <ContractActionMenu label="Documents" icon={<FileText size={14} />}>
                        {onOpenContrat && <Button variant="ghost" size="sm" className="justify-start !normal-case !tracking-normal" onClick={() => onOpenContrat(contratActif)}>Contrat et signature</Button>}
                        {onOpenAttestation && <Button variant="ghost" size="sm" className="justify-start !normal-case !tracking-normal" iconLeft={<FileCheck size={14} />} onClick={() => onOpenAttestation(contratActif)}>Attestation de travail</Button>}
                    </ContractActionMenu>}
                    {canWrite && !brouillonWf && !workflowPlanifieFutur && <ContractActionMenu label="Autres actions" icon={<MoreHorizontal size={16} />}>
                        <Button variant="ghost" size="sm" className="justify-start !normal-case !tracking-normal text-[var(--color-danger-500)]" iconLeft={<UserMinus size={14} />} onClick={onOpenRupture}>Mettre fin au contrat</Button>
                    </ContractActionMenu>}
                </div>
            </div>

            {/* Alerte informative si contrat futur planifié */}
            {isContratFutur && (
                <Alert
                    type="info"
                    message={`Contrat à terme différé : prise d'effet automatique le ${fmtDate(contratActif.dateDebut)}.`}
                />
            )}

            <details open className="space-y-3 border-t border-[var(--color-border-subtle)] pt-3">
                <summary className="cursor-pointer text-sm font-semibold text-[var(--color-fg)] hover:text-[var(--color-brand-500)] transition-colors">Détails du contrat et de l’affectation</summary>
            {/* Grille Contrat + Affectation — séparation par surface, pas par bordures répétées */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 pt-1">
                {/* Bloc 1 : Termes du Contrat */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2.5">
                    <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                        <Building size={13} className="text-[var(--color-brand-500)]" /> Termes Contractuels
                    </h4>
                    <div className="space-y-2">
                        <InfoRow label="Type de contrat">{contratActif?.libelleTypeContrat || workflowCplActif?.libelleTypeContrat}</InfoRow>
                        <InfoRow label="Filiale employeuse">{contratActif?.libelleFiliale || workflowCplActif?.libelleFiliale}</InfoRow>
                        <InfoRow label="Date de début">{fmtDate(contratActif.dateDebut)}</InfoRow>
                        <InfoRow label="Date de fin">{contratActif.dateFin ? fmtDate(contratActif.dateFin) : null}</InfoRow>
                        <InfoRow label="Date de signature">{contratActif.dateSignature ? fmtDate(contratActif.dateSignature) : null}</InfoRow>
                        <InfoRow label="Effectif légal">{contratActif.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Hors effectif)'}</InfoRow>
                    </div>

                    {/* Période d'Essai — séparation subtile par surface */}
                    {etatEssai?.isEssaiActif && (
                        <div className={`py-2 px-3 mt-1 border space-y-2 ${etatEssai.isEssaiEchu
                            ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 text-[var(--color-danger-600)]'
                            : 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/20 text-[var(--color-warning-700)]'
                            }`}>
                            <div className="flex justify-between items-center font-medium text-xs">
                                <span className="flex items-center gap-1.5 font-bold">
                                    <Clock size={13} /> {
                                        etatEssai.isEssaiEchu
                                            ? "Période d'essai échue :"
                                            : (etatEssai.isDernierJour ? "Dernier jour d'essai :" : "Période d'essai :")
                                    }
                                </span>
                                <span className="font-bold">
                                    {etatEssai.dureeEssaiMois} mois
                                    {etatEssai.isEssaiEchu
                                        ? ` (terminée le ${fmtDate(etatEssai.dateFinEssaiStr)})`
                                        : (etatEssai.isDernierJour ? ' (échéance aujourd’hui)' : ` (fin le ${fmtDate(etatEssai.dateFinEssaiStr)})`)
                                    }
                                </span>
                            </div>
                            {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                                <div className="pt-2 border-t border-current/20 flex flex-wrap items-center gap-2">
                                    {etatEssai?.canConfirm === true && (
                                        <Button
                                            onClick={onOpenConfirmation}
                                            variant="success"
                                            size="xs"
                                            iconLeft={<CheckCircle size={13} />}
                                        >
                                            Confirmer l'essai
                                        </Button>
                                    )}
                                    {etatEssai?.canRenew === true ? (
                                        <Button
                                            onClick={onOpenRenouvellement}
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<RefreshCw size={13} />}
                                        >
                                            Renouveler l'essai
                                        </Button>
                                    ) : null}
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Bloc 2 : Affectation Courante */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2.5">
                    <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                        <Landmark size={13} className="text-[var(--color-brand-500)]" /> Affectation Courante
                    </h4>
                    <div className="space-y-2">
                        <InfoRow label="Site">{affectationActive?.libelleSite || workflowCplActif?.libelleSite}</InfoRow>
                        <InfoRow label="Département">{affectationActive?.libelleDepartement || workflowCplActif?.libelleDepartement}</InfoRow>
                        <InfoRow label="Fonction / Poste">{affectationActive?.libelleFonction || workflowCplActif?.libelleFonction}</InfoRow>
                        <InfoRow label="Catégorie pro">{affectationActive?.libelleCategoriePro || affectationActive?.libelleCategorieProfessionnelle || workflowCplActif?.libelleCategoriePro}</InfoRow>
                        <InfoRow label="Supérieur N+1">{affectationActive?.nomPrenomResponsable || affectationActive?.nomResponsableHierarchique || workflowCplActif?.nomPrenomResponsable}</InfoRow>
                    </div>
                    {/* Salaire mis en avant — donnée prioritaire (protégé par PAIE_READ) */}
                    <HasPermission authority="PAIE_READ">
                        <div className="pt-2 border-t border-[var(--color-border-subtle)] mt-2 flex justify-between items-baseline">
                            <span className="text-[var(--color-fg-muted)] text-xs">Salaire de base :</span>
                            <span className="font-extrabold text-[var(--color-fg)] font-data text-sm sm:text-base">
                                {fmtMoney(affectationActive?.salaireBase)} {
                                    (affectationActive?.libelleModeCalculPaie || workflowCplActif?.libelleModeCalculPaie || '').toLowerCase().includes('horaire')
                                        ? '/ h'
                                        : (affectationActive?.libelleModeCalculPaie || workflowCplActif?.libelleModeCalculPaie || '').toLowerCase().includes('journal')
                                            ? '/ j'
                                            : '/ mois'
                                }
                            </span>
                        </div>
                    </HasPermission>
                </div>
            </div>
            </details>
        </Card>
    );
}
