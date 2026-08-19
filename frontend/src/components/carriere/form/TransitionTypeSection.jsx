import { RefreshCw, ArrowRight } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import FormSectionHeader from './FormSectionHeader';

export default function TransitionTypeSection({ relevantEvTypes, formData, setField }) {
    return (
        <section className="space-y-4 pb-6 border-b border-ink-150 dark:border-ink-700">
            <FormSectionHeader step={1} icon={RefreshCw} title="Type de Mouvement RH & Dates" />

            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                {relevantEvTypes.map((t) => {
                    const descMap = {
                        MUTATION: 'Garde le contrat actif, modifie le site/service',
                        PROMOTION: 'Changement de fonction ou classification',
                        REVALORISATION: 'Ajustement du salaire de base',
                        TRANSFORMATION_CDI: 'Clôture l\'ancien CDD et active un nouveau CDI',
                        RENOUVELLEMENT_CDD: 'Avenant de prolongation du CDD',
                        EMBAUCHE_APRES_STAGE: 'Transformation de stage vers CDD/CDI',
                        REORGANISATION: 'Réorganisation interne / restructuration',
                    };
                    const displayDesc = t.description || descMap[t.code] || 'Mouvement de carrière RH';
                    const isSelected = formData.codeTypeEvenement === t.code;

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
                                p-3.5 border rounded-none cursor-pointer flex flex-col justify-between focus:outline-none focus:ring-2 focus:ring-brand-500
                                ${isSelected
                                    ? 'border-brand-500 bg-brand-500/10 shadow-sm ring-1 ring-brand-500/40'
                                    : 'border-ink-200 dark:border-ink-800 hover:border-brand-400 bg-white dark:bg-ink-950/30'}
                            `}
                        >
                            <div>
                                <p className="font-bold text-xs text-ink-900 dark:text-white flex items-center justify-between gap-1">
                                    <span className="truncate">{t.libelle}</span>
                                    {isSelected && <ArrowRight size={14} className="text-brand-500 shrink-0" />}
                                </p>
                                <p className="text-[11px] text-ink-400 mt-1 leading-normal">{displayDesc}</p>
                            </div>
                        </div>
                    );
                })}
            </div>

            {/* Grille dédiée pour la Date d'Effet & la Date de Signature */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 bg-ink-50/60 dark:bg-ink-900/30 p-3.5 border border-ink-200 dark:border-ink-800 rounded-none">
                <Input
                    label="Date de Prise d'Effet (Obligatoire)"
                    type="date"
                    required
                    value={formData.dateEffet}
                    onChange={(e) => setField('dateEffet', e.target.value)}
                    hint="Date d'application effective de cette décision RH"
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
