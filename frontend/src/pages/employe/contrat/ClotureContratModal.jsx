import { useState, useEffect } from 'react';
import { ShieldAlert, XCircle } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Select } from '@/components/ui/Input';
import Alert from '@/components/ui/Alert';
import contratService from '@/api/contratService';

export default function ClotureContratModal({
    isOpen,
    onClose,
    onSuccess,
    contratActif
}) {
    const [motifsSortie, setMotifsSortie] = useState([]);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const [formData, setFormData] = useState({
        dateFin: new Date().toISOString().split('T')[0],
        idMotifSortie: ''
    });

    useEffect(() => {
        if (!isOpen) return;
        setFormData({
            dateFin: new Date().toISOString().split('T')[0],
            idMotifSortie: ''
        });
        setError('');

        const loadMotifs = async () => {
            setLoading(true);
            try {
                const list = await contratService.getMotifsSortie();
                setMotifsSortie(list || []);
                if (list && list.length > 0) {
                    setFormData(prev => ({ ...prev, idMotifSortie: list[0].idMotifSortie }));
                }
            } catch (err) {
                console.error('Erreur chargement motifs de sortie:', err);
                setError('Impossible de charger la liste des motifs de sortie.');
            } finally {
                setLoading(false);
            }
        };

        loadMotifs();
    }, [isOpen]);

    if (!isOpen || !contratActif) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!formData.dateFin) {
            setError('Veuillez indiquer la date d’échéance ou de rupture effective du contrat.');
            return;
        }
        if (!formData.idMotifSortie) {
            setError('Veuillez sélectionner le motif de sortie du contrat.');
            return;
        }

        setSubmitting(true);
        try {
            const payload = {
                idFiliale: contratActif.idFiliale,
                idTypeContrat: contratActif.idTypeContrat,
                idTypeIntervenant: contratActif.idTypeIntervenant,
                numeroContrat: contratActif.numeroContrat,
                dateSignature: contratActif.dateSignature,
                dateDebut: contratActif.dateDebut,
                dateFin: formData.dateFin,
                dateFinEssai: contratActif.dateFinEssai,
                essaiRenouvele: contratActif.essaiRenouvele,
                comptabiliseEffectif: false,
                estActif: false,
                idMotifSortie: Number(formData.idMotifSortie)
            };

            await contratService.updateContrat(contratActif.idContrat, payload);
            if (typeof onSuccess === 'function') onSuccess();
            onClose();
        } catch (err) {
            console.error('Erreur clôture du contrat:', err);
            setError(err.response?.data || 'Erreur lors de la clôture du contrat de travail.');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-danger-500/5 flex items-center justify-between">
                    <div className="flex items-center gap-2 text-danger-600 dark:text-danger-400 font-bold text-base">
                        <XCircle size={20} />
                        Clôturer le Contrat de Travail
                    </div>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-600 dark:hover:text-white cursor-pointer">✕</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="p-6 space-y-4">
                        {error && (
                            <Alert type="error" onClose={() => setError('')}>
                                {error}
                            </Alert>
                        )}

                        <div className="p-3 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-800 space-y-1">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Contrat Sélectionné</p>
                            <p className="text-xs font-bold text-ink-900 dark:text-white">
                                {contratActif.typeContratLibelle} — {contratActif.numeroContrat || `N°${contratActif.idContrat}`}
                            </p>
                            <p className="text-[11px] text-ink-400">
                                En vigueur depuis le {contratActif.dateDebut} ({contratActif.filialeRaisonSociale})
                            </p>
                        </div>

                        <Input
                            label="Date de Fin Effective / Clôture"
                            type="date"
                            required
                            value={formData.dateFin}
                            onChange={e => setFormData({ ...formData, dateFin: e.target.value })}
                        />

                        <Select
                            label="Motif de Sortie / Fin de Contrat"
                            required
                            disabled={loading}
                            value={formData.idMotifSortie}
                            onChange={e => setFormData({ ...formData, idMotifSortie: e.target.value })}
                        >
                            <option value="">-- Sélectionner un motif --</option>
                            {motifsSortie.map(m => (
                                <option key={m.idMotifSortie} value={m.idMotifSortie}>
                                    {m.libelle} ({m.code})
                                </option>
                            ))}
                        </Select>

                        <div className="p-3 bg-warning-500/10 border border-warning-500/20 text-xs text-warning-700 dark:text-warning-300 flex items-start gap-2">
                            <ShieldAlert size={16} className="shrink-0 mt-0.5" />
                            <p>Cette action fermera le contrat actif actuel. Pour lui attribuer un nouveau contrat ou poste, utilisez ensuite l'Assistant Évolution de Carrière.</p>
                        </div>
                    </div>

                    <div className="px-6 py-3 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                        <Button type="button" variant="ghost" size="sm" onClick={onClose} disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" variant="danger" size="sm" loading={submitting}>
                            Confirmer la Clôture
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
