import { useState, useEffect } from 'react';
import { TrendingUp, Plus, Trash2, AlertTriangle, Layers, Percent } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDate } from '@/utils/paieUtils';

export default function IrsaRevalorisationModal({
    isOpen,
    onClose,
    onSubmit,
    currentTranches = [],
    submitting = false,
    actionError = null
}) {
    // Tomorrow date as min valid application date
    const tomorrow = new Date();
    tomorrow.setDate(tomorrow.getDate() + 1);
    const tomorrowStr = tomorrow.toISOString().split('T')[0];

    // Default target date: Jan 1st of next year or tomorrow
    const nextYear = new Date().getFullYear() + 1;
    const defaultDate = `${nextYear}-01-01` > tomorrowStr ? `${nextYear}-01-01` : tomorrowStr;

    const [nouvelleDateDebut, setNouvelleDateDebut] = useState(defaultDate);
    const [tranches, setTranches] = useState([]);
    const [localError, setLocalError] = useState(null);

    // Clone active tranches when modal opens
    useEffect(() => {
        if (isOpen) {
            setLocalError(null);
            setNouvelleDateDebut(defaultDate);

            // Filter active tranches (or all sorted if empty)
            const sorted = [...currentTranches].sort(
                (a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0)
            );

            if (sorted.length > 0) {
                setTranches(
                    sorted.map(t => ({
                        numeroTranche: t.numeroTranche,
                        trancheMin: t.trancheMin !== null && t.trancheMin !== undefined ? parseFloat(t.trancheMin) : 0,
                        trancheMax: t.trancheMax !== null && t.trancheMax !== undefined && t.trancheMax !== '' ? parseFloat(t.trancheMax) : '',
                        taux: t.taux !== null && t.taux !== undefined ? parseFloat(t.taux) : 0,
                    }))
                );
            } else {
                // Default Madagascar IRSA tranches template
                setTranches([
                    { numeroTranche: 1, trancheMin: 0, trancheMax: 350000, taux: 0 },
                    { numeroTranche: 2, trancheMin: 350001, trancheMax: 400000, taux: 5 },
                    { numeroTranche: 3, trancheMin: 400001, trancheMax: 500000, taux: 10 },
                    { numeroTranche: 4, trancheMin: 500001, trancheMax: 600000, taux: 15 },
                    { numeroTranche: 5, trancheMin: 600001, trancheMax: '', taux: 20 },
                ]);
            }
        }
    }, [isOpen, currentTranches, defaultDate]);

    // Handle change of a field in a tranche
    const handleTrancheChange = (index, field, value) => {
        setLocalError(null);
        setTranches(prev => {
            const next = [...prev];
            next[index] = { ...next[index], [field]: value };
            return next;
        });
    };

    // Add new tranche
    const handleAddTranche = () => {
        setLocalError(null);
        const last = tranches[tranches.length - 1];
        if (last && (last.trancheMax === '' || last.trancheMax === null || last.trancheMax === undefined)) {
            setLocalError("Veuillez d'abord définir un montant maximum pour la dernière tranche avant d'en ajouter une nouvelle.");
            return;
        }

        const nextNum = tranches.length + 1;
        const nextMin = last ? parseFloat(last.trancheMax) + 1 : 0;

        setTranches(prev => [
            ...prev,
            {
                numeroTranche: nextNum,
                trancheMin: nextMin,
                trancheMax: '',
                taux: 0,
            }
        ]);
    };

    // Remove tranche
    const handleRemoveTranche = (index) => {
        setLocalError(null);
        if (tranches.length <= 1) {
            setLocalError("Le barème doit comporter au moins une tranche d'imposition.");
            return;
        }
        setTranches(prev => {
            const filtered = prev.filter((_, i) => i !== index);
            // Re-index numeroTranche
            return filtered.map((t, idx) => ({
                ...t,
                numeroTranche: idx + 1
            }));
        });
    };

    // Form submission validation
    const handleSubmit = (e) => {
        e.preventDefault();
        setLocalError(null);

        if (!nouvelleDateDebut) {
            setLocalError("La date d'application est obligatoire.");
            return;
        }

        if (nouvelleDateDebut < tomorrowStr) {
            setLocalError("La date d'application doit être postérieure à aujourd'hui (date dans le futur).");
            return;
        }

        if (!tranches || tranches.length === 0) {
            setLocalError("Le nouveau barème doit comporter au moins une tranche.");
            return;
        }

        // Validate each tranche
        for (let i = 0; i < tranches.length; i++) {
            const t = tranches[i];
            const min = parseFloat(t.trancheMin);
            const max = t.trancheMax !== '' && t.trancheMax !== null && t.trancheMax !== undefined ? parseFloat(t.trancheMax) : null;
            const taux = parseFloat(t.taux);

            if (isNaN(min) || min < 0) {
                setLocalError(`Tranche #${t.numeroTranche} : le montant minimum est invalide.`);
                return;
            }

            if (i === 0 && min !== 0) {
                setLocalError("La première tranche (Tranche #1) doit obligatoirement commencer à 0 Ar.");
                return;
            }

            if (max !== null) {
                if (isNaN(max) || max <= min) {
                    setLocalError(`Tranche #${t.numeroTranche} : le montant max (${max ? max.toLocaleString('fr-FR') : ''} Ar) doit être supérieur au minimum (${min.toLocaleString('fr-FR')} Ar).`);
                    return;
                }
            }

            if (isNaN(taux) || taux < 0 || taux > 100) {
                setLocalError(`Tranche #${t.numeroTranche} : le taux (${taux}%) doit être compris entre 0% et 100%.`);
                return;
            }

            // Continuity with previous tranche
            if (i > 0) {
                const prev = tranches[i - 1];
                const prevMax = parseFloat(prev.trancheMax);
                if (isNaN(prevMax)) {
                    setLocalError(`La tranche #${prev.numeroTranche} n'a pas de montant maximum alors qu'elle est suivie par la tranche #${t.numeroTranche}. Seule la dernière tranche peut être sans plafond.`);
                    return;
                }
                if (min !== prevMax + 1 && min !== prevMax) {
                    setLocalError(`Continuité non respectée : la tranche #${t.numeroTranche} démarre à ${min.toLocaleString('fr-FR')} Ar alors que la précédente s'arrête à ${prevMax.toLocaleString('fr-FR')} Ar.`);
                    return;
                }
            }
        }

        // Payload preparation
        const payload = {
            nouvelleDateDebut,
            tranches: tranches.map(t => ({
                numeroTranche: t.numeroTranche,
                trancheMin: parseFloat(t.trancheMin),
                trancheMax: t.trancheMax !== '' && t.trancheMax !== null && t.trancheMax !== undefined ? parseFloat(t.trancheMax) : null,
                taux: parseFloat(t.taux),
            }))
        };

        onSubmit(payload);
    };

    // Calculate eve of application date for the info alert
    let dateVeilleStr = '';
    if (nouvelleDateDebut) {
        const d = new Date(nouvelleDateDebut);
        d.setDate(d.getDate() - 1);
        dateVeilleStr = fmtDate(d.toISOString().split('T')[0]);
    }

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Revaloriser le Barème IRSA (Nouvelle Loi de Finances)"
            icon={<TrendingUp size={18} />}
            size="2xl"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button
                        type="submit"
                        form="irsa-revalorisation-form"
                        variant="primary"
                        size="sm"
                        loading={submitting}
                        iconLeft={<TrendingUp size={16} />}
                    >
                        Appliquer la nouvelle grille IRSA
                    </Button>
                </>
            }
        >
            <form id="irsa-revalorisation-form" onSubmit={handleSubmit} className="space-y-4">
                {(actionError || localError) && (
                    <Alert type="error" message={localError || actionError} />
                )}

                {/* Explication du versionnement */}
                <div className="bg-[var(--color-warning-500)]/8 border border-[var(--color-warning-500)]/25 p-3.5 flex items-start gap-3">
                    <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                    <div className="text-xs text-[var(--color-fg-muted)] space-y-1">
                        <p className="font-bold text-[var(--color-fg)]">
                            Revalorisation légale du barème — Clôture &amp; reconduction automatique
                        </p>
                        <p>
                            Le barème actuellement en vigueur sera automatiquement clôturé au{' '}
                            <strong className="text-[var(--color-fg)]">{dateVeilleStr || 'la veille'}</strong>.
                            La nouvelle grille de tranches ci-dessous sera appliquée à tout calcul de paie dont la période débute à partir du{' '}
                            <strong className="text-[var(--color-fg)]">{fmtDate(nouvelleDateDebut)}</strong>.
                        </p>
                        <p className="text-[var(--color-fg-muted)]">
                            Les tranches précédentes restent consultables dans l'historique archivé pour le recalcul d'anciens bulletins.
                        </p>
                    </div>
                </div>

                {/* Date d'application */}
                <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                    <div className="max-w-xs">
                        <Input
                            label="Date d'application (Date d'effet)"
                            type="date"
                            min={tomorrowStr}
                            required
                            value={nouvelleDateDebut}
                            onChange={(e) => setNouvelleDateDebut(e.target.value)}
                            hint="Date à laquelle cette nouvelle grille entre en vigueur"
                        />
                    </div>
                </div>

                {/* Grille des tranches éditable */}
                <div className="border border-[var(--color-border-subtle)] overflow-hidden">
                    <div className="bg-[var(--color-surface)] px-4 py-2.5 border-b border-[var(--color-border-subtle)] flex justify-between items-center">
                        <div className="flex items-center gap-2">
                            <Layers size={15} className="text-[var(--color-brand-500)]" />
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Tranches de la nouvelle grille ({tranches.length} tranche{tranches.length > 1 ? 's' : ''})
                            </span>
                        </div>
                        <Button
                            type="button"
                            variant="secondary"
                            size="sm"
                            onClick={handleAddTranche}
                            iconLeft={<Plus size={14} />}
                        >
                            Ajouter une tranche
                        </Button>
                    </div>

                    <div className="overflow-x-auto">
                        <table className="w-full text-xs text-left border-collapse">
                            <thead>
                                <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] uppercase tracking-wider font-semibold">
                                    <th className="p-2.5 w-20">Tranche</th>
                                    <th className="p-2.5">Seuil Min (Ar)</th>
                                    <th className="p-2.5">Seuil Max (Ar)</th>
                                    <th className="p-2.5 w-32">Taux (%)</th>
                                    <th className="p-2.5 w-16 text-center">Action</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                                {tranches.map((t, idx) => {
                                    const isLast = idx === tranches.length - 1;
                                    return (
                                        <tr key={idx} className="hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                            <td className="p-2.5 font-bold text-[var(--color-brand-500)] whitespace-nowrap">
                                                T{t.numeroTranche}
                                            </td>
                                            <td className="p-2.5">
                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="1"
                                                    required
                                                    value={t.trancheMin}
                                                    disabled={idx === 0}
                                                    onChange={(e) => handleTrancheChange(idx, 'trancheMin', e.target.value)}
                                                    className="w-full text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] px-2 py-1.5 focus:outline-none focus:border-[var(--color-brand-500)] disabled:opacity-60 disabled:cursor-not-allowed text-right font-mono"
                                                />
                                            </td>
                                            <td className="p-2.5">
                                                <input
                                                    type="number"
                                                    min={parseFloat(t.trancheMin) + 1 || 0}
                                                    step="1"
                                                    placeholder={isLast ? "Sans plafond (∞)" : "Requis"}
                                                    value={t.trancheMax}
                                                    onChange={(e) => handleTrancheChange(idx, 'trancheMax', e.target.value)}
                                                    className="w-full text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] px-2 py-1.5 focus:outline-none focus:border-[var(--color-brand-500)] text-right font-mono"
                                                />
                                            </td>
                                            <td className="p-2.5">
                                                <div className="relative">
                                                    <input
                                                        type="number"
                                                        min="0"
                                                        max="100"
                                                        step="0.01"
                                                        required
                                                        value={t.taux}
                                                        onChange={(e) => handleTrancheChange(idx, 'taux', e.target.value)}
                                                        className="w-full text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] pl-2 pr-6 py-1.5 focus:outline-none focus:border-[var(--color-brand-500)] text-right font-bold text-[var(--color-danger-500)] font-mono"
                                                    />
                                                    <Percent size={11} className="absolute right-2 top-2.5 text-[var(--color-fg-muted)] pointer-events-none" />
                                                </div>
                                            </td>
                                            <td className="p-2.5 text-center">
                                                <button
                                                    type="button"
                                                    onClick={() => handleRemoveTranche(idx)}
                                                    disabled={tranches.length <= 1}
                                                    className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
                                                    title="Supprimer cette tranche"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>
            </form>
        </Modal>
    );
}
