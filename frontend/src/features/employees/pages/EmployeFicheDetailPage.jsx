import { useState, useEffect, useCallback, useMemo, useRef, lazy, Suspense } from 'react';
import { useParams, Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
    ArrowLeft, User, Users, Briefcase, XCircle, Folder,
    AlertTriangle, Calendar, Clock, ArrowRight, Compass, Banknote
} from 'lucide-react';
import employeService from '@/features/employees/api/employeService';
import documentRhService from '@/features/employees/api/documentRhService';
import EmployeHeaderCard from '@/features/employees/components/EmployeHeaderCard';
import Button from '@/components/ui/forms/Button';
import { Select } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import Breadcrumb from '@/components/ui/navigation/Breadcrumb';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';

// Lazy loading des sous-onglets & modales de la fiche employé pour optimiser les performances
const EmployeeFormModal = lazy(() => import('@/features/employees/components/modals/EmployeeFormModal'));
const CarriereTab = lazy(() => import('@/features/employees/components/carriere/CarriereTabContent'));
const FichePersonnelleTab = lazy(() => import('@/features/employees/components/tabs/FichePersonnelleTab'));
const DocumentsRhTab = lazy(() => import('@/features/employees/components/tabs/DocumentsRhTab'));
const FamilleContactsTab = lazy(() => import('@/features/employees/components/tabs/FamilleContactsTab'));
const CongesTab = lazy(() => import('@/features/employees/components/tabs/CongesTab'));
const PointageTab = lazy(() => import('@/features/employees/components/tabs/PointageTab'));
const EmployePaieTab = lazy(() => import('@/features/employees/components/tabs/EmployePaieTab'));
const UploadDocumentModal = lazy(() => import('@/features/employees/components/modals/UploadDocumentModal'));
import SEO from '@/components/system/SEO';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SlideOver from '@/components/ui/overlays/SlideOver';
import EmployeSideSummaryPanel from '@/features/employees/components/EmployeSideSummaryPanel';

const TabLoader = () => (
    <div className="p-6">
        <SkeletonTable rows={5} columns={4} />
    </div>
);

