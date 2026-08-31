import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Plus, Edit2, Gift, MapPin, X, ShieldAlert } from 'lucide-react';
import DerogationIndemnitesTab from '@/components/parametres/pointage/DerogationIndemnitesTab';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select, Checkbox } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput, fmtMoney, PERIODICITES_INDEMNITE } from '@/utils/paieUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';

export default function IndemnitesSitesTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const subSection = searchParams.get('subtab') || 'indemnites';

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'indemnites_sites');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const todayStr = fmtDateInput(new Date());

    const [indemnites, setIndemnites] = useState([]);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const toast = useToast();

    // Modale Indemnité
    const [showIndemniteModal, setShowIndemniteModal] = useState(false);
    const [editingIndemnite, setEditingIndemnite] = useState(null);
    const [indemniteFormData, setIndemniteFormData] = useState({
        code: '', libelle: '', imposableIrsa: false, fractionnable: false
    });

    // Modale Config Site
    const [showSiteIndemniteModal, setShowSiteIndemniteModal] = useState(false);
    const [editingSiteIndemnite, setEditingSiteIndemnite] = useState(null);
    const [siteIndemniteFormData, setSiteIndemniteFormData] = useState({
        idSite: '', idIndemnite: '', periodicite: 'JOURNALIER', montantReference: '', dateDebut: todayStr, dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [indData, siteIndData, sitesData] = await Promise.all([
                paieConfigService.getIndemnites(),
                paieConfigService.getSiteIndemnites(),
                organisationService.getSites()
            ]);
            setIndemnites(indData || []);
            setSiteIndemnites(siteIndData || []);
            setSites(sitesData || []);
        } catch (err) {
            setError("Erreur lors du chargement des indemnités.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Indemnité
    const handleSaveIndemnite = async (e) => {
        e.preventDefault();
        setActionError(null);
        try {
            if (editingIndemnite) {
                await paieConfigService.updateIndemnite(editingIndemnite.idIndemnite, indemniteFormData);
                toast({ title: 'Succès', message: `Indemnité "${indemniteFormData.libelle}" modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createIndemnite(indemniteFormData);
                toast({ title: 'Succès', message: `Indemnité "${indemniteFormData.libelle}" créée.`, type: 'success' });
            }
            setShowIndemniteModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Site Indemnité
    const handleSaveSiteIndemnite = async (e) => {
        e.preventDefault();
        setActionError(null);
        if (!siteIndemniteFormData.idSite || !siteIndemniteFormData.idIndemnite) {
            setActionError("Veuillez sélectionner un site et une indemnité.");
            return;
        }
        const payload = {
            ...siteIndemniteFormData,
            idSite: parseInt(siteIndemniteFormData.idSite),
            idIndemnite: parseInt(siteIndemniteFormData.idIndemnite),
            montantReference: parseFloat(siteIndemniteFormData.montantReference) || 0
        };
        try {
            if (editingSiteIndemnite) {
                await paieConfigService.updateSiteIndemnite(editingSiteIndemnite.idSiteIndemnite, payload);
                toast({ title: 'Succès', message: "Tarif d'indemnité par site modifié.", type: 'success' });
            } else {
                await paieConfigService.createSiteIndemnite(payload);
                toast({ title: 'Succès', message: "Nouveau tarif d'indemnité par site enregistré.", type: 'success' });
            }
            setShowSiteIndemniteModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Liste des options de mode de calcul d'indemnité explicites
    const periodiciteOptions = PERIODICITES_INDEMNITE;

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Onglets internes */}
            <div className="flex border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none p-2 gap-2 shadow-sm">
                <button
                    onClick={() => setSubSection('indemnites')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-none  cursor-pointer ${
                        subSection === 'indemnites'
                            ? 'bg-[var(--color-brand-500)] text-white'
                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)]'
                    }`}
                >
                    <Gift size={16} />
                    Référentiel des Indemnités ({indemnites.length})
                </button>
                <button
                    onClick={() => setSubSection('sites_config')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-none  cursor-pointer ${
                        subSection === 'sites_config'
                            ? 'bg-[var(--color-brand-500)] text-white'
                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)]'
                    }`}
                >
                    <MapPin size={16} />
                    Montants par Site d'Exploitation ({siteIndemnites.length})
                </button>
                <button
                    onClick={() => setSubSection('exclusions')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-none  cursor-pointer ${
                        subSection === 'exclusions'
                            ? 'bg-[var(--color-brand-500)] text-white'
                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)]'
                    }`}
                >
                    <ShieldAlert size={16} />
                    Exclusions par Employé
                </button>
            </div>

            {/* SECTION 1: CATALOGUE INDEMNITES */}
            {subSection === 'indemnites' && (
                <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                        <h4 className="font-bold text-[var(--color-fg)] text-base">Types d'Indemnités (Repas, Déplacement, Logement...)</h4>
                        <Button
                            onClick={() => {
                                setEditingIndemnite(null);
                                setIndemniteFormData({ code: '', libelle: '', imposableIrsa: false, fractionnable: false });
                                setActionError(null);
                                setShowIndemniteModal(true);
                            }}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Nouvelle Indemnité
                        </Button>
                    </div>

                    <Table>
                        <Thead>
                            <Th>Code</Th>
                            <Th>Libellé</Th>
                            <Th>Mode Pointage</Th>
                            <Th>Régime Fiscal IRSA</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {indemnites.length === 0 ? (
                                <Tr>
                                    <Td colSpan={5} className="p-0">
                                        <EmptyState title="Aucune indemnité" description="Aucune indemnité enregistrée." />
                                    </Td>
                                </Tr>
                            ) : (
                                indemnites.map((ind) => (
                                    <Tr key={ind.idIndemnite}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)]">{ind.code}</Td>
                                        <Td className="font-bold text-[var(--color-fg)]">{ind.libelle}</Td>
                                        <Td>
                                            <Badge variant={ind.fractionnable ? 'brand' : 'neutral'} size="sm">
                                                {ind.fractionnable ? "Demi-journée (M / S)" : "Journée entière"}
                                            </Badge>
                                        </Td>
                                        <Td>
                                            <Badge variant={ind.imposableIrsa ? 'warning' : 'success'} size="sm">
                                                {ind.imposableIrsa ? "Imposable (IRSA)" : "Exonéré"}
                                            </Badge>
                                        </Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingIndemnite(ind);
                                                    setIndemniteFormData({
                                                        code: ind.code,
                                                        libelle: ind.libelle,
                                                        imposableIrsa: ind.imposableIrsa,
                                                        fractionnable: !!ind.fractionnable
                                                    });
                                                    setActionError(null);
                                                    setShowIndemniteModal(true);
                                                }}
                                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                        </Td>
                                    </Tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </div>
            )}

            {/* SECTION 2: CONFIGURATION PAR SITE */}
            {subSection === 'sites_config' && (
                <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                        <div>
                            <h4 className="font-bold text-[var(--color-fg)] text-base">Tarifs des Indemnités par Site d'Exploitation</h4>
                            <p className="text-xs text-[var(--color-fg-muted)]">Montants mensuels ou journaliers appliqués selon le site d'affectation.</p>
                        </div>
                        <Button
                            onClick={() => {
                                setEditingSiteIndemnite(null);
                                setSiteIndemniteFormData({
                                    idSite: '',
                                    idIndemnite: '',
                                    periodicite: periodiciteOptions[0]?.value || 'JOURNALIER',
                                    montantReference: '',
                                    dateDebut: todayStr,
                                    dateFin: ''
                                });
                                setActionError(null);
                                setShowSiteIndemniteModal(true);
                            }}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Configurer Tarif Site
                        </Button>
                    </div>

                    <Table>
                        <Thead>
                            <Th>Site d'Exploitation</Th>
                            <Th>Indemnité</Th>
                            <Th>Périodicité</Th>
                            <Th className="text-right">Montant Référence</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {siteIndemnites.length === 0 ? (
                                <Tr>
                                    <Td colSpan={5} className="p-0">
                                        <EmptyState title="Aucun tarif par site" description="Aucun tarif par site configuré." />
                                    </Td>
                                </Tr>
                            ) : (
                                siteIndemnites.map((si) => (
                                    <Tr key={si.idSiteIndemnite}>
                                        <Td className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                            <MapPin size={15} className="text-[var(--color-brand-500)]" />
                                            {si.siteLibelle}
                                        </Td>
                                        <Td className="font-medium text-[var(--color-fg)]">
                                            {si.indemniteLibelle} <span className="font-data text-xs text-[var(--color-fg-muted)]">({si.indemniteCode})</span>
                                        </Td>
                                        <Td>
                                            <Badge variant="default" size="sm">
                                                {PERIODICITES_INDEMNITE.find(p => p.value === si.periodicite)?.label?.split(' ')[0] || si.periodicite}
                                            </Badge>
                                        </Td>
                                        <Td isData className="text-right font-bold text-[var(--color-brand-500)]">
                                            {fmtMoney(si.montantReference)}
                                        </Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingSiteIndemnite(si);
                                                    setSiteIndemniteFormData({
                                                        idSite: si.idSite,
                                                        idIndemnite: si.idIndemnite,
                                                        periodicite: si.periodicite,
                                                        montantReference: si.montantReference,
                                                        dateDebut: fmtDateInput(si.dateDebut) || todayStr,
                                                        dateFin: fmtDateInput(si.dateFin) || ''
                                                    });
                                                    setActionError(null);
                                                    setShowSiteIndemniteModal(true);
                                                }}
                                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                        </Td>
                                    </Tr>
                                ))
                            )}
                        </Tbody>
                    </Table>
                </div>
            )}

            {/* SECTION 3: EXCLUSIONS INDIVIDUELLES PAR EMPLOYE */}
            {subSection === 'exclusions' && (
                <DerogationIndemnitesTab />
            )}

            {/* Modale Indemnité */}
            {showIndemniteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Gift size={18} className="text-[var(--color-brand-500)]" />
                                {editingIndemnite ? "Modifier l'Indemnité" : "Nouvelle Indemnité"}
                            </h3>
                            <button onClick={() => setShowIndemniteModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSaveIndemnite} className="p-6 space-y-4">
                            {actionError && <Alert type="error" message={actionError} />}
                            <Input
                                label="Code"
                                required
                                disabled={!!editingIndemnite}
                                value={indemniteFormData.code}
                                onChange={(e) => setIndemniteFormData({ ...indemniteFormData, code: e.target.value })}
                                placeholder="Ex: REPAS, DEPLACEMENT"
                            />
                            <Input
                                label="Libellé"
                                required
                                value={indemniteFormData.libelle}
                                onChange={(e) => setIndemniteFormData({ ...indemniteFormData, libelle: e.target.value })}
                                placeholder="Ex: Indemnité de Repas"
                            />
                            <Checkbox
                                label="Imposable à l'IRSA"
                                checked={indemniteFormData.imposableIrsa}
                                onChange={(e) => setIndemniteFormData({ ...indemniteFormData, imposableIrsa: e.target.checked })}
                            />
                            <Checkbox
                                label="Fractionnable par demi-journée (ex: Déplacement/Transport - Trajet Matin & Trajet Soir à 50%)"
                                checked={indemniteFormData.fractionnable}
                                onChange={(e) => setIndemniteFormData({ ...indemniteFormData, fractionnable: e.target.checked })}
                            />
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowIndemniteModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm">
                                    Enregistrer
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale Site Indemnité */}
            {showSiteIndemniteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <MapPin size={18} className="text-[var(--color-brand-500)]" />
                                {editingSiteIndemnite ? "Modifier Tarif Site" : "Nouveau Tarif par Site"}
                            </h3>
                            <button onClick={() => setShowSiteIndemniteModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSaveSiteIndemnite} className="p-6 space-y-4">
                            {actionError && <Alert type="error" message={actionError} />}
                            <Select
                                label="Site d'Exploitation"
                                required
                                value={siteIndemniteFormData.idSite}
                                onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idSite: e.target.value })}
                            >
                                <option value="">-- Choisir un site --</option>
                                {sites.map((s) => (
                                    <option key={s.idSite} value={s.idSite}>{s.libelle} ({s.codeSite})</option>
                                ))}
                            </Select>
                            <Select
                                label="Type d'Indemnité"
                                required
                                value={siteIndemniteFormData.idIndemnite}
                                onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idIndemnite: e.target.value })}
                            >
                                <option value="">-- Choisir une indemnité --</option>
                                {indemnites.map((i) => (
                                    <option key={i.idIndemnite} value={i.idIndemnite}>{i.libelle} ({i.code})</option>
                                ))}
                            </Select>
                            <div className="grid grid-cols-2 gap-4">
                                <Select
                                    label="Périodicité"
                                    required
                                    value={siteIndemniteFormData.periodicite}
                                    onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, periodicite: e.target.value })}
                                >
                                    {periodiciteOptions.map((p) => (
                                        <option key={p.value} value={p.value}>
                                            {p.label}
                                        </option>
                                    ))}
                                </Select>
                                <Input
                                    label="Montant (Ar)"
                                    type="number"
                                    min="0"
                                    step="100"
                                    required
                                    placeholder="Ex: 2500"
                                    value={siteIndemniteFormData.montantReference}
                                    onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, montantReference: e.target.value })}
                                />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <Input
                                    label="Date Début"
                                    type="date"
                                    required
                                    value={fmtDateInput(siteIndemniteFormData.dateDebut)}
                                    onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, dateDebut: e.target.value })}
                                />
                                <Input
                                    label="Date Fin (Optionnel)"
                                    type="date"
                                    min={fmtDateInput(siteIndemniteFormData.dateDebut)}
                                    value={fmtDateInput(siteIndemniteFormData.dateFin)}
                                    onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, dateFin: e.target.value })}
                                />
                            </div>
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowSiteIndemniteModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm">
                                    Enregistrer
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}