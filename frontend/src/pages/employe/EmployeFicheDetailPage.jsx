import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom';
import { 
    ArrowLeft, User, Users, Briefcase, XCircle, Folder,
    Power, AlertTriangle, Calendar, Clock, ArrowRight, Compass
} from 'lucide-react';
import employeService from '@/api/employe/employeService';
import documentRhService from '@/api/employe/documentRhService';
import EmployeHeaderCard from '@/components/employe/EmployeHeaderCard';
import EmployeSideSummaryPanel from '@/components/employe/EmployeSideSummaryPanel';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import Breadcrumb from '@/components/ui/navigation/Breadcrumb';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';

// Lazy loading des sous-onglets & modales de la fiche employé pour optimiser les performances
const EmployeFormModal = lazy(() => import('./dossier/EmployeFormModal'));
const CarriereTab = lazy(() => import('./carriere/CarriereTab'));
const FichePersonnelleTab = lazy(() => import('@/components/employe/tabs/FichePersonnelleTab'));
const DocumentsRhTab = lazy(() => import('@/components/employe/tabs/DocumentsRhTab'));
const FamilleContactsTab = lazy(() => import('@/components/employe/tabs/FamilleContactsTab'));
const CongesTab = lazy(() => import('@/components/employe/tabs/CongesTab'));
const PointageTab = lazy(() => import('@/components/employe/tabs/PointageTab'));
const UploadDocumentModal = lazy(() => import('@/components/employe/tabs/UploadDocumentModal'));
import SEO from '@/components/system/SEO';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

const VALID_TABS = ['personnel', 'documents', 'famille', 'carriere', 'conges', 'pointage'];

