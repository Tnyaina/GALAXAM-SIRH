import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom';
import { 
    ArrowLeft, User, Users, Briefcase, FileText, CheckCircle, XCircle, Folder,
    Power, AlertTriangle, Calendar
} from 'lucide-react';
import employeService from '@/api/employe/employeService';
import documentRhService from '@/api/employe/documentRhService';
import EmployeHeaderCard from '@/components/employe/EmployeHeaderCard';
import Button from '@/components/ui/forms/Button';
import Loading from '@/components/ui/feedback/Loading';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import { useAuth } from '@/context/AuthContext';

// Lazy loading des sous-onglets & modales de la fiche employé pour optimiser les performances
const EmployeFormModal = lazy(() => import('./dossier/EmployeFormModal'));
const CarriereTab = lazy(() => import('./carriere/CarriereTab'));
const FichePersonnelleTab = lazy(() => import('@/components/employe/tabs/FichePersonnelleTab'));
const DocumentsRhTab = lazy(() => import('@/components/employe/tabs/DocumentsRhTab'));
const FamilleContactsTab = lazy(() => import('@/components/employe/tabs/FamilleContactsTab'));
const HistoriqueStatutTab = lazy(() => import('@/components/employe/tabs/HistoriqueStatutTab'));
const CongesTab = lazy(() => import('@/components/employe/tabs/CongesTab'));
const UploadDocumentModal = lazy(() => import('@/components/employe/tabs/UploadDocumentModal'));
import SEO from '@/components/system/SEO';

const VALID_TABS = ['personnel', 'conges', 'documents', 'famille', 'carriere', 'historique'];

const TabLoader = ({ label = "Chargement..." }) => (
    <div className="p-6">
        <SkeletonTable rows={5} columns={4} />
    </div>
);

