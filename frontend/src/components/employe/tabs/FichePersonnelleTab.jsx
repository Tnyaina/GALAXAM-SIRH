import { User, Calendar, MapPin, Heart, Phone, Landmark } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

const InfoBlock = ({ label, value, mono = false, icon }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400 mb-0.5">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-ink-300">{icon}</span>}
            <p className={`text-sm font-semibold text-ink-800 dark:text-ink-100 ${mono ? 'font-mono' : ''}`}>{value || '-'}</p>
        </div>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
};

export default function FichePersonnelleTab({ emp, comptes = [] }) {
    if (!emp) return null;

    return (
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
                                        <td className="px-4 py-3 font-semibold">{cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque N°${cb.idBanque}` : '-')}</td>
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
                    <p className="text-xs text-ink-400">Aucun compte bancaire enregistré.</p>
                )}
            </div>
        </div>
    );
}
