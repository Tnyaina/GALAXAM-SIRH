// Briques d'UI partagées pour donner à tous les formulaires de carrière (Embauche,
// Transition, Confirmation/Renouvellement d'essai) l'apparence d'un vrai document
// contractuel plutôt que d'une simple suite de champs de saisie.

import { fmtDateFr } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import HasPermission from '@/features/auth/components/HasPermission';
//
// À placer par ex. dans src/components/carriere/ContractDocument.jsx et à importer
// depuis les modaux : import { Clause, ArticleHeader, DocumentFrame } from '@/features/employees/components/carriere/ContractDocument';

// Plus d'utilitaires non-composants exportés ici pour respecter react-refresh/only-export-components

/** Ligne "clause" façon contrat papier : libellé ... ligne pointillée ... valeur */
export function Clause({ label, value, placeholder = '……………………', className = '', strike = false }) {
    const hasValue = value !== undefined && value !== null && value !== '';
    return (
        <div className={`flex items-baseline gap-2 py-2 text-[13.5px] leading-relaxed ${className}`}>
            <span className="text-[var(--color-fg-muted)] whitespace-nowrap">{label}</span>
            <span className="flex-1 border-b border-dotted border-[var(--color-border-subtle)] translate-y-[-3px]" />
            <span className={
                strike
                    ? 'line-through decoration-[var(--color-fg-subtle)] text-[var(--color-fg-subtle)] whitespace-nowrap text-right'
                    : hasValue
                        ? 'font-semibold tabular-nums text-[var(--color-fg)] whitespace-nowrap text-right'
                        : 'font-normal text-[var(--color-fg-subtle)] whitespace-nowrap text-right'
            }>
                {hasValue ? value : placeholder}
            </span>
        </div>
    );
}

/** Paire "avant -> après" pour les avenants/transitions, plus parlante qu'une simple Clause quand la valeur change */
export function ClauseDiff({ label, before, after, unit = '' }) {
    const changed = String(before ?? '') !== String(after ?? '') && after !== undefined && after !== '';
    return (
        <div className={`flex items-baseline gap-2 py-2 text-[13.5px] leading-relaxed transition-all duration-200 ${
            changed ? 'bg-[var(--color-brand-500)]/10 px-2.5 -mx-2.5 border-l-2 border-l-[var(--color-brand-500)]' : ''
        }`}>
            <span className={`whitespace-nowrap ${changed ? 'font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]' : 'text-[var(--color-fg-muted)]'}`}>
                {label}
            </span>
            <span className="flex-1 border-b border-dotted border-[var(--color-border-subtle)] translate-y-[-3px]" />
            {changed ? (
                <span className="whitespace-nowrap text-right tabular-nums">
                    <span className="line-through text-[var(--color-fg-subtle)] mr-2 text-xs font-normal">
                        {before || 'Non renseigné'}{unit}
                    </span>
                    <span className="font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] bg-[var(--color-brand-500)]/15 px-1.5 py-0.5 rounded-none">{after}{unit}</span>
                </span>
            ) : (
                <span className={`whitespace-nowrap text-right tabular-nums ${after ? 'font-semibold text-[var(--color-fg)]' : 'text-[var(--color-fg-subtle)] font-normal'}`}>
                    {after || before || 'Non renseigné'}{unit}
                </span>
            )}
        </div>
    );
}

/** Titre d'article numéroté, réutilisé dans la colonne formulaire */
export function ArticleHeader({ n, icon, title, subtitle }) {
    return (
        <div className="flex items-start gap-3 pb-2.5 border-b border-[var(--color-border-subtle)]">
            <div className="w-6 h-6 border border-[var(--color-brand-500)]/40 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex items-center justify-center text-[10px] font-bold flex-shrink-0 mt-0.5">
                {n}
            </div>
            <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                    {icon} {title}
                </h3>
                {subtitle && <p className="text-[11px] text-[var(--color-fg-subtle)] mt-0.5">{subtitle}</p>}
            </div>
        </div>
    );
}

export function DocumentFrame({ children, watermark = 'Brouillon', className = '' }) {
    return (
        <div className={`relative bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-md overflow-hidden ${className}`}>
            {/* Filigrane d'arrière-plan */}
            <div className="pointer-events-none select-none absolute inset-0 flex items-center justify-center overflow-hidden">
                <span className="text-[58px] font-black uppercase tracking-[0.18em] text-[var(--color-fg-subtle)]/[0.05] rotate-[-25deg] whitespace-nowrap">
                    {watermark}
                </span>
            </div>

            <div className="relative p-7 sm:p-8 font-serif text-[var(--color-fg)] space-y-5">
                {children}
            </div>
        </div>
    );
}

export function DocumentHeader({ eyebrow, title, numero }) {
    return (
        <div className="text-center space-y-1 pb-3 border-b-2 border-double border-[var(--color-border-subtle)]">
            {eyebrow && (
                <p className="text-[10px] tracking-[0.25em] font-bold uppercase text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-sans">
                    {eyebrow}
                </p>
            )}
            <h2 className="text-base font-bold uppercase tracking-wide text-[var(--color-fg)]">
                {title}
            </h2>
            {numero ? (
                <p className="text-[11px] font-sans text-[var(--color-fg-muted)]">
                    Référence <span className="font-semibold text-[var(--color-fg)] tabular-nums">N° {numero}</span>
                </p>
            ) : (
                <p className="text-[10px] font-sans uppercase tracking-wide text-[var(--color-fg-subtle)]">Référence auto-générée à la validation</p>
            )}
        </div>
    );
}

