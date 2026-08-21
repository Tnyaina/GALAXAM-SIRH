import { useState, useEffect, useCallback } from 'react';
import {   Eye } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';

import DepartementsList from './departements/DepartementsList';
import DepartementFormModal from './departements/DepartementFormModal';
import ServicesList from './services/ServicesList';
import ServiceFormModal from './services/ServiceFormModal';

export default function DepartementsServicesTab() {
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

    const loadDepartements = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getDepartements();
            setDepartements(data || []);
            if (data?.length > 0 && !selectedDepartement) {
                setSelectedDepartement(data[0]);
            }
        } catch (err) {
            setError("Erreur lors du chargement des départements.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, [selectedDepartement]);

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

    useEffect(() => {
        loadDepartements();
    }, [loadDepartements]);

    useEffect(() => {
        if (selectedDepartement) {
            loadServicesForDepartement(selectedDepartement.idDepartement);
        }
    }, [selectedDepartement]);

    // Department Handlers
    const handleOpenCreateDept = () => {
        setEditingDept(null);
        setDeptFormData({ codeDepartement: '', libelle: '', dateFinPrevue: '' });
        setActionError(null);
        setShowDeptModal(true);
    };

    const handleOpenEditDept = (dept, e) => {
        e.stopPropagation();
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

    const handleToggleDeptStatus = async (dept, e) => {
        e.stopPropagation();
        if (submitting) return;
        setActionSuccess(null);
        setSubmitting(true);
        try {
            if (dept.estActif) {
                await organisationService.deactivateDepartement(dept.idDepartement);
                setActionSuccess(`Département "${dept.libelle}" désactivé.`);
            } else {
                await organisationService.activateDepartement(dept.idDepartement);
                setActionSuccess(`Département "${dept.libelle}" réactivé.`);
            }
            loadDepartements();
        } catch {
            setError("Erreur lors de la modification du statut.");
        } finally {
            setSubmitting(false);
        }
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

    const handleToggleServiceStatus = async (service) => {
        if (submitting) return;
        setActionSuccess(null);
        setSubmitting(true);
        try {
            if (service.estActif) {
                await organisationService.deactivateService(service.idService);
                setActionSuccess(`Service "${service.libelle}" désactivé.`);
            } else {
                await organisationService.activateService(service.idService);
                setActionSuccess(`Service "${service.libelle}" réactivé.`);
            }
            if (selectedDepartement) {
                loadServicesForDepartement(selectedDepartement.idDepartement);
            }
        } catch {
            setError("Erreur lors de la modification du statut.");
        } finally {
            setSubmitting(false);
        }
    };

    const filteredDepartements = departements.filter(d => showInactive || d.estActif);
    const filteredServices = services.filter(s => showInactive || s.estActif);

    return (
        <div className="space-y-6">
            {error && (
                <div className="p-4 bg-danger-500/10 text-danger-500 border border-danger-500/20 text-sm">
                    {error}
                </div>
            )}
            {actionSuccess && (
                <div className="p-3 bg-success-500/10 text-success-600 border border-success-500/20 text-xs font-semibold">
                    {actionSuccess}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                {/* Colonne Gauche : Départements */}
                <DepartementsList
                    departements={filteredDepartements}
                    selectedDepartement={selectedDepartement}
                    loading={loading}
                    showInactive={showInactive}
                    onSelectDepartement={setSelectedDepartement}
                    onOpenCreateDept={handleOpenCreateDept}
                    onOpenEditDept={handleOpenEditDept}
                    onToggleDeptStatus={handleToggleDeptStatus}
                    onToggleShowInactive={() => setShowInactive(!showInactive)}
                />

                {/* Colonne Droite : Services */}
                <ServicesList
                    selectedDepartement={selectedDepartement}
                    services={filteredServices}
                    loadingServices={loadingServices}
                    onOpenCreateService={handleOpenCreateService}
                    onOpenEditService={handleOpenEditService}
                    onToggleServiceStatus={handleToggleServiceStatus}
                />
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
        </div>
    );
}