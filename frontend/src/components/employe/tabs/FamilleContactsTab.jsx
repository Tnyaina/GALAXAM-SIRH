import { Heart, Baby, ShieldAlert, Phone, MapPin, Calendar, Briefcase } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import useFiscalConfig, { evaluateEnfantIrsaEligibility } from '@/hooks/useFiscalConfig';

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
    const { fiscalConfig } = useFiscalConfig();
    const hasConjoint = Boolean(conjoint && (conjoint.nom || conjoint.prenom));

    // Évaluation dynamique selon la Loi de Finances active
    const enfantsAvecEval = enfants.map(enf => ({
        ...enf,
        evalIrsa: evaluateEnfantIrsaEligibility(enf, fiscalConfig)
    }));

    const deductiblesCount = enfantsAvecEval.filter(e => e.evalIrsa.eligible).length;
    const totalDeductionAr = deductiblesCount * fiscalConfig.deductionParCharge;

    return (
        <div className="space-y-5">
            {/* Carte 1 : Conjoint Déclaré */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <Heart size={15} className="text-brand-500" />
                        Conjoint Déclaré
                    </CardTitle>
                    <Badge variant={hasConjoint ? 'success' : 'default'} size="xs" dot={hasConjoint}>
                        {hasConjoint ? 'Marié(e) / Déclaré' : 'Non déclaré'}
                    </Badge>
                </CardHeader>
                <CardBody className="p-5">
                    {hasConjoint ? (
                        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-x-6 gap-y-4">
                            <InfoBlock label="Nom" value={conjoint.nom} />
                            <InfoBlock label="Prénom" value={conjoint.prenom} />
                            <InfoBlock label="Date de naissance" value={fmtDate(conjoint.dateNaissance)} icon={<Calendar size={13} />} />
                            <InfoBlock label="Lieu de naissance" value={conjoint.lieuNaissance} icon={<MapPin size={13} />} />
                            <InfoBlock label="Employeur / Société" value={conjoint.societe} icon={<Briefcase size={13} />} />
                            <InfoBlock label="Fonction / Métier" value={conjoint.fonction} />
                        </div>
                    ) : (
                        <EmptyState
                            icon={Heart}
                            title="Aucun conjoint renseigné"
                            description="Collaborateur célibataire ou conjoint non déclaré sur le dossier RH."
                            compact
                        />
                    )}
                </CardBody>
            </Card>

            {/* Carte 2 : Enfants Déclarés */}
            <Card shadow="sm">
                <CardHeader className="flex flex-wrap items-center justify-between gap-3">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <Baby size={15} className="text-brand-500" />
                        Enfants Déclarés ({enfants.length})
                    </CardTitle>
                    {enfants.length > 0 && (
                        <div className="flex items-center gap-2">
                            <Badge variant="default" size="xs">
                                {enfants.filter(e => e.aCharge).length} à charge
                            </Badge>
                            {deductiblesCount > 0 ? (
                                <Badge variant="success" size="xs" dot>
                                    {deductiblesCount} déductible{deductiblesCount > 1 ? 's' : ''} IRSA (-{totalDeductionAr.toLocaleString('fr-FR')} Ar/mois • {fiscalConfig.codeVersion})
                                </Badge>
                            ) : (
                                <Badge variant="warning" size="xs">
                                    Aucune déduction active ({fiscalConfig.codeVersion})
                                </Badge>
                            )}
                        </div>
                    )}
                </CardHeader>
                <CardBody className="p-0">
                    {enfants.length > 0 ? (
                        <Table>
                            <Thead>
                                <Tr>
                                    <Th>Nom & Prénom</Th>
                                    <Th>Sexe</Th>
                                    <Th>Naissance & Âge</Th>
                                    <Th>Statut Fiscal / Scolaire</Th>
                                    <Th align="center">À Charge</Th>
                                    <Th align="center">Déduction IRSA ({fiscalConfig.codeVersion})</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {enfantsAvecEval.map((enf, i) => (
                                    <Tr key={i}>
                                        <Td className="font-bold text-[var(--color-fg)]">
                                            {enf.nom} {enf.prenom}
                                        </Td>
                                        <Td className="text-[var(--color-fg-muted)]">
                                            {enf.sexe === 'M' ? 'Garçon' : enf.sexe === 'F' ? 'Fille' : (enf.sexe || '—')}
                                        </Td>
                                        <Td isData className="font-mono text-xs text-[var(--color-fg)]">
                                            <div>{fmtDate(enf.dateNaissance)} {enf.evalIrsa.age !== null ? `(${enf.evalIrsa.age} ans)` : ''}</div>
                                            {enf.lieuNaissance && (
                                                <div className="text-[11px] font-sans text-[var(--color-fg-muted)] flex items-center gap-1 mt-0.5 font-normal">
                                                    <MapPin size={10} className="shrink-0 text-[var(--color-fg-subtle)]" />
                                                    <span className="truncate">{enf.lieuNaissance}</span>
                                                </div>
                                            )}
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
                                                    {enf.evalIrsa.age !== null && enf.evalIrsa.age < fiscalConfig.ageLimiteDefaut ? 'Mineur à charge' : 'Majeur standard'}
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
                                            {enf.evalIrsa.eligible ? (
                                                <Badge variant="success" size="xs" className="font-bold" title={enf.evalIrsa.motif}>
                                                    -{fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar
                                                </Badge>
                                            ) : (
                                                <Badge variant="default" size="xs" title={enf.evalIrsa.motif}>
                                                    Exclu
                                                </Badge>
                                            )}
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    ) : (
                        <div className="p-5">
                            <EmptyState
                                icon={Baby}
                                title="Aucun enfant déclaré"
                                description="Aucun ayant droit ou personne à charge déclaré pour le calcul des déductions IRSA."
                                compact
                            />
                        </div>
                    )}
                </CardBody>
            </Card>

            {/* Carte 3 : Contacts d'Urgence */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <ShieldAlert size={15} className="text-brand-500" />
                        Contacts d'Urgence ({contactsUrgence.length})
                    </CardTitle>
                    <span className="text-[11px] text-[var(--color-fg-subtle)]">
                        À joindre prioritairement en cas d'accident ou d'incident sur site
                    </span>
                </CardHeader>
                <CardBody className="p-5">
                    {contactsUrgence.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {contactsUrgence.map((ct, i) => (
                                <Card key={i} padding shadow="none" className="bg-[var(--color-bg-subtle)]/60 space-y-2.5">
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
                                </Card>
                            ))}
                        </div>
                    ) : (
                        <EmptyState
                            icon={ShieldAlert}
                            title="Aucun contact d'urgence enregistré"
                            description="Il est recommandé de renseigner au moins une personne de confiance à joindre en cas d'incident."
                            compact
                        />
                    )}
                </CardBody>
            </Card>
        </div>
    );
}