export function ArticleBlock({ n, title, children }) {
    return (
        <div className="space-y-1.5 pt-3 first:pt-0">
            <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)] pb-1">
                Article {n} — {title}
            </p>
            {children}
        </div>
    );
}

export function DocumentFooter({ lieu = '', dateSignature, status = 'Brouillon (non actif)' }) {
    return (
        <div className="pt-5 mt-5 border-t border-[var(--color-border-subtle)] space-y-4">
            {/* Bloc signatures, comme au bas d'un contrat papier */}
            <div className="grid grid-cols-2 gap-6 font-sans text-[11px] text-[var(--color-fg-subtle)]">
                <div className="space-y-6">
                    <p>Pour l'employeur</p>
                    <div className="border-b border-dotted border-[var(--color-border-subtle)] h-6" />
                </div>
                <div className="space-y-6">
                    <p>Pour le salarié, précédé de « Lu et approuvé »</p>
                    <div className="border-b border-dotted border-[var(--color-border-subtle)] h-6" />
                </div>
            </div>

            <div className="flex items-center justify-between text-[11px] font-sans text-[var(--color-fg-subtle)] pt-1">
                <span>Fait{lieu ? ` à ${lieu}` : ''}{dateSignature ? `, le ${dateSignature}` : ''} en double exemplaire</span>
                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 border border-[var(--color-border-subtle)] uppercase tracking-wide text-[10px] font-bold text-[var(--color-fg-muted)]">
                    <span className="w-1.5 h-1.5 rounded-none bg-[var(--color-warning-500)]" />
                    {status}
                </span>
            </div>
        </div>
    );
}

/** Composant Aperçu de Contrat standardisé pour toutes les modales de carrière */
export function ContractPreview({
    className = '',
    libFiliale,
    nomEmploye,
    formData,
    libTypeContrat,
    libTypeIntervenant,
    exigeDateFin,
    libSite,
    libDepartement,
    libService,
    libFonction,
    libCategorie,
    libModePaie,
    libResponsable,
}) {
    return (
        <DocumentFrame className={className}>
            <DocumentHeader
                eyebrow={libFiliale?.raisonSociale || libFiliale?.raison_sociale || 'Filiale employeuse'}
                title="Contrat de Travail"
                numero={formData?.numeroContrat}
            />

            <div className="pt-2 text-[13px] leading-relaxed space-y-3">
                <p>
                    Entre <strong>{libFiliale?.raisonSociale || libFiliale?.raison_sociale || '……………'}</strong>, ci-après « l'Employeur »,
                    {' '}et <strong>{nomEmploye || '……………'}</strong>, ci-après « le Salarié », il est convenu ce qui suit :
                </p>

                <ArticleBlock n="1" title="Nature & Durée">
                    <Clause label="Type de contrat" value={libTypeContrat?.libelle} />
                    <Clause label="Profil d'intervenant" value={libTypeIntervenant?.libelle} />
                    <Clause label="Prise d'effet" value={fmtDateFr(formData?.dateDebut)} />
                    <Clause label="Terme" value={fmtDateFr(formData?.dateFin)} placeholder={exigeDateFin ? '……………………' : 'Durée indéterminée'} />
                    <Clause label="Signature le" value={fmtDateFr(formData?.dateSignature)} />
                    {formData?.aPeriodeEssai && (
                        <Clause label="Période d'essai" value={`${formData?.dureeEssaiMois || 0} mois (Fin le ${fmtDateFr(formData?.dateFinEssai) || '…'})`} />
                    )}
                    <Clause label="Effectif légal" value={formData?.comptabiliseEffectif ? 'Salarié comptabilisé' : 'Hors effectif'} />
                </ArticleBlock>

                <ArticleBlock n="2" title="Affectation">
                    <Clause label="Site" value={libSite?.libelle} />
                    <Clause label="Département" value={libDepartement?.libelle} />
                    <Clause label="Service" value={libService?.libelle} />
                    <Clause label="Fonction occupée" value={libFonction?.libelle} />
                    <Clause label="Catégorie pro." value={libCategorie?.libelle} />
                    <Clause label="Rattaché à" value={libResponsable ? `${libResponsable.nom || ''} ${libResponsable.prenom || ''}` : ''} placeholder="Aucun N+1" />
                </ArticleBlock>

                <ArticleBlock n="3" title="Rémunération">
                    <Clause label="Mode de calcul" value={libModePaie?.libelle} />
                    <HasPermission authority="PAIE_READ" fallback={<Clause label="Salaire de base" value="[Confidentiel]" />}>
                        <Clause label="Salaire de base" value={formData?.salaireBase ? fmtMoney(formData?.salaireBase) : ''} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]" />
                    </HasPermission>
                    <Clause label="Date d'effet (dossier)" value={fmtDateFr(formData?.dateEffet || formData?.dateDebut)} />
                </ArticleBlock>

                {formData?.motif && (
                    <div className="pt-2">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">Article 4 — Motif</p>
                        <p className="text-[var(--color-fg-muted)] text-[12.5px]">{formData?.motif}</p>
                    </div>
                )}
            </div>

            <DocumentFooter
                lieu={libSite?.libelle || libFiliale?.ville || ''}
                dateSignature={fmtDateFr(formData?.dateSignature || formData?.dateDebut)}
            />
        </DocumentFrame>
    );
}