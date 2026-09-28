import { User, MapPin, Heart, Landmark, Shield } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { fmtDate } from '@/utils/dateUtils';

/**
 * InfoRow — Ligne label / valeur épurée
 * La hiérarchie visuelle est assurée par l'espacement et la typographie.
 */
const InfoRow = ({ label, children, mono = false }) => (
    <div className="flex justify-between items-baseline gap-2 min-w-0 py-1.5 border-b border-[var(--color-border-subtle)]/50 last:border-0">
        <span className="text-xs text-[var(--color-fg-muted)] shrink-0 font-medium">{label}</span>
        <span className={`font-bold text-xs text-[var(--color-fg)] text-right truncate ${mono ? 'font-mono' : ''}`}>
            {children || '—'}
        </span>
    </div>
);

export default function FichePersonnelleTab({ emp, comptes = [] }) {
    if (!emp) return null;

    return (
        <div className="space-y-4 font-sans">
            {/* 1. Carte Principale : Identité Juridique & État Civil (Grille 2 volets équilibrée) */}
            <Card shadow="sm">
                <CardHeader className="flex items-center justify-between">
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <User size={15} className="text-[var(--color-brand-500)]" />
                        Identité Juridique &amp; État Civil
                    </CardTitle>
                    <div className="flex items-center gap-2">
                        {emp.situationFamiliale && (
                            <Badge variant="default" size="xs">
                                {emp.situationFamiliale}
                            </Badge>
                        )}
                        {emp.sexe && (
                            <Badge variant="default" size="xs">
                                {emp.sexe === 'M' ? 'Masculin' : emp.sexe === 'F' ? 'Féminin' : emp.sexe}
                            </Badge>
                        )}
                    </div>
                </CardHeader>
                <CardBody className="p-5">
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                        {/* Volet Gauche : État Civil & Filiation */}
                        <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2">
                            <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                                <Heart size={13} className="text-[var(--color-brand-500)]" />
                                État Civil &amp; Filiation
                            </h4>
                            <div className="space-y-0.5 pt-1">
                                <InfoRow label="Date de naissance">
                                    {fmtDate(emp.dateNaissance)}{emp.age ? ` (${emp.age} ans)` : ''}
                                </InfoRow>
                                <InfoRow label="Lieu de naissance">{emp.lieuNaissance}</InfoRow>
                                <InfoRow label="Nationalité">{emp.nationalite}</InfoRow>
                                <InfoRow label="Situation familiale">{emp.situationFamiliale}</InfoRow>
                                <InfoRow label="Filiation (Père)">{emp.pereNomPrenom}</InfoRow>
                                <InfoRow label="Filiation (Mère)">{emp.mereNomPrenom}</InfoRow>
                            </div>
                        </div>

                        {/* Volet Droit : Pièces Officielles & Sécurité Sociale */}
                        <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2">
                            <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                                <Shield size={13} className="text-[var(--color-brand-500)]" />
                                Identité Légale &amp; CNaPS
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
                    <CardTitle size="sm" className="uppercase tracking-wider flex items-center gap-2">
                        <Landmark size={15} className="text-[var(--color-brand-500)]" />
                        Domicile &amp; Règlements Bancaires
                    </CardTitle>
                    {comptes.length > 0 && (
                        <Badge variant="brand" size="xs">
                            {comptes.length} compte{comptes.length > 1 ? 's' : ''} enregistré{comptes.length > 1 ? 's' : ''}
                        </Badge>
                    )}
                </CardHeader>
                <CardBody className="p-5 space-y-4">
                    {/* Domicile en bandeau feutré */}
                    <div className="bg-[var(--color-bg-subtle)]/50 border border-[var(--color-border-subtle)] p-3.5 sm:p-4 flex items-center gap-3 text-xs">
                        <MapPin size={16} className="text-[var(--color-brand-500)] shrink-0" />
                        <div>
                            <span className="text-[11px] font-medium text-[var(--color-fg-muted)] block">Adresse de résidence</span>
                            <span className="font-bold text-[var(--color-fg)]">
                                {emp.adresse || 'Adresse non renseignée'}{emp.ville ? `, ${emp.ville}` : ''}
                            </span>
                        </div>
                    </div>

                    {/* Comptes bancaires */}
                    <div className="space-y-2">
                        <h4 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">
                            Comptes Bancaires pour Virement ({comptes.length})
                        </h4>
                        {comptes.length > 0 ? (
                            <Table>
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
