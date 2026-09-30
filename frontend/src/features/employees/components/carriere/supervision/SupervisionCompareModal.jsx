import { memo } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/features/dashboard/components/dashboardPresentation';

function SupervisionCompareModal({
    isOpen,
    onClose,
    employeeGroup,
    onNavigateCarriere
}) {
    if (!employeeGroup) return null;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={`Vérification des Doublons · ${employeeGroup.nomPrenom || ''}`}
            size="lg"
        >
            <div className="space-y-4">
                <Alert type="warning" title="Analyse des Contrats">
                    Des contrats de même type et de même date de début ont été détectés. Comparez les numéros, dates et affectations avant toute décision.
                </Alert>

                <div className="overflow-x-auto">
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>N° Contrat</Th>
                                <Th>Type</Th>
                                <Th>Statut</Th>
                                <Th>Date Début</Th>
                                <Th>Date Fin</Th>
                                <Th>Site / Fonction</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {employeeGroup.contrats?.map((ct, i) => (
                                <Tr key={i}>
                                    <Td isData className="font-bold text-[var(--color-brand-600)]">
                                        {ct.numeroContrat || '-'}
                                    </Td>
                                    <Td className="font-semibold">{ct.libelleTypeContrat}</Td>
                                    <Td>
                                        <Badge variant={ct.codeStatutContrat === 'ACTIF' ? 'success' : 'default'} size="xs">
                                            {ct.libelleStatutContrat}
                                        </Badge>
                                    </Td>
                                    <Td isData>{fmtDate(ct.dateDebut)}</Td>
                                    <Td isData>{ct.dateFin ? fmtDate(ct.dateFin) : 'Indéterminée'}</Td>
                                    <Td muted>{ct.nomSite} / {ct.nomFonction}</Td>
                                </Tr>
                            ))}
                        </Tbody>
                    </Table>
                </div>

                <div className="flex justify-end gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
                    <Button variant="outline" size="sm" onClick={onClose}>
                        Fermer
                    </Button>
                    <Button
                        variant="primary"
                        size="sm"
                        onClick={() => {
                            onClose();
                            onNavigateCarriere(employeeGroup.idEmploye);
                        }}
                    >
                        Ouvrir la fiche carrière
                    </Button>
                </div>
            </div>
        </Modal>
    );
}

export default memo(SupervisionCompareModal);