const TabLoader = () => (
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
    const toast = useToast();

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

    // Modale Suppression Document RH
    const [docToDelete, setDocToDelete] = useState(null);

    // Modale Édition Employé, Désactivation & Réactivation
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [isReactiverModalOpen, setIsReactiverModalOpen] = useState(false);
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
        if (submitting) return;
        setSubmitting(true);
        try {
            await employeService.updateEmploye(id, formData);
            toast({ title: 'Succès', message: 'Fiche employé modifiée avec succès.', type: 'success' });
            setIsFormOpen(false);
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur modification employé:', err);
            throw err;
        } finally {
            setSubmitting(false);
        }
    };

    const handleDesactiverConfirm = async () => {
        if (submitting) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.desactiverEmploye(id, motifDesactivation);
            setIsDesactivationModalOpen(false);
            toast({ title: 'Succès', message: 'Employé désactivé avec succès.', type: 'success' });
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la désactivation.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleReactiverConfirm = async () => {
        if (submitting) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.reactiverEmploye(id);
            setIsReactiverModalOpen(false);
            toast({ title: 'Succès', message: 'Employé réactivé avec succès.', type: 'success' });
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la réactivation.');
        } finally {
            setSubmitting(false);
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
        if (uploadingDoc) return;
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
            toast({ title: 'Succès', message: 'Document RH téléversé avec succès.', type: 'success' });
            setIsUploadDocModalOpen(false);
            loadDocuments();
        } catch (err) {
            console.error('Erreur upload document:', err);
            setDocErrorMsg(err.response?.data || "Erreur lors du téléversement du document.");
        } finally {
            setUploadingDoc(false);
        }
    };

    const handleDeleteDoc = (idDoc, nomDoc) => {
        setDocToDelete({ idDoc, nomDoc });
    };

    const handleConfirmDeleteDoc = async () => {
        if (!docToDelete || submitting) return;
        setSubmitting(true);
        try {
            await documentRhService.deleteDocument(docToDelete.idDoc);
            toast({ title: 'Succès', message: `Document "${docToDelete.nomDoc}" supprimé.`, type: 'success' });
            loadDocuments();
        } catch (err) {
            console.error('Erreur suppression document:', err);
            setError('Erreur lors de la suppression du document.');
        } finally {
            setSubmitting(false);
            setDocToDelete(null);
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

    const hasNoActiveContract = !emp?.idContratActif && !emp?.dateEmbauche && !emp?.nomFonction && (!emp?.statutContrat || emp?.statutContrat === 'Aucun contrat' || emp?.statutContrat === 'BROUILLON');

    const mainTabs = [
        { id: 'personnel', label: 'Fiche Personnelle', icon: <User size={16} /> },
        { id: 'documents', label: `Documents RH (${documentsData.documents.length})`, icon: <Folder size={16} /> },
        { id: 'famille', label: 'Famille & Contacts', icon: <Users size={16} /> },
        { id: 'carriere', label: 'Carrière & Affectation', icon: <Briefcase size={16} /> },
        { id: 'conges', label: 'Congés & Absences', icon: <Calendar size={16} /> },
        { id: 'pointage', label: 'Pointages & Présence', icon: <Clock size={16} /> }
    ];

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO 
                title={`Fiche de ${emp?.nom} ${emp?.prenom}`} 
                description={`Fiche individuelle de ${emp?.nom} ${emp?.prenom} (${emp?.matricule})`} 
            />
            {/* Fil d'Ariane */}
            <Breadcrumb items={[
                { label: 'Tableau de bord', href: '/' },
                { label: 'Collaborateurs', href: '/employes' },
                { label: `${emp?.nomComplet || `${emp?.nom || ''} ${emp?.prenom || ''}`.trim() || 'Fiche Collaborateur'}${emp?.matricule ? ` (${emp.matricule})` : ''}` }
            ]} />

            {/* En-tête Profil Employé */}
            <EmployeHeaderCard
                emp={emp}
                canWrite={canWrite}
                onEdit={() => setIsFormOpen(true)}
                onDesactiver={() => setIsDesactivationModalOpen(true)}
                onReactiver={() => setIsReactiverModalOpen(true)}
            />

            {/* Bandeau d'orientation post-création si pas de contrat actif */}
            {hasNoActiveContract && canWrite && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-l-4 border-l-[var(--color-brand-500)] p-3 sm:p-3.5 flex flex-col sm:flex-row sm:items-center justify-between gap-3 shadow-2xs rounded-none">
                    <div className="flex items-center gap-2.5">
                        <div className="w-8 h-8 flex items-center justify-center bg-[var(--color-bg-subtle)] text-[var(--color-brand-500)] shrink-0 border border-[var(--color-border-subtle)]">
                            <Compass size={16} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2 flex-wrap">
                                <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">Étape suivante recommandée : Contrat & Affectation</p>
                                <span className="px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/20">
                                    Nouveau collaborateur
                                </span>
                            </div>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                Cet employé n'a pas encore de contrat actif. Configurez son contrat initial pour l'intégrer aux plannings, feuilles de pointage et gestion des congés.
                            </p>
                        </div>
                    </div>
                    <Button 
                        size="xs" 
                        variant="primary" 
                        onClick={() => handleTabChange('carriere')}
                        className="shrink-0"
                        iconRight={<ArrowRight size={13} />}
                    >
                        Configurer le contrat
                    </Button>
                </div>
            )}

            {/* Grille Master-Detail (Wireframe 1) : Contenu Principal (9/12) + Volet Contextuel (3/12) */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-3.5">
                {/* Colonne Principale : Onglets & Contenu Métier */}
                <div className="lg:col-span-8 xl:col-span-9 bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xs overflow-hidden">
                    <Tabs 
                        tabs={mainTabs} 
                        activeTab={activeTab} 
                        onChange={handleTabChange} 
                        variant="underline" 
                    />

                    {/* Contenu de l'onglet actif (Chargement lazy) */}
                    <div className="p-4 sm:p-4.5">

                        {/* Onglet 1: Fiche Personnelle */}
                        {activeTab === 'personnel' && (
                            <Suspense fallback={<TabLoader label="Chargement de la fiche personnelle..." />}>
                                <FichePersonnelleTab emp={emp} comptes={comptes} />
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

                        {/* Onglet 5: Congés & Absences */}
                        {activeTab === 'conges' && (
                            <Suspense fallback={<TabLoader label="Chargement des congés de l'employé..." />}>
                                <CongesTab idEmploye={id} employe={emp} canWrite={canWrite} />
                            </Suspense>
                        )}

                        {/* Onglet 6: Pointages & Présence */}
                        {activeTab === 'pointage' && (
                            <Suspense fallback={<TabLoader label="Chargement des pointages de l'employé..." />}>
                                <PointageTab idEmploye={id} />
                            </Suspense>
                        )}

                    </div>
                </div>

                {/* Volet Contextuel Permanent (Wireframe 1) : Sticky */}
                <aside className="lg:col-span-4 xl:col-span-3">
                    <div className="lg:sticky lg:top-3.5">
                        <EmployeSideSummaryPanel
                            emp={emp}
                            documentsCount={documentsData.documents.length}
                            activeTab={activeTab}
                            onNavigateTab={handleTabChange}
                            canWrite={canWrite}
                        />
                    </div>
                </aside>
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
            <ConfirmDialog
                isOpen={isDesactivationModalOpen}
                onClose={() => setIsDesactivationModalOpen(false)}
                onConfirm={handleDesactiverConfirm}
                title="Désactiver l'employé"
                message={`Spécifiez le motif de désactivation de l'employé ${emp.nom} ${emp.prenom}.`}
                confirmLabel="Confirmer la Désactivation"
                cancelLabel="Annuler"
                variant="danger"
                loading={submitting}
            >
                <div className="space-y-3 pt-2">
                    <div className="bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-2.5 rounded-none text-xs text-[var(--color-warning-700)] flex items-start gap-2">
                        <AlertTriangle size={15} className="shrink-0 mt-0.5" />
                        <span>
                            <strong>Note RH :</strong> La désactivation passe le profil de l'employé en inactif. Veillez également à clôturer son contrat dans l'onglet <em>Carrière & Affectation</em>.
                        </span>
                    </div>
                    <Select
                        label="Motif de Désactivation"
                        value={motifDesactivation}
                        onChange={(e) => setMotifDesactivation(e.target.value)}
                    >
                        <option value="Demission">Démission</option>
                        <option value="Licenciement">Licenciement</option>
                        <option value="Fin_contrat">Fin de contrat</option>
                        <option value="Retraite">Retraite</option>
                        <option value="Deces">Décès</option>
                        <option value="Autre">Autre</option>
                    </Select>
                </div>
            </ConfirmDialog>

            {/* Modale de réactivation */}
            <ConfirmDialog
                isOpen={isReactiverModalOpen}
                onClose={() => setIsReactiverModalOpen(false)}
                onConfirm={handleReactiverConfirm}
                title="Réactiver l'employé"
                message={`Êtes-vous sûr de vouloir réactiver le collaborateur "${emp.nom} ${emp.prenom}" ? Son profil repassera en statut Actif.`}
                confirmLabel="Réactiver le profil"
                cancelLabel="Annuler"
                variant="primary"
                loading={submitting}
            />

            {/* Modale de confirmation de suppression de document */}
            <ConfirmDialog
                isOpen={!!docToDelete}
                onClose={() => setDocToDelete(null)}
                onConfirm={handleConfirmDeleteDoc}
                title="Supprimer le document RH"
                message={`Êtes-vous sûr de vouloir supprimer définitivement le document "${docToDelete?.nomDoc}" ? Cette action est irréversible.`}
                confirmLabel="Supprimer définitivement"
                cancelLabel="Annuler"
                variant="danger"
                loading={submitting}
            />
        </div>
    );
}
