import { useCallback, useEffect, useState } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { pointageSyntheseService, attendanceService } from '@/features/attendance/api/attendanceApi';
import { getErrorMessage } from '@/api/apiClient';
import { formatHeuresMinutes, formatHeuresDecimal } from '@/features/attendance/utils/attendanceDisplayUtils';
import { ChevronDown } from 'lucide-react';

const decimalHours = (minutes) => `${(Number(minutes || 0) / 60).toFixed(2).replace('.', ',')} h`;
const decimalFromHhMm = (value) => {
    if (!value || typeof value !== 'string' || !value.includes('h') || value === '—') return null;
    const negative = value.startsWith('-') || value.startsWith('−');
    const positive = value.startsWith('+');
    const [hours, mins] = value.replace(/^[+−-]/, '').split('h');
    const decimal = (Number(hours || 0) + Number(mins || 0) / 60).toFixed(2);
    return `${negative ? '−' : positive ? '+' : ''}${decimal}h`;
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
                        {[
                            ['Absence horaire à retenir', recap.absenceFinale, 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'],
                            ['HS finales à 30 %', recap.hs30Finales, 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'],
                            ['HS finales à 50 %', recap.hs50Finales, 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'],
                        ].map(([label, value, tone]) => (
                            <div key={label} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3">
                                <span className="text-xs text-[var(--color-fg-muted)]">{label}</span>
                                <strong className={`block mt-1 font-mono text-xl ${tone}`}>{value || '—'}</strong>
                                {decimalFromHhMm(value) && <span className="block font-mono text-xs text-[var(--color-fg-muted)]">({decimalFromHhMm(value)})</span>}
                            </div>
                        ))}
                    </div>
                </section>
                <p className="rounded border border-[var(--color-border-subtle)] p-3 text-xs text-[var(--color-fg-muted)]">Les durées qualifiées de nuit, dimanche et jour férié sont déjà comprises dans le temps travaillé. Elles sont ventilées séparément en paie.</p>
                <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left border-collapse min-w-[900px]">
                        <thead><tr className="border-b border-[var(--color-border-subtle)]"><th className="py-2">Semaine</th><th>Objectif</th><th>Travaillé</th><th>Déficit</th><th>HS 30 %</th><th>HS 50 %</th><th>Nuit</th><th>Dimanche</th><th>Jour férié</th></tr></thead>
                        <tbody>{semaines.map((week, index) => (
                            <tr key={`${week.debutSemaine}-${index}`} className="border-b border-[var(--color-border-subtle)]">
                                <td className="py-2">{week.debutSemaine} → {week.finSemaine}</td>
                                <td>{Number(week.seuilHeuresTroncon || 0).toFixed(2)} h</td>
                                <td>{Number(week.totalHeuresTravaillees || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresDeficit || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresHs30 || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresHs50 || 0).toFixed(2)} h</td>
                                {[week.heuresNuit, week.heuresDimanche, week.heuresFerie].map((hours, qualificationIndex) => {
                                    const value = Number(hours || 0);
                                    return <td key={qualificationIndex} className="whitespace-nowrap">{formatHeuresMinutes(value)} <span className="text-[var(--color-fg-muted)]">({formatHeuresDecimal(value)})</span></td>;
                                })}
                            </tr>
                        ))}</tbody>
                    </table>
                </div>
                <section className="space-y-2">
                <h3 className="font-semibold">Rapprochement du solde</h3>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 font-mono text-xs items-stretch">
                    {[
                        ['Déficit cumulé', recap.deficitHebdo],
                        ['HS cumulées (30 % + 50 %)', recap.hsTotalHebdo],
                        ['Ajustements', recap.ajustements],
                    ].map(([label, value]) => (
                        <div key={label} className="border border-[var(--color-border-subtle)] p-3">
                            {label}
                            <strong className="block mt-1 text-base">{value || '—'}</strong>
                            {decimalFromHhMm(value) && <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">({decimalFromHhMm(value)})</span>}
                        </div>
                    ))}
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 sm:p-4">
                        <span className="text-[var(--color-fg-muted)]">Solde final</span>
                        <strong className="block mt-1 text-base leading-tight">{recap.soldeFinal || '—'}</strong>
                        {decimalFromHhMm(recap.soldeFinal) && <span className="block mt-0.5 text-[11px] text-[var(--color-fg-muted)]">({decimalFromHhMm(recap.soldeFinal)})</span>}
                    </div>
                </div>
                <p className="text-xs text-[var(--color-fg-muted)]">Le calcul est fait en minutes ; les équivalents décimaux sont arrondis pour l’affichage.</p>
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
                    <p className="text-xs text-[var(--color-fg-muted)]">Ces heures sont déjà comprises dans « Travaillé » et dans le solde final. Les qualifications peuvent se chevaucher (par exemple, une heure de nuit un dimanche) : elles décrivent les heures à ventiler en paie, elles ne s’ajoutent pas une seconde fois au solde.</p>
                </section>
                {recap.regleVentilation && <p className="text-xs text-[var(--color-fg-muted)]">{recap.regleVentilation}</p>}
                {recap.alerteSolde && <p role="alert" className="text-[var(--color-danger-600)] text-xs">{recap.alerteSolde}</p>}
                <section className="space-y-2">
                    <h3 className="font-semibold">Ajustements d’heures</h3>
                    {loading ? <p>Chargement…</p> : ajustements === null ? <p className="text-[var(--color-warning-700)]">Liste indisponible. Rechargez pour modifier les ajustements.</p> : ajustements.length === 0 ? <p className="text-[var(--color-fg-muted)]">Aucun ajustement.</p> : ajustements.map((item) => (
                        <div key={item.id} className="border-b py-2 text-xs">
                            <div className="flex items-start justify-between gap-3">
                                <div><strong>{item.minutesSignees > 0 ? '+' : ''}{decimalHours(item.minutesSignees)}</strong> · {item.motif}<div className="text-[var(--color-fg-muted)]">{item.statut} · {item.dateSaisie}</div></div>
                                {canAdjust && item.statut === 'VALIDE' && <button type="button" disabled={saving} onClick={() => { setCancelingId(item.id); setMotifAnnulation(''); }} className="text-[var(--color-danger-600)] hover:underline">Annuler</button>}
                            </div>
                            {cancelingId === item.id && <div className="w-full mt-2 space-y-2">
                                <input value={motifAnnulation} onChange={(e) => setMotifAnnulation(e.target.value)} placeholder="Motif d’annulation" aria-label="Motif d’annulation" className="w-full border p-2" />
                                <div className="flex gap-2"><Button disabled={motifAnnulation.trim().length < 5 || saving} onClick={() => cancel(item)}>Confirmer l’annulation</Button><Button variant="secondary" onClick={() => setCancelingId(null)}>Retour</Button></div>
                            </div>}
                        </div>
                    ))}
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
