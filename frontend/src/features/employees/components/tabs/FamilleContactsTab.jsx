import { Heart, Baby, ShieldAlert, Phone, User, Edit2, Plus } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import useFiscalConfig, { evaluateEnfantIrsaEligibility } from '@/features/employees/hooks/useFiscalConfig';
import { fmtDate } from '@/utils/dateUtils';

/**
 * InfoRow — Ligne label / valeur épurée
 * Affiche 'Non renseigné' adouci si la valeur est vide pour éviter les tirets bruts.
 */
const InfoRow = ({ label, children, mono = false }) => {
    const rawText = typeof children === 'string' ? children.trim() : children;
    const hasValue = rawText !== null && rawText !== undefined && rawText !== '' && rawText !== '—';

    return (
        <div className="flex justify-between items-baseline gap-2 min-w-0 py-1.5 border-b border-[var(--color-border-subtle)]/50 last:border-0">
            <span className="text-xs text-[var(--color-fg-muted)] shrink-0 font-medium">{label}</span>
            <span className={`text-xs text-right truncate ${mono && hasValue ? 'font-mono' : ''} ${hasValue ? 'font-bold text-[var(--color-fg)]' : 'text-[var(--color-fg-subtle)] font-normal'}`}>
                {hasValue ? children : 'Non renseigné'}
            </span>
        </div>
    );
};

