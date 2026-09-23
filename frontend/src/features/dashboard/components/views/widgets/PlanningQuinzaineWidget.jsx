import { useState, useMemo } from 'react';
import { CalendarRange, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { fmtDate } from '../../dashboardPresentation';
import SkeletonListRows from './SkeletonListRows';

export default function PlanningQuinzaineWidget({ loading, absences = [], sites = [], className = '' }) {
    const [selectedSiteFilter, setSelectedSiteFilter] = useState('ALL');

    const filteredAbsences = useMemo(() => {
        if (selectedSiteFilter === 'ALL') return absences;
        return absences.filter(a => String(a.idSite || a.nomSite) === String(selectedSiteFilter));
    }, [absences, selectedSiteFilter]);

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between gap-3 w-full">
                    <div className="flex items-center gap-2">
                        <CalendarRange size={16} className="text-brand-500 shrink-0" />
                        <CardTitle>Supervision des Départs & Congés (15 Jours)</CardTitle>
                    </div>

                    <div className="flex items-center gap-2">
                        {sites.length > 0 && (
                            <div className="flex items-center gap-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] p-0.5 text-xs">
                                <button
                                    onClick={() => setSelectedSiteFilter('ALL')}
                                    className={`px-2 py-0.5 font-semibold transition-colors ${selectedSiteFilter === 'ALL' ? 'bg-[var(--color-brand-500)] text-white' : 'text-[var(--color-fg-muted)]'}`}
                                >
                                    Tous
                                </button>
                                {sites.map(s => (
                                    <button
                                        key={s.idSite}
                                        onClick={() => setSelectedSiteFilter(s.idSite)}
                                        className={`px-2 py-0.5 font-semibold transition-colors ${String(selectedSiteFilter) === String(s.idSite) ? 'bg-[var(--color-brand-500)] text-white' : 'text-[var(--color-fg-muted)]'}`}
                                    >
                                        {s.codeSite || s.nomSite}
                                    </button>
                                ))}
                            </div>
                        )}
                        <Link
                            to="/conges?tab=soldes"
                            className="text-xs font-bold text-[var(--color-brand-500)] hover:underline flex items-center gap-1"
                        >
                            Planning <ArrowRight size={12} />
                        </Link>
                    </div>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && filteredAbsences.length === 0 && (
                    <EmptyState compact description="Aucun départ en congé programmé sur la quinzaine." />
                )}
                {!loading && filteredAbsences.length > 0 && (
                    <div className="max-h-[300px] overflow-y-auto pr-1 divide-y divide-[var(--color-border-subtle)]">
                        {filteredAbsences.map((ab, idx) => (
                            <div key={idx} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {ab.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">({ab.nomSite || 'TNR'})</span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {ab.typeConge || 'Congé'} · Du <span className="font-data">{fmtDate(ab.dateDebut)}</span> au <span className="font-data text-[var(--color-brand-500)] font-semibold">{fmtDate(ab.dateFin)}</span>
                                    </p>
                                </div>
                                <div className="flex items-center gap-2 shrink-0">
                                    <span className="font-data font-bold text-xs bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] px-2 py-0.5 border border-[var(--color-brand-500)]/20">
                                        {ab.nbJoursDecomptes || ab.nbJours || 1}j
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
