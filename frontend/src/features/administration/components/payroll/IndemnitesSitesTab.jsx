import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Plus, Edit2, Gift, MapPin, ShieldAlert, Power, Check } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import DerogationIndemnitesTab from '@/features/administration/components/attendance/DerogationIndemnitesTab';
import paieConfigService from '@/features/administration/api/paieConfigService';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select, Checkbox } from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { PERIODICITES_INDEMNITE } from '@/features/payroll/utils/payrollPresentation';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormModal from '@/components/ui/overlays/FormModal';
import Tabs from '@/components/ui/navigation/Tabs';
import useConfirm from '@/hooks/useConfirm';
import { useToast } from '@/components/ui/feedback/Toast';

export default function IndemnitesSitesTab() {
    // Contexte, navigation et confirmation
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

    // État local : données, filtres et modales.
    const [indemnites, setIndemnites] = useState([]);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();
    const { confirm, ConfirmModal } = useConfirm();

    // Modale Indemnité
    const [showIndemniteModal, setShowIndemniteModal] = useState(false);
    const [editingIndemnite, setEditingIndemnite] = useState(null);
    const [indemniteFormData, setIndemniteFormData] = useState({
        code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: false, soumisCotisation: '', fractionnable: false
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

    // Effet : charge les indemnités et leurs déclinaisons par site.
    useEffect(() => {
        loadData();
    }, []);

    // Handlers Indemnité
    const handleSaveIndemnite = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
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
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleIndemniteStatus = (item) => {
        const isActif = item.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} l'indemnité`,
            message: isActif
                ? `Êtes-vous sûr de vouloir désactiver l'indemnité "${item.libelle}" (${item.code}) ?\n\nATTENTION : Cela désactivera automatiquement tous ses tarifs par site d'exploitation et clôturera les dérogations individuelles associées.`
                : `Êtes-vous sûr de vouloir réactiver l'indemnité "${item.libelle}" (${item.code}) ?`,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    if (isActif) {
                        await paieConfigService.deactivateIndemnite(item.idIndemnite);
                        toast({
                            title: 'Indemnité désactivée',
                            message: `L'indemnité "${item.code}" et ses configurations associées ont été désactivées.`,
                            type: 'success'
                        });
                    } else {
                        await paieConfigService.activateIndemnite(item.idIndemnite);
                        toast({
                            title: 'Indemnité réactivée',
                            message: `L'indemnité "${item.code}" a été réactivée avec succès.`,
                            type: 'success'
                        });
                    }
                    loadData();
                } catch (err) {
                    toast({
                        title: 'Erreur',
                        message: getErrorMessage(err, "Erreur lors du changement de statut."),
                        type: 'error'
                    });
                }
            }
        });
    };

    // Handlers Site Indemnité
    const handleSaveSiteIndemnite = async (e) => {
        e.preventDefault();
        if (submitting) return;
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
        setSubmitting(true);
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
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleSiteIndemniteStatus = (item) => {
        const isActif = item.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} le tarif par site`,
            message: isActif
                ? `Êtes-vous sûr de vouloir désactiver le tarif de "${item.indemniteLibelle}" pour le site "${item.siteLibelle}" ?\n\nCela clôturera également la date de validité à aujourd'hui.`
                : `Êtes-vous sûr de vouloir réactiver ce tarif pour le site "${item.siteLibelle}" ?`,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    if (isActif) {
                        await paieConfigService.deactivateSiteIndemnite(item.idSiteIndemnite);
                        toast({
                            title: 'Tarif désactivé',
                            message: `Le tarif d'indemnité a été désactivé.`,
                            type: 'success'
                        });
                    } else {
                        await paieConfigService.activateSiteIndemnite(item.idSiteIndemnite);
                        toast({
                            title: 'Tarif réactivé',
                            message: `Le tarif d'indemnité a été réactivé.`,
                            type: 'success'
                        });
                    }
                    loadData();
                } catch (err) {
                    toast({
                        title: 'Erreur',
                        message: getErrorMessage(err, "Erreur lors du changement de statut."),
                        type: 'error'
                    });
                }
            }
        });
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

    // Rendu
    return (
        <div className="space-y-6">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Onglets de navigation internes */}
            <div>
                <Tabs
                    tabs={[
                        { id: 'indemnites', label: 'Types d\'Indemnités', icon: <Gift size={16} />, count: indemnites.length },
                        { id: 'sites_config', label: 'Tarifs par Site', icon: <MapPin size={16} />, count: siteIndemnites.length },
                        { id: 'exclusions', label: 'Exclusions Employés', icon: <ShieldAlert size={16} /> }
                    ]}
                    activeTab={subSection}
                    onChange={setSubSection}
                    variant="pills"
                />
            </div>

            {/* SECTION 1: CATALOGUE INDEMNITES */}
            {subSection === 'indemnites' && (
                <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                        <div>
                            <h4 className="font-bold text-[var(--color-fg)] text-base">Catalogue des Indemnités (Repas, Transport, Logement...)</h4>
                            <p className="text-xs text-[var(--color-fg-muted)]">Nomenclature globale des primes et indemnités d'exploitation.</p>
                        </div>
                        <Button
                            onClick={() => {
                                setEditingIndemnite(null);
                                setIndemniteFormData({ code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: false, soumisCotisation: '', fractionnable: false });
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

                    <Table bordered={false}>
                        <Thead>
                            <Th>Code</Th>
                            <Th>Libellé</Th>
                            <Th>Mode Pointage</Th>
                            <Th>Régime Fiscal IRSA</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {indemnites.length === 0 ? (
                                <Tr>
                                    <Td colSpan={6} className="p-0">
                                        <EmptyState
                                            icon={Gift}
                                            title="Aucune indemnité configurée"
                                            description="Créez votre première indemnité (repas, panier, transport)."
                                            actionLabel="Créer une indemnité"
                                            onAction={() => {
                                                setEditingIndemnite(null);
                                                setIndemniteFormData({ code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: false, soumisCotisation: '', fractionnable: false });
                                                setActionError(null);
                                                setShowIndemniteModal(true);
                                            }}
                                        />
                                    </Td>
                                </Tr>
                            ) : (
                                indemnites.map((ind) => {
                                    const isActif = ind.estActif !== false;
                                    return (
                                        <Tr key={ind.idIndemnite}>
                                            <Td isData className="font-bold text-[var(--color-brand-500)]">{ind.code}</Td>
                                            <Td className="font-bold text-[var(--color-fg)]">{ind.libelle}</Td>
                                            <Td>
                                                <Badge variant={ind.fractionnable ? 'brand' : 'default'} size="sm">
                                                    {ind.fractionnable ? "Demi-journée (M / S)" : "Journée entière"}
                                                </Badge>
                                            </Td>
                                            <Td>
                                                <Badge variant={ind.imposableIrsa ? 'warning' : 'success'} size="sm">
                                                    {ind.imposableIrsa ? "Imposable (IRSA)" : "Exonéré"}
                                                </Badge>
                                            </Td>
                                            <Td>
                                                <Badge
                                                    variant={isActif ? 'success' : 'default'}
                                                    size="sm"
                                                    dot
                                                >
                                                    {isActif ? 'Actif' : 'Désactivé'}
                                                </Badge>
                                            </Td>
                                            <Td className="text-right">
                                                <div className="flex items-center justify-end gap-1">
                                                    <button
                                                        onClick={() => {
                                                            setEditingIndemnite(ind);
                                                            setIndemniteFormData({
                                                                code: ind.code,
                                                                libelle: ind.libelle,
                                                                imposableIrsa: ind.imposableIrsa,
                                                                soumisCotisation: ind.soumisCotisation === null || ind.soumisCotisation === undefined ? '' : String(ind.soumisCotisation),
                                                                codeRubriquePaie: ind.codeRubriquePaie || '',
                                                                fractionnable: !!ind.fractionnable
                                                            });
                                                            setActionError(null);
                                                            setShowIndemniteModal(true);
                                                        }}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                        title="Modifier l'indemnité"
                                                        aria-label={`Modifier ${ind.libelle}`}
                                                    >
                                                        <Edit2 size={15} />
                                                    </button>
                                                    {isActif ? (
                                                        <button
                                                            onClick={() => requestToggleIndemniteStatus(ind)}
                                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Désactiver l'indemnité (cascade sur sites et dérogations)"
                                                            aria-label={`Désactiver ${ind.libelle}`}
                                                        >
                                                            <Power size={15} />
                                                        </button>
                                                    ) : (
                                                        <button
                                                            onClick={() => requestToggleIndemniteStatus(ind)}
                                                            className="p-1.5 text-[var(--color-success-600)] hover:bg-[var(--color-success-600)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Réactiver l'indemnité"
                                                            aria-label={`Réactiver ${ind.libelle}`}
                                                        >
                                                            <Check size={15} />
                                                        </button>
                                                    )}
                                                </div>
                                            </Td>
                                        </Tr>
                                    );
                                })
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
                            <h4 className="font-bold text-[var(--color-fg)] text-base">Grille Tarifaire des Indemnités par Site d'Exploitation</h4>
                            <p className="text-xs text-[var(--color-fg-muted)]">Montants journaliers ou mensuels applicables selon le lieu d'affectation.</p>
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
                            Configurer un Tarif Site
                        </Button>
                    </div>

                    <Table bordered={false}>
                        <Thead>
                            <Th>Site d'Exploitation</Th>
                            <Th>Indemnité</Th>
                            <Th>Périodicité</Th>
                            <Th className="text-right">Montant Référence</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {siteIndemnites.length === 0 ? (
                                <Tr>
                                    <Td colSpan={6} className="p-0">
                                        <EmptyState
                                            icon={MapPin}
                                            title="Aucun tarif par site configuré"
                                            description="Associez des montants d'indemnité à chaque site d'exploitation."
                                            actionLabel="Configurer un tarif"
                                            onAction={() => {
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
                                        />
                                    </Td>
                                </Tr>
                            ) : (
                                siteIndemnites.map((si) => {
                                    const isActif = si.estActif !== false;
                                    return (
                                        <Tr key={si.idSiteIndemnite}>
                                            <Td className="font-bold text-[var(--color-fg)]">
                                                <div className="flex items-center gap-2">
                                                    <MapPin size={15} className="text-[var(--color-brand-500)] shrink-0" />
                                                    <span>{si.siteLibelle}</span>
                                                </div>
                                            </Td>
                                            <Td className="font-medium text-[var(--color-fg)]">
                                                {si.indemniteLibelle} <span className="font-mono text-xs text-[var(--color-fg-muted)]">({si.indemniteCode})</span>
                                            </Td>
                                            <Td>
                                                <Badge variant="default" size="sm">
                                                    {PERIODICITES_INDEMNITE.find(p => p.value === si.periodicite)?.label?.split(' ')[0] || si.periodicite}
                                                </Badge>
                                            </Td>
                                            <Td isData className="text-right font-bold text-[var(--color-brand-500)]">
                                                {fmtMoney(si.montantReference)}
                                            </Td>
                                            <Td>
                                                <Badge
                                                    variant={isActif ? 'success' : 'default'}
                                                    size="sm"
                                                    dot
                                                >
                                                    {isActif ? 'Actif' : 'Désactivé'}
                                                </Badge>
                                            </Td>
                                            <Td className="text-right">
                                                <div className="flex items-center justify-end gap-1">
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
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                        title="Modifier le tarif"
                                                        aria-label={`Modifier le tarif pour ${si.siteLibelle}`}
                                                    >
                                                        <Edit2 size={15} />
                                                    </button>
                                                    {isActif ? (
                                                        <button
                                                            onClick={() => requestToggleSiteIndemniteStatus(si)}
                                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Désactiver le tarif site"
                                                            aria-label={`Désactiver le tarif pour ${si.siteLibelle}`}
                                                        >
                                                            <Power size={15} />
                                                        </button>
                                                    ) : (
                                                        <button
                                                            onClick={() => requestToggleSiteIndemniteStatus(si)}
                                                            className="p-1.5 text-[var(--color-success-600)] hover:bg-[var(--color-success-600)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Réactiver le tarif site"
                                                            aria-label={`Réactiver le tarif pour ${si.siteLibelle}`}
                                                        >
                                                            <Check size={15} />
                                                        </button>
                                                    )}
                                                </div>
                                            </Td>
                                        </Tr>
                                    );
                                })
                            )}
                        </Tbody>
                    </Table>
                </div>
            )}

            {/* SECTION 3: EXCLUSIONS INDIVIDUELLES PAR EMPLOYE */}
            {subSection === 'exclusions' && (
                <DerogationIndemnitesTab />
            )}

            {/* Modale Formulaire Indemnité */}
            <FormModal
                isOpen={showIndemniteModal}
                onClose={() => setShowIndemniteModal(false)}
                onSubmit={handleSaveIndemnite}
                title={editingIndemnite ? "Modifier l'Indemnité" : "Nouvelle Indemnité"}
                icon={<Gift size={18} />}
                size="md"
                isEdit={!!editingIndemnite}
                loading={submitting}
                error={actionError}
            >
                <div className="space-y-4">
                    <Input
                        label="Code de l'Indemnité"
                        required
                        disabled={!!editingIndemnite}
                        value={indemniteFormData.code}
                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, code: e.target.value.toUpperCase() })}
                        placeholder="Ex: REPAS, DEPLACEMENT, PANIER"
                    />
                    <Input
                        label="Libellé Complet"
                        required
                        value={indemniteFormData.libelle}
                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, libelle: e.target.value })}
                        placeholder="Ex: Indemnité Forfaitaire de Repas"
                    />
                    <Input
                        label="Code rubrique Paie (facultatif)"
                        value={indemniteFormData.codeRubriquePaie || ''}
                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, codeRubriquePaie: e.target.value.toUpperCase() })}
                        placeholder="Par défaut : INDEMNITE_{CODE}"
                    />
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                        <Checkbox
                            label="Imposable à l'IRSA (sinon exonérée d'impôt sur le revenu)"
                            checked={indemniteFormData.imposableIrsa}
                            onChange={(e) => setIndemniteFormData({ ...indemniteFormData, imposableIrsa: e.target.checked })}
                        />
                        <Select
                            label="Soumise aux cotisations sociales"
                            required
                            value={indemniteFormData.soumisCotisation}
                            onChange={(e) => setIndemniteFormData({ ...indemniteFormData, soumisCotisation: e.target.value === '' ? '' : e.target.value === 'true' })}
                            hint="Indépendant de l'IRSA : à qualifier selon la nature de l'indemnité."
                        >
                            <option value="">-- À qualifier --</option>
                            <option value="true">Oui</option>
                            <option value="false">Non</option>
                        </Select>
                        <Checkbox
                            label="Fractionnable par demi-journée (ex: 50% matin / 50% après-midi)"
                            checked={indemniteFormData.fractionnable}
                            onChange={(e) => setIndemniteFormData({ ...indemniteFormData, fractionnable: e.target.checked })}
                        />
                    </div>
                </div>
            </FormModal>

            {/* Modale Formulaire Site Indemnité */}
            <FormModal
                isOpen={showSiteIndemniteModal}
                onClose={() => setShowSiteIndemniteModal(false)}
                onSubmit={handleSaveSiteIndemnite}
                title={editingSiteIndemnite ? "Modifier le Tarif par Site" : "Nouveau Tarif par Site"}
                icon={<MapPin size={18} />}
                size="md"
                isEdit={!!editingSiteIndemnite}
                loading={submitting}
                error={actionError}
            >
                <div className="space-y-4">
                    <Select
                        label="Site d'Exploitation"
                        required
                        value={siteIndemniteFormData.idSite}
                        onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idSite: e.target.value })}
                    >
                        <option value="">-- Choisir un site --</option>
                        {sites
                            .filter(s => (s.estActif !== false && s.actif !== false) || String(s.idSite) === String(siteIndemniteFormData.idSite))
                            .map((s) => (
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
                        {indemnites
                            .filter(i => (i.estActif !== false) || String(i.idIndemnite) === String(siteIndemniteFormData.idIndemnite))
                            .map((i) => (
                                <option key={i.idIndemnite} value={i.idIndemnite}>{i.libelle} ({i.code})</option>
                            ))}
                    </Select>
                    <div className="grid grid-cols-2 gap-3">
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
                        <MoneyInput
                            label="Montant (Ar)"
                            min="0"
                            required
                            placeholder="Ex: 2500"
                            value={siteIndemniteFormData.montantReference}
                            onValueChange={(value) => setSiteIndemniteFormData({ ...siteIndemniteFormData, montantReference: value })}
                        />
                    </div>
                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            label="Date d'Effet / Début"
                            type="date"
                            required
                            value={fmtDateInput(siteIndemniteFormData.dateDebut)}
                            onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, dateDebut: e.target.value })}
                        />
                        <Input
                            label="Date de Fin (Optionnel)"
                            type="date"
                            min={fmtDateInput(siteIndemniteFormData.dateDebut)}
                            value={fmtDateInput(siteIndemniteFormData.dateFin)}
                            onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, dateFin: e.target.value })}
                        />
                    </div>
                </div>
            </FormModal>

            {/* Modale de confirmation globale */}
            <ConfirmModal />
        </div>
    );
}
