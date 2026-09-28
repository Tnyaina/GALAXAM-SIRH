import { RefreshCw, ArrowRight, FileSignature, Building2, CheckCircle2, Lock } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import FormSectionHeader from './FormSectionHeader';

export default function TransitionTypeSection({ relevantEvTypes = [], formData, setField }) {
    const CONTRACTUAL_CODES = ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT', 'REPRISE_ANCIENNETE'];
    
    const contractualEvents = relevantEvTypes.filter(t => CONTRACTUAL_CODES.includes(t.code));
    const internalEvents = relevantEvTypes.filter(t => !CONTRACTUAL_CODES.includes(t.code));

    const descMap = {
        MUTATION: 'Garde le contrat actif, modifie le site/service',
        PROMOTION: 'Changement de fonction ou classification',
        REVALORISATION: 'Ajustement du salaire de base',
        TRANSFORMATION_CDI: 'Clôture l\'ancien CDD et active un nouveau CDI',
        RENOUVELLEMENT_CDD: 'Avenant de prolongation du CDD',
        EMBAUCHE_APRES_STAGE: 'Transformation de stage vers CDD/CDI',
        REPRISE_CONSULTANT: 'Bascule vers un contrat de prestation / consultant',
        REPRISE_ANCIENNETE: 'Nouveau contrat avec continuité de l\'ancienneté acquise',
        REORGANISATION: 'Réorganisation interne / restructuration',
    };

    const renderEventCard = (t) => {
        const isEligible = t.isEligible !== false;
        const displayDesc = t.description || descMap[t.code] || 'Mouvement de carrière RH';
        const isSelected = formData.codeTypeEvenement === t.code;

        if (!isEligible) {
            return (
                <div
                    key={t.code}
                    className="p-3.5 border border-dashed border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] opacity-55 cursor-not-allowed flex flex-col justify-between select-none"
                    title={t.disabledReason || 'Action non disponible pour la situation contractuelle actuelle'}
                >
                    <div>
                        <p className="font-bold text-xs flex items-center justify-between gap-1.5 text-[var(--color-fg-subtle)]">
                            <span className="truncate">{t.libelle}</span>
                            <Lock size={12} className="text-[var(--color-fg-subtle)] shrink-0" />
                        </p>
                        <p className="text-[10px] mt-1 text-[var(--color-danger-500)] font-medium">
                            {t.disabledReason || 'Non éligible'}
                        </p>
                    </div>
                </div>
            );
        }

        return (
            <div
                key={t.code}
                role="button"
                tabIndex={0}
                aria-checked={isSelected}
                onClick={() => setField('codeTypeEvenement', t.code)}
                onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        setField('codeTypeEvenement', t.code);
                    }
                }}
                className={`
                    p-3.5 border-2 rounded-none cursor-pointer flex flex-col justify-between focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-500)] transition-all
                    ${isSelected
                        ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 shadow-xs'
                        : 'border-[var(--color-border-subtle)] hover:border-[var(--color-brand-400)] bg-[var(--color-surface)]'}
                `}
            >
                <div>
                    <p className={`font-bold text-xs flex items-center justify-between gap-1.5 ${isSelected ? 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' : 'text-[var(--color-fg)]'}`}>
                        <span className="truncate">{t.libelle}</span>
                        {isSelected ? (
                            <CheckCircle2 size={15} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shrink-0" />
                        ) : (
                            <ArrowRight size={13} className="text-[var(--color-fg-subtle)] shrink-0" />
                        )}
                    </p>
                    <p className={`text-[11px] mt-1 leading-normal ${isSelected ? 'text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] font-medium' : 'text-[var(--color-fg-subtle)]'}`}>
                        {displayDesc}
                    </p>
                </div>
            </div>
        );
    };

    return (
        <section className="space-y-4 pb-6 border-b border-[var(--color-border-subtle)]">
            <FormSectionHeader step={1} icon={RefreshCw} title="Type de Mouvement RH & Dates" />

            {/* Groupes d'événements */}
            <div className="space-y-4">
                {/* 1. Évolutions Contractuelles */}
                {contractualEvents.length > 0 && (
                    <div className="space-y-2">
                        <div className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] uppercase tracking-wider">
                            <FileSignature size={14} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]" />
                            <span>Évolutions Contractuelles (Nouveau Contrat)</span>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                            {contractualEvents.map(renderEventCard)}
                        </div>
                    </div>
                )}

                {/* 2. Évolutions Internes & Avenants */}
                {internalEvents.length > 0 && (
                    <div className="space-y-2">
                        <div className="flex items-center gap-1.5 text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                            <Building2 size={14} className="text-[var(--color-fg-muted)]" />
                            <span>Évolutions Internes &amp; Avenants (Poste / Salaire)</span>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                            {internalEvents.map(renderEventCard)}
                        </div>
                    </div>
                )}
            </div>

            {/* Grille dédiée pour la Date d'Effet & la Date de Signature */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 bg-[var(--color-bg-subtle)] p-3.5 border border-[var(--color-border-subtle)] rounded-none">
                <Input
                    label="Date de Prise d'Effet (Obligatoire)"
                    type="date"
                    required
                    value={formData.dateEffet}
                    onChange={(e) => setField('dateEffet', e.target.value)}
                    hint="Date d'application effective de cette décision"
                />
                <Input
                    label="Date de Signature de l'Avenant"
                    type="date"
                    value={formData.dateSignature}
                    onChange={(e) => setField('dateSignature', e.target.value)}
                    hint="Date d'émargement / signature du document"
                />
            </div>
        </section>
    );
}
