import { Heart, Baby, ShieldAlert } from 'lucide-react';
import Badge from '@/components/ui/Badge';

const InfoBlock = ({ label, value }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-ink-400 mb-0.5">{label}</p>
        <p className="text-sm font-semibold text-ink-800 dark:text-ink-100">{value || '-'}</p>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '-';
    try { return new Date(d).toLocaleDateString('fr-FR'); } catch { return '-'; }
};

export default function FamilleContactsTab({ conjoint, enfants = [], contactsUrgence = [] }) {
    return (
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
                    <p className="text-xs text-ink-400">Aucun conjoint renseigné (Célibataire ou non déclaré).</p>
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
                    <p className="text-xs text-ink-400">Aucun enfant enregistré.</p>
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
                    <p className="text-xs text-ink-400">Aucun contact d'urgence renseigné.</p>
                )}
            </div>
        </div>
    );
}
