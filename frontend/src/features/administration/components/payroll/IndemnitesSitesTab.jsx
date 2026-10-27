import SharedButton from '@/components/ui/forms/Button';
import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Plus, Edit2, Gift, MapPin, ShieldAlert, Power, Check } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import DerogationIndemnitesTab from '@/features/administration/components/attendance/DerogationIndemnitesTab';
import paieConfigService from '@/features/administration/api/paieConfigService';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { PERIODICITES_INDEMNITE } from '@/features/payroll/utils/payrollPresentation';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormModal from '@/components/ui/overlays/FormModal';
import SideTabs from '@/components/ui/navigation/SideTabs';
import useConfirm from '@/hooks/useConfirm';
import { useToast } from '@/components/ui/feedback/Toast';

export default function IndemnitesSitesTab() {
    // Contexte, navigation et confirmation
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedSubSection = searchParams.get('subtab');
    const subSection = ['indemnites', 'sites_config', 'exclusions'].includes(requestedSubSection) ? requestedSubSection : 'indemnites';

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
        code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: '', soumisCotisation: '', fractionnable: false
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
        if (indemniteFormData.imposableIrsa === '' || indemniteFormData.soumisCotisation === '') {
            setActionError("Qualifiez l'IRSA et les cotisations sociales avant d'enregistrer l'indemnité.");
            return;
        }
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
        <SideTabs
            tabs={[
                { id: 'indemnites', label: 'Types d’indemnités', icon: <Gift size={16} />, count: indemnites.length },
                { id: 'sites_config', label: 'Tarifs par site', icon: <MapPin size={16} />, count: siteIndemnites.length },
                { id: 'exclusions', label: 'Exclusions employés', icon: <ShieldAlert size={16} /> }
            ]}
            activeTab={subSection}
            onChange={setSubSection}
            className="space-y-6"
        >
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

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
                                setIndemniteFormData({ code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: '', soumisCotisation: '', fractionnable: false });
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
                                                setIndemniteFormData({ code: '', libelle: '', codeRubriquePaie: '', imposableIrsa: '', soumisCotisation: '', fractionnable: false });
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
                                                    <SharedButton plain
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
                                                    </SharedButton>
                                                    {isActif ? (
                                                        <SharedButton plain
                                                            onClick={() => requestToggleIndemniteStatus(ind)}
                                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Désactiver l'indemnité (cascade sur sites et dérogations)"
                                                            aria-label={`Désactiver ${ind.libelle}`}
                                                        >
                                                            <Power size={15} />
                                                        </SharedButton>
                                                    ) : (
                                                        <SharedButton plain
                                                            onClick={() => requestToggleIndemniteStatus(ind)}
                                                            className="p-1.5 text-[var(--color-success-600)] hover:bg-[var(--color-success-600)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Réactiver l'indemnité"
                                                            aria-label={`Réactiver ${ind.libelle}`}
                                                        >
                                                            <Check size={15} />
                                                        </SharedButton>
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
                                                    <SharedButton plain
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
                                                    </SharedButton>
                                                    {isActif ? (
                                                        <SharedButton plain
                                                            onClick={() => requestToggleSiteIndemniteStatus(si)}
                                                            className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Désactiver le tarif site"
                                                            aria-label={`Désactiver le tarif pour ${si.siteLibelle}`}
                                                        >
                                                            <Power size={15} />
                                                        </SharedButton>
                                                    ) : (
                                                        <SharedButton plain
                                                            onClick={() => requestToggleSiteIndemniteStatus(si)}
                                                            className="p-1.5 text-[var(--color-success-600)] hover:bg-[var(--color-success-600)]/10 rounded-none cursor-pointer transition-colors"
                                                            title="Réactiver le tarif site"
                                                            aria-label={`Réactiver le tarif pour ${si.siteLibelle}`}
                                                        >
                                                            <Check size={15} />
                                                        </SharedButton>
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
                size="2xl"
                isEdit={!!editingIndemnite}
                loading={submitting}
                error={actionError}
            >
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6 items-start">
                    <div className="space-y-4 min-w-0">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Identification</h3>
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
                            label="Code de ligne sur le bulletin (facultatif)"
                            value={indemniteFormData.codeRubriquePaie || ''}
                            onChange={(e) => setIndemniteFormData({ ...indemniteFormData, codeRubriquePaie: e.target.value.toUpperCase() })}
                            placeholder="Par défaut : INDEMNITE_{CODE}"
                            hint="Identifie la ligne du bulletin ; ne reprend pas les paramètres d'une rubrique de calcul."
                        />
                        <Checkbox
                            label="Fractionnable par demi-journée (50 % matin / 50 % après-midi)"
                            checked={indemniteFormData.fractionnable}
                            onChange={(e) => setIndemniteFormData({ ...indemniteFormData, fractionnable: e.target.checked })}
                        />
                    </div>
                    <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-4 min-w-0">
                        <div>
                            <h3 className="text-xs font-bold text-[var(--color-fg)]">Traitement en paie</h3>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">Paramètres appliqués au montant calculé par le pointage.</p>
                        </div>
                        <SearchSelect
                            label="Imposable à l'IRSA"
                            required
                            value={indemniteFormData.imposableIrsa === '' ? '' : String(indemniteFormData.imposableIrsa)}
                            onChange={(val) => setIndemniteFormData({ ...indemniteFormData, imposableIrsa: val === '' ? '' : val === 'true' })}
                            options={[
                                { value: 'true', label: 'Oui — imposable' },
                                { value: 'false', label: 'Non — hors assiette IRSA' }
                            ]}
                            placeholder="-- À qualifier --"
                            hint="Un complément de salaire est en principe imposable ; vérifiez la nature réelle d'un remboursement de frais."
                        />
                        <SearchSelect
                            label="Soumise aux cotisations sociales"
                            required
                            value={indemniteFormData.soumisCotisation === '' ? '' : String(indemniteFormData.soumisCotisation)}
                            onChange={(val) => setIndemniteFormData({ ...indemniteFormData, soumisCotisation: val === '' ? '' : val === 'true' })}
                            options={[
                                { value: 'true', label: 'Oui' },
                                { value: 'false', label: 'Non' }
                            ]}
                            placeholder="-- À qualifier --"
                            hint="Selon la nature du paiement : rémunération ou remboursement de frais. Ce choix pilote CNaPS, Santé et FMFP."
                        />
                        <div className="border-t border-[var(--color-border-subtle)] pt-3" aria-live="polite">
                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">Aperçu du traitement</p>
                            <dl className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-x-4 gap-y-2 mt-2 text-xs">
                                <dt className="text-[var(--color-fg-muted)]">Intervention</dt>
                                <dd className="text-right font-medium text-[var(--color-fg)]">{indemniteFormData.imposableIrsa === '' ? 'À qualifier' : indemniteFormData.imposableIrsa ? 'Dans le brut' : 'Ajout au net après impôt'}</dd>
                                <dt className="text-[var(--color-fg-muted)]">IRSA</dt>
                                <dd className="text-right font-medium text-[var(--color-fg)]">{indemniteFormData.imposableIrsa === '' ? 'À qualifier' : indemniteFormData.imposableIrsa ? 'Assiette alimentée' : 'Hors assiette'}</dd>
                                <dt className="text-[var(--color-fg-muted)]">CNaPS / Santé / FMFP</dt>
                                <dd className="text-right font-medium text-[var(--color-fg)]">
                                    {indemniteFormData.soumisCotisation === '' ? 'À qualifier' : indemniteFormData.soumisCotisation ? 'Assiettes alimentées' : 'Hors assiettes'}
                                </dd>
                            </dl>
                        </div>
                        {indemniteFormData.imposableIrsa === false && (
                            <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">
                                Vérifiez le fondement de l'exclusion IRSA et les frais réellement couverts. Le libellé « indemnité » ne suffit pas à justifier une exonération.
                            </p>
                        )}
                        {indemniteFormData.imposableIrsa === false && indemniteFormData.soumisCotisation === true && (
                            <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">
                                Cette combinaison est ajoutée au net mais entre dans l'assiette sociale dans le moteur actuel. Faites vérifier ce traitement avant usage.
                            </p>
                        )}
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
                size="lg"
                isEdit={!!editingSiteIndemnite}
                loading={submitting}
                error={actionError}
            >
                <div className="space-y-4 py-1">
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <SearchSelect
                            label="Site d'Exploitation"
                            required
                            value={siteIndemniteFormData.idSite}
                            onChange={(val) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idSite: val })}
                            options={sites
                                .filter(s => (s.estActif !== false && s.actif !== false) || String(s.idSite) === String(siteIndemniteFormData.idSite))
                                .map((s) => ({
                                    value: String(s.idSite),
                                    label: s.libelle,
                                    sublabel: s.codeSite
                                }))}
                            placeholder="-- Choisir un site --"
                        />
                        <SearchSelect
                            label="Type d'Indemnité"
                            required
                            value={siteIndemniteFormData.idIndemnite}
                            onChange={(val) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idIndemnite: val })}
                            options={indemnites
                                .filter(i => (i.estActif !== false) || String(i.idIndemnite) === String(siteIndemniteFormData.idIndemnite))
                                .map((i) => ({
                                    value: String(i.idIndemnite),
                                    label: i.libelle,
                                    sublabel: i.code
                                }))}
                            placeholder="-- Choisir une indemnité --"
                        />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <SearchSelect
                            label="Périodicité"
                            required
                            value={siteIndemniteFormData.periodicite}
                            onChange={(val) => setSiteIndemniteFormData({ ...siteIndemniteFormData, periodicite: val })}
                            options={periodiciteOptions}
                            placeholder="-- Choisir une périodicité --"
                        />
                        <MoneyInput
                            label="Montant (Ar)"
                            min="0"
                            required
                            placeholder="Ex: 2500"
                            value={siteIndemniteFormData.montantReference}
                            onValueChange={(value) => setSiteIndemniteFormData({ ...siteIndemniteFormData, montantReference: value })}
                        />
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
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
                            hint="Laisser vide si permanent"
                        />
                    </div>
                </div>
            </FormModal>

            {/* Modale de confirmation globale */}
            <ConfirmModal />
        </SideTabs>
    );
}
