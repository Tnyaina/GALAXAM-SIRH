import { Clock, LayoutGrid, BarChart2, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageHeader({
    siteLabel,
    dateLongue,
    isToday,
    viewMode,
    onViewModeChange
}) {
    return (
        <div className="space-y-4">
            {/* Fil d'Ariane / Retour */}
            <Link 
                to="/dashboard" 
                className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold"
            >
                <ArrowLeft size={16} />
                Retour au Tableau de bord
            </Link>

            {/* En-tête Format Standard Galaxam */}
            <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
                <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-none blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />
                
                <div className="relative flex items-center gap-5">
                    <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
                        <Clock size={26} strokeWidth={2.5} />
                    </div>
                    <div>
                        <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">
                            Saisie & Exploitation des Pointages
                        </h1>
                        <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
                            Supervision quotidienne par site, pré-saisie des cadres autonomes et gestion dynamique des indemnités.
                        </p>
                        {dateLongue && (
                            <p className="text-xs font-semibold text-[var(--color-fg)] mt-2 flex items-center gap-2">
                                <span className="text-[var(--color-brand-600)] font-bold">{siteLabel}</span>
                                <span className="text-[var(--color-fg-muted)]">•</span> 
                                <span className="capitalize">{dateLongue}</span>
                                {isToday && <Badge variant="info">Aujourd'hui</Badge>}
                            </p>
                        )}
                    </div>
                </div>

                <div className="relative flex items-center gap-2 shrink-0">
                    <Button
                        variant={viewMode === 'grille' ? 'primary' : 'outline'}
                        size="sm"
                        onClick={() => onViewModeChange('grille')}
                        icon={<LayoutGrid size={16} />}
                    >
                        Grille
                    </Button>
                    <Button
                        variant={viewMode === 'gantt' ? 'primary' : 'outline'}
                        size="sm"
                        onClick={() => onViewModeChange('gantt')}
                        icon={<BarChart2 size={16} />}
                    >
                        Gantt
                    </Button>
                </div>
            </div>
        </div>
    );
}
