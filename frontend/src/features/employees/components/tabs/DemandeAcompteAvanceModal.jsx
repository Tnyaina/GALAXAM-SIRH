import { useEffect, useState } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import paieService from '@/features/payroll/api/paieService';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import { getErrorMessage } from '@/api/apiClient';

export default function DemandeAcompteAvanceModal({ idEmploye, onClose, onCreated }) {
    const { periodes, context, selectedPeriodeId, selectPeriode, loading: loadingPeriod, error: periodError } = usePayrollCampaign();
    const [types, setTypes] = useState([]);
    const [typeCode, setTypeCode] = useState('');
    const [montant, setMontant] = useState('');
    const [motif, setMotif] = useState('');
    const [loadingTypes, setLoadingTypes] = useState(true);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);
    const [typesError, setTypesError] = useState(null);

    useEffect(() => {
        let active = true;
        setTypesError(null);
        paieService.listerTypesMouvements(selectedPeriodeId).then(result => {
            if (!active) return;
            const options = (Array.isArray(result) ? result : []).filter(item => ['ACOMPTE', 'AVANCE'].includes(item.categorie));
            setTypes(options);
            setTypeCode(current => options.some(item => item.code === current) ? current : '');
            setLoadingTypes(false);
        }).catch(err => { if (active) { setTypesError(getErrorMessage(err, 'Types de demande indisponibles.')); setLoadingTypes(false); } });
        return () => { active = false; };
    }, [selectedPeriodeId]);

    const canSubmit = context?.availableActions?.ADD_VARIABLE?.allowed === true
        && !loadingPeriod && !loadingTypes && !submitting && Boolean(selectedPeriodeId)
        && Boolean(typeCode) && Number(montant) > 0 && Boolean(motif.trim());
    const periodOptions = periodes.map(period => {
        const id = period.idPeriodePaie || period.id_periode_paie || period.id;
        return { value: String(id), label: [period.code, period.libelle].filter(Boolean).join(' — ') || String(id), sublabel: period.statutCode || period.statut || period.state || '' };
    }).filter(period => period.value && period.value !== 'undefined');
    const selectedType = types.find(item => item.code === typeCode);
    const submit = async event => {
        event.preventDefault();
        if (!canSubmit) return;
        setSubmitting(true); setError(null);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(idEmploye), idPeriodePaie: Number(selectedPeriodeId),
                typeMouvement: typeCode, montant: Number(montant), motif: motif.trim(),
            });
            onCreated?.();
            onClose();
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible d’enregistrer la demande.'));
        } finally { setSubmitting(false); }
    };

    return <Modal isOpen onClose={onClose} title="Demander un acompte ou une avance" size="lg">
        <form onSubmit={submit} className="space-y-4">
            <p className="text-xs text-[var(--color-fg-muted)]">La demande sera enregistrée en attente d’approbation dans la campagne de paie sélectionnée.</p>
            {error && <FormError message={error} />}
            {periodError && <Alert type="error" message={periodError} />}
            <SearchSelect label="Période de paie" required value={selectedPeriodeId || ''}
                onChange={value => value && selectPeriode(Number(value))} options={periodOptions}
                placeholder={loadingPeriod ? 'Chargement des périodes…' : 'Rechercher une période…'}
                disabled={loadingPeriod || periodOptions.length === 0} />
            {!loadingPeriod && !periodError && periodOptions.length === 0 &&
                <Alert type="warning" message="Aucune période de paie disponible. Créez ou ouvrez une période dans le module Paie avant de saisir une demande." />}
            <Select label="Nature de la demande" value={typeCode} onChange={setTypeCode}
                options={types.map(item => ({ value: item.code, label: item.libelle }))}
                placeholder={loadingTypes ? 'Chargement des types…' : 'Choisir un type de demande…'} />
            {typesError && <Alert type="error" message={typesError} />}
            {selectedType?.description && <p className="text-xs text-[var(--color-fg-muted)]">{selectedType.description}</p>}
            <MoneyInput label="Montant (Ar)" min="0.01" required placeholder="Ex. 50000" value={montant} onValueChange={setMontant} />
            <Input label="Motif" required placeholder="Ex. Dépense imprévue à couvrir" value={motif} onChange={event => setMotif(event.target.value)} />
            {!loadingPeriod && selectedPeriodeId && !context?.availableActions?.ADD_VARIABLE?.allowed &&
                <Alert type="warning" message="Les demandes ne sont pas ouvertes pour cette période. Sélectionnez une période autorisée." />}
            <div className="flex justify-end gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                <Button type="button" variant="outline" size="sm" onClick={onClose}>Annuler</Button>
                <Button type="submit" variant="primary" size="sm" disabled={!canSubmit}>Enregistrer la demande</Button>
            </div>
        </form>
    </Modal>;
}
