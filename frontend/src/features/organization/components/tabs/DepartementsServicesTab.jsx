import { useState, useEffect, useCallback } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';

import DepartementsList from '../views/DepartementsList';
import ServicesList from '../views/ServicesList';
import DepartementFormModal from '../modals/DepartementFormModal';
import ServiceFormModal from '../modals/ServiceFormModal';

export default function DepartementsServicesTab() {
    const { toast } = useToast();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('ORGANISATION_WRITE');
    // État local
    const [departements, setDepartements] = useState([]);
    const [services, setServices] = useState([]);
    const [selectedDepartement, setSelectedDepartement] = useState(null);

    const [loading, setLoading] = useState(true);
    const [loadingServices, setLoadingServices] = useState(false);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [showInactive, setShowInactive] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // État de confirmation
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        onConfirm: null,
        loading: false
    });

    // Modale Département
    const [showDeptModal, setShowDeptModal] = useState(false);
    const [editingDept, setEditingDept] = useState(null);
    const [deptFormData, setDeptFormData] = useState({
        codeDepartement: '', libelle: ''
    });
    const [deptFieldErrors, setDeptFieldErrors] = useState({});

    // Modale Service
    const [showServiceModal, setShowServiceModal] = useState(false);
    const [editingService, setEditingService] = useState(null);
    const [serviceFormData, setServiceFormData] = useState({
        codeService: '', libelle: '', idDepartement: ''
    });
    const [serviceFieldErrors, setServiceFieldErrors] = useState({});

    // Chargements de données
    const loadDepartements = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getDepartements();
            const list = Array.isArray(data) ? data : (data?.items || data?.content || []);
            setDepartements(list);
            setSelectedDepartement((prev) => {
                if (prev) {
                    const match = list.find(d => d.idDepartement === prev.idDepartement);
                    return match || (list.length > 0 ? list[0] : null);
                }
                return list.length > 0 ? list[0] : null;
            });
        } catch (err) {
            setError("Erreur lors du chargement des départements.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, []);

    const loadServicesForDepartement = async (idDept) => {
        setLoadingServices(true);
        try {
            const data = await organisationService.getServices(idDept);
            setServices(data || []);
        } catch (err) {
            console.error("Erreur lors du chargement des services :", err);
        } finally {
            setLoadingServices(false);
        }
    };

    // Charge les départements au montage et après les mutations via la même fonction.
    useEffect(() => {
        loadDepartements();
    }, [loadDepartements]);

    useEffect(() => {
        let isMounted = true;
        if (selectedDepartement) {
            setLoadingServices(true);
            organisationService.getServices(selectedDepartement.idDepartement)
                .then(data => {
                    if (isMounted) setServices(data || []);
                })
                .catch(err => {
                    console.error("Erreur lors du chargement des services :", err);
                })
                .finally(() => {
                    if (isMounted) setLoadingServices(false);
                });
        } else {
            setServices([]);
        }
        return () => {
            isMounted = false;
        };
    }, [selectedDepartement]);

    // Actions utilisateur : départements, services et confirmations.
    const handleOpenCreateDept = () => {
        setEditingDept(null);
        setDeptFormData({ codeDepartement: '', libelle: '' });
        setDeptFieldErrors({});
        setActionError(null);
        setShowDeptModal(true);
    };

    const handleOpenEditDept = (dept, e) => {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
        setEditingDept(dept);
        setDeptFormData({
            codeDepartement: dept.codeDepartement,
            libelle: dept.libelle
        });
        setDeptFieldErrors({});
        setActionError(null);
        setShowDeptModal(true);
    };

    const handleSaveDept = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!deptFormData.codeDepartement.trim()) {
            errors.codeDepartement = "Le code département est obligatoire.";
        }
        if (!deptFormData.libelle.trim()) {
            errors.libelle = "Le libellé est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setDeptFieldErrors(errors);
            return;
        }
        setDeptFieldErrors({});

        setSubmitting(true);
        try {
            if (editingDept) {
                const updated = await organisationService.updateDepartement(editingDept.idDepartement, deptFormData);
                toast({ title: 'Succès', message: `Département "${deptFormData.libelle}" modifié.`, type: 'success' });
                if (selectedDepartement?.idDepartement === editingDept.idDepartement) {
                    setSelectedDepartement(updated);
                }
            } else {
                const created = await organisationService.createDepartement(deptFormData);
                toast({ title: 'Succès', message: `Département "${deptFormData.libelle}" créé.`, type: 'success' });
                setSelectedDepartement(created);
            }
            setShowDeptModal(false);
            loadDepartements();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde du département."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleDeptStatus = (dept, e) => {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
        const isDeactivating = dept.estActif;

        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le département "${dept.libelle}" ?` : `Réactiver le département "${dept.libelle}" ?`,
            message: isDeactivating
                ? `Attention : La désactivation de ce département désactivera également l'accès à ses services rattachés et masquera l'entité lors des nouvelles affectations de collaborateurs.`
                : `Le département et ses services redeviendront actifs.`,
            confirmLabel: isDeactivating ? "Désactiver le département" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateDepartement(dept.idDepartement);
                        toast({ title: 'Succès', message: `Département "${dept.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateDepartement(dept.idDepartement);
                        toast({ title: 'Succès', message: `Département "${dept.libelle}" réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadDepartements();
                } catch {
                    setError("Erreur lors de la modification du statut du département.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    // Service Handlers
    const handleOpenCreateService = () => {
        setEditingService(null);
        setServiceFormData({
            codeService: '',
            libelle: '',
            idDepartement: selectedDepartement?.idDepartement ? String(selectedDepartement.idDepartement) : ''
        });
        setServiceFieldErrors({});
        setActionError(null);
        setShowServiceModal(true);
    };

    const handleOpenEditService = (service) => {
        setEditingService(service);
        setServiceFormData({
            codeService: service.codeService,
            libelle: service.libelle,
            idDepartement: String(service.idDepartement)
        });
        setServiceFieldErrors({});
        setActionError(null);
        setShowServiceModal(true);
    };

    const handleSaveService = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!serviceFormData.idDepartement) {
            errors.idDepartement = "Veuillez sélectionner un département de rattachement.";
        }
        if (!serviceFormData.codeService.trim()) {
            errors.codeService = "Le code service est obligatoire.";
        }
        if (!serviceFormData.libelle.trim()) {
            errors.libelle = "Le libellé du service est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setServiceFieldErrors(errors);
            return;
        }
        setServiceFieldErrors({});

        const payload = {
            ...serviceFormData,
            idDepartement: parseInt(serviceFormData.idDepartement)
        };

        setSubmitting(true);
        try {
            if (editingService) {
                await organisationService.updateService(editingService.idService, payload);
                toast({ title: 'Succès', message: `Service "${serviceFormData.libelle}" modifié.`, type: 'success' });
            } else {
                await organisationService.createService(payload);
                toast({ title: 'Succès', message: `Service "${serviceFormData.libelle}" créé.`, type: 'success' });
            }
            setShowServiceModal(false);
            if (selectedDepartement) {
                loadServicesForDepartement(selectedDepartement.idDepartement);
            }
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde du service."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleServiceStatus = (service) => {
        const isDeactivating = service.estActif;

        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le service "${service.libelle}" ?` : `Réactiver le service "${service.libelle}" ?`,
            message: isDeactivating
                ? `La désactivation de ce service le rendra indisponible pour les futurs contrats et pointages. Les salariés actuellement rattachés conserveront leur historique.`
                : `Le service redeviendra disponible pour les affectations.`,
            confirmLabel: isDeactivating ? "Désactiver le service" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateService(service.idService);
                        toast({ title: 'Succès', message: `Service "${service.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateService(service.idService);
                        toast({ title: 'Succès', message: `Service "${service.libelle}" réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    if (selectedDepartement) {
                        loadServicesForDepartement(selectedDepartement.idDepartement);
                    }
                } catch {
                    setError("Erreur lors de la modification du statut du service.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const filteredDepartements = departements.filter(d => showInactive || d.estActif);
    const filteredServices = services.filter(s => showInactive || s.estActif);

    return (
        <div className="space-y-4">
            {error && <Alert type="error" message={error} />}
            {!canWrite && <Alert type="info" message="Mode consultation : vos droits ne permettent pas de modifier les départements ou services." />}

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                {/* Colonne Gauche : Départements (5 cols sur 12) */}
                <div className="lg:col-span-5">
                    <DepartementsList
                        departements={filteredDepartements}
                        selectedDepartement={selectedDepartement}
                        loading={loading}
                        showInactive={showInactive}
                        canWrite={canWrite}
                        onSelectDepartement={setSelectedDepartement}
                        onOpenCreateDept={handleOpenCreateDept}
                        onOpenEditDept={handleOpenEditDept}
                        onToggleDeptStatus={requestToggleDeptStatus}
                        onToggleShowInactive={() => setShowInactive(!showInactive)}
                    />
                </div>

                {/* Colonne Droite : Services (7 cols sur 12) */}
                <div className="lg:col-span-7">
                    <ServicesList
                        selectedDepartement={selectedDepartement}
                        services={filteredServices}
                        loadingServices={loadingServices}
                        canWrite={canWrite}
                        onOpenCreateService={handleOpenCreateService}
                        onOpenEditService={handleOpenEditService}
                        onToggleServiceStatus={requestToggleServiceStatus}
                    />
                </div>
            </div>

            {/* Modale Département */}
            <DepartementFormModal
                isOpen={showDeptModal}
                onClose={() => setShowDeptModal(false)}
                onSubmit={handleSaveDept}
                editingDept={editingDept}
                formData={deptFormData}
                setFormData={setDeptFormData}
                actionError={actionError}
                fieldErrors={deptFieldErrors}
                setFieldErrors={setDeptFieldErrors}
                submitting={submitting}
            />

            {/* Modale Service */}
            <ServiceFormModal
                isOpen={showServiceModal}
                onClose={() => setShowServiceModal(false)}
                onSubmit={handleSaveService}
                editingService={editingService}
                formData={serviceFormData}
                setFormData={setServiceFormData}
                departements={departements}
                actionError={actionError}
                fieldErrors={serviceFieldErrors}
                setFieldErrors={setServiceFieldErrors}
                submitting={submitting}
            />

            {/* Boîte de confirmation sécurisée */}
            <ConfirmDialog
                isOpen={confirmState.isOpen}
                onClose={() => setConfirmState(prev => ({ ...prev, isOpen: false }))}
                onConfirm={confirmState.onConfirm}
                title={confirmState.title}
                message={confirmState.message}
                confirmLabel={confirmState.confirmLabel}
                variant={confirmState.variant}
                loading={confirmState.loading}
            />
        </div>
    );
}
