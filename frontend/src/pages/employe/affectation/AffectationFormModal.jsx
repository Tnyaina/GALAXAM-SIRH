import { useState, useEffect, useCallback } from 'react';
import { UserCheck, ShieldAlert, Link2, Building, Wallet, CalendarDays, StickyNote, FileWarning } from 'lucide-react';
import contratService from '@/api/employe/contratService';
import referentielService from '@/api/admin/referentielService';
import employeService from '@/api/employe/employeService';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Button from '@/components/ui/Button';

function SectionHeader({ icon: Icon, children }) {
    return (
        <h4 className="text-[11px] font-bold uppercase tracking-widest text-ink-400 dark:text-ink-400 flex items-center gap-1.5 pb-2">
            <Icon size={13} className="text-brand-500" />
            {children}
        </h4>
    );
}

export default function AffectationFormModal({ isOpen, onClose, onSave, idEmploye, affectationData = null, isSubmitting = false, onNeedContrat }) {
    const [contrats, setContrats] = useState([]);
    const [referentielsLoaded, setReferentielsLoaded] = useState(false);
    const [sites, setSites] = useState([]);
    const [departements, setDepartements] = useState([]);
    const [services, setServices] = useState([]);
    const [fonctions, setFonctions] = useState([]);
    const [employesN1, setEmployesN1] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);

    const [error, setError] = useState('');

    const emptyForm = {
        idContrat: '',
        idMotifMouvement: '',
        idSite: '',
        idDepartement: '',
        idService: '',
        idFonction: '',
        idResponsableHierarchique: '',
        idCategorieProfessionnelle: '',
        idModeCalculPaie: '',
        salaireBase: '',
        dateDebut: new Date().toISOString().split('T')[0],
        dateFin: '',
        commentaire: ''
    };

    const [formData, setFormData] = useState({ ...emptyForm });

    const loadReferentiels = useCallback(async () => {
        try {
            const [
                cList, siteList, depList, srvList, fncList, empList, carRes
            ] = await Promise.all([
                contratService.getContratsByEmploye(idEmploye),
                referentielService.getSites(true),
                referentielService.getDepartements(true),
                referentielService.getServices(true),
                referentielService.getFonctions(true),
                employeService.getEmployes({ actif: true }),
                contratService.getAllReferentielsCarriere()
            ]);

            setContrats(cList || []);
            setSites(siteList || []);
            setDepartements(depList || []);
            setServices(srvList || []);
            setFonctions(fncList || []);
            setEmployesN1((empList || []).filter(e => String(e.idEmploye) !== String(idEmploye)));

            if (carRes) {
                setMotifsMouvement(carRes.motifsMouvement || []);
                setCategoriesPro(carRes.categoriesPro || []);
                setModesPaie(carRes.modesPaie || []);
            }

            if (!affectationData) {
                setFormData(prev => ({
                    ...prev,
                    idContrat: prev.idContrat || (cList[0]?.idContrat || ''),
                    idSite: prev.idSite || (siteList[0]?.idSite || ''),
                    idDepartement: prev.idDepartement || (depList[0]?.idDepartement || ''),
                    idService: prev.idService || (srvList[0]?.idService || ''),
                    idFonction: prev.idFonction || (fncList[0]?.idFonction || ''),
                    idMotifMouvement: prev.idMotifMouvement || (carRes?.motifsMouvement?.[0]?.idMotifMouvement || ''),
                    idCategorieProfessionnelle: prev.idCategorieProfessionnelle || (carRes?.categoriesPro?.[0]?.idCategorieProfessionnelle || ''),
                    idModeCalculPaie: prev.idModeCalculPaie || (carRes?.modesPaie?.[0]?.idModeCalculPaie || '')
                }));
            }
        } catch (err) {
            console.error('Erreur chargement référentiels affectation:', err);
        } finally {
            setReferentielsLoaded(true);
        }
    }, [idEmploye, affectationData]);

    const fmtDate = (d) => {
        if (!d) return '';
        if (typeof d === 'string') {
            const s = d.trim();
            if (s.includes('T')) return s.split('T')[0];
            if (s.includes(' ')) return s.split(' ')[0];
            if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
        }
        try {
            const dateObj = new Date(d);
            if (isNaN(dateObj.getTime())) return '';
            return dateObj.toISOString().split('T')[0];
        } catch {
            return '';
        }
    };

    useEffect(() => {
        if (isOpen) {
            setReferentielsLoaded(false);
            loadReferentiels();
            if (affectationData) {
                setFormData({
                    idContrat: affectationData.idContrat || '',
                    idMotifMouvement: affectationData.idMotifMouvement || '',
                    idSite: affectationData.idSite || '',
                    idDepartement: affectationData.idDepartement || '',
                    idService: affectationData.idService || '',
                    idFonction: affectationData.idFonction || '',
                    idResponsableHierarchique: affectationData.idResponsableHierarchique || '',
                    idCategorieProfessionnelle: affectationData.idCategorieProfessionnelle || '',
                    idModeCalculPaie: affectationData.idModeCalculPaie || '',
                    salaireBase: affectationData.salaireBase !== undefined ? affectationData.salaireBase : '',
                    dateDebut: fmtDate(affectationData.dateDebut),
                    dateFin: fmtDate(affectationData.dateFin),
                    commentaire: affectationData.commentaire || ''
                });
            } else {
                setFormData({ ...emptyForm });
            }
            setError('');
        }
    }, [isOpen, affectationData, loadReferentiels]);

    const bloqueParAbsenceContrat = referentielsLoaded && !affectationData && contrats.length === 0;

    const filteredServices = formData.idDepartement
        ? services.filter(s => String(s.idDepartement) === String(formData.idDepartement))
        : services;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!formData.idContrat) {
            setError('Veuillez rattacher cette affectation à un contrat de travail.');
            return;
        }
        if (!formData.idMotifMouvement) {
            setError('Veuillez sélectionner le motif de mouvement (Embauche, Promotion, Mutation...).');
            return;
        }
        if (!formData.idSite) {
            setError('Veuillez sélectionner le site.');
            return;
        }
        if (!formData.idDepartement) {
            setError('Veuillez sélectionner le département.');
            return;
        }
        if (!formData.idService) {
            setError('Veuillez sélectionner le service.');
            return;
        }
        if (!formData.idFonction) {
            setError('Veuillez sélectionner la fonction / poste.');
            return;
        }
        if (!formData.idCategorieProfessionnelle) {
            setError('Veuillez sélectionner la catégorie professionnelle.');
            return;
        }
        if (!formData.idModeCalculPaie) {
            setError('Veuillez sélectionner le mode de calcul de paie.');
            return;
        }
        if (!formData.salaireBase || parseFloat(formData.salaireBase) < 0) {
            setError('Veuillez saisir un salaire de base valide.');
            return;
        }
        if (!formData.dateDebut) {
            setError("La date de début d'affectation est obligatoire.");
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
                    <UserCheck size={20} className="text-brand-500" />
                    {affectationData ? "Modifier l'Affectation / Posture" : 'Nouvelle Affectation / Mouvement de Carrière'}
                </div>

                {bloqueParAbsenceContrat ? (
                    <div className="p-6 space-y-4">
                        <div className="bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 p-4 flex items-start gap-3">
                            <FileWarning size={18} className="text-amber-600 flex-shrink-0 mt-0.5" />
                            <div>
                                <p className="text-sm font-bold text-amber-800 dark:text-amber-400">Aucun contrat de travail actif</p>
                                <p className="text-xs text-amber-700 dark:text-amber-500 mt-1">
                                    Une affectation doit obligatoirement être rattachée à un contrat de travail.
                                    Établissez d'abord le contrat de cet employé, puis revenez créer son affectation.
                                </p>
                            </div>
                        </div>
                        <div className="flex justify-end gap-3 pt-2 border-t border-ink-150 dark:border-ink-700">
                            <Button type="button" onClick={onClose} variant="ghost" size="sm">
                                Annuler
                            </Button>
                            <Button
                                type="button"
                                onClick={() => { onClose(); onNeedContrat?.(); }}
                                variant="primary"
                                size="sm"
                            >
                                Établir le Contrat Maintenant
                            </Button>
                        </div>
                    </div>
                ) : (
                <form onSubmit={handleSubmit}>
                    <div className="p-6 space-y-4 max-h-[75vh] overflow-y-auto">
                        {error && (
                            <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                <ShieldAlert size={14} />
                                {error}
                            </div>
                        )}

                        <div>
                        <SectionHeader icon={Link2}>Rattachement</SectionHeader>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Select
                                label="Contrat de Travail Associé"
                                required
                                value={formData.idContrat}
                                onChange={e => setFormData({ ...formData, idContrat: e.target.value })}
                            >
                                <option value="">-- Sélectionner un contrat --</option>
                                {contrats.map(c => (
                                    <option key={c.idContrat} value={c.idContrat}>
                                        {c.typeContratLibelle} ({c.filialeRaisonSociale}) — {c.numeroContrat || `N°${c.idContrat}`}
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Motif du Mouvement / Affectation"
                                required
                                value={formData.idMotifMouvement}
                                onChange={e => setFormData({ ...formData, idMotifMouvement: e.target.value })}
                            >
                                <option value="">-- Sélectionner le motif --</option>
                                {motifsMouvement.map(m => (
                                    <option key={m.idMotifMouvement} value={m.idMotifMouvement}>
                                        {m.libelle}
                                    </option>
                                ))}
                            </Select>
                        </div>
                        </div>

                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700">
                        <SectionHeader icon={Building}>Affectation Organisationnelle</SectionHeader>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Select
                                label="Site d'Affectation"
                                required
                                value={formData.idSite}
                                onChange={e => setFormData({ ...formData, idSite: e.target.value })}
                            >
                                <option value="">-- Sélectionner un site --</option>
                                {sites.map(s => (
                                    <option key={s.idSite} value={s.idSite}>
                                        {s.libelle} ({s.codeSite})
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Département"
                                required
                                value={formData.idDepartement}
                                onChange={e => setFormData({ ...formData, idDepartement: e.target.value, idService: '' })}
                            >
                                <option value="">-- Sélectionner un département --</option>
                                {departements.map(d => (
                                    <option key={d.idDepartement} value={d.idDepartement}>
                                        {d.libelle} ({d.codeDepartement})
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Service"
                                required
                                value={formData.idService}
                                onChange={e => setFormData({ ...formData, idService: e.target.value })}
                            >
                                <option value="">-- Sélectionner un service --</option>
                                {filteredServices.map(s => (
                                    <option key={s.idService} value={s.idService}>
                                        {s.libelle} ({s.codeService})
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Fonction / Poste Occupé"
                                required
                                value={formData.idFonction}
                                onChange={e => setFormData({ ...formData, idFonction: e.target.value })}
                            >
                                <option value="">-- Sélectionner la fonction --</option>
                                {fonctions.map(f => (
                                    <option key={f.idFonction} value={f.idFonction}>
                                        {f.libelle} ({f.codeFonction})
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Responsable Hiérarchique (N+1)"
                                value={formData.idResponsableHierarchique}
                                onChange={e => setFormData({ ...formData, idResponsableHierarchique: e.target.value })}
                            >
                                <option value="">-- Aucun (Supérieur Direct) --</option>
                                {employesN1.map(e => (
                                    <option key={e.idEmploye} value={e.idEmploye}>
                                        {e.nom} {e.prenom} ({e.matricule})
                                    </option>
                                ))}
                            </Select>
                        </div>
                        </div>

                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700">
                        <SectionHeader icon={Wallet}>Rémunération & Catégorie</SectionHeader>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Select
                                label="Catégorie Professionnelle"
                                required
                                value={formData.idCategorieProfessionnelle}
                                onChange={e => setFormData({ ...formData, idCategorieProfessionnelle: e.target.value })}
                            >
                                <option value="">-- Sélectionner la catégorie --</option>
                                {categoriesPro.map(cp => (
                                    <option key={cp.idCategorieProfessionnelle} value={cp.idCategorieProfessionnelle}>
                                        {cp.libelle} ({cp.code})
                                    </option>
                                ))}
                            </Select>

                            <Select
                                label="Mode de Calcul de la Paie"
                                required
                                value={formData.idModeCalculPaie}
                                onChange={e => setFormData({ ...formData, idModeCalculPaie: e.target.value })}
                            >
                                <option value="">-- Sélectionner --</option>
                                {modesPaie.map(mp => (
                                    <option key={mp.idModeCalculPaie} value={mp.idModeCalculPaie}>
                                        {mp.libelle}
                                    </option>
                                ))}
                            </Select>

                            <Input
                                label="Salaire de Base Brut (MGA)"
                                type="number"
                                required
                                value={formData.salaireBase}
                                onChange={e => setFormData({ ...formData, salaireBase: e.target.value })}
                                placeholder="Ex: 850000"
                            />
                        </div>
                        </div>

                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700">
                        <SectionHeader icon={CalendarDays}>Période</SectionHeader>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <Input
                                label="Date de Début d'Affectation"
                                type="date"
                                required
                                value={formData.dateDebut}
                                onChange={e => setFormData({ ...formData, dateDebut: e.target.value })}
                            />

                            <Input
                                label="Date de Fin (Optionnel)"
                                type="date"
                                value={formData.dateFin}
                                onChange={e => setFormData({ ...formData, dateFin: e.target.value })}
                                hint="Laisser vide si affectation en cours"
                            />
                        </div>
                        </div>

                        <div className="pt-4 border-t border-ink-150 dark:border-ink-700">
                        <SectionHeader icon={StickyNote}>Notes</SectionHeader>
                        <Textarea
                            label="Commentaire / Notes d'Avenant"
                            rows={2}
                            value={formData.commentaire}
                            onChange={e => setFormData({ ...formData, commentaire: e.target.value })}
                            placeholder="Détails sur la promotion, mutation ou motif de révision salariale..."
                        />
                        </div>
                    </div>

                    {/* Pied de modale */}
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={isSubmitting}>
                            {affectationData ? 'Enregistrer les Modifications' : "Enregistrer l'Affectation"}
                        </Button>
                    </div>
                </form>
                )}
            </div>
        </div>
    );
}