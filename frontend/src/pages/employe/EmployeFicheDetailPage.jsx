import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { 
    ArrowLeft, User, Phone, Users, ShieldAlert, 
    Building, Edit2, Power, Calendar, MapPin, Heart, Baby, 
    Landmark, Briefcase, FileText, CheckCircle, XCircle, Folder,
    Upload, Download, Trash2, Plus, AlertTriangle, 
    UserCheck, TrendingUp, Building2
} from 'lucide-react';
import employeService from '@/api/employeService';
import documentRhService from '@/api/documentRhService';
import contratService from '@/api/contratService';
import EmployeFormModal from './EmployeFormModal';
import ContratFormModal from './ContratFormModal';
import AffectationFormModal from './AffectationFormModal';
import ContratAffectationWizard from './ContratAffectationWizard';
import ClotureContratModal from './ClotureContratModal';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';
import Input, { Textarea, Select } from '@/components/ui/Input';
import { getFullPhotoUrl } from '@/utils/fileUtils';

const InfoBlock = ({ label, value, mono = false, icon }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400 mb-0.5">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-ink-300">{icon}</span>}
            <p className={`text-sm font-semibold text-ink-800 dark:text-ink-100 ${mono ? 'font-mono' : ''}`}>{value || '-'}</p>
        </div>
    </div>
);

