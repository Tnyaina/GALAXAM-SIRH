import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, Building, Layers, Eye } from 'lucide-react';
import organisationService from '@/api/organisationService';

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
        codeDepartement: '',
        libelle: '',
        dateFinPrevue: ''
    });

    // Modale Service
    const [showServiceModal, setShowServiceModal] = useState(false);
    const [editingService, setEditingService] = useState(null);
    const [serviceFormData, setServiceFormData] = useState({
        codeService: '',
        libelle: '',
        idDepartement: ''
    });

    useEffect(() => {
        loadDepartements();
    }, []);

    useEffect(() => {
        if (selectedDepartement) {
            loadServicesForDepartement(selectedDepartement.idDepartement);
        }
    }, [selectedDepartement]);

    const loadDepartements = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getDepartements();
            setDepartements(data);
            if (data.length > 0 && !selectedDepartement) {
                setSelectedDepartement(data[0]);
            }
        } catch (err) {
            setError("Erreur lors du chargement des départements.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const loadServicesForDepartement = async (idDept) => {
        setLoadingServices(true);
        try {
            const data = await organisationService.getServices(idDept);
            setServices(data);
        } catch (err) {
            console.error("Erreur lors du chargement des services :", err);
        } finally {
            setLoadingServices(false);
        }
    };

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
            dateFinPrevue: dept.dateFinPrevue || ''
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
        } catch (err) {
            setError("Erreur lors de la modification du statut.");
        }
    };

    // Service Handlers
    const handleOpenCreateService = () => {
        setEditingService(null);
        setServiceFormData({
            codeService: '',
            libelle: '',
            idDepartement: selectedDepartement?.idDepartement || ''
        });
        setActionError(null);
        setShowServiceModal(true);
    };

    const handleOpenEditService = (service) => {
        setEditingService(service);
        setServiceFormData({
            codeService: service.codeService,
            libelle: service.libelle,
            idDepartement: service.idDepartement
        });
        setActionError(null);
        setShowServiceModal(true);
    };

    const handleSaveService = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!serviceFormData.codeService.trim()) {
            setActionError("Le code service est obligatoire.");
            return;
        }
        if (!serviceFormData.libelle.trim()) {
            setActionError("Le libellé service est obligatoire.");
            return;
        }

        try {
            if (editingService) {
                await organisationService.updateService(editingService.idService, serviceFormData);
                setActionSuccess(`Service "${serviceFormData.libelle}" modifié.`);
            } else {
                await organisationService.createService(serviceFormData);
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
        } catch (err) {
            setError("Erreur lors de la modification du statut.");
        }
    };

    const filteredDepartements = showInactive ? departements : departements.filter(d => d.estActif !== false);
    const filteredServices = showInactive ? services : services.filter(s => s.estActif !== false);

    if (loading) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'affichage désactivés */}
            <div className="flex justify-end bg-white dark:bg-ink-900 p-2 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <label className="flex items-center gap-2 text-xs font-semibold text-ink-500 dark:text-ink-300 cursor-pointer px-2 py-1 hover:bg-ink-50 dark:hover:bg-ink-950 rounded-sm">
                    <input
                        type="checkbox"
                        checked={showInactive}
                        onChange={(e) => setShowInactive(e.target.checked)}
                        className="w-4 h-4 text-brand-500 rounded-sm"
                    />
                    <Eye size={14} />
                    <span>Afficher désactivés</span>
                </label>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                
                {/* Colonne Gauche : Liste des Départements */}
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-4 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                            <Building size={18} className="text-brand-500" />
                            Départements ({filteredDepartements.length})
                        </h3>
                        <button
                            onClick={handleOpenCreateDept}
                            className="p-1 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors text-brand-500 cursor-pointer"
                            title="Créer un département"
                        >
                            <Plus size={18} />
                        </button>
                    </div>

                    <div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1">
                        {filteredDepartements.map((dept) => {
                            const isSelected = selectedDepartement?.idDepartement === dept.idDepartement;
                            return (
                                <div
                                    key={dept.idDepartement}
                                    onClick={() => setSelectedDepartement(dept)}
                                    className={`
                                        w-full flex items-center justify-between p-3 rounded-sm border text-left cursor-pointer transition-all
                                        ${isSelected
                                            ? 'bg-brand-500/10 border-brand-500 text-brand-600 dark:text-brand-500 font-semibold'
                                            : 'bg-white dark:bg-ink-900 border-ink-150 dark:border-ink-800 text-ink-900 dark:text-ink-100 hover:bg-ink-50 dark:hover:bg-ink-950/30'
                                        }
                                    `}
                                >
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm truncate">{dept.libelle}</p>
                                        <p className="text-xs text-ink-400 font-normal truncate">{dept.codeDepartement}</p>
                                    </div>
                                    <div className="flex items-center gap-1.5 pl-2 shrink-0">
                                        <button
                                            onClick={(e) => handleOpenEditDept(dept, e)}
                                            className="p-1 hover:bg-ink-200 dark:hover:bg-ink-800 rounded-sm text-ink-400 hover:text-ink-900 dark:hover:text-white cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={13} />
                                        </button>
                                        <button
                                            onClick={(e) => handleToggleDeptStatus(dept, e)}
                                            className={`px-1.5 py-0.5 rounded-sm text-[10px] font-bold uppercase transition-colors cursor-pointer ${
                                                dept.estActif
                                                    ? 'bg-success-500/10 text-success-500 hover:bg-danger-500/10 hover:text-danger-500'
                                                    : 'bg-ink-200 dark:bg-ink-800 text-ink-400 hover:bg-success-500/10 hover:text-success-500'
                                            }`}
                                        >
                                            {dept.estActif ? "Actif" : "Inactif"}
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Colonne Droite : Services rattachés au département sélectionné */}
                <div className="lg:col-span-2 bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    {selectedDepartement ? (
                        <div className="space-y-4">
                            <div className="flex flex-col sm:flex-row justify-between sm:items-center pb-4 border-b border-ink-150 dark:border-ink-800 gap-3">
                                <div>
                                    <h4 className="font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                                        <Layers size={18} className="text-brand-500" />
                                        Services rattachés à : <span className="text-brand-500">{selectedDepartement.libelle}</span>
                                    </h4>
                                    <p className="text-xs text-ink-400">Code Département : {selectedDepartement.codeDepartement}</p>
                                </div>
                                <button
                                    onClick={handleOpenCreateService}
                                    className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                                >
                                    <Plus size={16} />
                                    Ajouter un Service
                                </button>
                            </div>

                            {loadingServices ? (
                                <div className="flex justify-center items-center py-12">
                                    <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
                                </div>
                            ) : (
                                <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                                    <table className="w-full border-collapse text-left text-sm">
                                        <thead>
                                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                                <th className="px-4 py-3">Code Service</th>
                                                <th className="px-4 py-3">Nom du Service</th>
                                                <th className="px-4 py-3">Statut</th>
                                                <th className="px-4 py-3 text-right">Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                            {filteredServices.length > 0 ? (
                                                filteredServices.map((srv) => (
                                                    <tr key={srv.idService} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                        <td className="px-4 py-3 font-mono font-semibold text-brand-500">{srv.codeService}</td>
                                                        <td className="px-4 py-3 font-medium">{srv.libelle}</td>
                                                        <td className="px-4 py-3">
                                                            {srv.estActif ? (
                                                                <span className="inline-flex items-center gap-1.5 text-success-500 font-semibold text-xs">
                                                                    <span className="w-1.5 h-1.5 rounded-full bg-success-500"></span>
                                                                    Actif
                                                                </span>
                                                            ) : (
                                                                <span className="inline-flex items-center gap-1.5 text-ink-400 font-semibold text-xs">
                                                                    <span className="w-1.5 h-1.5 rounded-full bg-ink-400"></span>
                                                                    Désactivé
                                                                </span>
                                                            )}
                                                        </td>
                                                        <td className="px-4 py-3 text-right">
                                                            <div className="flex justify-end gap-2">
                                                                <button
                                                                    onClick={() => handleOpenEditService(srv)}
                                                                    className="p-1 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                                    title="Modifier"
                                                                >
                                                                    <Edit2 size={15} />
                                                                </button>
                                                                <button
                                                                    onClick={() => handleToggleServiceStatus(srv)}
                                                                    className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                                        srv.estActif
                                                                            ? 'text-danger-500 hover:bg-danger-500/10'
                                                                            : 'text-success-500 hover:bg-success-500/10'
                                                                    }`}
                                                                >
                                                                    {srv.estActif ? "Désactiver" : "Activer"}
                                                                </button>
                                                            </div>
                                                        </td>
                                                    </tr>
                                                ))
                                            ) : (
                                                <tr>
                                                    <td colSpan="4" className="px-4 py-8 text-center text-ink-400">
                                                        Aucun service rattaché à ce département.
                                                    </td>
                                                </tr>
                                            )}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    ) : (
                        <div className="flex flex-col items-center justify-center py-20 text-ink-400">
                            <Building size={36} className="mb-2 opacity-50" />
                            <p className="text-sm">Veuillez sélectionner un département à gauche.</p>
                        </div>
                    )}
                </div>
            </div>

            {/* Modale Département */}
            {showDeptModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingDept ? "Modifier le Département" : "Nouveau Département"}
                        </div>
                        <form onSubmit={handleSaveDept}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Département</label>
                                    <input
                                        type="text"
                                        value={deptFormData.codeDepartement}
                                        onChange={(e) => setDeptFormData({ ...deptFormData, codeDepartement: e.target.value })}
                                        disabled={!!editingDept}
                                        placeholder="Ex: LOG, LMD"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé</label>
                                    <input
                                        type="text"
                                        value={deptFormData.libelle}
                                        onChange={(e) => setDeptFormData({ ...deptFormData, libelle: e.target.value })}
                                        placeholder="Ex: Galaxam Logistics Core"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Date Fin Prévue (Optionnel)</label>
                                    <input
                                        type="date"
                                        value={deptFormData.dateFinPrevue}
                                        onChange={(e) => setDeptFormData({ ...deptFormData, dateFinPrevue: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowDeptModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                                >
                                    Enregistrer
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale Service */}
            {showServiceModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingService ? "Modifier le Service" : "Nouveau Service"}
                        </div>
                        <form onSubmit={handleSaveService}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Département de Rattachement</label>
                                    <select
                                        value={serviceFormData.idDepartement}
                                        onChange={(e) => setServiceFormData({ ...serviceFormData, idDepartement: parseInt(e.target.value) })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {departements.map((d) => (
                                            <option key={d.idDepartement} value={d.idDepartement}>
                                                {d.libelle} ({d.codeDepartement})
                                            </option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Service</label>
                                    <input
                                        type="text"
                                        value={serviceFormData.codeService}
                                        onChange={(e) => setServiceFormData({ ...serviceFormData, codeService: e.target.value })}
                                        placeholder="Ex: ADM, OPE"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé du Service</label>
                                    <input
                                        type="text"
                                        value={serviceFormData.libelle}
                                        onChange={(e) => setServiceFormData({ ...serviceFormData, libelle: e.target.value })}
                                        placeholder="Ex: Administration & RH"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowServiceModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                                >
                                    Enregistrer
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