export default function FamilleContactsTab({
    conjoint,
    enfants = [],
    contactsUrgence = [],
    canWrite = false,
    onEditFamille,
    onEditUrgence,
}) {
    const { fiscalConfig } = useFiscalConfig();
    const hasConjoint = Boolean(conjoint && (conjoint.nom || conjoint.prenom));

    // Évaluation dynamique IRSA
    const enfantsAvecEval = enfants.map(enf => ({
        ...enf,
        evalIrsa: evaluateEnfantIrsaEligibility(enf, fiscalConfig)
    }));

    const deductiblesCount = enfantsAvecEval.filter(e => e.evalIrsa.eligible).length;
    const totalDeductionAr = deductiblesCount * fiscalConfig.deductionParCharge;

    return (
        <div className="space-y-4 font-sans">
            {/* 1. Carte Principale : Foyer & Ayants Droit */}
            <Card shadow="sm">
                <CardHeader className="flex flex-wrap items-center justify-between gap-3">
                    <CardTitle size="sm" className="tracking-wider flex items-center gap-2">
                        <Heart size={15} className="text-[var(--color-brand-600)]" />
                        Situation familiale &amp; Ayants droit
                    </CardTitle>
                    <div className="flex items-center gap-2 flex-wrap">
                        <Badge variant={hasConjoint ? 'success' : 'default'} size="xs">
                            {hasConjoint ? 'Marié(e)' : 'Célibataire'}
                        </Badge>
                        <Badge variant="default" size="xs">
                            {enfants.length} enfant{enfants.length > 1 ? 's' : ''}
                        </Badge>
                        {deductiblesCount > 0 && (
                            <Badge variant="brand" size="xs" dot>
                                {deductiblesCount} déductible{deductiblesCount > 1 ? 's' : ''} IRSA (-{totalDeductionAr.toLocaleString('fr-FR')} Ar)
                            </Badge>
                        )}
                        {canWrite && onEditFamille && (
                            <Button
                                type="button"
                                variant="ghost"
                                size="xs"
                                onClick={onEditFamille}
                                iconLeft={<Edit2 size={12} />}
                                className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold ml-1"
                            >
                                Modifier le foyer
                            </Button>
                        )}
                    </div>
                </CardHeader>
                <CardBody className="p-5 space-y-5">
                    {/* Section Conjoint */}
                    {hasConjoint ? (
                        <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2">
                            <h4 className="font-bold text-[var(--color-fg)] text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                                <User size={13} className="text-[var(--color-brand-600)]" />
                                Conjoint déclaré
                            </h4>
                            <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-x-6 gap-y-1 pt-1">
                                <InfoRow label="Nom &amp; Prénom">{conjoint.nomComplet || `${conjoint.nom || ''} ${conjoint.prenom || ''}`.trim()}</InfoRow>
                                <InfoRow label="Date de naissance">
                                    {fmtDate(conjoint.dateNaissance)}{conjoint.age ? ` (${conjoint.age} ans)` : ''}{conjoint.lieuNaissance ? ` à ${conjoint.lieuNaissance}` : ''}
                                </InfoRow>
                                <InfoRow label="Employeur / Métier">
                                    {conjoint.societe ? `${conjoint.societe}${conjoint.fonction ? ` (${conjoint.fonction})` : ''}` : (conjoint.fonction || 'Non renseigné')}
                                </InfoRow>
                            </div>
                        </div>
                    ) : (
                        <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] px-4 py-3 flex items-center justify-between gap-3 text-xs text-[var(--color-fg-muted)]">
                            <div className="flex items-center gap-2">
                                <Heart size={14} className="text-[var(--color-brand-600)] shrink-0" />
                                <span>Célibataire ou conjoint non déclaré sur ce dossier.</span>
                            </div>
                            {canWrite && onEditFamille && (
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="xs"
                                    onClick={onEditFamille}
                                    iconLeft={<Plus size={12} />}
                                    className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold"
                                >
                                    Déclarer un conjoint
                                </Button>
                            )}
                        </div>
                    )}

                    {/* Section Enfants */}
                    <div className="space-y-2.5">
                        <div className="flex items-center justify-between">
                            <h4 className="text-xs font-bold text-[var(--color-fg)] flex items-center gap-1.5">
                                <Baby size={13} className="text-[var(--color-brand-600)]" />
                                Enfants déclarés ({enfants.length})
                            </h4>
                        </div>

                        {enfants.length > 0 ? (
                            <Table bordered={false}>
                                <Thead>
                                    <Tr>
                                        <Th>Enfant</Th>
                                        <Th>Naissance &amp; Âge</Th>
                                        <Th>Situation</Th>
                                        <Th align="center">Déduction IRSA</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {enfantsAvecEval.map((enf, i) => (
                                        <Tr key={i}>
                                            <Td>
                                                <p className="font-bold text-xs text-[var(--color-fg)]">
                                                    {enf.nom} {enf.prenom}
                                                </p>
                                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                                    {enf.genreLibelle || (enf.sexe === 'M' ? 'Garçon' : enf.sexe === 'F' ? 'Fille' : 'Non précisé')}
                                                </span>
                                            </Td>
                                            <Td isData className="font-mono text-xs text-[var(--color-fg)]">
                                                <span>{fmtDate(enf.dateNaissance)}</span>
                                                {(enf.evalIrsa.age !== null || enf.age !== undefined) && (
                                                    <span className="text-[var(--color-fg-muted)] font-sans ml-1">
                                                        ({enf.evalIrsa.age ?? enf.age} ans)
                                                    </span>
                                                )}
                                            </Td>
                                            <Td className="text-xs">
                                                {enf.estHandicape ? (
                                                    <Badge variant="purple" size="xs">Situation de Handicap</Badge>
                                                ) : enf.estEtudiant ? (
                                                    <span className="text-[var(--color-brand-600)] font-semibold">
                                                        Étudiant{enf.certificatFourni ? ' (certificat fourni)' : ' (certificat en attente)'}
                                                    </span>
                                                ) : (enf.evalIrsa.age ?? enf.age) !== null && (enf.evalIrsa.age ?? enf.age) < fiscalConfig.ageLimiteDefaut ? (
                                                    <span className="text-[var(--color-fg-muted)]">Mineur à charge</span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-muted)]">Majeur</span>
                                                )}
                                            </Td>
                                            <Td align="center">
                                                {enf.evalIrsa.eligible ? (
                                                    <Badge variant="success" size="xs" className="font-bold">
                                                        -{fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar
                                                    </Badge>
                                                ) : (
                                                    <Badge variant="default" size="xs">
                                                        Non éligible
                                                    </Badge>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        ) : (
                            <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] px-4 py-3 flex items-center justify-between gap-3 text-xs text-[var(--color-fg-muted)]">
                                <div className="flex items-center gap-2">
                                    <Baby size={14} className="text-[var(--color-brand-600)] shrink-0" />
                                    <span>Aucun enfant déclaré pour ce collaborateur.</span>
                                </div>
                                {canWrite && onEditFamille && (
                                    <Button
                                        type="button"
                                        variant="ghost"
                                        size="xs"
                                        onClick={onEditFamille}
                                        iconLeft={<Plus size={12} />}
                                        className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold"
                                    >
                                        Ajouter un enfant
                                    </Button>
                                )}
                            </div>
                        )}
                    </div>
                </CardBody>
            </Card>

            {/* 2. Carte Secondaire : Contacts d'Urgence */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between gap-2">
                    <div className="flex items-center gap-2.5">
                        <CardTitle size="sm" className="tracking-wider flex items-center gap-2">
                            <ShieldAlert size={15} className="text-[var(--color-brand-600)]" />
                            Contacts d'urgence
                        </CardTitle>
                        {contactsUrgence.length > 0 && (
                            <Badge variant="default" size="xs">
                                {contactsUrgence.length} contact{contactsUrgence.length > 1 ? 's' : ''}
                            </Badge>
                        )}
                    </div>
                    {canWrite && onEditUrgence && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="xs"
                            onClick={onEditUrgence}
                            iconLeft={<Plus size={12} />}
                            className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold"
                        >
                            {contactsUrgence.length > 0 ? 'Gérer les contacts' : 'Ajouter un contact'}
                        </Button>
                    )}
                </CardHeader>
                <CardBody className="p-5">
                    {contactsUrgence.length > 0 ? (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            {contactsUrgence.map((ct, i) => (
                                <div
                                    key={i}
                                    className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-4 space-y-2.5"
                                >
                                    <div className="flex items-center justify-between gap-2">
                                        <p className="text-sm font-bold text-[var(--color-fg)]">
                                            {ct.nom} {ct.prenom}
                                        </p>
                                        <Badge variant="default" size="xs" className="font-semibold">
                                            {ct.lienParente || 'Proche'}
                                        </Badge>
                                    </div>
                                    <div className="space-y-1.5 text-xs pt-1 border-t border-[var(--color-border-subtle)]">
                                        <div className="flex items-center justify-between">
                                            <span className="text-[var(--color-fg-muted)] text-[11px]">Téléphone d'urgence</span>
                                            {ct.telephone ? (
                                                <a
                                                    href={`tel:${ct.telephone}`}
                                                    className="font-mono font-bold text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] hover:underline flex items-center gap-1.5"
                                                >
                                                    <Phone size={13} />
                                                    {ct.telephone}
                                                </a>
                                            ) : (
                                                <span className="text-[var(--color-fg-subtle)]">Non renseigné</span>
                                            )}
                                        </div>
                                        {ct.adresse && (
                                            <div className="flex items-center justify-between text-[11px]">
                                                <span className="text-[var(--color-fg-muted)]">Adresse</span>
                                                <span className="text-[var(--color-fg)] truncate max-w-[200px]">
                                                    {ct.adresse}
                                                </span>
                                            </div>
                                        )}
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <EmptyState
                            icon={ShieldAlert}
                            title="Aucun contact d'urgence enregistré"
                            description="Aucun contact de confiance enregistré pour ce collaborateur."
                            actionLabel={canWrite && onEditUrgence ? "Ajouter un contact d'urgence" : undefined}
                            onAction={canWrite ? onEditUrgence : undefined}
                            compact
                        />
                    )}
                </CardBody>
            </Card>
        </div>
    );
}
