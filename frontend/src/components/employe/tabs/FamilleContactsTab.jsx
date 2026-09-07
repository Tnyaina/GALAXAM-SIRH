import { Heart, Baby, ShieldAlert, Phone, MapPin, Calendar, Briefcase } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value, icon }) => (
    <div className="space-y-1">
        <p className="text-[11px] font-medium text-[var(--color-fg-muted)]">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-[var(--color-fg-subtle)] shrink-0">{icon}</span>}
            <p className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)] truncate">
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
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Heart size={14} className="text-brand-500" />
                        Conjoint Déclaré
                    </h3>
                    <Badge variant="default" size="xs">
                        {hasConjoint ? 'Marié(e) / Déclaré' : 'Non déclaré'}
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
                    <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                        Aucun conjoint renseigné (Collaborateur célibataire ou conjoint non déclaré).
                    </p>
                )}
            </div>

            {/* Carte 2 : Enfants Déclarés */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2 pb-3 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Baby size={14} className="text-brand-500" />
                        Enfants Déclarés ({enfants.length})
                    </h3>
                    {enfants.length > 0 && (
                        <div className="flex items-center gap-3 text-xs">
                            <span className="text-[var(--color-fg-muted)]">
                                {enfants.filter(e => e.aCharge).length} à charge du foyer
                            </span>
                            <span className="font-semibold text-emerald-600 dark:text-emerald-400">
                                • {enfants.filter(e => {
                                    if (!e.aCharge) return false;
                                    if (e.estHandicape) return true;
                                    if (!e.dateNaissance) return false;
                                    const dob = new Date(e.dateNaissance);
                                    if (isNaN(dob.getTime())) return false;
                                    const now = new Date();
                                    let age = now.getFullYear() - dob.getFullYear();
                                    const m = now.getMonth() - dob.getMonth();
                                    if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age--;
                                    if (age < 21) return true;
                                    if (age < 25 && e.estEtudiant && e.certificatFourni) {
                                        return !e.dateFinScolarite || new Date(e.dateFinScolarite) >= now;
                                    }
                                    return false;
                                }).length} déductible(s) IRSA (-2 000 Ar/chacun)
                            </span>
                        </div>
                    )}
                </div>

                {enfants.length > 0 ? (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Nom & Prénom</Th>
                                <Th>Sexe</Th>
                                <Th>Naissance & Âge</Th>
                                <Th>Statut Fiscal / Scolaire</Th>
                                <Th align="center">À Charge</Th>
                                <Th align="center">Déduction IRSA</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {enfants.map((enf, i) => {
                                let age = null;
                                if (enf.dateNaissance) {
                                    const dob = new Date(enf.dateNaissance);
                                    if (!isNaN(dob.getTime())) {
                                        const now = new Date();
                                        age = now.getFullYear() - dob.getFullYear();
                                        const m = now.getMonth() - dob.getMonth();
                                        if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) age--;
                                    }
                                }
                                const isEligible = Boolean(
                                    enf.aCharge && (
                                        enf.estHandicape ||
                                        (age !== null && age < 21) ||
                                        (age !== null && age < 25 && enf.estEtudiant && enf.certificatFourni && (!enf.dateFinScolarite || new Date(enf.dateFinScolarite) >= new Date()))
                                    )
                                );

                                return (
                                    <Tr key={i}>
                                        <Td className="font-bold text-[var(--color-fg)]">
                                            {enf.nom} {enf.prenom}
                                        </Td>
                                        <Td className="text-[var(--color-fg-muted)]">
                                            {enf.sexe === 'M' ? 'Garçon' : enf.sexe === 'F' ? 'Fille' : (enf.sexe || '—')}
                                        </Td>
                                        <Td isData className="font-mono text-xs text-[var(--color-fg)]">
                                            {fmtDate(enf.dateNaissance)} {age !== null ? `(${age} ans)` : ''}
                                        </Td>
                                        <Td className="text-xs">
                                            {enf.estHandicape ? (
                                                <Badge variant="purple" size="xs">Situation de Handicap</Badge>
                                            ) : enf.estEtudiant ? (
                                                <div className="flex flex-col gap-0.5">
                                                    <span className="font-semibold text-sky-600 dark:text-sky-400">Étudiant majeur</span>
                                                    <span className="text-[11px] text-[var(--color-fg-subtle)]">
                                                        {enf.certificatFourni ? `Certificat fourni ${enf.dateFinScolarite ? `(échéance ${fmtDate(enf.dateFinScolarite)})` : ''}` : 'Certificat manquant'}
                                                    </span>
                                                </div>
                                            ) : (
                                                <span className="text-[var(--color-fg-muted)]">
                                                    {age !== null && age < 21 ? 'Mineur à charge' : 'Majeur standard'}
                                                </span>
                                            )}
                                        </Td>
                                        <Td align="center">
                                            {enf.aCharge ? (
                                                <Badge variant="success" size="xs" className="font-bold">Oui</Badge>
                                            ) : (
                                                <Badge variant="default" size="xs">Non</Badge>
                                            )}
                                        </Td>
                                        <Td align="center">
                                            {isEligible ? (
                                                <Badge variant="success" size="xs" className="font-bold">-2 000 Ar</Badge>
                                            ) : (
                                                <Badge variant="default" size="xs">Exclu</Badge>
                                            )}
                                        </Td>
                                    </Tr>
                                );
                            })}
                        </Tbody>
                    </Table>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                        Aucun enfant déclaré pour ce collaborateur.
                    </p>
                )}
            </div>

            {/* Carte 3 : Contacts d'Urgence */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <ShieldAlert size={14} className="text-brand-500" />
                        Contacts d'Urgence ({contactsUrgence.length})
                    </h3>
                    <span className="text-[11px] text-[var(--color-fg-subtle)]">
                        À joindre prioritairement en cas d'accident ou d'incident sur site
                    </span>
                </div>

                {contactsUrgence.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
                        {contactsUrgence.map((ct, i) => (
                            <div
                                key={i}
                                className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-2.5"
                            >
                                <div className="flex justify-between items-center">
                                    <p className="text-sm font-bold text-[var(--color-fg)]">
                                        {ct.nom} {ct.prenom}
                                    </p>
                                    <Badge variant="default" size="xs" className="font-semibold">
                                        {ct.lienParente || 'Contact'}
                                    </Badge>
                                </div>
                                <div className="grid grid-cols-2 gap-3 text-xs pt-1">
                                    <div>
                                        <span className="text-[var(--color-fg-muted)] text-[11px] block mb-0.5">Téléphone d'urgence</span>
                                        {ct.telephone ? (
                                            <a
                                                href={`tel:${ct.telephone}`}
                                                className="font-mono font-bold text-brand-600 hover:text-brand-700 hover:underline flex items-center gap-1"
                                            >
                                                <Phone size={12} />
                                                {ct.telephone}
                                            </a>
                                        ) : (
                                            <span className="text-[var(--color-fg-subtle)]">—</span>
                                        )}
                                    </div>
                                    <div>
                                        <span className="text-[var(--color-fg-muted)] text-[11px] block mb-0.5">Adresse / Domicile</span>
                                        <span className="text-[var(--color-fg)] truncate block">
                                            {ct.adresse || '—'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                        Aucun contact d'urgence renseigné.
                    </p>
                )}
            </div>
        </div>
    );
}
