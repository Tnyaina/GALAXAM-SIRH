import { User, Calendar, MapPin, Heart, Phone, Mail, Landmark, Shield, CreditCard } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

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

export default function FichePersonnelleTab({
    emp,
    comptes = []
}) {
    if (!emp) return null;

    return (
        <div className="space-y-5 max-w-full">
            {/* Section 1 : État Civil & Identité Juridique */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <User size={15} className="text-brand-500" />
                        État Civil & Identité Juridique
                    </CardTitle>
                    <div className="flex items-center gap-2">
                        {emp.situationFamiliale && (
                            <Badge variant="default" size="xs">
                                {emp.situationFamiliale}
                            </Badge>
                        )}
                        <Badge variant="default" size="xs">
                            {emp.sexe === 'M' ? 'Masculin (M)' : 'Féminin (F)'}
                        </Badge>
                    </div>
                </CardHeader>
                <CardBody className="p-5">
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4">
                        <InfoBlock
                            label="Date de naissance"
                            value={`${fmtDate(emp.dateNaissance)}${emp.age ? ` (${emp.age} ans)` : ''}`}
                            icon={<Calendar size={13} />}
                        />
                        <InfoBlock label="Lieu de naissance" value={emp.lieuNaissance} icon={<MapPin size={13} />} />
                        <InfoBlock label="Nationalité" value={emp.nationalite || 'Malgache'} />
                        <InfoBlock label="Situation familiale" value={emp.situationFamiliale} icon={<Heart size={13} />} />

                        <InfoBlock label="N° CIN" value={emp.cinNumero} mono icon={<CreditCard size={13} />} />
                        <InfoBlock
                            label="Délivrance CIN"
                            value={emp.cinDateDelivrance ? `${fmtDate(emp.cinDateDelivrance)} à ${emp.cinLieuDelivrance || '—'}` : '—'}
                        />
                        <InfoBlock label="Filiation (Père)" value={emp.pereNomPrenom} />
                        <InfoBlock label="Filiation (Mère)" value={emp.mereNomPrenom} />

                        {emp.cinDuplicataDate && (
                            <div className="sm:col-span-2">
                                <InfoBlock
                                    label="Duplicata CIN"
                                    value={`${fmtDate(emp.cinDuplicataDate)} à ${emp.cinDuplicataLieu || '—'}`}
                                />
                            </div>
                        )}
                        <div className="sm:col-span-2">
                            <InfoBlock label="N° Permis de conduire" value={emp.permisConduireNumero} mono />
                        </div>
                    </div>
                </CardBody>
            </Card>

            {/* Section 2 : Domicile & Coordonnées */}
            <Card shadow="sm">
                <CardHeader>
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <MapPin size={15} className="text-brand-500" />
                        Domicile & Coordonnées
                    </CardTitle>
                </CardHeader>
                <CardBody className="p-5">
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4">
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
                        <div className="sm:col-span-4">
                            <InfoBlock
                                label="Adresse e-mail"
                                value={emp.email}
                                icon={<Mail size={13} />}
                                href={emp.email ? `mailto:${emp.email}` : null}
                            />
                        </div>
                    </div>
                </CardBody>
            </Card>

            {/* Section 3 : Sécurité Sociale & Affiliations Bancaires */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <Landmark size={15} className="text-brand-500" />
                        Sécurité Sociale & Coordonnées Bancaires
                    </CardTitle>
                    {emp.numAffiliationCnaps && (
                        <Badge variant="default" size="xs" className="font-mono">
                            CNaPS : {emp.numAffiliationCnaps}
                        </Badge>
                    )}
                </CardHeader>
                <CardBody className="p-5 space-y-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4">
                        <InfoBlock label="N° Affiliation CNaPS" value={emp.numAffiliationCnaps} mono icon={<Shield size={13} />} />
                        <InfoBlock label="Date déclaration CNaPS" value={fmtDate(emp.dateDeclaration)} />
                    </div>

                    <div className="pt-4 border-t border-[var(--color-border-subtle)] space-y-3">
                        <div className="flex items-center justify-between">
                            <h4 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">
                                Comptes Bancaires pour Virement ({comptes.length})
                            </h4>
                        </div>
                        {comptes.length > 0 ? (
                            <Table>
                                <Thead>
                                    <Tr>
                                        <Th>Banque</Th>
                                        <Th>Agence</Th>
                                        <Th>N° Compte / RIB</Th>
                                        <Th align="center">Usage</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {comptes.map((cb, i) => (
                                        <Tr key={i}>
                                            <Td className="font-bold text-xs text-[var(--color-fg)]">
                                                {cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque #${cb.idBanque}` : '—')}
                                            </Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)]">{cb.agence || '—'}</Td>
                                            <Td isData className="font-mono text-xs font-bold text-[var(--color-fg)]">
                                                {cb.numCompteRib || '—'}
                                            </Td>
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
                            <EmptyState
                                icon={Landmark}
                                title="Aucun compte bancaire enregistré"
                                description="Le salaire du collaborateur sera réglé selon le mode de paiement par défaut de l'établissement."
                                compact
                            />
                        )}
                    </div>
                </CardBody>
            </Card>
        </div>
    );
}
