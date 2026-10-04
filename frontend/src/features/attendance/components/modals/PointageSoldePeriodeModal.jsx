import { useCallback, useEffect, useState } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { pointageSyntheseService, attendanceService } from '@/features/attendance/api/attendanceApi';
import { getErrorMessage } from '@/api/apiClient';
import { formatHeuresMinutes, formatHeuresDecimal, formatDate } from '@/features/attendance/utils/attendanceDisplayUtils';
import { ChevronDown } from 'lucide-react';

const decimalHours = (minutes) => `${(Number(minutes || 0) / 60).toFixed(2)} h`;
const decimalFromHhMm = (value) => {
    if (!value || typeof value !== 'string' || !value.includes('h') || value === '—') return null;
    const negative = value.startsWith('-') || value.startsWith('−');
    const positive = value.startsWith('+');
    const [hours, mins] = value.replace(/^[+−-]/, '').split('h');
    const decimal = (Number(hours || 0) + Number(mins || 0) / 60).toFixed(2);
    return `${negative ? '−' : positive ? '+' : ''}${decimal}h`;
};
const formatDateTime = (str) => {
    if (!str) return '—';
    try {
        const clean = str.replace(' ', 'T');
        const d = new Date(clean);
        if (isNaN(d.getTime())) return str.split('.')[0];
        return d.toLocaleDateString('fr-FR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch {
        return str.split('.')[0];
    }
};
const parseAdjustmentMinutes = (value, mode) => {
    const raw = String(value || '').trim();
    if (mode === 'horaire') {
        const match = raw.match(/^(\d{1,3}):(\d{2})$/);
        if (!match || Number(match[2]) > 59) return null;
        return Number(match[1]) * 60 + Number(match[2]);
    }
    if (!/^\d+(?:[.,]\d{1,2})?$/.test(raw)) return null;
    const hours = Number(raw.replace(',', '.'));
    return Number.isFinite(hours) ? Math.round(hours * 60) : null;
};

export default function PointageSoldePeriodeModal({ employe, idPeriodePaie, readOnly, onClose, onChanged }) {
    const [ajustements, setAjustements] = useState(null);
    const [fiche, setFiche] = useState(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [sens, setSens] = useState('-');
    const [modeSaisie, setModeSaisie] = useState('decimal');
    const [dureeAjustement, setDureeAjustement] = useState('');
    const [motif, setMotif] = useState('');
    const [cancelingId, setCancelingId] = useState(null);
    const [motifAnnulation, setMotifAnnulation] = useState('');
    const idEmploye = employe?.idEmploye;

    const load = useCallback(async () => {
        if (!idEmploye || !idPeriodePaie) return;
        setLoading(true);
        setAjustements(null);
        setFiche(null);
        try {
            const [history, sheet] = await Promise.all([
                pointageSyntheseService.getAjustements({ idPeriodePaie, idEmploye }),
                attendanceService.getFicheEmploye(idEmploye, { period: 'periode', idPeriode: idPeriodePaie })
            ]);
            setAjustements(history);
            setFiche(sheet);
            setError('');
        } catch (cause) {
            setError(getErrorMessage(cause, 'Impossible de charger les ajustements.'));
        } finally {
            setLoading(false);
        }
    }, [idEmploye, idPeriodePaie]);

    useEffect(() => { load(); }, [load]);

    const recap = fiche?.recap || {};
    const soldeSource = fiche?.soldePeriode;
    const semaines = soldeSource?.tronconsSemaine || [];
    const qualifications = semaines.reduce((totals, week) => ({
        nuit: totals.nuit + Number(week.heuresNuit || 0),
        dimanche: totals.dimanche + Number(week.heuresDimanche || 0),
        ferie: totals.ferie + Number(week.heuresFerie || 0),
    }), { nuit: 0, dimanche: 0, ferie: 0 });
    const canAdjust = !readOnly && fiche?.availableActions?.ADJUST_BALANCE === true;
    const minutesAjustement = parseAdjustmentMinutes(dureeAjustement, modeSaisie);
    const inputValid = minutesAjustement !== null && minutesAjustement > 0 && motif.trim().length >= 5;
    const create = async () => {
        if (!inputValid || !canAdjust || ajustements === null || saving) return;
        setSaving(true);
        try {
            await pointageSyntheseService.creerAjustement({
                idPeriodePaie, idEmploye: employe.idEmploye,
                minutesSignees: sens === '-' ? -minutesAjustement : minutesAjustement, motif: motif.trim()
            });
            setDureeAjustement(''); setMotif('');
            await load();
            onChanged?.();
        } catch (cause) {
            setError(getErrorMessage(cause, "Impossible d'enregistrer l'ajustement."));
        } finally { setSaving(false); }
    };

    const cancel = async (item) => {
        if (!canAdjust || motifAnnulation.trim().length < 5) return;
        setSaving(true);
        try {
            await pointageSyntheseService.annulerAjustement({
                idPeriodePaie, idEmploye: employe.idEmploye, idAjustement: item.id, motif: motifAnnulation.trim()
            });
            setCancelingId(null); setMotifAnnulation('');
            await load();
            onChanged?.();
        } catch (cause) {
            setError(getErrorMessage(cause, "Impossible d'annuler l'ajustement."));
        } finally { setSaving(false); }
    };

    return (
        <Modal isOpen={Boolean(employe)} onClose={onClose} title={`Solde horaire de période — ${employe?.nom || ''} ${employe?.prenom || ''}`} size="4xl">
            <div className="p-5 space-y-5 text-sm">
                <p className="text-[var(--color-fg-muted)]">Vérifiez les valeurs finales, puis les semaines et les ajustements qui les expliquent avant de valider la période.</p>
                <section className="space-y-2" aria-label="Valeurs finales utilisées par la paie">
                    <div>
                        <h3 className="font-semibold">Valeurs finales utilisées par la paie</h3>
                        <p className="text-xs text-[var(--color-fg-muted)]">Ces durées seront figées lors de la validation du pointage. Les congés restent traités sur leurs propres lignes.</p>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                        {(() => {
                            const isAbsence = Boolean(fiche?.hasRetardNet ?? (recap.absenceFinale && recap.absenceFinale !== '00h00' && recap.absenceFinale !== '—'));
                            const isHs30 = recap.hs30Finales && recap.hs30Finales !== '00h00' && recap.hs30Finales !== '—';
                            const isHs50 = recap.hs50Finales && recap.hs50Finales !== '00h00' && recap.hs50Finales !== '—';
                            return [
                                {
                                    label: 'Absence horaire à retenir',
                                    value: recap.absenceFinale,
                                    badge: isAbsence ? 'Retenue' : null,
                                    highlight: isAbsence,
                                    tone: isAbsence ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]',
                                    cardBorder: isAbsence ? 'border-2 border-[var(--color-danger-500)]/40 bg-[var(--color-danger-500)]/5' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'
                                },
                                {
                                    label: 'HS 30 % finales',
                                    value: recap.hs30Finales,
                                    badge: isHs30 ? '+30%' : null,
                                    highlight: isHs30,
                                    tone: isHs30 ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]',
                                    cardBorder: isHs30 ? 'border-2 border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/5' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'
                                },
                                {
                                    label: 'HS 50 % finales',
                                    value: recap.hs50Finales,
                                    badge: isHs50 ? '+50%' : null,
                                    highlight: isHs50,
                                    tone: isHs50 ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]',
                                    cardBorder: isHs50 ? 'border-2 border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/5' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'
                                },
                            ].map((item) => (
                                <div key={item.label} className={`p-3 relative transition-all rounded-none ${item.cardBorder}`}>
                                    <div className="flex items-center justify-between gap-1">
                                        <span className="text-xs font-medium text-[var(--color-fg-muted)]">
                                            {item.label} : <strong className={`font-mono text-base ${item.tone}`}>{item.value || '—'}</strong>
                                        </span>
                                        {item.badge && (
                                            <span className={`text-[10px] px-1.5 py-0.5 font-bold uppercase rounded-none ${
                                                item.highlight && item.badge === 'Retenue'
                                                    ? 'bg-[var(--color-danger-500)]/15 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]'
                                                    : 'bg-[var(--color-success-500)]/15 text-[var(--color-success-700)] dark:text-[var(--color-success-300)]'
                                            }`}>
                                                {item.badge}
                                            </span>
                                        )}
                                    </div>
                                    {decimalFromHhMm(item.value) && (
                                        <span className="block font-mono text-xs text-[var(--color-fg-muted)] mt-1">
                                            Équivalent : ({decimalFromHhMm(item.value)})
                                        </span>
                                    )}
                                </div>
                            ));
                        })()}
                    </div>
                </section>
                    <Table density="compact" bordered={false} tableClassName="min-w-[900px] text-xs">
                        <Thead>
                            <Tr>
                                <Th>Semaine</Th>
                                <Th>Objectif</Th>
                                <Th>Travaillé</Th>
                                <Th>Déficit</Th>
                                <Th>HS 30 %</Th>
                                <Th>HS 50 %</Th>
                                <Th>Nuit</Th>
                                <Th>Dimanche</Th>
                                <Th>Jour férié</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {semaines.map((week, index) => (
                                <Tr key={`${week.debutSemaine}-${index}`}>
                                    <Td className="font-medium">{formatDate(week.debutSemaine)} → {formatDate(week.finSemaine)}</Td>
                                    <Td className="font-mono text-[var(--color-fg-muted)]">{Number(week.seuilHeuresTroncon || 0).toFixed(2)} h</Td>
                                    <Td className="font-mono">
                                        {Number(week.totalHeuresTravaillees || 0) < Number(week.seuilHeuresTroncon || 0) ? (
                                            <span className="font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                                {Number(week.totalHeuresTravaillees || 0).toFixed(2)} h
                                            </span>
                                        ) : Number(week.totalHeuresTravaillees || 0) > Number(week.seuilHeuresTroncon || 0) ? (
                                            <span className="font-semibold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                                {Number(week.totalHeuresTravaillees || 0).toFixed(2)} h
                                            </span>
                                        ) : (
                                            <span>{Number(week.totalHeuresTravaillees || 0).toFixed(2)} h</span>
                                        )}
                                    </Td>
                                    <Td className="font-mono">
                                        {Number(week.heuresDeficit || 0) > 0 ? (
                                            <span className="font-semibold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                −{Number(week.heuresDeficit).toFixed(2)} h
                                            </span>
                                        ) : (
                                            <span className="text-[var(--color-fg-muted)]">-</span>
                                        )}
                                    </Td>
                                    <Td className="font-mono">
                                        {Number(week.heuresHs30 || 0) > 0 ? (
                                            <span className="font-semibold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                +{Number(week.heuresHs30).toFixed(2)} h
                                            </span>
                                        ) : (
                                            <span className="text-[var(--color-fg-muted)]">-</span>
                                        )}
                                    </Td>
                                    <Td className="font-mono">
                                        {Number(week.heuresHs50 || 0) > 0 ? (
                                            <span className="font-semibold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                +{Number(week.heuresHs50).toFixed(2)} h
                                            </span>
                                        ) : (
                                            <span className="text-[var(--color-fg-muted)]">-</span>
                                        )}
                                    </Td>
                                    {[week.heuresNuit, week.heuresDimanche, week.heuresFerie].map((hours, qualificationIndex) => {
                                        const value = Number(hours || 0);
                                        return (
                                            <Td key={qualificationIndex} className="whitespace-nowrap font-mono">
                                                {value > 0 ? (
                                                    <span className="font-semibold text-[var(--color-fg)]">
                                                        {formatHeuresMinutes(value)} <span className="text-[var(--color-fg-muted)] font-normal">({formatHeuresDecimal(value)})</span>
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-muted)]">-</span>
                                                )}
                                            </Td>
                                        );
                                    })}
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                <section className="space-y-2">
                <h3 className="font-semibold">Rapprochement du solde</h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 font-mono text-xs items-stretch">
                    {/* 1. Déficit cumulé */}
                    {(() => {
                        const rawDeficit = recap.deficitHebdo || '00h00';
                        const hasDeficit = rawDeficit !== '00h00' && rawDeficit !== '—' && !rawDeficit.startsWith('00h00');
                        const formattedDeficit = hasDeficit && !rawDeficit.startsWith('−') && !rawDeficit.startsWith('-')
                            ? `−${rawDeficit}`
                            : rawDeficit;
                        return (
                            <div className={`p-3 ${hasDeficit ? 'border-2 border-[var(--color-danger-500)]/30 bg-[var(--color-danger-500)]/5' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'}`}>
                                <span className="text-[var(--color-fg-muted)]">Déficit cumulé</span>
                                <strong className={`block mt-1 text-base ${hasDeficit ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold' : ''}`}>
                                    {formattedDeficit}
                                </strong>
                                {decimalFromHhMm(formattedDeficit) && (
                                    <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">
                                        ({decimalFromHhMm(formattedDeficit)})
                                    </span>
                                )}
                            </div>
                        );
                    })()}

                    {/* 2. HS cumulées */}
                    {(() => {
                        const rawHs = recap.hsTotalHebdo || '00h00';
                        const hasHs = rawHs !== '00h00' && rawHs !== '—' && !rawHs.startsWith('00h00');
                        const formattedHs = hasHs && !rawHs.startsWith('+') ? `+${rawHs}` : rawHs;
                        return (
                            <div className={`p-3 ${hasHs ? 'border-2 border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'}`}>
                                <span className="text-[var(--color-fg-muted)]">HS cumulées (30 % + 50 %)</span>
                                <strong className={`block mt-1 text-base ${hasHs ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-bold' : ''}`}>
                                    {formattedHs}
                                </strong>
                                {decimalFromHhMm(formattedHs) && (
                                    <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">
                                        ({decimalFromHhMm(formattedHs)})
                                    </span>
                                )}
                            </div>
                        );
                    })()}

                    {/* 3. Ajustements */}
                    {(() => {
                        const rawAj = recap.ajustements || '00h00';
                        const isNeg = rawAj.startsWith('−') || rawAj.startsWith('-');
                        const isPos = rawAj.startsWith('+');
                        const hasAj = rawAj !== '00h00' && rawAj !== '—';
                        return (
                            <div className={`p-3 ${hasAj ? (isNeg ? 'border-2 border-[var(--color-danger-500)]/30 bg-[var(--color-danger-500)]/5' : isPos ? 'border-2 border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5' : 'border border-[var(--color-border-subtle)]') : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'}`}>
                                <span className="text-[var(--color-fg-muted)]">Ajustements</span>
                                <strong className={`block mt-1 text-base ${isNeg ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold' : isPos ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-bold' : ''}`}>
                                    {rawAj}
                                </strong>
                                {decimalFromHhMm(rawAj) && (
                                    <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">
                                        ({decimalFromHhMm(rawAj)})
                                    </span>
                                )}
                            </div>
                        );
                    })()}

                    {/* 4. Solde final */}
                    {(() => {
                        const solde = recap.soldeFinal || '00h00';
                        const isNeg = solde.startsWith('−') || solde.startsWith('-');
                        const isPos = solde.startsWith('+');
                        return (
                            <div className={`p-3 sm:p-4 rounded-none transition-all ${isNeg ? 'border-2 border-[var(--color-danger-500)]/50 bg-[var(--color-danger-500)]/10' : isPos ? 'border-2 border-[var(--color-success-500)]/50 bg-[var(--color-success-500)]/10' : 'border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]'}`}>
                                <span className={`block text-[10px] font-bold uppercase tracking-wider ${isNeg ? 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]' : isPos ? 'text-[var(--color-success-700)] dark:text-[var(--color-success-300)]' : 'text-[var(--color-fg-muted)]'}`}>
                                    Solde final {isNeg ? '· Déficit' : isPos ? '· Excédent' : ''}
                                </span>
                                <strong className={`block mt-1 text-xl font-bold font-mono leading-tight ${isNeg ? 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]' : isPos ? 'text-[var(--color-success-700)] dark:text-[var(--color-success-300)]' : ''}`}>
                                    {solde}
                                </strong>
                                {decimalFromHhMm(solde) && (
                                    <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">
                                        ({decimalFromHhMm(solde)})
                                    </span>
                                )}
                            </div>
                        );
                    })()}
                </div>
                </section>
                <section className="space-y-3 border-t border-[var(--color-border-subtle)] pt-4">
                    <h3 className="font-semibold">Heures qualifiées incluses dans le temps travaillé</h3>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                        {[
                            ['Nuit', qualifications.nuit],
                            ['Dimanche', qualifications.dimanche],
                            ['Jour férié', qualifications.ferie],
                        ].map(([label, value]) => (
                            <div key={label} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3">
                                <span className="text-[var(--color-fg-muted)]">{label}</span>
                                <strong className="block mt-1 font-mono">{formatHeuresMinutes(value)} <span className="text-[var(--color-fg-muted)] font-normal">({formatHeuresDecimal(value)})</span></strong>
                            </div>
                        ))}
                    </div>
                </section>
                {recap.regleVentilation && <p className="text-xs text-[var(--color-fg-muted)]">{recap.regleVentilation}</p>}
                {recap.alerteSolde && <p role="alert" className="text-[var(--color-danger-600)] text-xs">{recap.alerteSolde}</p>}
                <section className="space-y-2">
                    <h3 className="font-semibold">Ajustements d’heures</h3>
                    {loading ? <p>Chargement…</p> : ajustements === null ? <p className="text-[var(--color-warning-700)]">Liste indisponible. Rechargez pour modifier les ajustements.</p> : ajustements.length === 0 ? <p className="text-[var(--color-fg-muted)]">Aucun ajustement.</p> : ajustements.map((item) => {
                        const statusLabel = item.statut === 'VALIDE' ? 'Validé' : item.statut === 'ANNULE' ? 'Annulé' : item.statut;
                        return (
                            <div key={item.id} className="border-b border-[var(--color-border-subtle)] py-2 text-xs">
                                <div className="flex items-start justify-between gap-3">
                                    <div>
                                        <strong className={item.minutesSignees < 0 ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}>
                                            {item.minutesSignees > 0 ? '+' : ''}{decimalHours(item.minutesSignees)}
                                        </strong> · {item.motif}
                                        <div className="text-[var(--color-fg-muted)] mt-0.5">
                                            {statusLabel} · {formatDateTime(item.dateSaisie)}
                                        </div>
                                    </div>
                                    {canAdjust && item.statut === 'VALIDE' && (
                                        <button type="button" disabled={saving} onClick={() => { setCancelingId(item.id); setMotifAnnulation(''); }} className="text-[var(--color-danger-600)] hover:underline">
                                            Annuler
                                        </button>
                                    )}
                                </div>
                                {cancelingId === item.id && (
                                    <div className="w-full mt-2 space-y-2">
                                        <input value={motifAnnulation} onChange={(e) => setMotifAnnulation(e.target.value)} placeholder="Motif d’annulation" aria-label="Motif d’annulation" className="w-full border p-2" />
                                        <div className="flex gap-2">
                                            <Button disabled={motifAnnulation.trim().length < 5 || saving} onClick={() => cancel(item)}>Confirmer l’annulation</Button>
                                            <Button variant="secondary" onClick={() => setCancelingId(null)}>Retour</Button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        );
                    })}
                </section>
                {canAdjust && <section className="space-y-2 border-t pt-4">
                    <h3 className="font-semibold">Ajouter un ajustement</h3>
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4">
                        <div className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_1.5fr] gap-3 items-start">
                            <label className="flex flex-col gap-1">
                                <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">Action</span>
                                <span className="relative">
                                    <select value={sens} onChange={(e) => setSens(e.target.value)} aria-label="Sens de l’ajustement" className="w-full appearance-none border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2 pr-9 text-sm font-medium text-[var(--color-fg)] outline-none hover:border-[var(--color-fg-muted)] focus:border-[var(--color-brand-500)]">
                                        <option value="-">Retirer des heures</option><option value="+">Ajouter des heures</option>
                                    </select>
                                    <ChevronDown size={16} className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                                </span>
                            </label>
                            <div className="flex flex-col gap-1">
                                <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">Format</span>
                                <div className="flex h-[38px] border border-[var(--color-border)] bg-[var(--color-surface)] p-0.5" role="group" aria-label="Format de durée">
                                    {[
                                        ['decimal', 'Décimal'],
                                        ['horaire', 'Horaire'],
                                    ].map(([mode, label]) => (
                                        <button
                                            key={mode}
                                            type="button"
                                            aria-pressed={modeSaisie === mode}
                                            onClick={() => { setModeSaisie(mode); setDureeAjustement(''); }}
                                            className={`flex-1 px-2 text-xs font-semibold transition-colors ${modeSaisie === mode ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                                        >
                                            {label}
                                        </button>
                                    ))}
                                </div>
                            </div>
                            <Input
                                label={modeSaisie === 'decimal' ? 'Durée en heures' : 'Durée (HH:MM)'}
                                type="text"
                                inputMode={modeSaisie === 'decimal' ? 'decimal' : 'numeric'}
                                value={dureeAjustement}
                                onChange={(e) => setDureeAjustement(e.target.value)}
                                aria-label={modeSaisie === 'decimal' ? 'Durée en heures décimales' : 'Durée au format heures et minutes'}
                                placeholder={modeSaisie === 'decimal' ? 'Ex. 1,50' : 'Ex. 01:30'}
                                className="py-2 text-sm tabular-nums"
                                hint={minutesAjustement > 0 ? `Équivalent : ${formatHeuresMinutes(minutesAjustement / 60)} (${formatHeuresDecimal(minutesAjustement / 60)})` : modeSaisie === 'decimal' ? 'Saisissez une valeur en heures, par exemple 1,50.' : 'Saisissez les heures et minutes, par exemple 01:30.'}
                                aria-invalid={dureeAjustement !== '' && minutesAjustement === null}
                            />
                        </div>
                    </div>
                    <Input label="Motif précis" required value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Expliquez la raison de l’ajustement" aria-label="Motif de l’ajustement" hint="Obligatoire — au moins 5 caractères." />
                    <Button disabled={!inputValid || !canAdjust || ajustements === null || saving} onClick={create}>Enregistrer l’ajustement</Button>
                </section>}
                {error && <p role="alert" className="text-[var(--color-danger-600)]">{error}</p>}
            </div>
        </Modal>
    );
}
