import { Fragment } from 'react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { formatHeuresMinutes, formatHeuresDecimal } from '@/features/attendance/utils/attendanceDisplayUtils';
import { fmtDate } from '@/utils/dateUtils';

export default function WeeklyPointageCalculationTable({ weeks = [], pointages = [] }) {
    return (
        <div className="overflow-x-auto">
            <Table density="compact" divided>
                <Thead>
                    <Tr>
                        <Th>Semaine</Th>
                        <Th className="text-center">Jours</Th>
                        <Th className="text-center">Objectif ajusté</Th>
                        <Th className="text-center">Heures réalisées</Th>
                        <Th className="text-center" title="Heures réalisées moins objectif ajusté">Écart semaine</Th>
                        <Th className="text-center">Résultat paie</Th>
                    </Tr>
                </Thead>
                <Tbody>
                    {weeks.length === 0 && (
                        <Tr><Td colSpan={6} className="py-6 text-center text-xs text-[var(--color-fg-muted)]">Aucune semaine calculée pour cette période.</Td></Tr>
                    )}
                    {weeks.map((week, index) => {
                        const start = week.debutSemaine || week.dateDebut;
                        const end = week.finSemaine || week.dateFin;
                        const objective = Number(week.seuilHeuresTroncon ?? week.heuresAttendues ?? 0);
                        const worked = Number(week.totalHeuresTravaillees ?? week.heuresFaites ?? 0);
                        const differenceMinutes = Math.round((worked - objective) * 60);
                        const surplus = Number(week.heuresHsBrutes ?? week.heuresSuppBrutes ?? 0);
                        const used = Number(week.heuresCompensees ?? 0);
                        const late = Number(week.heuresRetard ?? ((week.minutesRetard ?? 0) / 60));
                        const overtime30 = Number(week.heuresHs30 ?? week.heuresSuppNettes30 ?? 0);
                        const overtime50 = Number(week.heuresHs50 ?? week.heuresSuppNettes50 ?? 0);
                        const overtime = Number(week.heuresHsNettes ?? (overtime30 + overtime50));
                        const deficit = Number(week.heuresDeficit ?? week.heuresAbsenceRetenues ?? 0);
                        const missingDays = pointages.filter((day) => day.dateComplete >= start && day.dateComplete <= end
                            && day.statut === 'ABSENT' && !day.idPointage);

                        return (
                            <Fragment key={start || index}>
                                <Tr>
                                    <Td className="py-4 font-mono text-xs">
                                        <strong className="block text-[var(--color-fg)]">Semaine {week.numeroSemaine ?? week.numSemaine ?? index + 1}</strong>
                                        <span className="text-[var(--color-fg-muted)]">du {fmtDate(start)} au {fmtDate(end)}</span>
                                    </Td>
                                    <Td className="py-4 text-center font-mono text-xs">{week.nbJoursTroncon ?? week.joursOuvrables ?? '-'} j</Td>
                                    <Td className="py-4 text-center font-mono text-xs">
                                        <strong className="block font-semibold">{formatHeuresMinutes(objective)}</strong>
                                        <span className="text-[10px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(objective)})</span>
                                    </Td>
                                    <Td className="py-4 text-center font-mono text-xs">
                                        <strong className="block">{formatHeuresMinutes(worked)}</strong>
                                        <span className="text-[10px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(worked)})</span>
                                    </Td>
                                    <Td className={`py-4 text-center font-mono text-xs font-bold ${differenceMinutes > 0 ? 'text-[var(--color-success-600)]' : differenceMinutes < 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                        <strong className="block">{differenceMinutes > 0 ? '+' : differenceMinutes < 0 ? '−' : ''}{formatHeuresMinutes(Math.abs(differenceMinutes) / 60)}</strong>
                                        <span className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                                            ({differenceMinutes > 0 ? '+' : differenceMinutes < 0 ? '−' : ''}{formatHeuresDecimal(Math.abs(differenceMinutes) / 60)})
                                        </span>
                                    </Td>
                                    <Td className="py-4 text-center font-mono text-xs">
                                        {missingDays.length > 0 ? (
                                            <Badge variant="warning" size="sm">Saisie incomplète · {missingDays.length} j à saisir</Badge>
                                        ) : overtime > 0 ? (
                                            <div>
                                                <Badge variant="success" size="sm">+{formatHeuresMinutes(overtime)} HS</Badge>
                                                <span className="block mt-1 text-[10px] text-[var(--color-fg-muted)]">
                                                    {overtime30 > 0 && `${formatHeuresDecimal(overtime30)} à 30 %`}
                                                    {overtime30 > 0 && overtime50 > 0 && ' · '}
                                                    {overtime50 > 0 && `${formatHeuresDecimal(overtime50)} à 50 %`}
                                                </span>
                                            </div>
                                        ) : deficit > 0 ? (
                                            <div>
                                                <Badge variant="danger" size="sm">−{formatHeuresMinutes(deficit)} de déficit</Badge>
                                                <span className="block mt-1 text-[10px] text-[var(--color-fg-muted)] font-mono">
                                                    −{formatHeuresDecimal(deficit)}
                                                </span>
                                            </div>
                                        ) : <span className="text-[var(--color-fg-muted)]">Aucun impact</span>}
                                    </Td>
                                </Tr>
                                <Tr>
                                    <Td colSpan={6} className="pb-3 pt-1 text-xs text-[var(--color-fg-muted)]">
                                        <details>
                                            <summary className="cursor-pointer font-semibold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                                {missingDays.length > 0 ? 'Voir les jours à saisir' : 'Voir le détail'}
                                            </summary>
                                            {missingDays.length > 0 ? (
                                                <div className="mt-3 rounded border border-[var(--color-border-subtle)] p-3">
                                                    <strong className="block mb-1 text-[var(--color-fg)]">Saisie à compléter</strong>
                                                    <p>{missingDays.map((day) => fmtDate(day.dateComplete)).join(' · ')}</p>
                                                    <p className="mt-1">Le déficit calculé reste provisoire tant que ces journées n&apos;ont pas de statut enregistré.</p>
                                                </div>
                                            ) : (
                                                <div className="mt-3 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                                                    <div className="rounded border border-[var(--color-border-subtle)] p-3">
                                                        <strong className="block mb-2 text-[var(--color-fg)]">Calcul des heures</strong>
                                                        <p>Surplus journalier : <strong>+{formatHeuresMinutes(surplus)}</strong> <span className="text-[11px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(surplus)})</span></p>
                                                        <p>Dont utilisé pour les journées courtes : <strong>{formatHeuresMinutes(used)}</strong> <span className="text-[11px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(used)})</span></p>
                                                        {used > 0 && <p className="mt-2">La part utilisée est incluse dans le surplus ; elle ne s&apos;ajoute pas au résultat paie.</p>}
                                                    </div>
                                                    <div className="rounded border border-[var(--color-border-subtle)] p-3">
                                                        <strong className="block mb-2 text-[var(--color-fg)]">Heures qualifiées</strong>
                                                        {[
                                                            ['Nuit', Number(week.heuresNuit || 0)],
                                                            ['Dimanche', Number(week.heuresDimanche || 0)],
                                                            ['Jour férié', Number(week.heuresFerie || 0)],
                                                        ].map(([label, hours]) => (
                                                            <p key={label} className="flex justify-between gap-2">
                                                                <span>{label}</span>
                                                                <strong className="font-mono text-right">{formatHeuresMinutes(hours)} <span className="text-[11px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(hours)})</span></strong>
                                                            </p>
                                                        ))}
                                                        <p className="mt-2 text-[11px]">Ces qualifications peuvent se chevaucher et ne s&apos;additionnent pas au solde.</p>
                                                    </div>
                                                    <div className="rounded border border-[var(--color-border-subtle)] p-3">
                                                        <strong className="block mb-2 text-[var(--color-fg)]">Ponctualité</strong>
                                                        <p>Retards constatés : <strong>{formatHeuresMinutes(late)}</strong> <span className="text-[11px] text-[var(--color-fg-muted)]">({formatHeuresDecimal(late)})</span></p>
                                                        <p className="mt-2">Suivi distinct, sans seconde déduction des heures réalisées.</p>
                                                    </div>
                                                </div>
                                            )}
                                        </details>
                                    </Td>
                                </Tr>
                            </Fragment>
                        );
                    })}
                </Tbody>
            </Table>
        </div>
    );
}
