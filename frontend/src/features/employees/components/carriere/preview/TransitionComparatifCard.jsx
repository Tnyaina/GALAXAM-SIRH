import { ArrowRight, TrendingUp, TrendingDown, Minus, Clock } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import HasPermission from '@/features/auth/components/HasPermission';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';

/**
 * TransitionComparatifCard
 * 
 * Composant de synthèse comparative instantanée Avant ➔ Après pour les transitions RH.
 * Met en valeur les modifications effectives, calcule les deltas de salaire (montant et pourcentage),
 * et présente un tableau différentiel clair et auditable.
 */
export default function TransitionComparatifCard({
    typeEvenementActuel,
    nomEmploye,
    formData,
    exigeNouveauContrat,
    avantTypeContrat,
    apresTypeContrat,
    avantSite,
    apresSite,
    avantDepartement,
    apresDepartement,
    avantService,
    apresService,
    avantFonction,
    apresFonction,
    avantCategorie,
    apresCategorie,
    avantResponsable,
    apresResponsable,
    avantModePaie,
    apresModePaie,
    avantSalaire,
}) {
    // Calcul financier du delta de rémunération
    const salAvant = avantSalaire !== null && avantSalaire !== undefined ? Number(avantSalaire) : null;
    const salApres = formData?.salaireBase !== null && formData?.salaireBase !== undefined && formData.salaireBase !== ''
        ? Number(formData.salaireBase)
        : null;

    const hasSalaryData = salAvant !== null && salApres !== null;
    const diffMontant = hasSalaryData ? salApres - salAvant : null;
    const diffPct = hasSalaryData && salAvant > 0
        ? ((diffMontant / salAvant) * 100).toFixed(1)
        : null;

    const isAugmentation = diffMontant !== null && diffMontant > 0;
    const isDiminution = diffMontant !== null && diffMontant < 0;

    // Définition des critères d'évaluation différentielle
    const criteres = [
        ...(exigeNouveauContrat || avantTypeContrat !== apresTypeContrat ? [{
            id: 'contrat',
            label: 'Type de contrat',
            avant: avantTypeContrat || null,
            apres: apresTypeContrat || null,
            modifie: Boolean(apresTypeContrat && avantTypeContrat !== apresTypeContrat),
        }] : []),
        {
            id: 'site',
            label: "Site d'affectation",
            avant: avantSite || null,
            apres: apresSite || null,
            modifie: Boolean(apresSite && avantSite !== apresSite),
        },
        {
            id: 'departement',
            label: 'Département',
            avant: avantDepartement || null,
            apres: apresDepartement || null,
            modifie: Boolean(apresDepartement && avantDepartement !== apresDepartement),
        },
        {
            id: 'service',
            label: 'Service',
            avant: avantService || null,
            apres: apresService || null,
            modifie: Boolean(apresService && avantService !== apresService),
        },
        {
            id: 'fonction',
            label: 'Poste / Fonction',
            avant: avantFonction || null,
            apres: apresFonction || null,
            modifie: Boolean(apresFonction && avantFonction !== apresFonction),
        },
        {
            id: 'categorie',
            label: 'Catégorie professionnelle',
            avant: avantCategorie || null,
            apres: apresCategorie || null,
            modifie: Boolean(apresCategorie && avantCategorie !== apresCategorie),
        },
        {
            id: 'responsable',
            label: 'Supérieur hiérarchique',
            avant: avantResponsable || null,
            apres: apresResponsable || null,
            modifie: Boolean(apresResponsable && avantResponsable !== apresResponsable),
        },
        {
            id: 'modePaie',
            label: 'Mode de calcul paie',
            avant: avantModePaie || null,
            apres: apresModePaie || null,
            modifie: Boolean(apresModePaie && avantModePaie !== apresModePaie),
        },
    ];

    const nbModifications = criteres.filter(c => c.modifie).length + (diffMontant && diffMontant !== 0 ? 1 : 0);

    return (
        <Card bordered className="bg-[var(--color-surface)] shadow-xs space-y-4">
            {/* En-tête : Événement RH + Badge modifications */}
            <CardHeader divided className="flex items-center justify-between gap-3 bg-[var(--color-bg-subtle)] px-4 py-3">
                <div>
                    <CardTitle size="sm" className="flex items-center gap-2">
                        <span>{typeEvenementActuel?.libelle || 'Mouvement RH'}</span>
                        <Badge variant="primary" size="xs">
                            Prise d'effet : {formData?.dateEffet ? fmtDate(formData.dateEffet) : 'Non définie'}
                        </Badge>
                    </CardTitle>
                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                        Collaborateur : <strong className="text-[var(--color-fg)]">{nomEmploye || 'Non renseigné'}</strong>
                    </p>
                </div>
                <Badge variant={nbModifications > 0 ? 'info' : 'default'} size="sm" className="font-semibold">
                    {nbModifications} modification{nbModifications > 1 ? 's' : ''} détectée{nbModifications > 1 ? 's' : ''}
                </Badge>
            </CardHeader>

            <CardBody className="space-y-4 px-4 py-1">
                {/* 1. Carte Focus Rémunération avec calcul de Delta (conditionnée à PAIE_READ) */}
                <HasPermission authority="PAIE_READ">
                    <div className="p-3.5 bg-[var(--color-bg-subtle)] space-y-2">
                        <div className="flex items-center justify-between text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            <span>Évolution de Rémunération</span>
                            {hasSalaryData && (
                                <Badge
                                    variant={isAugmentation ? 'success' : isDiminution ? 'danger' : 'default'}
                                    size="xs"
                                    icon={isAugmentation ? <TrendingUp size={12} /> : isDiminution ? <TrendingDown size={12} /> : <Minus size={12} />}
                                >
                                    {isAugmentation
                                        ? `+${fmtMoney(diffMontant)} Ar (+${diffPct}%)`
                                        : isDiminution
                                            ? `${fmtMoney(diffMontant)} Ar (${diffPct}%)`
                                            : 'Rémunération inchangée'}
                                </Badge>
                            )}
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-3 items-center gap-3 pt-1 text-center">
                            {/* Salaire Actuel */}
                            <div className="p-2 bg-[var(--color-bg-subtle)]">
                                <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-semibold block">Actuel</span>
                                <span className="text-sm font-bold font-data text-[var(--color-fg-muted)]">
                                    {salAvant !== null ? `${fmtMoney(salAvant)} Ar` : <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                                </span>
                            </div>

                            {/* Flèche transition */}
                            <div className="flex justify-center items-center text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold">
                                <ArrowRight size={20} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]" />
                            </div>

                            {/* Nouveau Salaire Cible */}
                            <div className={`p-2 ${
                                isAugmentation
                                    ? 'bg-[var(--color-success-500)]/10'
                                    : isDiminution
                                        ? 'bg-[var(--color-danger-500)]/10'
                                        : 'bg-[var(--color-bg-subtle)]'
                            }`}>
                                <span className="text-[10px] text-[var(--color-fg-muted)] uppercase font-semibold block">Nouveau</span>
                                <span className="text-base font-extrabold font-data text-[var(--color-fg)]">
                                    {salApres !== null ? `${fmtMoney(salApres)} Ar` : <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                                </span>
                            </div>
                        </div>
                    </div>
                </HasPermission>

                {/* 2. Tableau Comparatif des Dimensions RH */}
                <div className="space-y-1.5">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                        Tableau Différentiel des Affectations
                    </h4>

                    <Table bordered={false}>
                        <Thead>
                            <Tr>
                                <Th>Dimension</Th>
                                <Th>Situation Actuelle (Avant)</Th>
                                <Th width="24px" align="center"></Th>
                                <Th>Nouvelle Situation (Après)</Th>
                                <Th align="right">Statut</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {criteres.map((c) => (
                                <Tr
                                    key={c.id}
                                    className={c.modifie ? 'bg-[var(--color-brand-500)]/[0.04] font-medium' : ''}
                                >
                                    <Td className="text-xs font-semibold text-[var(--color-fg-muted)]">
                                        {c.label}
                                    </Td>
                                    <Td className={`text-xs ${c.modifie ? 'line-through text-[var(--color-fg-muted)] opacity-70' : 'text-[var(--color-fg)]'}`}>
                                        {c.avant || <span className="text-[var(--color-fg-subtle)] font-normal">Non renseigné</span>}
                                    </Td>
                                    <Td align="center">
                                        {c.modifie ? (
                                            <ArrowRight size={14} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] inline-block" />
                                        ) : (
                                            <span className="text-[var(--color-fg-subtle)] text-xs font-normal">·</span>
                                        )}
                                    </Td>
                                    <Td className={`text-xs ${c.modifie ? 'font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]' : 'text-[var(--color-fg)]'}`}>
                                        {c.apres || <span className="text-[var(--color-fg-subtle)] font-normal">Non renseigné</span>}
                                    </Td>
                                    <Td align="right">
                                        {c.modifie ? (
                                            <Badge variant="info" size="xs">Modifié</Badge>
                                        ) : (
                                            <Badge variant="default" size="xs">Inchangé</Badge>
                                        )}
                                    </Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                </div>

                {/* 3. Période d'essai (si applicable) */}
                {formData?.aPeriodeEssai && (
                    <div className="p-3 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 text-xs flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                            <Clock size={16} className="text-[var(--color-warning-500)] shrink-0" />
                            <span>
                                <strong>Période d'essai :</strong> {formData.dureeEssaiMois || '3'} mois
                                {formData.dateFinEssai && ` (Échéance : ${fmtDate(formData.dateFinEssai)})`}
                            </span>
                        </div>
                        <Badge variant="warning" size="xs">Essai Actif</Badge>
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
