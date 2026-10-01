import { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/api/apiClient';
import paieService from '@/features/payroll/api/paieService';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';

const initial = {
    code: '', libelle: '', categorie: 'PRIME', sens: 'GAIN', typeValeur: 'MONTANT',
    soumisIrsa: true, soumisCnaps: true, soumisSante: true,
    referenceReglementaire: '', dateDebutEffet: '', description: '',
    estFixe: false, estActif: true,
    etapeCalcul: 'BRUT', dateFinEffet: '', conditionApplication: 'TOUS',
};

const categories = [
    { value: 'PRIME', label: 'Prime' },
    { value: 'INDEMNITE_IMPOSABLE', label: 'Indemnité imposable' },
    { value: 'INDEMNITE_NON_IMPOSABLE', label: 'Indemnité hors assiettes' },
    { value: 'RETENUE_DIVERSE', label: 'Retenue sur le net' },
];

export default function PayrollRubriquesConfig() {
    const { hasPermission } = useAuth();
    const canEdit = hasPermission('ROLE_ADMIN');
    const [rubriques, setRubriques] = useState([]);
    const [form, setForm] = useState(initial);
    const [editingId, setEditingId] = useState(null);
    const [versionSource, setVersionSource] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');

    const refresh = async () => {
        const data = await paieService.getCatalogueVariables();
        setRubriques(Array.isArray(data?.rubriques) ? data.rubriques : []);
    };
    useEffect(() => {
        refresh().catch(cause => setError(getErrorMessage(cause, 'Catalogue indisponible.')))
            .finally(() => setLoading(false));
    }, []);

    const manualRubriques = useMemo(() => rubriques.filter(r =>
        !r.estFixe && ['GAIN', 'RETENUE'].includes(r.sens)
        && ['PRIME', 'INDEMNITE_IMPOSABLE', 'INDEMNITE_NON_IMPOSABLE', 'RETENUE_DIVERSE'].includes(r.categorie)
    ), [rubriques]);

    const setField = (name, value) => setForm(previous => ({ ...previous, [name]: value }));
    const setCategory = (category) => {
        const retenue = category === 'RETENUE_DIVERSE';
        const exonerated = retenue || category === 'INDEMNITE_NON_IMPOSABLE';
        setForm(previous => ({ ...previous, categorie: category, sens: retenue ? 'RETENUE' : 'GAIN',
            soumisIrsa: !exonerated, soumisCnaps: !exonerated, soumisSante: !exonerated,
            etapeCalcul: retenue ? 'RETENUE_NET' : exonerated ? 'GAIN_NET' : 'BRUT' }));
    };

    const submit = async event => {
        event.preventDefault();
        setError(''); setSuccess('');
        if (!form.code.trim() || !form.libelle.trim() || !form.referenceReglementaire.trim() || (!editingId && !form.dateDebutEffet)) {
            setError('Renseignez le code, le libellé, la référence du traitement et sa date d’effet.');
            return;
        }
        setSaving(true);
        try {
            const payload = { ...form, dateDebutEffet: form.dateDebutEffet || null, dateFinEffet: form.dateFinEffet || null, code: form.code.trim().toUpperCase(),
                libelle: form.libelle.trim(), referenceReglementaire: form.referenceReglementaire.trim() };
            if (versionSource) await paieService.creerVersionRubrique(versionSource, payload);
            else if (editingId) await paieService.modifierRubriqueVariable(editingId, payload);
            else await paieService.creerRubriqueVariable(payload);
            await refresh();
            setForm(initial);
            setEditingId(null); setVersionSource(null);
            setSuccess(versionSource ? 'Nouvelle version créée. Les types liés utiliseront la version applicable à la période.' : editingId ? 'Rubrique mise à jour.' : 'Rubrique créée. Vous pouvez maintenant l’associer à un type de variable.');
        } catch (cause) {
            setError(getErrorMessage(cause, 'Création impossible.'));
        } finally { setSaving(false); }
    };

    return <div className="space-y-5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5">
        <div>
            <h3 className="font-bold text-base">Rubriques manuelles et assiettes</h3>
            <p className="text-sm text-[var(--color-fg-muted)] mt-1">Définissez le traitement d’un gain ou d’une retenue, puis associez la rubrique à un type de variable dans l’onglet voisin.</p>
        </div>
        <Alert type="info" message="Choisissez d’abord où le montant intervient, puis les assiettes qu’il affecte. Un gain dans le brut peut être exclu de l’IRSA ou des cotisations. Un ajout au net après impôt est hors assiettes. Les assiettes CNaPS et santé se règlent séparément. Le FMFP conserve le traitement actuel lié à l’assiette CNaPS." />
        {error && <Alert type="error" message={error} />}
        {success && <Alert type="success" message={success} onClose={() => setSuccess('')} />}
        {canEdit && <form onSubmit={submit} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4 space-y-4">
            <div className="flex items-center justify-between"><h4 className="font-semibold">{versionSource ? 'Créer une nouvelle version' : editingId ? 'Modifier une rubrique manuelle' : 'Créer une rubrique manuelle'}</h4>
                {(editingId || versionSource) && <Button type="button" variant="ghost" onClick={() => { setEditingId(null); setVersionSource(null); setForm(initial); setError(''); }}>Annuler la modification</Button>}
            </div>
            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                <Input label="Code *" value={form.code} onChange={e => setField('code', e.target.value)} placeholder="PRIME_MISSION" />
                <Input label="Libellé *" value={form.libelle} onChange={e => setField('libelle', e.target.value)} placeholder="Prime de mission" />
                <Select label="Nature" disabled={Boolean(versionSource)} value={form.categorie} onChange={setCategory} options={categories} />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <Select label="Étape d’intervention" value={form.etapeCalcul} onChange={value => setForm(previous => ({
                    ...previous, etapeCalcul: value,
                    ...(value === 'GAIN_NET' ? { soumisIrsa: false, soumisCnaps: false, soumisSante: false } : {}),
                }))} options={form.sens === 'RETENUE'
                    ? [{ value: 'RETENUE_NET', label: 'Retenue sur le net après impôt' }]
                    : [{ value: 'BRUT', label: 'Dans le brut, avant cotisations et impôt' },
                        { value: 'GAIN_NET', label: 'Ajout au net après impôt' }]} />
                <div className="text-sm text-[var(--color-fg-muted)] self-end py-2">Méthode : montant saisi en ariary, conservé à deux décimales.</div>
            </div>
            {form.sens === 'GAIN' && form.etapeCalcul === 'BRUT' && <div className="flex flex-wrap gap-5 text-sm">
                <label className="flex items-center gap-2"><input type="checkbox" checked={form.soumisIrsa} onChange={e => setField('soumisIrsa', e.target.checked)} /> Inclus dans l’assiette IRSA</label>
                <label className="flex items-center gap-2"><input type="checkbox" checked={form.soumisCnaps} onChange={e => setForm(previous => ({ ...previous, soumisCnaps: e.target.checked }))} /> Inclus dans l’assiette CNaPS</label>
                <label className="flex items-center gap-2"><input type="checkbox" checked={form.soumisSante} onChange={e => setForm(previous => ({ ...previous, soumisSante: e.target.checked }))} /> Inclus dans l’assiette santé</label>
            </div>}
            {form.etapeCalcul === 'GAIN_NET' && <p className="text-xs text-[var(--color-fg-muted)]">Ce montant est ajouté après le calcul de l’impôt et ne modifie aucune assiette.</p>}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <Input label="Référence du traitement *" value={form.referenceReglementaire} onChange={e => setField('referenceReglementaire', e.target.value)} placeholder="Texte, accord ou décision interne" />
                <Input label="Applicable à partir du *" type="date" value={form.dateDebutEffet} onChange={e => setField('dateDebutEffet', e.target.value)} />
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <Input label="Applicable jusqu’au (facultatif)" type="date" value={form.dateFinEffet} onChange={e => setField('dateFinEffet', e.target.value)} />
                <Select label="Condition d’application" value={form.conditionApplication} onChange={value => setField('conditionApplication', value)} options={[
                    { value: 'TOUS', label: 'Tous les salariés' },
                    { value: 'ASSUJETTI_IRSA', label: 'Salarié assujetti à l’IRSA' },
                    { value: 'ASSUJETTI_CNAPS', label: 'Salarié assujetti à la CNaPS' },
                    { value: 'ASSUJETTI_SANTE', label: 'Salarié assujetti à la santé' },
                ]} />
            </div>
            <p className="text-xs text-[var(--color-fg-muted)]">Une règle doit couvrir toute la période. Un mouvement approuvé incompatible bloque le calcul et doit être corrigé. Une nouvelle version conserve la famille métier et ferme la précédente à la veille de sa date d’effet.</p>
            <Textarea label="Description et consignes" value={form.description} onChange={e => setField('description', e.target.value)} />
            <div className="flex justify-end"><Button type="submit" variant="primary" disabled={saving}>{saving ? 'Enregistrement…' : versionSource ? 'Créer la version' : editingId ? 'Enregistrer la rubrique' : 'Créer la rubrique'}</Button></div>
        </form>}
        <div className="space-y-2">
            <h4 className="font-semibold text-sm">Rubriques disponibles</h4>
            {loading ? <p className="text-sm">Chargement…</p> : manualRubriques.map(r =>
                <div key={r.id} className="border border-[var(--color-border-subtle)] p-3 grid grid-cols-1 md:grid-cols-[1fr_auto] gap-2 text-sm">
                    <div><strong>{r.libelle}</strong> <span className="font-mono text-xs text-[var(--color-fg-muted)]">{r.code}</span>
                        <p className="text-xs text-[var(--color-fg-muted)]">{r.referenceReglementaire || 'Référence historique non renseignée'} · {r.dateDebutEffet || 'Début historique'} → {r.dateFinEffet || 'Sans fin'} · {r.conditionApplication || 'TOUS'}</p></div>
                    <div className="text-xs flex items-center gap-3">{r.etapeCalcul === 'BRUT' ? 'Dans le brut' : r.etapeCalcul === 'GAIN_NET' ? 'Ajout au net' : 'Retenue nette'} · IRSA {r.soumisIrsa ? 'oui' : 'non'} · CNaPS {r.soumisCnaps ? 'oui' : 'non'} · Santé {r.soumisSante ? 'oui' : 'non'}
                        {canEdit && <Button type="button" variant="ghost" size="sm" onClick={() => {
                            setVersionSource(null); setEditingId(r.id); setError(''); setSuccess('');
                            setForm({ ...initial, code: r.code, libelle: r.libelle, categorie: r.categorie,
                                sens: r.sens, soumisIrsa: r.soumisIrsa, soumisCnaps: r.soumisCnaps,
                                etapeCalcul: r.etapeCalcul || (r.sens === 'RETENUE' ? 'RETENUE_NET' : r.soumisIrsa ? 'BRUT' : 'GAIN_NET'),
                                soumisSante: r.soumisSante, description: r.description || '',
                                referenceReglementaire: r.referenceReglementaire || '',
                                dateDebutEffet: r.dateDebutEffet || '', dateFinEffet: r.dateFinEffet || '', conditionApplication: r.conditionApplication || 'TOUS', estActif: r.estActif });
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        }}>Modifier</Button>}
                        {canEdit && !r.dateFinEffet && <Button type="button" variant="ghost" size="sm" onClick={() => {
                            setEditingId(null); setVersionSource(r.id); setError(''); setSuccess('');
                            setForm({ ...initial, ...r, code: '', dateDebutEffet: '', dateFinEffet: '', idRubriqueOrigine: undefined });
                            window.scrollTo({ top: 0, behavior: 'smooth' });
                        }}>Nouvelle version</Button>}
                    </div>
                </div>
            )}
        </div>
    </div>;
}
