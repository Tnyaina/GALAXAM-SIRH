import { memo } from 'react';

function SupervisionLegend() {
    return (
        <div className="flex items-center gap-4 flex-wrap text-xs text-[var(--color-fg-muted)] px-1 pt-2">
            <span className="font-extrabold uppercase tracking-wider text-[var(--color-fg)]">
                Repère couleur :
            </span>
            <span className="inline-flex items-center gap-1.5">
                <span className="w-3 h-1 bg-[var(--color-brand-500)] inline-block"></span>
                En attente de signature
            </span>
            <span className="inline-flex items-center gap-1.5">
                <span className="w-3 h-1 bg-warning-500 inline-block"></span>
                Échéance proche (&lt; 60 j)
            </span>
            <span className="inline-flex items-center gap-1.5">
                <span className="w-3 h-1 bg-danger-500 inline-block"></span>
                Doublon potentiel à vérifier
            </span>
        </div>
    );
}

export default memo(SupervisionLegend);
