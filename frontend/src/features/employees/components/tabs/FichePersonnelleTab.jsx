import { User, MapPin, Heart, Landmark, Shield, Plus } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Button from '@/components/ui/forms/Button';
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

export default function FichePersonnelleTab({ emp, comptes = [], canWrite = false, onEdit, onAddCompte }) {
    if (!emp) return null;

    const genreLibelle = emp.genreLibelle || (emp.sexe === 'M' ? 'Masculin' : emp.sexe === 'F' ? 'Féminin' : emp.sexe);

    return (
        <div className="space-y-4 font-sans">
            {/* 1. Carte Principale : Identité & État Civil */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="tracking-wider flex items-center gap-2">
                        <User size={15} className="text-[var(--color-brand-600)]" />
                        Identité &amp; État civil
                    </CardTitle>
                    {genreLibelle && (
                        <Badge variant="default" size="xs">
                            {genreLibelle}
                        </Badge>
                    )}
                </CardHeader>
                <CardBody className="p-5">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                        {/* Volet Gauche : État Civil & Situation */}
                        <div className="bg-[var(--color-bg-subtle)]/50 p-4 sm:p-5 space-y-2">
                            <h4 className="font-bold text-[var(--color-fg)] text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                                <Heart size={13} className="text-[var(--color-brand-600)]" />
                                État civil &amp; Situation personnelle
                            </h4>
                            <div className="space-y-0.5 pt-1">
                                <InfoRow label="Date de naissance">
                                    {fmtDate(emp.dateNaissance)}{emp.age ? ` (${emp.age} ans)` : ''}
                                </InfoRow>
                                <InfoRow label="Lieu de naissance">{emp.lieuNaissance}</InfoRow>
                                <InfoRow label="Nationalité">{emp.nationalite}</InfoRow>
                                <InfoRow label="Genre">{genreLibelle}</InfoRow>
                                <InfoRow label="Situation familiale">{emp.situationFamiliale}</InfoRow>
                                <InfoRow label="Filiation (Père)">{emp.pereNomPrenom}</InfoRow>
                                <InfoRow label="Filiation (Mère)">{emp.mereNomPrenom}</InfoRow>
                            </div>
                        </div>

                        {/* Volet Droit : Pièces Officielles & Sécurité Sociale */}
                        <div className="bg-[var(--color-bg-subtle)]/50 p-4 sm:p-5 space-y-2">
                            <h4 className="font-bold text-[var(--color-fg)] text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                                <Shield size={13} className="text-[var(--color-brand-600)]" />
                                Pièces d'identité &amp; CNaPS
                            </h4>
                            <div className="space-y-0.5 pt-1">
                                <InfoRow label="N° Carte d'Identité (CIN)" mono>{emp.cinNumero}</InfoRow>
                                <InfoRow label="Délivrance CIN">
                                    {emp.cinDateDelivrance ? `${fmtDate(emp.cinDateDelivrance)} à ${emp.cinLieuDelivrance || '—'}` : null}
                                </InfoRow>
                                {emp.cinDuplicataDate && (
                                    <InfoRow label="Duplicata CIN">
                                        {`${fmtDate(emp.cinDuplicataDate)} à ${emp.cinDuplicataLieu || '—'}`}
                                    </InfoRow>
                                )}
                                <InfoRow label="N° Permis de conduire" mono>{emp.permisConduireNumero}</InfoRow>
                                <InfoRow label="N° Affiliation CNaPS" mono>{emp.numAffiliationCnaps}</InfoRow>
                                <InfoRow label="Date déclaration CNaPS">{fmtDate(emp.dateDeclaration)}</InfoRow>
                            </div>
                        </div>
                    </div>
                </CardBody>
            </Card>

            {/* 2. Carte Secondaire : Domicile & Coordonnées Bancaires */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="tracking-wider flex items-center gap-2">
                        <Landmark size={15} className="text-[var(--color-brand-600)]" />
                        Domicile &amp; Règlements bancaires
                    </CardTitle>
                    {comptes.length > 0 && (
                        <Badge variant="brand" size="xs">
                            {comptes.length} compte{comptes.length > 1 ? 's' : ''} enregistré{comptes.length > 1 ? 's' : ''}
                        </Badge>
                    )}
                </CardHeader>
                <CardBody className="p-5 space-y-4">
                    {/* Domicile : adresse et ville distinctes et préservées */}
                    <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-3.5 sm:p-4 flex items-start gap-3 text-xs">
                        <MapPin size={16} className="text-[var(--color-brand-600)] shrink-0 mt-0.5" />
                        <div className="space-y-1 min-w-0 flex-1">
                            <span className="text-[11px] font-medium text-[var(--color-fg-muted)] block uppercase tracking-wider">
                                Adresse de résidence &amp; Ville
                            </span>
                            <div className="flex flex-wrap items-center gap-x-4 gap-y-1">
                                <span className="font-bold text-[var(--color-fg)]">
                                    {emp.adresse || 'Adresse non renseignée'}
                                </span>
                                {emp.ville && (
                                    <span className="text-[var(--color-fg-muted)] font-medium">
                                        <span className="text-[var(--color-fg-subtle)] mr-1">•</span>Ville : <strong className="text-[var(--color-fg)]">{emp.ville}</strong>
                                    </span>
                                )}
                            </div>
                        </div>
                    </div>

                    {/* Comptes bancaires */}
                    <div className="space-y-2">
                        <div className="flex items-center justify-between">
                            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Comptes bancaires pour virement ({comptes.length})
                            </h4>
                            {canWrite && onEdit && (
                                <Button
                                    type="button"
                                    variant="ghost"
                                    size="xs"
                                    onClick={onAddCompte || onEdit}
                                    iconLeft={<Plus size={12} />}
                                    className="text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] font-semibold"
                                >
                                    {comptes.length > 0 ? 'Gérer les comptes' : 'Ajouter un compte'}
                                </Button>
                            )}
                        </div>

                        {comptes.length > 0 ? (
                            <Table bordered={false}>
                                <Thead>
                                    <Tr>
                                        <Th>Banque &amp; Agence</Th>
                                        <Th>N° Compte / RIB</Th>
                                        <Th align="center">Usage</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {comptes.map((cb, i) => (
                                        <Tr key={i}>
                                            <Td>
                                                <p className="font-bold text-xs text-[var(--color-fg)]">
                                                    {cb.nomBanque || cb.libelleBanque || cb.codeBanque || (cb.idBanque ? `Banque #${cb.idBanque}` : '—')}
                                                </p>
                                                {cb.agence && <p className="text-[11px] text-[var(--color-fg-muted)]">{cb.agence}</p>}
                                            </Td>
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
                                description="Aucun compte de virement associé. Renseignez un compte bancaire si les salaires doivent être versés par virement."
                                actionLabel={canWrite && (onAddCompte || onEdit) ? "Renseigner un compte" : undefined}
                                onAction={canWrite ? (onAddCompte || onEdit) : undefined}
                                compact
                            />
                        )}
                    </div>
                </CardBody>
            </Card>
        </div>
    );
}
