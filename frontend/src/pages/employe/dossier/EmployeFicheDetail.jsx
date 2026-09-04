import { X, User, CreditCard, Phone, ShieldAlert, Edit2, Power, Calendar, MapPin, Heart, Baby, Landmark } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { getFullPhotoUrl } from '@/utils/fileUtils';

const InfoRow = ({ label, value, icon, mono = false }) => (
    <div className="flex items-start gap-3 py-2">
        {icon && <div className="text-ink-300 mt-0.5 flex-shrink-0">{icon}</div>}
        <div className="flex-1">
            <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400">{label}</p>
            <p className={`text-sm text-ink-800 dark:text-ink-100 ${mono ? 'font-mono' : ''}`}>{value || '-'}</p>
        </div>
    </div>
);

const SectionTitle = ({ icon, children }) => (
    <div className="flex items-center gap-2 py-3 border-b border-ink-150 dark:border-ink-700 mb-3">
        <div className="text-brand-500">{icon}</div>
        <h3 className="text-sm font-bold uppercase tracking-wider text-ink-800 dark:text-white">{children}</h3>
    </div>
);

export default function EmployeFicheDetail({ isOpen, onClose, detailData, onEdit, onToggleStatus }) {
    if (!isOpen || !detailData) return null;

    const emp = detailData.employe || detailData;
    const conjoint = detailData.conjoint;
    const enfants = detailData.enfants || [];
    const contactsUrgence = detailData.contactsUrgence || [];
    const comptes = detailData.comptesBancaires || [];

    const fmtDate = (d) => {
        if (!d) return '-';
        try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between">
                    <div className="flex items-center gap-4">
                        <div className="w-14 h-14 bg-ink-50 dark:bg-ink-800 border border-ink-200 dark:border-ink-700 flex items-center justify-center text-ink-300 overflow-hidden">
                            {emp.photoUrl ? (
                                <img 
                                    src={getFullPhotoUrl(emp.photoUrl)} 
                                    alt="Photo" 
                                    className="w-full h-full object-cover" 
                                    onError={(e) => {
                                        e.currentTarget.onerror = null;
                                        e.currentTarget.style.display = 'none';
                                        if (e.currentTarget.nextElementSibling) {
                                            e.currentTarget.nextElementSibling.classList.remove('hidden');
                                        }
                                    }}
                                />
                            ) : null}
                            <User size={28} className={emp.photoUrl ? 'hidden' : ''} />
                        </div>
                        <div>
                            <h2 className="text-lg font-bold text-ink-900 dark:text-white">
                                {emp.nom} {emp.prenom}
                            </h2>
                            <div className="flex items-center gap-2 mt-0.5">
                                <span className="text-xs font-mono font-semibold text-brand-500">{emp.matricule}</span>
                                {emp.estActif ? (
                                    <Badge variant="success" size="sm" dot>Actif</Badge>
                                ) : (
                                    <Badge variant="danger" size="sm" dot>Inactif</Badge>
                                )}
                            </div>
                        </div>
                    </div>
                    <button onClick={onClose} className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-none  cursor-pointer">
                        <X size={18} />
                    </button>
                </div>

                {/* Contenu scrollable */}
                <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4">

                    {/* Identité */}
                    <div>
                        <SectionTitle icon={<User size={16} />}>Identité</SectionTitle>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                            <InfoRow label="Sexe" value={emp.sexe === 'M' ? 'Masculin' : 'Féminin'} />
                            <InfoRow label="Date de Naissance" value={fmtDate(emp.dateNaissance)} icon={<Calendar size={14} />} />
                            <InfoRow label="Lieu de Naissance" value={emp.lieuNaissance} icon={<MapPin size={14} />} />
                            <InfoRow label="Nationalité" value={emp.nationalite} />
                            <InfoRow label="Situation Familiale" value={emp.situationFamiliale} icon={<Heart size={14} />} />
                            <InfoRow label="Père" value={emp.pereNomPrenom} />
                            <InfoRow label="Mère" value={emp.mereNomPrenom} />
                        </div>
                    </div>

                    {/* CIN */}
                    <div>
                        <SectionTitle icon={<CreditCard size={16} />}>CIN & Documents</SectionTitle>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                            <InfoRow label="N° CIN" value={emp.cinNumero} mono />
                            <InfoRow label="Date Délivrance" value={fmtDate(emp.cinDateDelivrance)} />
                            <InfoRow label="Lieu Délivrance" value={emp.cinLieuDelivrance} />
                            {emp.cinDuplicataDate && <InfoRow label="Duplicata Date" value={fmtDate(emp.cinDuplicataDate)} />}
                            {emp.cinDuplicataLieu && <InfoRow label="Duplicata Lieu" value={emp.cinDuplicataLieu} />}
                            {emp.permisConduireNumero && <InfoRow label="N° Permis" value={emp.permisConduireNumero} mono />}
                        </div>
                    </div>

                    {/* Coordonnées */}
                    <div>
                        <SectionTitle icon={<Phone size={16} />}>Coordonnées</SectionTitle>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                            <div className="md:col-span-2">
                                <InfoRow label="Adresse" value={emp.adresse} icon={<MapPin size={14} />} />
                            </div>
                            <InfoRow label="Ville" value={emp.ville} />
                            <InfoRow label="Téléphone" value={emp.telephone} mono />
                            <InfoRow label="Email" value={emp.email} />
                        </div>
                    </div>

                    {/* Conjoint */}
                    {conjoint && conjoint.nom && (
                        <div>
                            <SectionTitle icon={<Heart size={16} />}>Conjoint</SectionTitle>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                                <InfoRow label="Nom" value={conjoint.nom} />
                                <InfoRow label="Prénom" value={conjoint.prenom} />
                                <InfoRow label="Date Naissance" value={fmtDate(conjoint.dateNaissance)} />
                                <InfoRow label="Société" value={conjoint.societe} />
                                <InfoRow label="Fonction" value={conjoint.fonction} />
                            </div>
                        </div>
                    )}

                    {/* Enfants */}
                    {enfants.length > 0 && (
                        <div>
                            <SectionTitle icon={<Baby size={16} />}>Enfants ({enfants.length})</SectionTitle>
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
                                            <tr key={i} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                <td className="px-4 py-3">{enf.nom}</td>
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
                        </div>
                    )}

                    {/* Contacts d'urgence */}
                    {contactsUrgence.length > 0 && (
                        <div>
                            <SectionTitle icon={<ShieldAlert size={16} />}>Contacts d'Urgence</SectionTitle>
                            <div className="space-y-3">
                                {contactsUrgence.map((ct, i) => (
                                    <div key={i} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700">
                                        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                                            <InfoRow label="Nom & Prénom" value={`${ct.nom || ''} ${ct.prenom || ''}`} />
                                            <InfoRow label="Lien de Parenté" value={ct.lienParente} />
                                            <InfoRow label="Téléphone" value={ct.telephone} mono />
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* CNaPS & Banque */}
                    <div>
                        <SectionTitle icon={<Landmark size={16} />}>CNaPS & Banque</SectionTitle>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-x-6">
                            <InfoRow label="N° CNaPS" value={emp.numAffiliationCnaps} mono />
                            <InfoRow label="Date Déclaration" value={fmtDate(emp.dateDeclaration)} />
                        </div>
                        {comptes.length > 0 && (
                            <div className="mt-4 overflow-x-auto border border-ink-150 dark:border-ink-700">
                                <table className="w-full text-sm">
                                    <thead>
                                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 text-[10px] font-bold uppercase tracking-widest">
                                            <th className="px-4 py-3 text-left">Banque</th>
                                            <th className="px-4 py-3 text-left">Agence</th>
                                            <th className="px-4 py-3 text-left">N° Compte / RIB</th>
                                            <th className="px-4 py-3 text-left">Titulaire</th>
                                            <th className="px-4 py-3 text-left">Principal</th>
                                        </tr>
                                    </thead>
                                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                        {comptes.map((cb, i) => (
                                            <tr key={i} className="text-ink-900 dark:text-ink-100">
                                                <td className="px-4 py-3 font-semibold">{cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque N°${cb.idBanque}` : '-')}</td>
                                                <td className="px-4 py-3">{cb.agence || '-'}</td>
                                                <td className="px-4 py-3 font-mono text-xs">{cb.numCompteRib}</td>
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
                        )}
                    </div>
                </div>

                {/* Footer actions */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex items-center justify-between">
                    <div>
                        {emp.estActif ? (
                            <button
                                onClick={() => onToggleStatus && onToggleStatus(detailData)}
                                className="px-3 py-1.5 text-xs font-bold text-danger-500 hover:bg-danger-500/10 rounded-none  cursor-pointer flex items-center gap-1.5"
                            >
                                <Power size={14} />
                                Désactiver
                            </button>
                        ) : (
                            <button
                                onClick={() => onToggleStatus && onToggleStatus(detailData)}
                                className="px-3 py-1.5 text-xs font-bold text-success-500 hover:bg-success-500/10 rounded-none  cursor-pointer flex items-center gap-1.5"
                            >
                                <Power size={14} />
                                Réactiver
                            </button>
                        )}
                    </div>
                    <div className="flex gap-3">
                        <Button onClick={onClose} variant="ghost" size="sm">Fermer</Button>
                        <Button onClick={() => onEdit && onEdit(detailData)} variant="primary" size="sm" iconLeft={<Edit2 size={14} />}>
                            Modifier
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
