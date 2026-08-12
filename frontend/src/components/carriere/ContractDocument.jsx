// Briques d'UI partagées pour donner à tous les formulaires de carrière (Embauche,
// Transition, Confirmation/Renouvellement d'essai) l'apparence d'un vrai document
// contractuel plutôt que d'une simple suite de champs de saisie.

import { fmtDateFr, fmtMoney } from '@/utils/carriereUtils';
//
// À placer par ex. dans src/components/carriere/ContractDocument.jsx et à importer
// depuis les modaux : import { Clause, ArticleHeader, DocumentFrame } from '@/components/carriere/ContractDocument';

// Plus d'utilitaires non-composants exportés ici pour respecter react-refresh/only-export-components

/** Ligne "clause" façon contrat papier : libellé ... ligne pointillée ... valeur */
export function Clause({ label, value, placeholder = '……………………', className = '', strike = false }) {
    const hasValue = value !== undefined && value !== null && value !== '';
    return (
        <div className={`flex items-baseline gap-2 py-2 text-[13.5px] leading-relaxed ${className}`}>
            <span className="text-ink-500 dark:text-ink-400 whitespace-nowrap">{label}</span>
            <span className="flex-1 border-b border-dotted border-ink-300 dark:border-ink-700 translate-y-[-3px]" />
            <span className={
                strike
                    ? 'line-through decoration-ink-400 text-ink-400 dark:text-ink-600 whitespace-nowrap text-right'
                    : hasValue
                        ? 'font-semibold tabular-nums text-ink-900 dark:text-ink-50 whitespace-nowrap text-right'
                        : 'font-normal text-ink-300 dark:text-ink-600 whitespace-nowrap text-right'
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
        <div className="flex items-baseline gap-2 py-2 text-[13.5px] leading-relaxed">
            <span className="text-ink-500 dark:text-ink-400 whitespace-nowrap">{label}</span>
            <span className="flex-1 border-b border-dotted border-ink-300 dark:border-ink-700 translate-y-[-3px]" />
            {changed ? (
                <span className="whitespace-nowrap text-right tabular-nums">
                    <span className="line-through text-ink-400 dark:text-ink-600 mr-1.5">{before || '—'}{unit}</span>
                    <span className="font-bold text-brand-600 dark:text-brand-400">{after}{unit}</span>
                </span>
            ) : (
                <span className={`whitespace-nowrap text-right tabular-nums ${after ? 'font-semibold text-ink-900 dark:text-ink-50' : 'text-ink-300 dark:text-ink-600'}`}>
                    {after || before || '……………'}{unit}
                </span>
            )}
        </div>
    );
}

/** Titre d'article numéroté, réutilisé dans la colonne formulaire */
export function ArticleHeader({ n, icon, title, subtitle }) {
    return (
        <div className="flex items-start gap-3 pb-2.5 border-b border-ink-150 dark:border-ink-700">
            <div className="w-6 h-6 border border-brand-500/40 text-brand-600 dark:text-brand-400 flex items-center justify-center text-[10px] font-bold flex-shrink-0 mt-0.5">
                {n}
            </div>
            <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-700 dark:text-ink-200 flex items-center gap-2">
                    {icon} {title}
                </h3>
                {subtitle && <p className="text-[11px] text-ink-400 mt-0.5">{subtitle}</p>}
            </div>
        </div>
    );
}

export function DocumentFrame({ children, watermark = 'Brouillon', className = '' }) {
    return (
        <div className={`relative bg-white dark:bg-ink-950 border border-ink-200 dark:border-ink-700 rounded-sm shadow-md overflow-hidden ${className}`}>
            {/* Filigrane d'arrière-plan */}
            <div className="pointer-events-none select-none absolute inset-0 flex items-center justify-center overflow-hidden">
                <span className="text-[58px] font-black uppercase tracking-[0.18em] text-ink-400/[0.05] dark:text-ink-300/[0.04] rotate-[-25deg] whitespace-nowrap">
                    {watermark}
                </span>
            </div>

            <div className="relative p-7 sm:p-8 font-serif text-ink-900 dark:text-ink-100 space-y-5">
                {children}
            </div>
        </div>
    );
}

export function DocumentHeader({ eyebrow, title, numero }) {
    return (
        <div className="text-center space-y-1 pb-3 border-b-2 border-double border-ink-300 dark:border-ink-700">
            {eyebrow && (
                <p className="text-[10px] tracking-[0.25em] font-bold uppercase text-brand-600 dark:text-brand-400 font-sans">
                    {eyebrow}
                </p>
            )}
            <h2 className="text-base font-bold uppercase tracking-wide text-ink-900 dark:text-white">
                {title}
            </h2>
            {numero ? (
                <p className="text-[11px] font-sans text-ink-500 dark:text-ink-400">
                    Référence <span className="font-semibold text-ink-700 dark:text-ink-200 tabular-nums">N° {numero}</span>
                </p>
            ) : (
                <p className="text-[10px] font-sans uppercase tracking-wide text-ink-400">Référence auto-générée à la validation</p>
            )}
        </div>
    );
}

export function ArticleBlock({ n, title, children }) {
    return (
        <div className="space-y-1.5 pt-3 first:pt-0">
            <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-ink-500 dark:text-ink-400 border-b border-ink-150 dark:border-ink-800 pb-1">
                Article {n} — {title}
            </p>
            {children}
        </div>
    );
}

export function DocumentFooter({ status = 'Brouillon (non actif)' }) {
    return (
        <div className="pt-5 mt-5 border-t border-ink-150 dark:border-ink-800 space-y-4">
            {/* Bloc signatures, comme au bas d'un contrat papier */}
            <div className="grid grid-cols-2 gap-6 font-sans text-[11px] text-ink-400">
                <div className="space-y-6">
                    <p>Pour l'employeur</p>
                    <div className="border-b border-dotted border-ink-300 dark:border-ink-700 h-6" />
                </div>
                <div className="space-y-6">
                    <p>Pour le salarié, précédé de « Lu et approuvé »</p>
                    <div className="border-b border-dotted border-ink-300 dark:border-ink-700 h-6" />
                </div>
            </div>

            <div className="flex items-center justify-between text-[11px] font-sans text-ink-400 pt-1">
                <span>Fait en double exemplaire</span>
                <span className="inline-flex items-center gap-1.5 px-2 py-0.5 border border-ink-300 dark:border-ink-600 uppercase tracking-wide text-[10px] font-bold text-ink-500 dark:text-ink-300">
                    <span className="w-1.5 h-1.5 rounded-full bg-warning-500" />
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
                    <Clause label="Salaire de base" value={formData?.salaireBase ? fmtMoney(formData?.salaireBase) : ''} className="text-brand-500" />
                    <Clause label="Date d'effet (dossier)" value={fmtDateFr(formData?.dateEffet || formData?.dateDebut)} />
                </ArticleBlock>

                {formData?.motif && (
                    <div className="pt-2">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">Article 4 — Motif</p>
                        <p className="text-ink-600 dark:text-ink-300 italic text-[12.5px]">{formData?.motif}</p>
                    </div>
                )}
            </div>

            <DocumentFooter />
        </DocumentFrame>
    );
}