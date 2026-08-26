import { memo } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDate } from '@/components/dashboard/DashboardUtils';

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
            title={`Vérification des Doublons — ${employeeGroup.nomPrenom || ''}`}
            size="lg"
        >
            <div className="space-y-4">
                <Alert variant="warning" title="Analyse des Contrats">
                    Plusieurs enregistrements de contrat ont été détectés pour ce collaborateur. Comparez les dates et les numéros pour identifier les doublons à clôturer ou annuler.
                </Alert>

                <div className="overflow-x-auto border border-[var(--color-border-subtle)]">
                    <table className="w-full text-xs text-left">
                        <thead className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] font-bold uppercase tracking-wider text-[10px]">
                            <tr>
                                <th className="p-3">N° Contrat</th>
                                <th className="p-3">Type</th>
                                <th className="p-3">Statut</th>
                                <th className="p-3">Date Début</th>
                                <th className="p-3">Date Fin</th>
                                <th className="p-3">Site / Fonction</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--color-border-subtle)]">
                            {employeeGroup.contrats?.map((ct, i) => (
                                <tr key={i} className="hover:bg-[var(--color-bg-subtle)]/50">
                                    <td className="p-3 font-data font-bold text-[var(--color-brand-600)]">
                                        {ct.numeroContrat || '-'}
                                    </td>
                                    <td className="p-3 font-semibold">{ct.libelleTypeContrat}</td>
                                    <td className="p-3">
                                        <Badge variant={ct.codeStatutContrat === 'ACTIF' ? 'success' : 'default'} size="xs">
                                            {ct.libelleStatutContrat}
                                        </Badge>
                                    </td>
                                    <td className="p-3 font-data">{fmtDate(ct.dateDebut)}</td>
                                    <td className="p-3 font-data">{ct.dateFin ? fmtDate(ct.dateFin) : 'Indéterminée'}</td>
                                    <td className="p-3 text-[var(--color-fg-muted)]">
                                        {ct.nomSite} / {ct.nomFonction}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
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
