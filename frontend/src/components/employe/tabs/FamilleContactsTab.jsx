import { Heart, Baby, ShieldAlert } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value }) => (
    <div className="py-2">
        <p className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-fg-muted)] mb-0.5">{label}</p>
        <p className="text-sm font-semibold text-[var(--color-fg)]">{value || '-'}</p>
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
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <Heart size={14} /> Conjoint
                </h2>
                {conjoint && conjoint.nom ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-6 bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)]">
                        <InfoBlock label="Nom" value={conjoint.nom} />
                        <InfoBlock label="Prénom" value={conjoint.prenom} />
                        <InfoBlock label="Date de Naissance" value={fmtDate(conjoint.dateNaissance)} />
                        <InfoBlock label="Employeur / Société" value={conjoint.societe} />
                        <InfoBlock label="Fonction" value={conjoint.fonction} />
                    </div>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)]">Aucun conjoint renseigné (Célibataire ou non déclaré).</p>
                )}
            </div>

            {/* Enfants */}
            <div>
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <Baby size={14} /> Enfants Déclarés ({enfants.length})
                </h2>
                {enfants.length > 0 ? (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Nom</Th>
                                <Th>Prénom</Th>
                                <Th>Sexe</Th>
                                <Th>Date Naissance</Th>
                                <Th>À Charge</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {enfants.map((enf, i) => (
                                <Tr key={i}>
                                    <Td className="font-semibold">{enf.nom}</Td>
                                    <Td>{enf.prenom}</Td>
                                    <Td>{enf.sexe === 'M' ? 'Garçon' : 'Fille'}</Td>
                                    <Td isData className="font-mono text-xs">{fmtDate(enf.dateNaissance)}</Td>
                                    <Td>
                                        {enf.aCharge ? (
                                            <Badge variant="success" size="sm">Oui</Badge>
                                        ) : (
                                            <Badge variant="default" size="sm">Non</Badge>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)]">Aucun enfant enregistré.</p>
                )}
            </div>

            {/* Contacts d'Urgence */}
            <div>
                <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)] pb-2 border-b border-[var(--color-border-subtle)] mb-4 flex items-center gap-2">
                    <ShieldAlert size={14} /> Contacts d'Urgence
                </h2>
                {contactsUrgence.length > 0 ? (
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {contactsUrgence.map((ct, i) => (
                            <div key={i} className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2 shadow-2xs">
                                <div className="flex justify-between items-center">
                                    <p className="text-sm font-bold text-[var(--color-fg)]">{ct.nom} {ct.prenom}</p>
                                    <Badge variant="brand" size="sm">{ct.lienParente}</Badge>
                                </div>
                                <div className="grid grid-cols-2 gap-2 text-xs">
                                    <div>
                                        <span className="text-[var(--color-fg-muted)] uppercase text-[9px] block">Téléphone</span>
                                        <span className="font-mono font-bold text-[var(--color-brand-500)]">{ct.telephone || '-'}</span>
                                    </div>
                                    <div>
                                        <span className="text-[var(--color-fg-muted)] uppercase text-[9px] block">Adresse</span>
                                        <span className="text-[var(--color-fg)]">{ct.adresse || '-'}</span>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <p className="text-xs text-[var(--color-fg-muted)]">Aucun contact d'urgence renseigné.</p>
                )}
            </div>
        </div>
    );
}
