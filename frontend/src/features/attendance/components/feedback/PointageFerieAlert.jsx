import { Calendar, Sparkles } from 'lucide-react';

export default function PointageFerieAlert({
    isFerie = false,
    libelleFerie = '',
    siteLabel = ''
}) {
    if (!isFerie) return null;

    return (
        <div className="bg-[var(--color-warning-500)]/10 border-l-4 border-l-[var(--color-warning-500)] p-4 shadow-xs text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)]">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-start sm:items-center gap-2.5">
                    <Calendar size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5 sm:mt-0" />
                    <div>
                        <span className="font-bold text-[13px]">
                            Jour férié chômé · {libelleFerie || 'Férié officiel'}
                        </span>
                        <span className="text-[12px] opacity-90 block sm:inline sm:ml-2">
                            sur le site {siteLabel}.
                        </span>
                    </div>
                </div>

                <div className="flex items-center gap-1.5 text-[11.5px] font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-500)]/15 px-2.5 py-1 border border-[var(--color-warning-500)]/30 shrink-0">
                    <Sparkles size={13} className="text-[var(--color-warning-600)]" />
                    <span>Saisie ouverte : les heures saisies compteront en Férié travaillé (+100%)</span>
                </div>
            </div>
        </div>
    );
}