export default function EmployeFicheDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('EMPLOYE_WRITE');

    const [detailData, setDetailData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [searchParams, setSearchParams] = useSearchParams();
    const tabFromUrl = searchParams.get('tab');
    const activeTab = VALID_TABS.includes(tabFromUrl) ? tabFromUrl : 'personnel';

    const handleTabChange = (tabId) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    // État Documents RH
    const [documentsData, setDocumentsData] = useState({ documents: [], obligatoiresManquants: [] });
    const [loadingDocs, setLoadingDocs] = useState(false);
    const [typesDoc, setTypesDoc] = useState([]);

    // Modale Téléversement Document
    const [isUploadDocModalOpen, setIsUploadDocModalOpen] = useState(false);
    const [selectedTypeId, setSelectedTypeId] = useState('');
    const [selectedFile, setSelectedFile] = useState(null);
    const [docDate, setDocDate] = useState('');
    const [docCommentaire, setDocCommentaire] = useState('');
    const [uploadingDoc, setUploadingDoc] = useState(false);
    const [docErrorMsg, setDocErrorMsg] = useState('');

    // Modale Édition Employé & Désactivation
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');
    const [submitting, setSubmitting] = useState(false);

    const loadEmployeDetail = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const data = await employeService.getEmployeFicheDetail(id);
            setDetailData(data);
        } catch (err) {
            console.error('Erreur chargement fiche employé:', err);
            setError('Impossible de charger la fiche de cet employé.');
        } finally {
            setLoading(false);
        }
    }, [id]);

    const loadDocuments = useCallback(async () => {
        setLoadingDocs(true);
        try {
            const res = await documentRhService.getDocumentsByEmploye(id);
            setDocumentsData(res || { documents: [], obligatoiresManquants: [] });
        } catch (err) {
            console.error('Erreur chargement documents RH:', err);
        } finally {
            setLoadingDocs(false);
        }
    }, [id]);

    const loadTypesDoc = useCallback(async () => {
        try {
            const res = await documentRhService.getTypesDocument(true);
            setTypesDoc(res || []);
        } catch (err) {
            console.error('Erreur chargement types documents:', err);
        }
    }, []);

    useEffect(() => {
        if (id && !isNaN(Number(id))) {
            loadEmployeDetail();
            loadDocuments();
            loadTypesDoc();
        } else if (id && isNaN(Number(id))) {
            setError("Identifiant d'employé invalide.");
            setLoading(false);
        }
    }, [id, loadEmployeDetail, loadDocuments, loadTypesDoc]);

    const handleSaveForm = async (formData) => {
        setSubmitting(true);
        setActionSuccess('');
        try {
            await employeService.updateEmploye(id, formData);
            setActionSuccess('Fiche employé modifiée avec succès.');
            setIsFormOpen(false);
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur modification employé:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || "Erreur lors de l'enregistrement de la fiche.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleDesactiverConfirm = async (e) => {
        e.preventDefault();
        setError('');
        try {
            await employeService.desactiverEmploye(id, motifDesactivation);
            setIsDesactivationModalOpen(false);
            setActionSuccess('Employé désactivé avec succès.');
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la désactivation.');
        }
    };

    // Actions Documents RH
    const handleOpenUploadModal = (typeId = '') => {
        setSelectedTypeId(typeId || (typesDoc[0]?.idTypeDocument || ''));
        setSelectedFile(null);
        setDocDate('');
        setDocCommentaire('');
        setDocErrorMsg('');
        setIsUploadDocModalOpen(true);
    };

    const handleUploadDocSubmit = async (e) => {
        e.preventDefault();
        setDocErrorMsg('');

        if (!selectedTypeId) {
            setDocErrorMsg('Veuillez sélectionner un type de document.');
            return;
        }
        if (!selectedFile) {
            setDocErrorMsg('Veuillez choisir un fichier à téléverser.');
            return;
        }

        setUploadingDoc(true);
        try {
            await documentRhService.uploadDocument(id, selectedFile, selectedTypeId, docDate, docCommentaire);
            setActionSuccess('Document RH téléversé avec succès.');
            setIsUploadDocModalOpen(false);
            loadDocuments();
        } catch (err) {
            console.error('Erreur upload document:', err);
            setDocErrorMsg(err.response?.data || "Erreur lors du téléversement du document.");
        } finally {
            setUploadingDoc(false);
        }
    };

    const handleDeleteDoc = async (idDoc, nomDoc) => {
        if (!window.confirm(`Voulez-vous vraiment supprimer le document "${nomDoc}" ?`)) return;
        try {
            await documentRhService.deleteDocument(idDoc);
            setActionSuccess(`Document "${nomDoc}" supprimé.`);
            loadDocuments();
        } catch (err) {
            console.error('Erreur suppression document:', err);
            setError('Erreur lors de la suppression du document.');
        }
    };

    const handleDownloadDoc = async (idDoc, nomOriginal) => {
        try {
            await documentRhService.downloadDocument(idDoc, nomOriginal);
        } catch (err) {
            console.error('Erreur téléchargement document:', err);
            setError('Erreur lors du téléchargement du document.');
        }
    };

    if (loading) {
        return (
            <Loading display="page" label="Chargement de la fiche employé..." size="lg" />
        );
    }

    if (error || !detailData) {
        return (
            <div className="space-y-6">
                <Link to="/employes" className="inline-flex items-center gap-2 text-sm text-brand-500 hover:underline font-semibold">
                    <ArrowLeft size={16} />
                    Retour au Répertoire du personnel
                </Link>
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-8 text-center space-y-3">
                    <XCircle size={40} className="mx-auto text-danger-500" />
                    <p className="text-base font-bold text-ink-900 dark:text-white">{error || "Employé non trouvé"}</p>
                    <Button onClick={() => navigate('/employes')} variant="outline" size="sm">
                        Retour au Répertoire
                    </Button>
                </div>
            </div>
        );
    }

    const emp = detailData.employe || detailData;
    const conjoint = detailData.conjoint;
    const enfants = detailData.enfants || [];
    const contactsUrgence = detailData.contactsUrgence || [];
    const comptes = detailData.comptesBancaires || [];

    const mainTabs = [
        { id: 'personnel', label: 'Fiche Personnelle', icon: <User size={16} /> },
        { id: 'conges', label: 'Congés & Absences', icon: <Calendar size={16} /> },
        { id: 'documents', label: `Documents RH (${documentsData.documents.length})`, icon: <Folder size={16} /> },
        { id: 'famille', label: 'Famille & Contacts', icon: <Users size={16} /> },
        { id: 'carriere', label: 'Carrière & Affectation', icon: <Briefcase size={16} /> },
        { id: 'historique', label: 'Historique & Statut', icon: <FileText size={16} /> }
    ];

    return (
        <div className="space-y-6">
            <SEO 
                title={`Fiche de ${emp?.nom} ${emp?.prenom}`} 
                description={`Fiche individuelle de ${emp?.nom} ${emp?.prenom} (${emp?.matricule})`} 
            />
            {/* Lien retour */}
            <Link to="/employes" className="inline-flex items-center gap-2 text-sm text-brand-500 hover:underline font-semibold ">
                <ArrowLeft size={16} />
                Retour au Répertoire du personnel
            </Link>

            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}

            {/* En-tête Profil Employé */}
            <EmployeHeaderCard
                emp={emp}
                canWrite={canWrite}
                onEdit={() => setIsFormOpen(true)}
                onDesactiver={() => setIsDesactivationModalOpen(true)}
            />

            {/* Navigation par Onglets Structurés */}
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <Tabs 
                    tabs={mainTabs} 
                    activeTab={activeTab} 
                    onChange={handleTabChange} 
                    variant="underline" 
                />

                {/* Contenu de l'onglet actif (Chargement lazy) */}
                <div className="p-6">

                    {/* Onglet 1: Fiche Personnelle */}
                    {activeTab === 'personnel' && (
                        <Suspense fallback={<TabLoader label="Chargement de la fiche personnelle..." />}>
                            <FichePersonnelleTab emp={emp} comptes={comptes} />
                        </Suspense>
                    )}

                    {/* Onglet 2: Congés & Absences */}
                    {activeTab === 'conges' && (
                        <Suspense fallback={<TabLoader label="Chargement des congés de l'employé..." />}>
                            <CongesTab idEmploye={id} canWrite={canWrite} />
                        </Suspense>
                    )}

                    {/* Onglet 2: Documents RH */}
                    {activeTab === 'documents' && (
                        <Suspense fallback={<TabLoader label="Chargement des documents RH..." />}>
                            <DocumentsRhTab
                                documentsData={documentsData}
                                loadingDocs={loadingDocs}
                                canWrite={canWrite}
                                onOpenUploadModal={handleOpenUploadModal}
                                onDownloadDoc={handleDownloadDoc}
                                onDeleteDoc={handleDeleteDoc}
                            />
                        </Suspense>
                    )}

                    {/* Onglet 3: Famille & Contacts */}
                    {activeTab === 'famille' && (
                        <Suspense fallback={<TabLoader label="Chargement des contacts familiaux..." />}>
                            <FamilleContactsTab
                                conjoint={conjoint}
                                enfants={enfants}
                                contactsUrgence={contactsUrgence}
                            />
                        </Suspense>
                    )}

                    {/* Onglet 4: Carrière & Affectation */}
                    {activeTab === 'carriere' && (
                        <Suspense fallback={<TabLoader label="Chargement du dossier carrière..." />}>
                            <CarriereTab
                                idEmploye={id}
                                employe={emp}
                                nomEmploye={`${emp.nom || ''} ${emp.prenom || ''}`}
                                matriculeEmploye={emp.matricule}
                                canWrite={canWrite}
                                autoOpenWorkflowId={searchParams.get('wf')}
                                onEmployeUpdated={loadEmployeDetail}
                            />
                        </Suspense>
                    )}

                    {/* Onglet 5: Historique & Statut */}
                    {activeTab === 'historique' && (
                        <Suspense fallback={<TabLoader label="Chargement de l'historique du dossier..." />}>
                            <HistoriqueStatutTab emp={emp} />
                        </Suspense>
                    )}

                </div>
            </div>

            {/* Modale Téléversement Document RH (Lazy) */}
            {isUploadDocModalOpen && (
                <Suspense fallback={null}>
                    <UploadDocumentModal
                        isOpen={isUploadDocModalOpen}
                        onClose={() => setIsUploadDocModalOpen(false)}
                        onSubmit={handleUploadDocSubmit}
                        typesDoc={typesDoc}
                        selectedTypeId={selectedTypeId}
                        setSelectedTypeId={setSelectedTypeId}
                        setSelectedFile={setSelectedFile}
                        docDate={docDate}
                        setDocDate={setDocDate}
                        docCommentaire={docCommentaire}
                        setDocCommentaire={setDocCommentaire}
                        uploadingDoc={uploadingDoc}
                        docErrorMsg={docErrorMsg}
                    />
                </Suspense>
            )}

            {/* Modale Édition Employé (Lazy) */}
            {isFormOpen && (
                <Suspense fallback={null}>
                    <EmployeFormModal
                        isOpen={isFormOpen}
                        onClose={() => setIsFormOpen(false)}
                        onSave={handleSaveForm}
                        employeData={detailData}
                        isSubmitting={submitting}
                    />
                </Suspense>
            )}

            {/* Modale de désactivation */}
            {isDesactivationModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                            <Power size={18} className="text-danger-500" />
                            Désactiver l'employé
                        </div>
                        <form onSubmit={handleDesactiverConfirm}>
                            <div className="p-6 space-y-4">
                                <p className="text-sm text-ink-500 dark:text-ink-400">
                                    Spécifiez le motif de désactivation de l'employé{' '}
                                    <strong className="text-ink-800 dark:text-white">{emp.nom} {emp.prenom}</strong>.
                                </p>
                                <div className="bg-warning-500/10 border border-warning-500/30 p-3 rounded-none text-xs text-warning-700 dark:text-warning-400 flex items-start gap-2">
                                    <AlertTriangle size={16} className="flex-shrink-0 mt-0.5" />
                                    <span>
                                        <strong>Note RH :</strong> La désactivation passe le profil de l'employé en inactif. Veillez également à clôturer son contrat ou créer le mouvement RH de fin de contrat correspondant dans l'onglet <em>Carrière & Affectation</em>.
                                    </span>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">Motif de Désactivation</label>
                                    <select
                                        value={motifDesactivation}
                                        onChange={(e) => setMotifDesactivation(e.target.value)}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-none bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        <option value="Demission">Démission</option>
                                        <option value="Licenciement">Licenciement</option>
                                        <option value="Fin_contrat">Fin de contrat</option>
                                        <option value="Retraite">Retraite</option>
                                        <option value="Deces">Décès</option>
                                        <option value="Autre">Autre</option>
                                    </select>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setIsDesactivationModalOpen(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-none  cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-danger-500 hover:bg-danger-600 text-white rounded-none  cursor-pointer"
                                >
                                    Confirmer la Désactivation
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
