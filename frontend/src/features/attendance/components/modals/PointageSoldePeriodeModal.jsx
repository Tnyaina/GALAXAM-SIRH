import { useCallback, useEffect, useState } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import { pointageSyntheseService, attendanceService } from '@/features/attendance/api/attendanceApi';
import { getErrorMessage } from '@/api/apiClient';

const decimalHours = (minutes) => `${(Number(minutes || 0) / 60).toFixed(2).replace('.', ',')} h`;

export default function PointageSoldePeriodeModal({ employe, idPeriodePaie, readOnly, onClose, onChanged }) {
    const [ajustements, setAjustements] = useState(null);
    const [fiche, setFiche] = useState(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [sens, setSens] = useState('-');
    const [heures, setHeures] = useState('0');
    const [minutes, setMinutes] = useState('');
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
    const canAdjust = !readOnly && fiche?.availableActions?.ADJUST_BALANCE === true;
    const heuresSaisies = Number(heures);
    const minutesSaisies = Number(minutes);
    const inputValid = Number.isInteger(heuresSaisies) && heuresSaisies >= 0 &&
        Number.isInteger(minutesSaisies) && minutesSaisies >= 0 && minutesSaisies < 60 &&
        heuresSaisies * 60 + minutesSaisies > 0 && motif.trim().length >= 5;
    const create = async () => {
        if (!inputValid || !canAdjust || ajustements === null || saving) return;
        setSaving(true);
        try {
            const quantity = heuresSaisies * 60 + minutesSaisies;
            await pointageSyntheseService.creerAjustement({
                idPeriodePaie, idEmploye: employe.idEmploye,
                minutesSignees: sens === '-' ? -quantity : quantity, motif: motif.trim()
            });
            setHeures('0'); setMinutes(''); setMotif('');
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
        <Modal isOpen={Boolean(employe)} onClose={onClose} title={`Solde horaire de période — ${employe?.nom || ''} ${employe?.prenom || ''}`} size="2xl">
            <div className="p-5 space-y-5 text-sm">
                <p className="text-[var(--color-fg-muted)]">1. Vérifiez les semaines. 2. Documentez chaque correction d’heures. 3. Contrôlez le solde avant validation de la période. Les badges restent inchangés.</p>
                <p className="rounded border border-[var(--color-border-subtle)] p-3 text-[var(--color-fg)]">Après le gel, la paie reprend l’absence horaire et les HS 30 % et HS 50 % finales. Les congés sont décomptés et indemnisés sur leurs propres lignes.</p>
                <div className="overflow-x-auto">
                    <table className="w-full text-xs text-left border-collapse">
                        <thead><tr className="border-b border-[var(--color-border-subtle)]"><th className="py-2">Semaine</th><th>Objectif</th><th>Travaillé</th><th>Déficit</th><th>HS 30 %</th><th>HS 50 %</th></tr></thead>
                        <tbody>{(soldeSource?.tronconsSemaine || []).map((week, index) => (
                            <tr key={`${week.debutSemaine}-${index}`} className="border-b border-[var(--color-border-subtle)]">
                                <td className="py-2">{week.debutSemaine} → {week.finSemaine}</td>
                                <td>{Number(week.seuilHeuresTroncon || 0).toFixed(2)} h</td>
                                <td>{Number(week.totalHeuresTravaillees || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresDeficit || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresHs30 || 0).toFixed(2)} h</td>
                                <td>{Number(week.heuresHs50 || 0).toFixed(2)} h</td>
                            </tr>
                        ))}</tbody>
                    </table>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 font-mono text-xs">
                    <div className="border p-3">Déficit cumulé<strong className="block mt-1">{recap.deficitHebdo || '—'}</strong></div>
                    <div className="border p-3">HS cumulées (30 % + 50 %)<strong className="block mt-1">{recap.hsTotalHebdo || '—'}</strong></div>
                    <div className="border p-3">Ajustements<strong className="block mt-1">{recap.ajustements || '—'}</strong></div>
                    <div className="border p-3">Solde final<strong className="block mt-1">{recap.soldeFinal || '—'}</strong></div>
                </div>
                <div className="text-xs border-l-2 border-[var(--color-brand-500)] pl-3">
                    Absence finale : {recap.absenceFinale || '—'} · HS 30 % finales : {recap.hs30Finales || '—'} · HS 50 % finales : {recap.hs50Finales || '—'}
                    <div className="text-[var(--color-fg-muted)] mt-1">{recap.regleVentilation} Les qualifications dimanche, férié et nuit sont conservées.</div>
                </div>
                {recap.alerteSolde && <p role="alert" className="text-rose-600 text-xs">{recap.alerteSolde}</p>}
                <section className="space-y-2">
                    <h3 className="font-semibold">Ajustements d’heures</h3>
                    {loading ? <p>Chargement…</p> : ajustements === null ? <p className="text-amber-700">Liste indisponible. Rechargez pour modifier les ajustements.</p> : ajustements.length === 0 ? <p className="text-[var(--color-fg-muted)]">Aucun ajustement.</p> : ajustements.map((item) => (
                        <div key={item.id} className="border-b py-2 text-xs">
                            <div className="flex items-start justify-between gap-3">
                                <div><strong>{item.minutesSignees > 0 ? '+' : ''}{decimalHours(item.minutesSignees)}</strong> · {item.motif}<div className="text-[var(--color-fg-muted)]">{item.statut} · {item.dateSaisie}</div></div>
                                {canAdjust && item.statut === 'VALIDE' && <button type="button" disabled={saving} onClick={() => { setCancelingId(item.id); setMotifAnnulation(''); }} className="text-rose-600 hover:underline">Annuler</button>}
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
                    <div className="flex flex-wrap gap-2 items-center">
                        <select value={sens} onChange={(e) => setSens(e.target.value)} aria-label="Sens de l’ajustement" className="border p-2"><option value="-">Retirer</option><option value="+">Ajouter</option></select>
                        <input type="number" min="0" step="1" value={heures} onChange={(e) => setHeures(e.target.value)} aria-label="Heures" className="w-20 border p-2" /> h
                        <input type="number" min="0" max="59" step="1" value={minutes} onChange={(e) => setMinutes(e.target.value)} aria-label="Minutes" className="w-20 border p-2" /> min
                    </div>
                    <input value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Motif précis (obligatoire)" aria-label="Motif de l’ajustement" className="w-full border p-2" />
                    <Button disabled={!inputValid || !canAdjust || ajustements === null || saving} onClick={create}>Enregistrer l’ajustement</Button>
                </section>}
                {error && <p role="alert" className="text-rose-600">{error}</p>}
            </div>
        </Modal>
    );
}
