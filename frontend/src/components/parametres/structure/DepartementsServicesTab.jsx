import { useState, useEffect, useCallback } from 'react';
import { CheckCircle, XCircle, Eye } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import Loading from '@/components/ui/feedback/Loading';

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
        }
    };

    const handleToggleDeptStatus = async (dept, e) => {
        e.stopPropagation();
        setActionSuccess(null);
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
        }
    };

    const handleToggleServiceStatus = async (service) => {
        setActionSuccess(null);
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
        }
    };

    const filteredDepartements = showInactive ? departements : departements.filter(d => d.estActif !== false);
    const filteredServices = showInactive ? services : services.filter(s => s.estActif !== false);

    if (loading && departements.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <Loading size="md" label="Chargement des départements et services..." />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'affichage désactivés */}
            <div className="flex justify-end bg-[var(--color-surface)] p-2 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none">
                    <input
                        type="checkbox"
                        checked={showInactive}
                        onChange={(e) => setShowInactive(e.target.checked)}
                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                    />
                    <Eye size={14} />
                    <span>Afficher désactivés</span>
                </label>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                {/* Colonne Gauche : Départements */}
                <DepartementsList
                    departements={filteredDepartements}
                    selectedDepartement={selectedDepartement}
                    onSelectDepartement={setSelectedDepartement}
                    onOpenCreate={handleOpenCreateDept}
                    onOpenEdit={handleOpenEditDept}
                    onToggleStatus={handleToggleDeptStatus}
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
            />
        </div>
    );
}