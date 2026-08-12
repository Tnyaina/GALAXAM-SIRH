import { useState, useEffect } from 'react';
import { CheckCircle, XCircle, FileText, UserCheck, Landmark } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';

import TypesContratList from './referentiels/TypesContratList';
import TypeContratFormModal from './referentiels/TypeContratFormModal';
import TypesIntervenantList from './referentiels/TypesIntervenantList';
import TypeIntervenantFormModal from './referentiels/TypeIntervenantFormModal';
import BanquesList from './referentiels/BanquesList';
import BanqueFormModal from './referentiels/BanqueFormModal';

export default function ReferentielsRhTab() {
    const [activeSection, setActiveSection] = useState('contrats'); // 'contrats' | 'intervenants' | 'banques'

    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [banques, setBanques] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Modale Contrat
    const [showContratModal, setShowContratModal] = useState(false);
    const [editingContrat, setEditingContrat] = useState(null);
    const [contratFormData, setContratFormData] = useState({ code: '', libelle: '', exigeDateFin: false });

    // Modale Intervenant
    const [showIntervenantModal, setShowIntervenantModal] = useState(false);
    const [editingIntervenant, setEditingIntervenant] = useState(null);
    const [intervenantFormData, setIntervenantFormData] = useState({
        code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true
    });

    // Modale Banque
    const [showBanqueModal, setShowBanqueModal] = useState(false);
    const [editingBanque, setEditingBanque] = useState(null);
    const [banqueFormData, setBanqueFormData] = useState({ codeBanque: '', nomBanque: '', codeSwift: '' });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [tcData, tiData, bData] = await Promise.all([
                organisationService.getTypesContrat(),
                organisationService.getTypesIntervenant(),
                organisationService.getBanques()
            ]);
            setTypesContrat(tcData);
            setTypesIntervenant(tiData);
            setBanques(bData);
        } catch (err) {
            setError("Erreur lors du chargement des référentiels RH.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Type Contrat
    const handleOpenCreateContrat = () => {
        setEditingContrat(null);
        setContratFormData({ code: '', libelle: '', exigeDateFin: false });
        setActionError(null);
        setShowContratModal(true);
    };

    const handleOpenEditContrat = (tc) => {
        setEditingContrat(tc);
        setContratFormData({ code: tc.code, libelle: tc.libelle, exigeDateFin: tc.exigeDateFin });
        setActionError(null);
        setShowContratModal(true);
    };

    const handleSaveContrat = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!contratFormData.code.trim() || !contratFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        try {
            if (editingContrat) {
                await organisationService.updateTypeContrat(editingContrat.idTypeContrat, contratFormData);
                setActionSuccess(`Type de contrat "${contratFormData.libelle}" modifié.`);
            } else {
                await organisationService.createTypeContrat(contratFormData);
                setActionSuccess(`Type de contrat "${contratFormData.libelle}" créé.`);
            }
            setShowContratModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Type Intervenant
    const handleOpenCreateIntervenant = () => {
        setEditingIntervenant(null);
        setIntervenantFormData({ code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true });
        setActionError(null);
        setShowIntervenantModal(true);
    };

    const handleOpenEditIntervenant = (ti) => {
        setEditingIntervenant(ti);
        setIntervenantFormData({
            code: ti.code, libelle: ti.libelle, soumisIrsa: ti.soumisIrsa, soumisIsi5: ti.soumisIsi5,
            soumisCnaps: ti.soumisCnaps, soumisSante: ti.soumisSante
        });
        setActionError(null);
        setShowIntervenantModal(true);
    };

    const handleSaveIntervenant = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!intervenantFormData.code.trim() || !intervenantFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        try {
            if (editingIntervenant) {
                await organisationService.updateTypeIntervenant(editingIntervenant.idTypeIntervenant, intervenantFormData);
                setActionSuccess(`Type d'intervenant "${intervenantFormData.libelle}" modifié.`);
            } else {
                await organisationService.createTypeIntervenant(intervenantFormData);
                setActionSuccess(`Type d'intervenant "${intervenantFormData.libelle}" créé.`);
            }
            setShowIntervenantModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Banque
    const handleOpenCreateBanque = () => {
        setEditingBanque(null);
        setBanqueFormData({ codeBanque: '', nomBanque: '', codeSwift: '' });
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleOpenEditBanque = (b) => {
        setEditingBanque(b);
        setBanqueFormData({ codeBanque: b.codeBanque, nomBanque: b.nomBanque, codeSwift: b.codeSwift || '' });
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleSaveBanque = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!banqueFormData.codeBanque.trim() || !banqueFormData.nomBanque.trim()) {
            setActionError("Le code banque et le nom de la banque sont obligatoires.");
            return;
        }
        try {
            if (editingBanque) {
                await organisationService.updateBanque(editingBanque.idBanque, banqueFormData);
                setActionSuccess(`Banque "${banqueFormData.nomBanque}" modifiée.`);
            } else {
                await organisationService.createBanque(banqueFormData);
                setActionSuccess(`Banque "${banqueFormData.nomBanque}" créée.`);
            }
            setShowBanqueModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

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

            {/* Onglets internes */}
            <div className="flex gap-2 border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 rounded-sm p-2 shadow-sm flex-wrap">
                <button
                    onClick={() => setActiveSection('contrats')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'contrats'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <FileText size={16} />
                    Types de Contrat ({typesContrat.length})
                </button>
                <button
                    onClick={() => setActiveSection('intervenants')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'intervenants'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <UserCheck size={16} />
                    Types d'Intervenant ({typesIntervenant.length})
                </button>
                <button
                    onClick={() => setActiveSection('banques')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'banques'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <Landmark size={16} />
                    Banques ({banques.length})
                </button>
            </div>

            {/* Section Contrats */}
            {activeSection === 'contrats' && (
                <TypesContratList
                    typesContrat={typesContrat}
                    onOpenCreate={handleOpenCreateContrat}
                    onOpenEdit={handleOpenEditContrat}
                />
            )}

            {/* Section Intervenants */}
            {activeSection === 'intervenants' && (
                <TypesIntervenantList
                    typesIntervenant={typesIntervenant}
                    onOpenCreate={handleOpenCreateIntervenant}
                    onOpenEdit={handleOpenEditIntervenant}
                />
            )}

            {/* Section Banques */}
            {activeSection === 'banques' && (
                <BanquesList
                    banques={banques}
                    onOpenCreate={handleOpenCreateBanque}
                    onOpenEdit={handleOpenEditBanque}
                />
            )}

            {/* Modale Type Contrat */}
            <TypeContratFormModal
                isOpen={showContratModal}
                onClose={() => setShowContratModal(false)}
                onSubmit={handleSaveContrat}
                editingContrat={editingContrat}
                formData={contratFormData}
                setFormData={setContratFormData}
                actionError={actionError}
            />

            {/* Modale Type Intervenant */}
            <TypeIntervenantFormModal
                isOpen={showIntervenantModal}
                onClose={() => setShowIntervenantModal(false)}
                onSubmit={handleSaveIntervenant}
                editingIntervenant={editingIntervenant}
                formData={intervenantFormData}
                setFormData={setIntervenantFormData}
                actionError={actionError}
            />

            {/* Modale Banque */}
            <BanqueFormModal
                isOpen={showBanqueModal}
                onClose={() => setShowBanqueModal(false)}
                onSubmit={handleSaveBanque}
                editingBanque={editingBanque}
                formData={banqueFormData}
                setFormData={setBanqueFormData}
                actionError={actionError}
            />
        </div>
    );
}
