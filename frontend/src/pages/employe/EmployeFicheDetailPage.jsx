import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { 
    ArrowLeft, User, CreditCard, Phone, Users, ShieldAlert, 
    Building, Edit2, Power, Calendar, MapPin, Heart, Baby, 
    Landmark, Briefcase, FileText, CheckCircle, XCircle 
} from 'lucide-react';
import employeService from '@/api/employeService';
import EmployeFormModal from './EmployeFormModal';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';

export default function EmployeFicheDetailPage() {
    const { id } = useParams();
    const navigate = useNavigate();

    const [detailData, setDetailData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [activeTab, setActiveTab] = useState('personnel'); // 'personnel', 'famille', 'carriere', 'historique'

    // Modales
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (id) loadEmployeDetail();
    }, [id]);

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
        try {
            await employeService.desactiverEmploye(id, motifDesactivation);
            setIsDesactivationModalOpen(false);
            setActionSuccess('Employé désactivé avec succès.');
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError('Erreur lors de la désactivation.');
        }
    };

    const handleReactiver = async () => {
        try {
            await employeService.reactiverEmploye(id);
            setActionSuccess('Employé réactivé avec succès.');
            loadEmployeDetail();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError('Erreur lors de la réactivation.');
        }
    };

    const fmtDate = (d) => {
        if (!d) return '-';
        try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
    };

    const getFullPhotoUrl = (url) => {
        if (!url) return null;
        if (url.startsWith('http')) return url;
        const cleanUrl = url.startsWith('/api') ? url : `/api${url.startsWith('/') ? '' : '/'}${url}`;
        return `http://localhost:8080${cleanUrl}`;
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

    const InfoBlock = ({ label, value, mono = false, icon }) => (
        <div className="py-2">
            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400 mb-0.5">{label}</p>
            <div className="flex items-center gap-1.5">
                {icon && <span className="text-ink-300">{icon}</span>}
                <p className={`text-sm font-semibold text-ink-800 dark:text-ink-100 ${mono ? 'font-mono' : ''}`}>{value || '-'}</p>
            </div>
        </div>
    );

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

            {/* Navigation par Onglets Structurés & Équilibrés */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4 overflow-x-auto">
                    {[
                        { id: 'personnel', label: 'Fiche Personnelle', icon: User },
                        { id: 'famille', label: 'Famille & Contacts', icon: Users },
                        { id: 'carriere', label: 'Carrière & Contrats', icon: Briefcase },
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

                    {/* Onglet 1: Fiche Personnelle (Identité + Coordonnées + CNaPS & Banque) */}
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

                    {/* Onglet 2: Famille & Contacts */}
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

                    {/* Onglet 3: Carrière & Contrats (Espace réservé pour futurs sprints) */}
                    {activeTab === 'carriere' && (
                        <div className="p-8 text-center bg-ink-50 dark:bg-ink-950 border border-dashed border-ink-200 dark:border-ink-700 space-y-3">
                            <Briefcase size={36} className="mx-auto text-ink-300" />
                            <p className="text-sm font-bold text-ink-800 dark:text-white">Module Carrière & Contrats (Prochains Sprints)</p>
                            <p className="text-xs text-ink-400 max-w-md mx-auto">
                                Ce volet centralisera l'affectation à une filiale (Galaxam, Vatsy...), le poste, la catégorie socio-professionnelle, ainsi que l'historique des contrats (CDI, CDD, Avenants).
                            </p>
                        </div>
                    )}

                    {/* Onglet 4: Historique & Statut */}
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

            {/* Modale d'édition */}
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
