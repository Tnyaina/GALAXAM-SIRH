import { useState, useEffect, useCallback } from 'react';
import { Briefcase, ShieldAlert, IdCard, CalendarDays, Flag } from 'lucide-react';
import contratService from '@/api/contratService';
import referentielService from '@/api/referentielService';
import Input, { Select, Checkbox } from '@/components/ui/Input';
import Button from '@/components/ui/Button';

function SectionHeader({ icon: Icon, children }) {
    return (
        <h4 className="text-[11px] font-bold uppercase tracking-widest text-ink-400 dark:text-ink-400 flex items-center gap-1.5 pb-2">
            <Icon size={13} className="text-brand-500" />
            {children}
        </h4>
    );
}

export default function ContratFormModal({ isOpen, onClose, onSave, idEmploye: _idEmploye, contratData = null, isSubmitting = false }) {
    const [filiales, setFiliales] = useState([]);
    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [motifsSortie, setMotifsSortie] = useState([]);

    const [error, setError] = useState('');

    const emptyForm = {
        idFiliale: '',
        idTypeContrat: '',
        idTypeIntervenant: '',
        numeroContrat: '',
        dateSignature: '',
        dateDebut: new Date().toISOString().split('T')[0],
        dateFin: '',
        dateFinEssai: '',
        essaiRenouvele: false,
        comptabiliseEffectif: true,
        estActif: true,
        idMotifSortie: ''
    };

    const [formData, setFormData] = useState({ ...emptyForm });

    const loadReferentiels = useCallback(async () => {
        try {
            const [filRes, tcRes, tiRes, msRes] = await Promise.all([
                referentielService.getFiliales(true),
                referentielService.getTypesContrat(true),
                referentielService.getTypesIntervenant(true),
                contratService.getMotifsSortie()
            ]);
            setFiliales(filRes || []);
            setTypesContrat(tcRes || []);
            setTypesIntervenant(tiRes || []);
            setMotifsSortie(msRes || []);

            // Définir valeurs par défaut si formulaire vide
            if (!contratData) {
                setFormData(prev => ({
                    ...prev,
                    idFiliale: prev.idFiliale || (filRes[0]?.idFiliale || ''),
                    idTypeContrat: prev.idTypeContrat || (tcRes[0]?.idTypeContrat || ''),
                    idTypeIntervenant: prev.idTypeIntervenant || (tiRes[0]?.idTypeIntervenant || '')
                }));
            }
        } catch (err) {
            console.error('Erreur chargement référentiels contrat:', err);
        }
    }, [contratData]);

    useEffect(() => {
        if (isOpen) {
            loadReferentiels();
            if (contratData) {
                setFormData({
                    idFiliale: contratData.idFiliale || '',
                    idTypeContrat: contratData.idTypeContrat || '',
                    idTypeIntervenant: contratData.idTypeIntervenant || '',
                    numeroContrat: contratData.numeroContrat || '',
                    dateSignature: contratData.dateSignature || '',
                    dateDebut: contratData.dateDebut || '',
                    dateFin: contratData.dateFin || '',
                    dateFinEssai: contratData.dateFinEssai || '',
                    essaiRenouvele: !!contratData.essaiRenouvele,
                    comptabiliseEffectif: contratData.comptabiliseEffectif !== undefined ? !!contratData.comptabiliseEffectif : true,
                    estActif: contratData.estActif !== undefined ? !!contratData.estActif : true,
                    idMotifSortie: contratData.idMotifSortie || ''
                });
            } else {
                setFormData({ ...emptyForm });
            }
            setError('');
        }
    }, [isOpen, contratData, loadReferentiels]);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!formData.idFiliale) {
            setError('Veuillez sélectionner la filiale de rattachement.');
            return;
        }
        if (!formData.idTypeContrat) {
            setError('Veuillez sélectionner le type de contrat.');
            return;
        }
        if (!formData.idTypeIntervenant) {
            setError("Veuillez sélectionner le type d'intervenant.");
            return;
        }
        if (!formData.dateDebut) {
            setError('La date de début de contrat est obligatoire.');
            return;
        }

        onSave(formData);
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-2xl w-full my-8 overflow-hidden">
                {/* En-tête */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                    <Briefcase size={20} className="text-brand-500" />
                    {contratData ? 'Modifier le Contrat de Travail' : 'Établir un Nouveau Contrat de Travail'}
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
                        {error && (
                            <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                <ShieldAlert size={14} />
                                {error}
                            </div>
                        )}

                        {/* Section 1 : Identification */}
                        <div>
                            <SectionHeader icon={IdCard}>Identification du Contrat</SectionHeader>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Select
                                    label="Filiale de Rattachement"
                                    required
                                    value={formData.idFiliale}
                                    onChange={e => setFormData({ ...formData, idFiliale: e.target.value })}
                                >
                                    <option value="">-- Sélectionner une filiale --</option>
                                    {filiales.map(f => (
                                        <option key={f.idFiliale} value={f.idFiliale}>
                                            {f.raisonSociale} ({f.codeFiliale})
                                        </option>
                                    ))}
                                </Select>

                                <Select
                                    label="Type de Contrat"
                                    required
                                    value={formData.idTypeContrat}
                                    onChange={e => setFormData({ ...formData, idTypeContrat: e.target.value })}
                                >
                                    <option value="">-- Sélectionner un type --</option>
                                    {typesContrat.map(tc => (
                                        <option key={tc.idTypeContrat} value={tc.idTypeContrat}>
                                            {tc.libelle} ({tc.code})
                                        </option>
                                    ))}
                                </Select>

                                <Select
                                    label="Type d'Intervenant"
                                    required
                                    value={formData.idTypeIntervenant}
                                    onChange={e => setFormData({ ...formData, idTypeIntervenant: e.target.value })}
                                >
                                    <option value="">-- Sélectionner --</option>
                                    {typesIntervenant.map(ti => (
                                        <option key={ti.idTypeIntervenant} value={ti.idTypeIntervenant}>
                                            {ti.libelle}
                                        </option>
                                    ))}
                                </Select>

                                <Input
                                    label="Numéro de Contrat / Réf"
                                    value={formData.numeroContrat}
                                    onChange={e => setFormData({ ...formData, numeroContrat: e.target.value })}
                                    placeholder="Ex: CT-2026-0045"
                                />
                            </div>
                        </div>

                        {/* Section 2 : Dates clés */}
                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700">
                            <SectionHeader icon={CalendarDays}>Dates Clés</SectionHeader>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Input
                                    label="Date de Signature"
                                    type="date"
                                    value={formData.dateSignature}
                                    onChange={e => setFormData({ ...formData, dateSignature: e.target.value })}
                                />

                                <Input
                                    label="Date de Début (Prise d'effet)"
                                    type="date"
                                    required
                                    value={formData.dateDebut}
                                    onChange={e => setFormData({ ...formData, dateDebut: e.target.value })}
                                />

                                <Input
                                    label="Date de Fin (Si CDD / Essai)"
                                    type="date"
                                    value={formData.dateFin}
                                    onChange={e => setFormData({ ...formData, dateFin: e.target.value })}
                                />

                                <Input
                                    label="Date Fin Période d'Essai"
                                    type="date"
                                    value={formData.dateFinEssai}
                                    onChange={e => setFormData({ ...formData, dateFinEssai: e.target.value })}
                                />
                            </div>
                        </div>

                        {/* Section 3 : Statut & sortie */}
                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700 space-y-3">
                            <SectionHeader icon={Flag}>Statut & Sortie</SectionHeader>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                <Checkbox
                                    label="Période d'essai renouvelée"
                                    checked={formData.essaiRenouvele}
                                    onChange={e => setFormData({ ...formData, essaiRenouvele: e.target.checked })}
                                />
                                <Checkbox
                                    label="Comptabilisé dans l'effectif"
                                    checked={formData.comptabiliseEffectif}
                                    onChange={e => setFormData({ ...formData, comptabiliseEffectif: e.target.checked })}
                                />
                                <Checkbox
                                    label="Contrat Actif"
                                    checked={formData.estActif}
                                    onChange={e => setFormData({ ...formData, estActif: e.target.checked })}
                                />
                            </div>

                            {!formData.estActif && (
                                <Select
                                    label="Motif de Fin de Contrat / Sortie"
                                    value={formData.idMotifSortie}
                                    onChange={e => setFormData({ ...formData, idMotifSortie: e.target.value })}
                                >
                                    <option value="">-- Aucun motif spécifié --</option>
                                    {motifsSortie.map(ms => (
                                        <option key={ms.idMotifSortie} value={ms.idMotifSortie}>
                                            {ms.libelle}
                                        </option>
                                    ))}
                                </Select>
                            )}
                        </div>
                    </div>

                    {/* Pied de modale */}
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={isSubmitting}>
                            {contratData ? 'Enregistrer les Modifications' : 'Créer le Contrat'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}