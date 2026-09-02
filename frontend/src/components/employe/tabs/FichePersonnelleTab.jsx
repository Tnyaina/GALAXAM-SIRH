import { User, Calendar, MapPin, Heart, Phone, Mail, Landmark, Shield, CreditCard } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

const InfoBlock = ({ label, value, mono = false, icon, href }) => (
    <div className="space-y-1">
        <p className="text-[11px] font-medium text-[var(--color-fg-muted)]">{label}</p>
        <div className="flex items-center gap-1.5">
            {icon && <span className="text-[var(--color-fg-subtle)] shrink-0">{icon}</span>}
            {href ? (
                <a
                    href={href}
                    className={`text-xs sm:text-[13px] font-bold text-brand-600 hover:text-brand-700 hover:underline truncate ${mono ? 'font-mono' : ''}`}
                >
                    {value || '—'}
                </a>
            ) : (
                <p className={`text-xs sm:text-[13px] font-bold text-[var(--color-fg)] truncate ${mono ? 'font-mono' : ''}`}>
                    {value || '—'}
                </p>
            )}
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

export default function FichePersonnelleTab({ emp, comptes = [] }) {
    if (!emp) return null;

    return (
        <div className="space-y-4">
            {/* Carte 1 : État Civil & Identité */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <User size={14} className="text-brand-500" />
                        État Civil & Identité Juridique
                    </h3>
                    <div className="flex items-center gap-2">
                        {emp.situationFamiliale && (
                            <Badge variant="default" size="xs">
                                {emp.situationFamiliale}
                            </Badge>
                        )}
                        <Badge variant="default" size="xs">
                            {emp.sexe === 'M' ? 'Masculin' : 'Féminin'}
                        </Badge>
                    </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 pt-1">
                    <InfoBlock label="Date de naissance" value={fmtDate(emp.dateNaissance)} icon={<Calendar size={13} />} />
                    <InfoBlock label="Lieu de naissance" value={emp.lieuNaissance} icon={<MapPin size={13} />} />
                    <InfoBlock label="Nationalité" value={emp.nationalite || 'Malgache'} />
                    <InfoBlock label="Situation familiale" value={emp.situationFamiliale} icon={<Heart size={13} />} />

                    <InfoBlock label="Filiation (Père)" value={emp.pereNomPrenom} />
                    <InfoBlock label="Filiation (Mère)" value={emp.mereNomPrenom} />
                    <InfoBlock label="N° CIN" value={emp.cinNumero} mono icon={<CreditCard size={13} />} />
                    <InfoBlock label="Délivrance CIN" value={emp.cinDateDelivrance ? `${fmtDate(emp.cinDateDelivrance)} à ${emp.cinLieuDelivrance || '—'}` : '—'} />

                    {emp.cinDuplicataDate && (
                        <InfoBlock label="Duplicata CIN" value={`${fmtDate(emp.cinDuplicataDate)} à ${emp.cinDuplicataLieu || '—'}`} />
                    )}
                    <InfoBlock label="N° Permis de conduire" value={emp.permisConduireNumero} mono />
                </div>
            </div>

            {/* Carte 2 : Domicile & Coordonnées */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2 pb-3 border-b border-[var(--color-border-subtle)]">
                    <Phone size={14} className="text-brand-500" />
                    Domicile & Coordonnées de Contact
                </h3>

                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 pt-1">
                    <div className="sm:col-span-2">
                        <InfoBlock label="Adresse du domicile" value={emp.adresse} icon={<MapPin size={13} />} />
                    </div>
                    <InfoBlock label="Ville de résidence" value={emp.ville} />
                    <InfoBlock
                        label="Téléphone principal"
                        value={emp.telephone}
                        mono
                        icon={<Phone size={13} />}
                        href={emp.telephone ? `tel:${emp.telephone}` : null}
                    />
                    <div className="sm:col-span-2">
                        <InfoBlock
                            label="Adresse e-mail"
                            value={emp.email}
                            icon={<Mail size={13} />}
                            href={emp.email ? `mailto:${emp.email}` : null}
                        />
                    </div>
                </div>
            </div>

            {/* Carte 3 : Sécurité Sociale & Coordonnées Bancaires */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex items-center justify-between pb-3 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Landmark size={14} className="text-brand-500" />
                        Sécurité Sociale & Coordonnées Bancaires
                    </h3>
                    {emp.numAffiliationCnaps && (
                        <Badge variant="default" size="xs" className="font-mono">
                            CNaPS : {emp.numAffiliationCnaps}
                        </Badge>
                    )}
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4 pt-1">
                    <InfoBlock label="N° Affiliation CNaPS" value={emp.numAffiliationCnaps} mono icon={<Shield size={13} />} />
                    <InfoBlock label="Date déclaration CNaPS" value={fmtDate(emp.dateDeclaration)} />
                </div>

                <div className="pt-2">
                    <h4 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)] mb-2.5">
                        Comptes Bancaires ({comptes.length})
                    </h4>
                    {comptes.length > 0 ? (
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Banque</Th>
                                    <Th>Agence</Th>
                                    <Th>N° Compte / RIB</Th>
                                    <Th>Titulaire</Th>
                                    <Th align="center">Usage</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {comptes.map((cb, i) => (
                                    <Tr key={i}>
                                        <Td className="font-bold text-[var(--color-fg)]">
                                            {cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque N°${cb.idBanque}` : '—')}
                                        </Td>
                                        <Td className="text-[var(--color-fg-muted)]">{cb.agence || '—'}</Td>
                                        <Td isData className="font-mono text-xs font-bold text-[var(--color-fg)]">
                                            {cb.numCompteRib || '—'}
                                        </Td>
                                        <Td className="text-[var(--color-fg-muted)]">{cb.titulaireCompte || '—'}</Td>
                                        <Td align="center">
                                            {cb.estPrincipal ? (
                                                <Badge variant="success" size="xs" className="font-bold">Principal</Badge>
                                            ) : (
                                                <Badge variant="default" size="xs">Secondaire</Badge>
                                            )}
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    ) : (
                        <p className="text-xs text-[var(--color-fg-muted)] p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center">
                            Aucun compte bancaire enregistré pour ce collaborateur.
                        </p>
                    )}
                </div>
            </div>
        </div>
    );
}
