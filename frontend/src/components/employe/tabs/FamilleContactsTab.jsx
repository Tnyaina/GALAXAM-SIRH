import { Heart, Baby, ShieldAlert, Phone, MapPin, Calendar, Briefcase } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value, icon }) => (
    <div className="space-y-1">
        <p className="text-[11px] font-medium text-slate-500 dark:text-ink-400">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-slate-400 dark:text-ink-500 shrink-0">{icon}</span>}
            <p className="text-xs sm:text-[13px] font-bold text-slate-900 dark:text-white truncate">
                {value || '—'}
            </p>
        </div>
    </div>
);

const fmtDate = (d) => {
    if (!d) return '—';
    try {
        const date = new Date(d);
        if (isNaN(date.getTime())) return '—';
        return date.toLocaleDateString('fr-FR');
    } catch {
        return '—';
    }
};

export default function FamilleContactsTab({ conjoint, enfants = [], contactsUrgence = [] }) {
    const hasConjoint = Boolean(conjoint && (conjoint.nom || conjoint.prenom));

    return (
        <div className="space-y-4">
            {/* Carte 1 : Conjoint Déclaré */}
            <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-ink-800">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white flex items-center gap-2">
                        <Heart size={14} className="text-brand-500" />
                        Conjoint Déclaré
                    </h3>
                    <Badge variant={hasConjoint ? "default" : "default"} size="xs">
                        {hasConjoint ? "Marié(e) / Déclaré" : "Non déclaré"}
                    </Badge>
                </div>

                {hasConjoint ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 pt-1">
                        <InfoBlock label="Nom" value={conjoint.nom} />
                        <InfoBlock label="Prénom" value={conjoint.prenom} />
                        <InfoBlock label="Date de naissance" value={fmtDate(conjoint.dateNaissance)} icon={<Calendar size={13} />} />
                        <InfoBlock label="Lieu de naissance" value={conjoint.lieuNaissance} icon={<MapPin size={13} />} />
                        <InfoBlock label="Employeur / Société" value={conjoint.societe} icon={<Briefcase size={13} />} />
                        <InfoBlock label="Fonction / Métier" value={conjoint.fonction} />
                    </div>
                ) : (
                    <p className="text-xs text-slate-400 p-3 bg-slate-50/50 dark:bg-ink-950/30 border border-slate-200/60 dark:border-ink-800 text-center">
                        Aucun conjoint renseigné (Collaborateur célibataire ou conjoint non déclaré).
                    </p>
                )}
            </div>

            {/* Carte 2 : Enfants Déclarés */}
            <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-ink-800">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white flex items-center gap-2">
                        <Baby size={14} className="text-brand-500" />
                        Enfants Déclarés ({enfants.length})
                    </h3>
                    {enfants.length > 0 && (
                        <span className="text-xs text-slate-500">
                            {enfants.filter(e => e.aCharge).length} à charge légale
                        </span>
                    )}
                </div>

                {enfants.length > 0 ? (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Nom & Prénom</Th>
                                <Th>Sexe</Th>
                                <Th>Date de Naissance</Th>
                                <Th align="center">À Charge Légale</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {enfants.map((enf, i) => (
                                <Tr key={i}>
                                    <Td className="font-bold text-slate-900 dark:text-white">
                                        {enf.nom} {enf.prenom}
                                    </Td>
                                    <Td className="text-slate-600 dark:text-ink-300">
                                        {enf.sexe === 'M' ? 'Garçon' : enf.sexe === 'F' ? 'Fille' : (enf.sexe || '—')}
                                    </Td>
                                    <Td isData className="font-mono text-xs text-slate-900 dark:text-white">
                                        {fmtDate(enf.dateNaissance)}
                                    </Td>
                                    <Td align="center">
                                        {enf.aCharge ? (
                                            <Badge variant="success" size="xs" className="font-bold">Oui</Badge>
                                        ) : (
                                            <Badge variant="default" size="xs">Non</Badge>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                ) : (
                    <p className="text-xs text-slate-400 p-3 bg-slate-50/50 dark:bg-ink-950/30 border border-slate-200/60 dark:border-ink-800 text-center">
                        Aucun enfant déclaré pour ce collaborateur.
                    </p>
                )}
            </div>

            {/* Carte 3 : Contacts d'Urgence */}
            <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-ink-800">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white flex items-center gap-2">
                        <ShieldAlert size={14} className="text-brand-500" />
                        Contacts d'Urgence ({contactsUrgence.length})
                    </h3>
                    <span className="text-[11px] text-slate-400">
                        À joindre prioritairement en cas d'accident ou d'incident sur site
                    </span>
                </div>

                {contactsUrgence.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
                        {contactsUrgence.map((ct, i) => (
                            <div
                                key={i}
                                className="p-4 bg-slate-50/60 dark:bg-ink-950/40 border border-slate-200/80 dark:border-ink-800 space-y-2.5"
                            >
                                <div className="flex justify-between items-center">
                                    <p className="text-sm font-bold text-slate-900 dark:text-white">
                                        {ct.nom} {ct.prenom}
                                    </p>
                                    <Badge variant="default" size="xs" className="font-semibold">
                                        {ct.lienParente || 'Contact'}
                                    </Badge>
                                </div>
                                <div className="grid grid-cols-2 gap-3 text-xs pt-1">
                                    <div>
                                        <span className="text-slate-500 dark:text-ink-400 text-[11px] block mb-0.5">Téléphone d'urgence</span>
                                        {ct.telephone ? (
                                            <a
                                                href={`tel:${ct.telephone}`}
                                                className="font-mono font-bold text-brand-600 hover:text-brand-700 hover:underline flex items-center gap-1"
                                            >
                                                <Phone size={12} />
                                                {ct.telephone}
                                            </a>
                                        ) : (
                                            <span className="text-slate-400">—</span>
                                        )}
                                    </div>
                                    <div>
                                        <span className="text-slate-500 dark:text-ink-400 text-[11px] block mb-0.5">Adresse / Domicile</span>
                                        <span className="text-slate-700 dark:text-ink-200 truncate block">
                                            {ct.adresse || '—'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="text-xs text-slate-400 p-3 bg-slate-50/50 dark:bg-ink-950/30 border border-slate-200/60 dark:border-ink-800 text-center">
                        Aucun contact d'urgence renseigné.
                    </p>
                )}
            </div>
        </div>
    );
}
