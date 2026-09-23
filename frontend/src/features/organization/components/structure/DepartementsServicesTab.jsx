import { useState, useEffect, useCallback } from 'react';
import organisationService from '@/features/organization/api/organisationService';
import { fmtDateInput } from '@/utils/dateUtils';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

import DepartementsList from './departements/DepartementsList';
import DepartementFormModal from './departements/DepartementFormModal';
import ServicesList from './services/ServicesList';
import ServiceFormModal from './services/ServiceFormModal';

export default function DepartementsServicesTab() {
    // État local
    const [departements, setDepartements] = useState([]);
    const [services, setServices] = useState([]);
    const [selectedDepartement, setSelectedDepartement] = useState(null);

    const [loading, setLoading] = useState(true);
    const [loadingServices, setLoadingServices] = useState(false);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
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
        codeDepartement: '', libelle: '', dateFinPrevue: ''
    });

    // Modale Service
    const [showServiceModal, setShowServiceModal] = useState(false);
    const [editingService, setEditingService] = useState(null);
    const [serviceFormData, setServiceFormData] = useState({
        codeService: '', libelle: '', idDepartement: ''
    });

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

    // Effets : charge les départements puis les services du département sélectionné.
    useEffect(() => {
        loadDepartements();
    }, [loadDepartements]);

    useEffect(() => {
        if (selectedDepartement) {
            loadServicesForDepartement(selectedDepartement.idDepartement);
        } else {
            setServices([]);
        }
    }, [selectedDepartement]);

    // Actions utilisateur : départements, services et confirmations.
    const handleOpenCreateDept = () => {
        setEditingDept(null);
        setDeptFormData({ codeDepartement: '', libelle: '', dateFinPrevue: '' });
        setActionError(null);
        setShowDeptModal(true);
    };

    const handleOpenEditDept = (dept, e) => {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
        setEditingDept(dept);
        setDeptFormData({
            codeDepartement: dept.codeDepartement,
            libelle: dept.libelle,
            dateFinPrevue: fmtDateInput(dept.dateFinPrevue) || ''
        });
        setActionError(null);
        setShowDeptModal(true);
    };

    const handleSaveDept = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setActionSuccess(null);

        if (!deptFormData.codeDepartement.trim()) {
            setActionError("Le code département est obligatoire.");
            return;
        }
        if (!deptFormData.libelle.trim()) {
            setActionError("Le libellé est obligatoire.");
            return;
        }

        setSubmitting(true);
        try {
            if (editingDept) {
                const updated = await organisationService.updateDepartement(editingDept.idDepartement, deptFormData);
                setActionSuccess(`Département "${deptFormData.libelle}" modifié.`);
                if (selectedDepartement?.idDepartement === editingDept.idDepartement) {
                    setSelectedDepartement(updated);
                }
            } else {
                const created = await organisationService.createDepartement(deptFormData);
                setActionSuccess(`Département "${deptFormData.libelle}" créé.`);
                setSelectedDepartement(created);
            }
            setShowDeptModal(false);
            loadDepartements();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
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
                        setActionSuccess(`Département "${dept.libelle}" désactivé.`);
                    } else {
                        await organisationService.activateDepartement(dept.idDepartement);
                        setActionSuccess(`Département "${dept.libelle}" réactivé.`);
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
        setActionError(null);
        setShowServiceModal(true);
    };

    const handleSaveService = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setActionSuccess(null);

        if (!serviceFormData.idDepartement) {
            setActionError("Veuillez sélectionner un département de rattachement.");
            return;
        }
        if (!serviceFormData.codeService.trim()) {
            setActionError("Le code service est obligatoire.");
            return;
        }
        if (!serviceFormData.libelle.trim()) {
            setActionError("Le libellé service est obligatoire.");
            return;
        }

        const payload = {
            ...serviceFormData,
            idDepartement: parseInt(serviceFormData.idDepartement)
        };

        setSubmitting(true);
        try {
            if (editingService) {
                await organisationService.updateService(editingService.idService, payload);
                setActionSuccess(`Service "${serviceFormData.libelle}" modifié.`);
            } else {
                await organisationService.createService(payload);
                setActionSuccess(`Service "${serviceFormData.libelle}" créé.`);
            }
            setShowServiceModal(false);
            if (selectedDepartement) {
                loadServicesForDepartement(selectedDepartement.idDepartement);
            }
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
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
                        setActionSuccess(`Service "${service.libelle}" désactivé.`);
                    } else {
                        await organisationService.activateService(service.idService);
                        setActionSuccess(`Service "${service.libelle}" réactivé.`);
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
        <div className="space-y-6">
            {error && <Alert type="error" message={error} />}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
                {/* Colonne Gauche : Départements (5 cols sur 12) */}
                <div className="lg:col-span-5">
                    <DepartementsList
                        departements={filteredDepartements}
                        selectedDepartement={selectedDepartement}
                        loading={loading}
                        showInactive={showInactive}
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