export default function EmployeFicheDetailPage() {
    // Contexte, navigation et hook métier
    const { id, section } = useParams();
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    // État local
    const [detailData, setDetailData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const detailRequestRef = useRef(0);
    const documentsRequestRef = useRef(0);
    const [isSummaryOpen, setIsSummaryOpen] = useState(false);
    const toast = useToast();

    const [searchParams, setSearchParams] = useSearchParams();
    // Données dérivées
    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');
    const canReadConges = hasPermission('CONGE_READ');
    const canValidateConges = hasPermission('CONGE_VALIDATE');
    const canReadPointages = hasPermission('POINTAGE_READ');
    const canReadPaie = hasPermission('PAIE_READ');
    const canWritePaie = hasPermission('PAIE_WRITE');
    const tabFromUrl = section || searchParams.get('tab');

    // Actions
    const handleTabChange = (tabId) => {
        if (section) {
            navigate(`/employes/${id}/${tabId}`);
            return;
        }
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tabId);
            return next;
        });
    };

    const [documentsData, setDocumentsData] = useState({ documents: [], obligatoiresManquants: [] });
    const [loadingDocs, setLoadingDocs] = useState(false);
    const [typesDoc, setTypesDoc] = useState([]);
    const [typesDocLoaded, setTypesDocLoaded] = useState(false);

    const mainTabs = useMemo(() => {
        const tabs = [
            { id: 'personnel', label: 'Fiche Personnelle', icon: <User size={16} /> },
            { id: 'famille', label: 'Famille & Contacts', icon: <Users size={16} /> },
            { id: 'documents', label: `Documents RH (${documentsData.documents.length})`, icon: <Folder size={16} /> },
            { id: 'carriere', label: 'Carrière & Affectation', icon: <Briefcase size={16} /> },
        ];
        if (canReadConges) {
            tabs.push({ id: 'conges', label: 'Congés & Absences', icon: <Calendar size={16} /> });
        }
        if (canReadPointages) {
            tabs.push({ id: 'pointage', label: 'Pointages & Présence', icon: <Clock size={16} /> });
        }
        if (canReadPaie) {
            tabs.push({ id: 'paie', label: 'Paie & Rémunération', icon: <Banknote size={16} /> });
        }
        return tabs;
    }, [documentsData.documents.length, canReadConges, canReadPointages, canReadPaie]);

    const activeTab = useMemo(() => {
        const allowedTabIds = mainTabs.map(t => t.id);
        return allowedTabIds.includes(tabFromUrl) ? tabFromUrl : 'personnel';
    }, [mainTabs, tabFromUrl]);

    // Modale Téléversement Document
    const [isUploadDocModalOpen, setIsUploadDocModalOpen] = useState(false);
    const [selectedTypeId, setSelectedTypeId] = useState('');
    const [selectedDocContractId, setSelectedDocContractId] = useState('');
    const [selectedFile, setSelectedFile] = useState(null);
    const [docDate, setDocDate] = useState('');
    const [docCommentaire, setDocCommentaire] = useState('');
    const [uploadingDoc, setUploadingDoc] = useState(false);
    const [docErrorMsg, setDocErrorMsg] = useState('');

    // Modale Suppression Document RH
    const [docToDelete, setDocToDelete] = useState(null);

    // Modale Édition Employé, Désactivation & Réactivation
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [formInitialStep, setFormInitialStep] = useState(0);
    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [isReactiverModalOpen, setIsReactiverModalOpen] = useState(false);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');
    const [submitting, setSubmitting] = useState(false);

    const loadEmployeDetail = useCallback(async (silent = false) => {
        const requestId = ++detailRequestRef.current;
        if (!silent) {
            setLoading(true);
        }
        setError('');
        try {
            const data = await employeService.getEmployeFicheDetail(id);
            if (requestId === detailRequestRef.current) setDetailData(data);
        } catch (err) {
            if (requestId === detailRequestRef.current) {
                console.error('Erreur chargement fiche employé:', err);
                setError('Impossible de charger la fiche de cet employé.');
            }
        } finally {
            if (!silent && requestId === detailRequestRef.current) {
                setLoading(false);
            }
        }
    }, [id]);

    const loadDocuments = useCallback(async () => {
        const requestId = ++documentsRequestRef.current;
        setLoadingDocs(true);
        try {
            const res = await documentRhService.getDocumentsByEmploye(id);
            if (requestId === documentsRequestRef.current) {
                setDocumentsData(res || { documents: [], obligatoiresManquants: [] });
            }
        } catch (err) {
            if (requestId === documentsRequestRef.current) {
                console.error('Erreur chargement documents RH:', err);
            }
        } finally {
            if (requestId === documentsRequestRef.current) setLoadingDocs(false);
        }
    }, [id]);

    useEffect(() => {
        if (id && !isNaN(Number(id))) {
            loadEmployeDetail();
            loadDocuments();
        } else if (id && isNaN(Number(id))) {
            setError("Identifiant d'employé invalide.");
            setLoading(false);
        }
    }, [id, loadEmployeDetail, loadDocuments]);

    // Les types de documents ne servent qu'au formulaire d'ajout. On évite
    // leur lecture sur chaque ouverture de fiche quand l'utilisateur n'ajoute rien.
    useEffect(() => {
        if (!isUploadDocModalOpen || typesDocLoaded) return undefined;
        let cancelled = false;
        documentRhService.getTypesDocument(true)
            .then((result) => {
                if (cancelled) return;
                const availableTypes = result || [];
                setTypesDoc(availableTypes);
                setTypesDocLoaded(true);
                setSelectedTypeId(current => current || availableTypes[0]?.idTypeDocument || '');
            })
            .catch((err) => {
                if (!cancelled) console.error('Erreur chargement types documents:', err);
            });
        return () => { cancelled = true; };
    }, [isUploadDocModalOpen, typesDocLoaded]);

    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.shiftKey && (e.key === 'S' || e.key === 's') && !['INPUT', 'TEXTAREA'].includes(e.target.tagName)) {
                e.preventDefault();
                setIsSummaryOpen(prev => !prev);
            }
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, []);

    const handleCloseForm = useCallback(() => {
        setIsFormOpen(false);
        setFormInitialStep(0);
    }, []);

    const handleOpenEdit = useCallback(() => {
        setFormInitialStep(0);
        setIsFormOpen(true);
    }, []);

    const handleOpenAddCompte = useCallback(() => {
        setFormInitialStep('banque');
        setIsFormOpen(true);
    }, []);

    const handleOpenFamille = useCallback(() => {
        setFormInitialStep('famille');
        setIsFormOpen(true);
    }, []);

    const handleOpenUrgence = useCallback(() => {
        setFormInitialStep('urgence');
        setIsFormOpen(true);
    }, []);

    const handleSaveForm = async (formData) => {
        if (submitting) return;
        setSubmitting(true);
        try {
            await employeService.updateEmploye(id, formData);
            toast({ title: 'Succès', message: 'Fiche employé modifiée avec succès.', type: 'success' });
            handleCloseForm();
            loadEmployeDetail(true);
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
            loadEmployeDetail(true);
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(getErrorMessage(err, 'Impossible de désactiver cet employé.'));
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
            loadEmployeDetail(true);
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(getErrorMessage(err, 'Impossible de réactiver cet employé.'));
        } finally {
            setSubmitting(false);
        }
    };

    // Actions Documents RH
    const handleOpenUploadModal = (typeId = '') => {
        setSelectedDocContractId('');
        setSelectedTypeId(typeId || '');
        setSelectedFile(null);
        setDocDate('');
        setDocCommentaire('');
        setDocErrorMsg('');
        setIsUploadDocModalOpen(true);
    };

    useEffect(() => {
        const listener = event => { if (Number(event.detail?.idEmploye) === Number(id)) loadDocuments(); };
        window.addEventListener('documents-rh-updated', listener);
        return () => window.removeEventListener('documents-rh-updated', listener);
    }, [id, loadDocuments]);

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
            await documentRhService.uploadDocument(id, selectedFile, selectedTypeId, docDate, docCommentaire, { idContrat: selectedDocContractId || undefined });
            toast({ title: 'Succès', message: 'Document RH téléversé avec succès.', type: 'success' });
            setIsUploadDocModalOpen(false);
            loadDocuments();
        } catch (err) {
            console.error('Erreur upload document:', err);
            setDocErrorMsg(getErrorMessage(err, "Impossible d'ajouter ce document."));
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

    const handleDownloadDoc = async (idDoc, nomFichier) => {
        try {
            await documentRhService.downloadDocument(idDoc, nomFichier);
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
                <Link to="/employes" className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold">
                    <ArrowLeft size={16} />
                    Retour au Répertoire du personnel
                </Link>
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 text-center space-y-3">
                    <XCircle size={40} className="mx-auto text-[var(--color-danger-500)]" />
                    <p className="text-base font-bold text-[var(--color-fg)]">{error || "Employé non trouvé"}</p>
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

    const hasNoActiveContract = emp?.hasContratActif !== undefined
        ? !emp.hasContratActif
        : (!emp?.idContratActif && !emp?.dateEmbauche && !emp?.nomFonction && (!emp?.statutContrat || emp?.statutContrat === 'Aucun contrat' || emp?.statutContrat === 'BROUILLON'));

    return (
        <div className="flex-1 min-h-0 flex flex-col gap-3 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans overflow-hidden">
            <SEO
                title={`Fiche de ${emp?.nom} ${emp?.prenom}`}
                description={`Fiche individuelle de ${emp?.nom} ${emp?.prenom} (${emp?.matricule})`}
            />
            {/* Zone Haute Fixe : Fil d'Ariane + En-tête Profil Employé */}
            <div className="shrink-0 flex flex-col gap-2">
                <Breadcrumb items={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Collaborateurs', href: '/employes' },
                    { label: `${emp?.nomComplet || `${emp?.nom || ''} ${emp?.prenom || ''}`.trim() || 'Fiche Collaborateur'}${emp?.matricule ? ` (${emp.matricule})` : ''}` }
                ]} />

                <EmployeHeaderCard
                    emp={emp}
                    canWrite={canWrite}
                    canValidate={canValidate}
                    onEdit={handleOpenEdit}
                    onDesactiver={() => setIsDesactivationModalOpen(true)}
                    onReactiver={() => setIsReactiverModalOpen(true)}
                    onOpenSummary={() => setIsSummaryOpen(true)}
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
            </div>

            {/* Conteneur Principal : Barre d'onglets et corps de dossier pleine largeur */}
            <div className="flex-1 min-h-0 w-full flex flex-col overflow-hidden">
                <div className="shrink-0 bg-[var(--color-surface)]">
                    <Tabs
                        tabs={mainTabs}
                        activeTab={activeTab}
                        onChange={handleTabChange}
                        variant="underline"
                    />
                </div>

                {/* Contenu de l'onglet actif avec scroll interne indépendant */}
                <div className="flex-1 min-h-0 overflow-y-auto p-4 sm:p-5">

                    {/* Onglet 1: Fiche Personnelle */}
                    {activeTab === 'personnel' && (
                        <Suspense fallback={<TabLoader label="Chargement de la fiche personnelle..." />}>
                            <FichePersonnelleTab
                                emp={emp}
                                comptes={comptes}
                                canWrite={canWrite}
                                onEdit={handleOpenEdit}
                                onAddCompte={handleOpenAddCompte}
                            />
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
                                canWrite={canWrite}
                                onEditFamille={handleOpenFamille}
                                onEditUrgence={handleOpenUrgence}
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
                                canValidate={canValidate}
                                autoOpenWorkflowId={searchParams.get('wf')}
                                onEmployeUpdated={loadEmployeDetail}
                            />
                        </Suspense>
                    )}

                    {/* Onglet 5: Congés & Absences */}
                    {activeTab === 'conges' && (
                        <Suspense fallback={<TabLoader label="Chargement des congés de l'employé..." />}>
                            <CongesTab idEmploye={id} employe={emp} canWrite={canWrite} canValidateConges={canValidateConges} />
                        </Suspense>
                    )}

                    {/* Onglet 6: Pointages & Présence */}
                    {activeTab === 'pointage' && (
                        <Suspense fallback={<TabLoader label="Chargement des pointages de l'employé..." />}>
                            <PointageTab idEmploye={id} employe={emp} />
                        </Suspense>
                    )}

                    {/* Onglet 7: Paie & Rémunération */}
                    {activeTab === 'paie' && (
                        <Suspense fallback={<TabLoader label="Chargement du dossier paie de l'employé..." />}>
                            <EmployePaieTab idEmploye={id} employe={emp} canWrite={canWritePaie} />
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
                        idEmploye={id}
                        selectedContractId={selectedDocContractId}
                        setSelectedContractId={setSelectedDocContractId}
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
                    <EmployeeFormModal
                        isOpen={isFormOpen}
                        onClose={handleCloseForm}
                        onSave={handleSaveForm}
                        employeData={detailData}
                        isSubmitting={submitting}
                        initialStep={formInitialStep}
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
                            <strong>Information :</strong> La désactivation passe le profil de l'employé en inactif. Veillez également à clôturer son contrat dans l'onglet <em>Carrière & Affectation</em>.
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

            {/* Volet Latéral Coulissant Synthèse Collaborateur (SlideOver KISS) */}
            <SlideOver
                isOpen={isSummaryOpen}
                onClose={() => setIsSummaryOpen(false)}
                title="Synthèse Collaborateur"
                subtitle={`${emp?.nomComplet || `${emp?.nom || ''} ${emp?.prenom || ''}`.trim() || 'Collaborateur'}${emp?.matricule ? ` (#${emp.matricule})` : ''} · ${emp?.nomFonction || 'Poste non renseigné'}`}
                icon={<User size={18} />}
                width="lg"
            >
                <EmployeSideSummaryPanel
                    emp={emp}
                    documentsCount={documentsData.documents.length}
                    activeTab={activeTab}
                    onNavigateTab={(tabId) => {
                        handleTabChange(tabId);
                        setIsSummaryOpen(false);
                    }}
                />
            </SlideOver>
        </div>
    );
}