export default function EmployeFicheDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [detailData, setDetailData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [activeTab, setActiveTab] = useState('personnel'); // 'personnel', 'documents', 'famille', 'carriere', 'historique'

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

    // État Contrats & Affectations
    const [carriereViewMode, setCarriereViewMode] = useState('timeline'); // 'timeline' | 'tables'
    const [contrats, setContrats] = useState([]);
    const [contratActif, setContratActif] = useState(null);
    const [affectations, setAffectations] = useState([]);
    const [affectationCourante, setAffectationCourante] = useState(null);
    const [loadingCarriere, setLoadingCarriere] = useState(false);

    // Modales Contrat & Affectation
    const [isWizardOpen, setIsWizardOpen] = useState(false);
    const [isClotureModalOpen, setIsClotureModalOpen] = useState(false);
    const [isContratModalOpen, setIsContratModalOpen] = useState(false);
    const [selectedContratToEdit, setSelectedContratToEdit] = useState(null);

    const [isAffectationModalOpen, setIsAffectationModalOpen] = useState(false);
    const [selectedAffectationToEdit, setSelectedAffectationToEdit] = useState(null);

    const [submittingCarriere, setSubmittingCarriere] = useState(false);

    // Modale Édition Employé & Désactivation
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');
    const [submitting, setSubmitting] = useState(false);

    const loadEmployeDetail = async () => {
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
    };

    const loadDocuments = async () => {
        setLoadingDocs(true);
        try {
            const res = await documentRhService.getDocumentsByEmploye(id);
            setDocumentsData(res || { documents: [], obligatoiresManquants: [] });
        } catch (err) {
            console.error('Erreur chargement documents RH:', err);
        } finally {
            setLoadingDocs(false);
        }
    };

    const loadTypesDoc = async () => {
        try {
            const res = await documentRhService.getTypesDocument(true);
            setTypesDoc(res || []);
        } catch (err) {
            console.error('Erreur chargement types documents:', err);
        }
    };

    const loadCarriere = async () => {
        setLoadingCarriere(true);
        try {
            const [cList, cActif, aList, aCourante] = await Promise.all([
                contratService.getContratsByEmploye(id),
                contratService.getContratActif(id),
                contratService.getAffectationsByEmploye(id),
                contratService.getAffectationCourante(id)
            ]);
            setContrats(cList || []);
            setContratActif(cActif || null);
            setAffectations(aList || []);
            setAffectationCourante(aCourante || null);
        } catch (err) {
            console.error('Erreur chargement données carrière:', err);
        } finally {
            setLoadingCarriere(false);
        }
    };

    useEffect(() => {
        if (id && !isNaN(Number(id))) {
            loadEmployeDetail();
            loadDocuments();
            loadTypesDoc();
            loadCarriere();
        } else if (id && isNaN(Number(id))) {
            setError("Identifiant d'employé invalide.");
            setLoading(false);
        }
    }, [id]);

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
            setError(err.response?.data || "Erreur lors de l'enregistrement de la fiche.");
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
            setError(err.response?.data || 'Erreur lors de la désactivation.');
        }
    };

    const handleReactiver = async () => {
        setError('');
        try {
            await employeService.reactiverEmploye(id);
            setActionSuccess('Employé réactivé avec succès.');
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(err.response?.data || 'Erreur lors de la réactivation.');
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

    // Actions Contrat & Affectation
    const handleSaveContrat = async (formData) => {
        setSubmittingCarriere(true);
        try {
            if (selectedContratToEdit) {
                await contratService.updateContrat(selectedContratToEdit.idContrat, formData);
                setActionSuccess('Contrat de travail modifié avec succès.');
            } else {
                await contratService.createContrat(id, formData);
                setActionSuccess('Nouveau contrat de travail créé.');
            }
            setIsContratModalOpen(false);
            loadCarriere();
        } catch (err) {
            console.error('Erreur enregistrement contrat:', err);
            setError(err.response?.data || "Erreur lors de l'enregistrement du contrat.");
        } finally {
            setSubmittingCarriere(false);
        }
    };

    const handleSaveAffectation = async (formData) => {
        setSubmittingCarriere(true);
        try {
            if (selectedAffectationToEdit) {
                await contratService.updateAffectation(selectedAffectationToEdit.idAffectation, formData);
                setActionSuccess('Affectation de carrière modifiée.');
            } else {
                await contratService.createAffectation(id, formData);
                setActionSuccess('Nouvelle affectation / mouvement de carrière enregistré.');
            }
            setIsAffectationModalOpen(false);
            loadCarriere();
        } catch (err) {
            console.error('Erreur enregistrement affectation:', err);
            setError(err.response?.data || "Erreur lors de l'enregistrement de l'affectation.");
        } finally {
            setSubmittingCarriere(false);
        }
    };

    const fmtDate = (d) => {
        if (!d) return '-';
        try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
    };

    const formatAriary = (amount) => {
        if (amount === undefined || amount === null || amount === '') return '- MGA';
        return new Intl.NumberFormat('fr-FR').format(amount) + ' MGA';
    };

    const formatBytes = (bytes) => {
        if (!bytes || bytes === 0) return '0 Octet';
        const k = 1024;
        const sizes = ['Octets', 'Ko', 'Mo', 'Go'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    };

    if (loading) {
        return (
            <div className="flex flex-col items-center justify-center py-20 space-y-4">
                <Loading size="lg" label="Chargement de la fiche employé..." />
            </div>
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

    return (
        <div className="space-y-6">
            {/* Lien retour */}
            <Link to="/employes" className="inline-flex items-center gap-2 text-sm text-brand-500 hover:underline font-semibold transition-all">
                <ArrowLeft size={16} />
                Retour au Répertoire du personnel
            </Link>

            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}

            {/* En-tête Profil Employé */}
            <div className="bg-white dark:bg-ink-900 rounded-sm shadow-sm border border-ink-150 dark:border-ink-700 p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
                <div className="flex items-center gap-5">
                    <div className="w-20 h-20 bg-ink-50 dark:bg-ink-800 border-2 border-ink-200 dark:border-ink-700 rounded-sm overflow-hidden flex items-center justify-center text-ink-300 flex-shrink-0">
                        {emp.photoUrl ? (
                            <img src={getFullPhotoUrl(emp.photoUrl)} alt="Photo" className="w-full h-full object-cover" />
                        ) : (
                            <User size={36} />
                        )}
                    </div>
                    <div className="space-y-1">
                        <div className="flex items-center gap-3">
                            <h1 className="text-2xl font-bold tracking-tight text-ink-900 dark:text-white">
                                {emp.nom} {emp.prenom}
                            </h1>
                            {emp.estActif ? (
                                <Badge variant="success" size="md" dot>Actif</Badge>
                            ) : (
                                <Badge variant="danger" size="md" dot>Inactif</Badge>
                            )}
                        </div>
                        <div className="flex items-center gap-4 text-xs text-ink-400">
                            <span>Matricule : <strong className="font-mono text-brand-500">{emp.matricule}</strong></span>
                            <span>•</span>
                            <span>CIN : <strong className="font-mono text-ink-700 dark:text-ink-200">{emp.cinNumero || '-'}</strong></span>
                            <span>•</span>
                            <span>Nationalité : <strong>{emp.nationalite}</strong></span>
                        </div>
                    </div>
                </div>

                {/* Actions en-tête */}
                <div className="flex items-center gap-3">
                    <Button
                        onClick={() => setIsFormOpen(true)}
                        variant="primary"
                        size="sm"
                        iconLeft={<Edit2 size={16} />}
                    >
                        Modifier la Fiche
                    </Button>
                    {emp.estActif ? (
                        <Button
                            onClick={() => setIsDesactivationModalOpen(true)}
                            variant="danger"
                            size="sm"
                            iconLeft={<Power size={16} />}
                        >
                            Désactiver
                        </Button>
                    ) : (
                        <Button
                            onClick={handleReactiver}
                            variant="success"
                            size="sm"
                            iconLeft={<Power size={16} />}
                        >
                            Réactiver
                        </Button>
                    )}
                </div>
            </div>

            {/* Navigation par Onglets Structurés */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4 overflow-x-auto">
                    {[
                        { id: 'personnel', label: 'Fiche Personnelle', icon: User },
                        { id: 'documents', label: `Documents RH (${documentsData.documents.length})`, icon: Folder },
                        { id: 'famille', label: 'Famille & Contacts', icon: Users },
                        { id: 'carriere', label: `Carrière & Contrats (${contrats.length})`, icon: Briefcase },
                        { id: 'historique', label: 'Historique & Statut', icon: FileText }
                    ].map(tab => {
                        const Icon = tab.icon;
                        return (
                            <button
                                key={tab.id}
                                onClick={() => setActiveTab(tab.id)}
                                className={`flex items-center gap-2 py-4 px-6 border-b-2 text-sm font-semibold transition-all whitespace-nowrap cursor-pointer ${
                                    activeTab === tab.id
                                        ? 'border-brand-500 text-brand-500 bg-white dark:bg-ink-900'
                                        : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                }`}
                            >
                                <Icon size={16} />
                                {tab.label}
                            </button>
                        );
                    })}
                </div>

                {/* Contenu de l'onglet actif */}
                <div className="p-6">

                    {/* Onglet 1: Fiche Personnelle */}
                    {activeTab === 'personnel' && (
                        <div className="space-y-8">
                            {/* Section État Civil & Identité */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <User size={14} /> État Civil & Identité
                                </h3>
                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
                                    <InfoBlock label="Sexe" value={emp.sexe === 'M' ? 'Masculin' : 'Féminin'} />
                                    <InfoBlock label="Date de Naissance" value={fmtDate(emp.dateNaissance)} icon={<Calendar size={14} />} />
                                    <InfoBlock label="Lieu de Naissance" value={emp.lieuNaissance} icon={<MapPin size={14} />} />
                                    <InfoBlock label="Situation Familiale" value={emp.situationFamiliale} icon={<Heart size={14} />} />
                                    <InfoBlock label="Nom du Père" value={emp.pereNomPrenom} />
                                    <InfoBlock label="Nom de la Mère" value={emp.mereNomPrenom} />
                                    <InfoBlock label="N° CIN" value={emp.cinNumero} mono />
                                    <InfoBlock label="Date Délivrance CIN" value={fmtDate(emp.cinDateDelivrance)} />
                                    <InfoBlock label="Lieu Délivrance CIN" value={emp.cinLieuDelivrance} />
                                    <InfoBlock label="N° Permis de Conduire" value={emp.permisConduireNumero} mono />
                                    {emp.cinDuplicataDate && <InfoBlock label="Duplicata CIN Date" value={fmtDate(emp.cinDuplicataDate)} />}
                                    {emp.cinDuplicataLieu && <InfoBlock label="Duplicata CIN Lieu" value={emp.cinDuplicataLieu} />}
                                </div>
                            </div>

                            {/* Section Coordonnées */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <Phone size={14} /> Domicile & Coordonnées
                                </h3>
                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6">
                                    <div className="sm:col-span-2">
                                        <InfoBlock label="Adresse Domicile" value={emp.adresse} icon={<MapPin size={14} />} />
                                    </div>
                                    <InfoBlock label="Ville" value={emp.ville} />
                                    <InfoBlock label="Téléphone" value={emp.telephone} mono icon={<Phone size={14} />} />
                                    <div className="sm:col-span-2">
                                        <InfoBlock label="Adresse Email" value={emp.email} />
                                    </div>
                                </div>
                            </div>

                            {/* Section CNaPS & Banque */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <Landmark size={14} /> Sécurité Sociale & Compte Bancaire
                                </h3>
                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6 mb-4">
                                    <InfoBlock label="N° Affiliation CNaPS" value={emp.numAffiliationCnaps} mono />
                                    <InfoBlock label="Date Déclaration CNaPS" value={fmtDate(emp.dateDeclaration)} />
                                </div>

                                {comptes.length > 0 ? (
                                    <div className="overflow-x-auto border border-ink-150 dark:border-ink-700">
                                        <table className="w-full text-sm">
                                            <thead>
                                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                                    <th className="px-4 py-3 text-left">Banque</th>
                                                    <th className="px-4 py-3 text-left">Agence</th>
                                                    <th className="px-4 py-3 text-left">N° Compte / RIB</th>
                                                    <th className="px-4 py-3 text-left">Titulaire</th>
                                                    <th className="px-4 py-3 text-left">Statut</th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                                {comptes.map((cb, i) => (
                                                    <tr key={i} className="text-ink-900 dark:text-ink-100">
                                                        <td className="px-4 py-3 font-semibold">{cb.nomBanque || cb.idBanque}</td>
                                                        <td className="px-4 py-3">{cb.agence || '-'}</td>
                                                        <td className="px-4 py-3 font-mono text-xs text-brand-500 font-bold">{cb.numCompteRib}</td>
                                                        <td className="px-4 py-3">{cb.titulaireCompte || '-'}</td>
                                                        <td className="px-4 py-3">
                                                            {cb.estPrincipal ? (
                                                                <Badge variant="primary" size="sm">Principal</Badge>
                                                            ) : (
                                                                <Badge variant="default" size="sm">Secondaire</Badge>
                                                            )}
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <p className="text-xs text-ink-400 italic">Aucun compte bancaire enregistré.</p>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Onglet 2: Documents RH */}
                    {activeTab === 'documents' && (
                        <div className="space-y-6">
                            {/* Bannière Alerte Documents Obligatoires Manquants */}
                            {documentsData.obligatoiresManquants && documentsData.obligatoiresManquants.length > 0 && (
                                <div className="bg-warning-500/10 border border-warning-500/30 p-4 rounded-sm flex items-start gap-3">
                                    <AlertTriangle size={20} className="text-warning-600 flex-shrink-0 mt-0.5" />
                                    <div className="space-y-1">
                                        <h4 className="text-xs font-bold uppercase tracking-wider text-warning-700">
                                            Documents Obligatoires Manquants ({documentsData.obligatoiresManquants.length})
                                        </h4>
                                        <p className="text-xs text-warning-600">
                                            Veuillez verser au dossier les pièces obligatoires manquantes pour compléter la conformité RH de cet employé :
                                        </p>
                                        <div className="flex flex-wrap gap-2 pt-1">
                                            {documentsData.obligatoiresManquants.map(m => (
                                                <button
                                                    key={m.idTypeDocument}
                                                    type="button"
                                                    onClick={() => handleOpenUploadModal(m.idTypeDocument)}
                                                    className="inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-bold bg-white dark:bg-ink-900 border border-warning-500/40 text-warning-700 hover:bg-warning-500/20 rounded-sm transition-all cursor-pointer"
                                                >
                                                    <Plus size={12} />
                                                    {m.libelle}
                                                </button>
                                            ))}
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Entête & Bouton Upload */}
                            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-2 border-b border-ink-150 dark:border-ink-700">
                                <div>
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2">
                                        <Folder size={14} /> Documents RH Déposés ({documentsData.documents.length})
                                    </h3>
                                    <p className="text-xs text-ink-400">Consultez, téléchargez ou ajoutez les pièces justificatives de l'employé</p>
                                </div>
                                <Button
                                    onClick={() => handleOpenUploadModal()}
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<Upload size={16} />}
                                >
                                    Téléverser un Document
                                </Button>
                            </div>

                            {/* Grille/Tableau des Documents */}
                            {loadingDocs ? (
                                <div className="py-12 flex justify-center">
                                    <Loading size="md" label="Chargement des documents..." />
                                </div>
                            ) : documentsData.documents.length === 0 ? (
                                <div className="p-10 text-center bg-ink-50 dark:bg-ink-950 border border-dashed border-ink-200 dark:border-ink-700 space-y-2">
                                    <Folder size={36} className="mx-auto text-ink-300" />
                                    <p className="text-sm font-bold text-ink-800 dark:text-white">Aucun document RH déposé</p>
                                    <p className="text-xs text-ink-400">Cliquez sur Téléverser un Document pour en ajouter un.</p>
                                </div>
                            ) : (
                                <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                                    <table className="w-full text-sm text-left">
                                        <thead>
                                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                                <th className="px-4 py-3">Document & Nom</th>
                                                <th className="px-4 py-3">Type & Catégorie</th>
                                                <th className="px-4 py-3">Taille</th>
                                                <th className="px-4 py-3">Date Document</th>
                                                <th className="px-4 py-3">Versé par</th>
                                                <th className="px-4 py-3 text-right">Actions</th>
                                            </tr>
                                        </thead>
                                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                            {documentsData.documents.map((doc) => (
                                                <tr key={doc.idDocument} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                    <td className="px-4 py-3">
                                                        <div className="flex items-center gap-3">
                                                            <div className="w-8 h-8 bg-brand-500/10 text-brand-500 flex items-center justify-center rounded-sm font-bold text-xs">
                                                                {doc.extension ? doc.extension.toUpperCase() : 'DOC'}
                                                            </div>
                                                            <div>
                                                                <p className="font-semibold text-ink-900 dark:text-white">{doc.nomOriginal}</p>
                                                                {doc.commentaire && <p className="text-xs text-ink-400 italic">{doc.commentaire}</p>}
                                                            </div>
                                                        </div>
                                                    </td>
                                                    <td className="px-4 py-3">
                                                        <div className="flex items-center gap-2">
                                                            <Badge variant="primary" size="sm">{doc.typeDocumentLibelle}</Badge>
                                                            {doc.typeDocumentObligatoire && (
                                                                <Badge variant="warning" size="sm">Obligatoire</Badge>
                                                            )}
                                                        </div>
                                                    </td>
                                                    <td className="px-4 py-3 text-xs font-mono text-ink-500">
                                                        {formatBytes(doc.tailleOctet)}
                                                    </td>
                                                    <td className="px-4 py-3 text-xs font-mono">
                                                        {fmtDate(doc.dateDocument)}
                                                    </td>
                                                    <td className="px-4 py-3 text-xs text-ink-500">
                                                        {doc.uploadedByIdentifiant || 'Admin'}
                                                    </td>
                                                    <td className="px-4 py-3 text-right">
                                                        <div className="flex justify-end gap-2">
                                                            <button
                                                                onClick={() => handleDownloadDoc(doc.idDocument, doc.nomOriginal)}
                                                                className="p-1.5 text-brand-500 hover:bg-brand-500/10 rounded-sm transition-all cursor-pointer"
                                                                title="Visualiser / Télécharger"
                                                            >
                                                                <Download size={16} />
                                                            </button>
                                                            <button
                                                                onClick={() => handleDeleteDoc(doc.idDocument, doc.nomOriginal)}
                                                                className="p-1.5 text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                                                title="Supprimer"
                                                            >
                                                                <Trash2 size={16} />
                                                            </button>
                                                        </div>
                                                    </td>
                                                </tr>
                                            ))}
                                        </tbody>
                                    </table>
                                </div>
                            )}
                        </div>
                    )}

                    {/* Onglet 3: Famille & Contacts */}
                    {activeTab === 'famille' && (
                        <div className="space-y-8">
                            {/* Conjoint */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <Heart size={14} /> Conjoint
                                </h3>
                                {conjoint && conjoint.nom ? (
                                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6 bg-ink-50 dark:bg-ink-950 p-4 border border-ink-150 dark:border-ink-700">
                                        <InfoBlock label="Nom" value={conjoint.nom} />
                                        <InfoBlock label="Prénom" value={conjoint.prenom} />
                                        <InfoBlock label="Date de Naissance" value={fmtDate(conjoint.dateNaissance)} />
                                        <InfoBlock label="Employeur / Société" value={conjoint.societe} />
                                        <InfoBlock label="Fonction" value={conjoint.fonction} />
                                    </div>
                                ) : (
                                    <p className="text-xs text-ink-400 italic">Aucun conjoint renseigné (Célibataire ou non déclaré).</p>
                                )}
                            </div>

                            {/* Enfants */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <Baby size={14} /> Enfants Déclarés ({enfants.length})
                                </h3>
                                {enfants.length > 0 ? (
                                    <div className="overflow-x-auto border border-ink-150 dark:border-ink-700">
                                        <table className="w-full text-sm">
                                            <thead>
                                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                                    <th className="px-4 py-3 text-left">Nom</th>
                                                    <th className="px-4 py-3 text-left">Prénom</th>
                                                    <th className="px-4 py-3 text-left">Sexe</th>
                                                    <th className="px-4 py-3 text-left">Date Naissance</th>
                                                    <th className="px-4 py-3 text-left">À Charge</th>
                                                </tr>
                                            </thead>
                                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                                {enfants.map((enf, i) => (
                                                    <tr key={i} className="hover:bg-ink-50/50 text-ink-900 dark:text-ink-100">
                                                        <td className="px-4 py-3 font-semibold">{enf.nom}</td>
                                                        <td className="px-4 py-3">{enf.prenom}</td>
                                                        <td className="px-4 py-3">{enf.sexe === 'M' ? 'Garçon' : 'Fille'}</td>
                                                        <td className="px-4 py-3 font-mono text-xs">{fmtDate(enf.dateNaissance)}</td>
                                                        <td className="px-4 py-3">
                                                            {enf.aCharge ? (
                                                                <Badge variant="success" size="sm">Oui</Badge>
                                                            ) : (
                                                                <Badge variant="default" size="sm">Non</Badge>
                                                            )}
                                                        </td>
                                                    </tr>
                                                ))}
                                            </tbody>
                                        </table>
                                    </div>
                                ) : (
                                    <p className="text-xs text-ink-400 italic">Aucun enfant enregistré.</p>
                                )}
                            </div>

                            {/* Contacts d'Urgence */}
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 mb-4 flex items-center gap-2">
                                    <ShieldAlert size={14} /> Contacts d'Urgence
                                </h3>
                                {contactsUrgence.length > 0 ? (
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        {contactsUrgence.map((ct, i) => (
                                            <div key={i} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 space-y-2">
                                                <div className="flex justify-between items-center">
                                                    <p className="text-sm font-bold text-ink-900 dark:text-white">{ct.nom} {ct.prenom}</p>
                                                    <Badge variant="primary" size="sm">{ct.lienParente}</Badge>
                                                </div>
                                                <div className="grid grid-cols-2 gap-2 text-xs">
                                                    <div>
                                                        <span className="text-ink-400 uppercase text-[9px] block">Téléphone</span>
                                                        <span className="font-mono font-bold text-brand-500">{ct.telephone || '-'}</span>
                                                    </div>
                                                    <div>
                                                        <span className="text-ink-400 uppercase text-[9px] block">Adresse</span>
                                                        <span>{ct.adresse || '-'}</span>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                ) : (
                                    <p className="text-xs text-ink-400 italic">Aucun contact d'urgence renseigné.</p>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Onglet 4: Carrière & Contrats */}
                    {activeTab === 'carriere' && (
                        <div className="space-y-8">
                            {/* Barre d'actions */}
                            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-2 border-b border-ink-150 dark:border-ink-700">
                                <div>
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2">
                                        <Briefcase size={14} /> Dossier Contrat & Carrière
                                    </h3>
                                    <p className="text-xs text-ink-400">Suivi contractuel, affectations de poste et salaire de base de l'employé</p>
                                </div>
                                <div className="flex items-center gap-3">
                                    <Button
                                        onClick={() => { setSelectedAffectationToEdit(null); setIsAffectationModalOpen(true); }}
                                        variant="outline"
                                        size="sm"
                                        iconLeft={<TrendingUp size={16} />}
                                    >
                                        + Nouvel Avenant / Poste seul
                                    </Button>
                                    <Button
                                        onClick={() => setIsWizardOpen(true)}
                                        variant="primary"
                                        size="sm"
                                        iconLeft={<Plus size={16} />}
                                    >
                                        + Évolution de Carrière (Assistant Contrat & Poste)
                                    </Button>
                                </div>
                            </div>

                            {loadingCarriere ? (
                                <div className="py-12 flex justify-center">
                                    <Loading size="md" label="Chargement du dossier de carrière..." />
                                </div>
                            ) : (
                                <>
                                    {/* Cartes Synthèse (Contrat Actif & Poste Courant) */}
                                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                                        {/* Carte 1: Contrat Actif */}
                                        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-6 rounded-sm shadow-sm relative overflow-hidden space-y-4">
                                            <div className="flex items-center justify-between">
                                                <h4 className="text-sm font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                                                    <Briefcase size={16} className="text-brand-500" />
                                                    Contrat de Travail Actif
                                                </h4>
                                                {contratActif && (
                                                    <div className="flex items-center gap-3">
                                                        <button
                                                            onClick={() => { setSelectedContratToEdit(contratActif); setIsContratModalOpen(true); }}
                                                            className="text-xs text-brand-500 font-semibold hover:underline flex items-center gap-1 cursor-pointer"
                                                        >
                                                            <Edit2 size={12} /> Modifier
                                                        </button>
                                                        <button
                                                            onClick={() => setIsClotureModalOpen(true)}
                                                            className="text-xs text-danger-500 font-semibold hover:underline flex items-center gap-1 cursor-pointer"
                                                        >
                                                            <XCircle size={12} /> Clôturer
                                                        </button>
                                                    </div>
                                                )}
                                            </div>

                                            {contratActif ? (
                                                <div className="space-y-4 pt-1">
                                                    <div className="flex items-center justify-between bg-ink-50 dark:bg-ink-950 p-3 border border-ink-150 dark:border-ink-800">
                                                        <div>
                                                            <p className="text-[10px] font-bold uppercase text-ink-400">Filiale d'Embauche</p>
                                                            <p className="text-sm font-bold text-ink-900 dark:text-white flex items-center gap-1.5">
                                                                <Building2 size={14} className="text-brand-500" />
                                                                {contratActif.filialeRaisonSociale} ({contratActif.filialeCode})
                                                            </p>
                                                        </div>
                                                        <Badge variant="primary" size="md">{contratActif.typeContratLibelle}</Badge>
                                                    </div>

                                                    <div className="grid grid-cols-2 gap-4">
                                                        <InfoBlock label="N° de Contrat" value={contratActif.numeroContrat || `N°${contratActif.idContrat}`} mono />
                                                        <InfoBlock label="Type d'Intervenant" value={contratActif.typeIntervenantLibelle} />
                                                        <InfoBlock label="Date Prise d'Effet" value={fmtDate(contratActif.dateDebut)} icon={<Calendar size={14} />} />
                                                        <InfoBlock label="Date de Fin (CDD)" value={fmtDate(contratActif.dateFin)} icon={<Calendar size={14} />} />
                                                        <InfoBlock
                                                            label="Période d'Essai"
                                                            value={contratActif.dateFinEssai ? `Jusqu'au ${fmtDate(contratActif.dateFinEssai)} ${contratActif.essaiRenouvele ? '(Renouvelée)' : ''}` : 'Sans essai'}
                                                            icon={<Calendar size={14} />}
                                                        />
                                                    </div>
                                                </div>
                                            ) : (
                                                <div className="py-8 text-center bg-ink-50/50 dark:bg-ink-950/20 border border-dashed border-ink-200 dark:border-ink-700 space-y-2">
                                                    <p className="text-xs font-bold text-ink-600 dark:text-ink-300">Aucun contrat actif enregistré</p>
                                                    <Button
                                                        onClick={() => setIsWizardOpen(true)}
                                                        variant="primary"
                                                        size="sm"
                                                    >
                                                        + Créer un contrat de travail
                                                    </Button>
                                                </div>
                                            )}
                                        </div>

                                        {/* Carte 2: Affectation & Poste Courant */}
                                        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-6 rounded-sm shadow-sm relative overflow-hidden space-y-4">
                                            <div className="flex items-center justify-between">
                                                <h4 className="text-sm font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                                                    <UserCheck size={16} className="text-brand-500" />
                                                    Affectation & Poste Courant
                                                </h4>
                                                {affectationCourante && (
                                                    <button
                                                        onClick={() => { setSelectedAffectationToEdit(affectationCourante); setIsAffectationModalOpen(true); }}
                                                        className="text-xs text-brand-500 font-semibold hover:underline flex items-center gap-1 cursor-pointer"
                                                    >
                                                        <Edit2 size={12} /> Modifier
                                                    </button>
                                                )}
                                            </div>

                                            {affectationCourante ? (
                                                <div className="space-y-4 pt-1">
                                                    {/* En-tête Rémunération de Base Brut */}
                                                    <div className="flex items-center justify-between bg-brand-500/10 border border-brand-500/20 p-3">
                                                        <div>
                                                            <p className="text-[10px] font-bold uppercase tracking-widest text-brand-600 dark:text-brand-400">Salaire de Base Brut</p>
                                                            <p className="text-lg font-bold font-mono text-brand-600 dark:text-brand-400">
                                                                {formatAriary(affectationCourante.salaireBase)}
                                                            </p>
                                                        </div>
                                                        <Badge variant="warning" size="md">{affectationCourante.modePaieLibelle}</Badge>
                                                    </div>

                                                    <div className="grid grid-cols-2 gap-4">
                                                        <InfoBlock label="Fonction / Poste Occupé" value={affectationCourante.fonctionLibelle} />
                                                        <InfoBlock label="Catégorie Professionnelle" value={affectationCourante.categorieLibelle} />
                                                        <InfoBlock label="Site d'Affectation" value={affectationCourante.siteLibelle} icon={<Building size={14} />} />
                                                        <InfoBlock label="Département" value={affectationCourante.departementLibelle} />
                                                        <InfoBlock label="Service" value={affectationCourante.serviceLibelle} />
                                                        <InfoBlock label="Responsable Direct (N+1)" value={affectationCourante.responsableNomComplet || 'Direction'} />
                                                    </div>
                                                </div>
                                            ) : (
                                                <div className="py-8 text-center bg-ink-50/50 dark:bg-ink-950/20 border border-dashed border-ink-200 dark:border-ink-700 space-y-2">
                                                    <p className="text-xs font-bold text-ink-600 dark:text-ink-300">Aucune affectation de poste courante</p>
                                                    <Button
                                                        onClick={() => setIsWizardOpen(true)}
                                                        variant="ghost"
                                                        size="sm"
                                                    >
                                                        + Affecter un poste & salaire
                                                    </Button>
                                                </div>
                                            )}
                                        </div>
                                    </div>

                                    {/* Barre de Commutateur de Vue (Frise Chronologique vs Tableaux) */}
                                    <div className="pt-6 border-t border-ink-150 dark:border-ink-700 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                                        <div>
                                            <h4 className="text-xs font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                                                <TrendingUp size={16} className="text-brand-500" />
                                                Historique & Trajectoire de Carrière
                                            </h4>
                                            <p className="text-xs text-ink-400 mt-0.5">Retracez l'évolution des contrats, postes et mouvements successifs</p>
                                        </div>

                                        <div className="flex items-center bg-ink-100 dark:bg-ink-800 p-1 rounded-sm text-xs border border-ink-200 dark:border-ink-700">
                                            <button
                                                type="button"
                                                onClick={() => setCarriereViewMode('timeline')}
                                                className={`px-3 py-1.5 font-bold transition-colors cursor-pointer ${carriereViewMode === 'timeline' ? 'bg-white dark:bg-ink-900 text-brand-500 shadow-xs' : 'text-ink-500 hover:text-ink-900 dark:hover:text-white'}`}
                                            >
                                                Frise Chronologique
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => setCarriereViewMode('tables')}
                                                className={`px-3 py-1.5 font-bold transition-colors cursor-pointer ${carriereViewMode === 'tables' ? 'bg-white dark:bg-ink-900 text-brand-500 shadow-xs' : 'text-ink-500 hover:text-ink-900 dark:hover:text-white'}`}
                                            >
                                                Tableaux Détaillés
                                            </button>
                                        </div>
                                    </div>

                                    {/* MODE 1 : FRISE CHRONOLOGIQUE (TIMELINE) */}
                                    {carriereViewMode === 'timeline' && (
                                        <div className="space-y-6 pt-2">
                                            {affectations.length === 0 && contrats.length === 0 ? (
                                                <div className="py-12 text-center bg-ink-50/50 dark:bg-ink-950/20 border border-dashed border-ink-200 dark:border-ink-700">
                                                    <p className="text-xs text-ink-400 italic">Aucun événement de carrière enregistré dans l'historique.</p>
                                                </div>
                                            ) : (
                                                <div className="relative pl-6 space-y-8 before:absolute before:left-2.5 before:top-3 before:bottom-3 before:w-0.5 before:bg-ink-200 dark:before:bg-ink-700">
                                                    {affectations.map((aff, index) => {
                                                        const matchingContrat = contrats.find(c => String(c.idContrat) === String(aff.idContrat));
                                                        const isCourante = Boolean(aff.estAffectationCourante && (matchingContrat ? matchingContrat.estActif : true));
                                                        return (
                                                            <div key={aff.idAffectation || index} className="relative group">
                                                                {/* Nœud de la timeline */}
                                                                <div className={`absolute -left-6 top-1.5 w-5 h-5 rounded-full border-2 bg-white dark:bg-ink-900 flex items-center justify-center ${isCourante ? 'border-brand-500 text-brand-500 shadow-sm' : 'border-ink-300 dark:border-ink-600 text-ink-400'}`}>
                                                                    <div className={`w-2 h-2 rounded-full ${isCourante ? 'bg-brand-500' : 'bg-ink-400'}`} />
                                                                </div>

                                                                {/* Carte d'événement de la frise */}
                                                                <div className={`border p-5 space-y-4 bg-white dark:bg-ink-900 transition-all ${isCourante ? 'border-brand-500/40 shadow-sm ring-1 ring-brand-500/20' : 'border-ink-150 dark:border-ink-700 hover:border-ink-300'}`}>
                                                                    {/* En-tête de l'événement */}
                                                                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-ink-150 dark:border-ink-700">
                                                                        <div className="space-y-1">
                                                                            <div className="flex items-center gap-2 flex-wrap">
                                                                                <Badge variant={isCourante ? 'success' : 'primary'} size="sm">
                                                                                    {aff.motifMouvementLibelle || 'Mouvement'}
                                                                                </Badge>
                                                                                <h5 className="text-sm font-bold text-ink-900 dark:text-white">
                                                                                    {aff.fonctionLibelle}
                                                                                </h5>
                                                                                {matchingContrat && (
                                                                                    <Badge variant="outline" size="sm">
                                                                                        {matchingContrat.typeContratLibelle}
                                                                                    </Badge>
                                                                                )}
                                                                            </div>
                                                                            {matchingContrat?.idContratPrecedent && (
                                                                                <p className="text-[11px] text-brand-600 dark:text-brand-400 font-medium font-mono">
                                                                                    ↳ Suite au Contrat {contrats.find(p => String(p.idContrat) === String(matchingContrat.idContratPrecedent))?.numeroContrat || `N°${matchingContrat.idContratPrecedent}`}
                                                                                </p>
                                                                            )}
                                                                        </div>

                                                                        <div className="flex items-center gap-3 text-xs">
                                                                            <span className="font-mono text-ink-400 bg-ink-50 dark:bg-ink-950 px-2 py-1 border border-ink-150 dark:border-ink-800">
                                                                                Du {fmtDate(aff.dateDebut)} {aff.dateFin ? `au ${fmtDate(aff.dateFin)}` : (isCourante ? 'à ce jour' : '(Fin de contrat)')}
                                                                            </span>
                                                                            {isCourante ? (
                                                                                <Badge variant="success" size="sm">Poste Actuel</Badge>
                                                                            ) : (
                                                                                <Badge variant="default" size="sm">Ancienne affectation</Badge>
                                                                            )}
                                                                        </div>
                                                                    </div>

                                                                    {/* Grille 3 colonnes d'informations détaillées */}
                                                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
                                                                        {/* Col 1: Structure & Emplacement */}
                                                                        <div className="space-y-1 bg-ink-50/50 dark:bg-ink-950/40 p-3 border border-ink-150 dark:border-ink-800">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Poste & Structure</p>
                                                                            <p><span className="text-ink-400">Fonction :</span> <strong>{aff.fonctionLibelle}</strong></p>
                                                                            <p><span className="text-ink-400">Département :</span> <strong>{aff.departementLibelle}</strong></p>
                                                                            <p><span className="text-ink-400">Service :</span> <strong>{aff.serviceLibelle}</strong></p>
                                                                            <p><span className="text-ink-400">Site :</span> <strong>{aff.siteLibelle}</strong></p>
                                                                        </div>

                                                                        {/* Col 2: Contrat & Rattachement */}
                                                                        <div className="space-y-1 bg-ink-50/50 dark:bg-ink-950/40 p-3 border border-ink-150 dark:border-ink-800">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Contrat & Filiale</p>
                                                                            <p><span className="text-ink-400">Type Contrat :</span> <strong>{matchingContrat?.typeContratLibelle || '-'}</strong></p>
                                                                            <p><span className="text-ink-400">N° Contrat :</span> <strong className="font-mono">{matchingContrat?.numeroContrat || (aff.idContrat ? `CT-${aff.idContrat}` : '-')}</strong></p>
                                                                            <p><span className="text-ink-400">Filiale :</span> <strong>{matchingContrat?.filialeRaisonSociale || '-'}</strong></p>
                                                                            <p><span className="text-ink-400">Intervenant :</span> <strong>{matchingContrat?.typeIntervenantLibelle || '-'}</strong></p>
                                                                        </div>

                                                                        {/* Col 3: Rémunération & Notes */}
                                                                        <div className="space-y-1 bg-ink-50/50 dark:bg-ink-950/40 p-3 border border-ink-150 dark:border-ink-800">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Rémunération & Mode</p>
                                                                            <p><span className="text-ink-400">Salaire de Base :</span> <strong className="font-mono text-brand-500 font-bold">{formatAriary(aff.salaireBase)}</strong></p>
                                                                            <p><span className="text-ink-400">Mode de Paie :</span> <strong>{aff.modePaieLibelle || '-'}</strong></p>
                                                                            <p><span className="text-ink-400">Classification :</span> <strong>{aff.categorieLibelle || '-'}</strong></p>
                                                                            {aff.commentaire && <p className="text-ink-400 italic text-[11px] mt-1">{aff.commentaire}</p>}
                                                                        </div>
                                                                    </div>

                                                                    {/* Bouton action au bas de chaque étape de carrière */}
                                                                    <div className="flex justify-end pt-1">
                                                                        <button
                                                                            onClick={() => { setSelectedAffectationToEdit(aff); setIsAffectationModalOpen(true); }}
                                                                            className="text-xs text-brand-500 font-semibold hover:underline flex items-center gap-1 cursor-pointer"
                                                                        >
                                                                            <Edit2 size={12} /> Éditer cette affectation
                                                                        </button>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        );
                                                    })}
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    {/* MODE 2 : TABLEAUX DÉTAILLÉS */}
                                    {carriereViewMode === 'tables' && (
                                        <div className="space-y-8 pt-2">
                                            {/* Historique des Contrats de Travail */}
                                            <div className="space-y-4">
                                                <div className="flex items-center justify-between pb-2 border-b border-ink-150 dark:border-ink-700">
                                                    <h4 className="text-xs font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                                                        <Briefcase size={14} className="text-brand-500" />
                                                        Historique des Contrats de Travail ({contrats.length})
                                                    </h4>
                                                </div>

                                                {contrats.length === 0 ? (
                                                    <p className="text-xs text-ink-400 italic">Aucun contrat enregistré.</p>
                                                ) : (
                                                    <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                                                        <table className="w-full text-sm text-left">
                                                            <thead>
                                                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                                                    <th className="px-4 py-3">Réf / N° Contrat</th>
                                                                    <th className="px-4 py-3">Type & Intervenant</th>
                                                                    <th className="px-4 py-3">Filiale</th>
                                                                    <th className="px-4 py-3">Période du Contrat</th>
                                                                    <th className="px-4 py-3">Motif Sortie</th>
                                                                    <th className="px-4 py-3 text-center">Statut</th>
                                                                    <th className="px-4 py-3 text-right">Action</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                                                {contrats.map((c) => (
                                                                    <tr key={c.idContrat} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                                        <td className="px-4 py-3 font-mono font-semibold text-xs">
                                                                            {c.numeroContrat || `CT-${c.idContrat}`}
                                                                            {c.idContratPrecedent && (
                                                                                <span className="block text-[10px] text-ink-400 font-normal font-mono">
                                                                                    ↳ Suite au Contrat {contrats.find(p => String(p.idContrat) === String(c.idContratPrecedent))?.numeroContrat || `N°${c.idContratPrecedent}`}
                                                                                </span>
                                                                            )}
                                                                        </td>
                                                                        <td className="px-4 py-3 font-semibold text-xs">
                                                                            <Badge variant="primary" size="sm">{c.typeContratLibelle}</Badge>
                                                                            <span className="block text-[11px] text-ink-400 font-normal mt-0.5">{c.typeIntervenantLibelle}</span>
                                                                        </td>
                                                                        <td className="px-4 py-3 text-xs">
                                                                            {c.filialeRaisonSociale} ({c.filialeCode})
                                                                        </td>
                                                                        <td className="px-4 py-3 text-xs font-mono">
                                                                            Du {fmtDate(c.dateDebut)} {c.dateFin ? `au ${fmtDate(c.dateFin)}` : '(Indéterminée)'}
                                                                        </td>
                                                                        <td className="px-4 py-3 text-xs italic text-ink-400">
                                                                            {c.motifSortieLibelle || '-'}
                                                                        </td>
                                                                        <td className="px-4 py-3 text-center">
                                                                            {c.estActif ? (
                                                                                <Badge variant="success" size="sm">Actif</Badge>
                                                                            ) : (
                                                                                <Badge variant="default" size="sm">Inactif / Clôturé</Badge>
                                                                            )}
                                                                        </td>
                                                                        <td className="px-4 py-3 text-right">
                                                                            <button
                                                                                onClick={() => { setSelectedContratToEdit(c); setIsContratModalOpen(true); }}
                                                                                className="text-xs text-brand-500 font-semibold hover:underline cursor-pointer"
                                                                            >
                                                                                Éditer
                                                                            </button>
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                )}
                                            </div>

                                            {/* Historique des Affectations & Avenants */}
                                            <div className="space-y-4 pt-4 border-t border-ink-150 dark:border-ink-700">
                                                <h4 className="text-xs font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                                                    <TrendingUp size={14} className="text-brand-500" />
                                                    Historique des Affectations & Mouvements de Carrière ({affectations.length})
                                                </h4>

                                                {affectations.length === 0 ? (
                                                    <p className="text-xs text-ink-400 italic">Aucun mouvement enregistré dans l'historique.</p>
                                                ) : (
                                                    <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-sm">
                                                        <table className="w-full text-sm text-left">
                                                            <thead>
                                                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                                                    <th className="px-4 py-3">Motif Mouvement</th>
                                                                    <th className="px-4 py-3">Poste & Fonction</th>
                                                                    <th className="px-4 py-3">Structure (Dépt/Service)</th>
                                                                    <th className="px-4 py-3">Salaire de Base</th>
                                                                    <th className="px-4 py-3">Période</th>
                                                                    <th className="px-4 py-3 text-right">Statut</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                                                {affectations.map((aff) => {
                                                                    const matchingContrat = contrats.find(c => String(c.idContrat) === String(aff.idContrat));
                                                                    const isCourante = Boolean(aff.estAffectationCourante && (matchingContrat ? matchingContrat.estActif : true));
                                                                    return (
                                                                    <tr key={aff.idAffectation} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                                        <td className="px-4 py-3">
                                                                            <Badge variant="primary" size="sm">{aff.motifMouvementLibelle}</Badge>
                                                                            {aff.commentaire && <p className="text-xs text-ink-400 italic mt-0.5">{aff.commentaire}</p>}
                                                                        </td>
                                                                        <td className="px-4 py-3 font-semibold">
                                                                            {aff.fonctionLibelle}
                                                                            <span className="block text-xs text-ink-400 font-normal">{aff.categorieLibelle}</span>
                                                                        </td>
                                                                        <td className="px-4 py-3 text-xs">
                                                                            {aff.departementLibelle} ➔ {aff.serviceLibelle}
                                                                            <span className="block text-ink-400">Site : {aff.siteLibelle}</span>
                                                                        </td>
                                                                        <td className="px-4 py-3 font-mono font-bold text-brand-500 text-xs">
                                                                            {formatAriary(aff.salaireBase)}
                                                                        </td>
                                                                        <td className="px-4 py-3 text-xs font-mono">
                                                                            Du {fmtDate(aff.dateDebut)} {aff.dateFin ? `au ${fmtDate(aff.dateFin)}` : (isCourante ? 'à ce jour' : '(Fin de contrat)')}
                                                                        </td>
                                                                        <td className="px-4 py-3 text-right">
                                                                            {isCourante ? (
                                                                                <Badge variant="success" size="sm">Courante</Badge>
                                                                            ) : (
                                                                                <Badge variant="default" size="sm">Clôturée</Badge>
                                                                            )}
                                                                        </td>
                                                                    </tr>
                                                                );
                                                                })}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                )}
                                            </div>
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    )}

                    {/* Onglet 5: Historique & Statut */}
                    {activeTab === 'historique' && (
                        <div className="space-y-6">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                <FileText size={14} /> Historique du Dossier
                            </h3>
                            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-6 bg-ink-50 dark:bg-ink-950 p-4 border border-ink-150 dark:border-ink-700">
                                <InfoBlock label="Statut du Compte" value={emp.estActif ? "Actif" : "Inactif"} />
                                {!emp.estActif && (
                                    <>
                                        <InfoBlock label="Date de Désactivation" value={fmtDate(emp.dateDesactivation)} />
                                        <InfoBlock label="Motif de Désactivation" value={emp.motifDesactivation || '-'} />
                                    </>
                                )}
                            </div>
                        </div>
                    )}

                </div>
            </div>

            {/* Modales Contrat & Affectation */}
            <ContratFormModal
                isOpen={isContratModalOpen}
                onClose={() => setIsContratModalOpen(false)}
                onSave={handleSaveContrat}
                idEmploye={id}
                contratData={selectedContratToEdit}
                isSubmitting={submittingCarriere}
            />

            <AffectationFormModal
                isOpen={isAffectationModalOpen}
                onClose={() => setIsAffectationModalOpen(false)}
                onSave={handleSaveAffectation}
                idEmploye={id}
                affectationData={selectedAffectationToEdit}
                isSubmitting={submittingCarriere}
            />

            {/* Assistant Guidé Carrière & Contrat */}
            <ContratAffectationWizard
                isOpen={isWizardOpen}
                onClose={() => setIsWizardOpen(false)}
                onSuccess={() => {
                    loadCarriere();
                    setActionSuccess('Nouveau dossier contrat et affectation enregistré avec succès.');
                }}
                idEmploye={id}
                employeNomComplet={`${detailData?.employe?.nom || ''} ${detailData?.employe?.prenom || ''}`}
            />

            {/* Modale Clôture de Contrat */}
            <ClotureContratModal
                isOpen={isClotureModalOpen}
                onClose={() => setIsClotureModalOpen(false)}
                onSuccess={() => {
                    loadCarriere();
                    setActionSuccess('Le contrat de travail a été clôturé avec succès.');
                }}
                contratActif={contratActif}
                idEmploye={id}
            />

            {/* Modale Téléversement Document RH */}
            {isUploadDocModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                            <Upload size={18} className="text-brand-500" />
                            Téléverser un Document RH
                        </div>
                        <form onSubmit={handleUploadDocSubmit}>
                            <div className="p-6 space-y-4">
                                {docErrorMsg && (
                                    <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {docErrorMsg}
                                    </div>
                                )}
                                <Select
                                    label="Type de Document"
                                    required
                                    value={selectedTypeId}
                                    onChange={(e) => setSelectedTypeId(e.target.value)}
                                >
                                    <option value="">-- Sélectionner --</option>
                                    {typesDoc.map(t => (
                                        <option key={t.idTypeDocument} value={t.idTypeDocument}>
                                            {t.libelle} {t.obligatoire ? '(Obligatoire)' : ''} — [{t.categorie}]
                                        </option>
                                    ))}
                                </Select>

                                <div className="space-y-1">
                                    <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">
                                        Fichier (PDF, JPG, PNG) <span className="text-danger-500">*</span>
                                    </label>
                                    <input
                                        type="file"
                                        required
                                        onChange={(e) => setSelectedFile(e.target.files?.[0] || null)}
                                        className="w-full text-xs text-ink-700 dark:text-ink-300 border border-ink-200 dark:border-ink-700 p-2 rounded-sm bg-ink-50 dark:bg-ink-950"
                                    />
                                </div>

                                <Input
                                    label="Date du Document"
                                    type="date"
                                    value={docDate}
                                    onChange={(e) => setDocDate(e.target.value)}
                                />

                                <Textarea
                                    label="Commentaire / Remarque"
                                    rows={2}
                                    value={docCommentaire}
                                    onChange={(e) => setDocCommentaire(e.target.value)}
                                    placeholder="Remarque éventuelle (numéro de diplôme, version, etc.)"
                                />
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <Button type="button" onClick={() => setIsUploadDocModalOpen(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm" loading={uploadingDoc}>
                                    Téléverser
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale d'édition Employé */}
            <EmployeFormModal
                isOpen={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSave={handleSaveForm}
                employeData={detailData}
                isSubmitting={submitting}
            />

            {/* Modale de désactivation */}
            {isDesactivationModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
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
                                <div className="space-y-1">
                                    <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">Motif de Désactivation</label>
                                    <select
                                        value={motifDesactivation}
                                        onChange={(e) => setMotifDesactivation(e.target.value)}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
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
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-danger-500 hover:bg-danger-600 text-white rounded-sm transition-colors cursor-pointer"
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
