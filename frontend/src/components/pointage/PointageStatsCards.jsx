import { Users, UserCheck, Calendar, Clock, TrendingUp } from 'lucide-react';

export default function PointageStatsCards({
    totalInscrits = 0,
    totalPresents = 0,
    totalConges = 0,
    totalRetards = 0,
    cumulMinutesRetard = 0,
    totalHeuresTravaillees = 0,
    siteLabel = ''
}) {
    return (
        <section className="grid grid-cols-2 md:grid-cols-5 gap-3.5">
            {/* 1. Effectif du site */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                <p className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <Users size={13} className="text-brand-500" />
                    <span>Effectif du site</span>
                </p>
                <p className="text-[24px] font-extrabold font-mono text-[var(--color-fg)] mt-1.5">
                    {totalInscrits} <span className="text-[13px] font-sans font-medium text-[var(--color-fg-muted)]">salarié{totalInscrits > 1 ? 's' : ''}</span>
                </p>
                <p className="text-[12px] text-[var(--color-fg-muted)] mt-0.5 truncate">
                    {siteLabel}
                </p>
            </div>

            {/* 2. Présence */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                <p className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <UserCheck size={13} className="text-[var(--color-success-500)]" />
                    <span>Présence</span>
                </p>
                <p className="text-[24px] font-extrabold font-mono text-[var(--color-success-600)] dark:text-[var(--color-success-400)] mt-1.5">
                    {totalPresents}<span className="text-[15px] font-sans font-semibold text-[var(--color-fg-muted)]">/{totalInscrits}</span>
                </p>
                <p className="text-[12px] text-[var(--color-fg-muted)] mt-0.5">
                    Pointés ou autonomes
                </p>
            </div>

            {/* 3. Congés & absences */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                <p className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <Calendar size={13} className="text-[var(--color-warning-500)]" />
                    <span>Congés &amp; absences</span>
                </p>
                <p className="text-[24px] font-extrabold font-mono text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] mt-1.5">
                    {totalConges}
                </p>
                <p className="text-[12px] text-[var(--color-fg-muted)] mt-0.5">
                    Justifiés (jour / demi-jour)
                </p>
            </div>

            {/* 4. Retards du jour */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                <p className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <Clock size={13} className={totalRetards > 0 ? 'text-danger-500' : 'text-[var(--color-fg-muted)]'} />
                    <span>Retards du jour</span>
                </p>
                <div className="flex items-baseline gap-1.5 mt-1.5">
                    <span className={`text-[24px] font-extrabold font-mono ${totalRetards > 0 ? 'text-danger-600 dark:text-danger-400' : 'text-[var(--color-fg)]'}`}>
                        {totalRetards}
                    </span>
                    {cumulMinutesRetard > 0 && (
                        <span className="text-[12px] font-bold font-mono text-danger-500">
                            (+{cumulMinutesRetard} min)
                        </span>
                    )}
                </div>
                <p className="text-[12px] text-[var(--color-fg-muted)] mt-0.5">
                    {totalRetards === 0 ? 'Aucun retard constaté' : 'À compenser en hebdo'}
                </p>
            </div>

            {/* 5. Heures travaillées */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs col-span-2 md:col-span-1">
                <p className="text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                    <TrendingUp size={13} className="text-[var(--color-info-500)]" />
                    <span>Heures travaillées</span>
                </p>
                <p className="text-[24px] font-extrabold font-mono text-[var(--color-fg)] mt-1.5">
                    {Number(totalHeuresTravaillees).toFixed(1)}<span className="text-[13px] font-sans font-medium text-[var(--color-fg-muted)]">h</span>
                </p>
                <p className="text-[12px] text-[var(--color-fg-muted)] mt-0.5">
                    Cumul de l'effectif
                </p>
            </div>
        </section>
    );
}
