import { useState } from 'react';
import { Search, Filter, Plus, Calendar, FileText } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import EmptyState from '@/components/ui/data-display/EmpyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Utilities
const formatDate = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

const getStatusBadgeVariant = (statut) => {
  switch (statut) {
    case 'VALIDE': return 'success';
    case 'REFUSE': return 'danger';
    case 'EN_ATTENTE': return 'warning';
    case 'ANNULE': return 'info';
    default: return 'brand';
  }
};

export default function DemandesTab({ 
  loading, 
  demandes = [], 
  onNewDemande 
}) {
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedDemande, setSelectedDemande] = useState(null);

  if (loading) {
    return (
      <Card>
        <CardBody>
          <SkeletonTable rows={8} />
        </CardBody>
      </Card>
    );
  }

  // Frontend filtering (in real scenario, this would be backend paginated and filtered)
  const filteredDemandes = demandes.filter((d) => {
    const employeName = `${d.prenom || ''} ${d.nom || ''}`.trim();
    return employeName.toLowerCase().includes(searchTerm.toLowerCase()) || 
      (d.codeTypeConge || '').toLowerCase().includes(searchTerm.toLowerCase());
  });

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Barre d'outils et filtres */}
      <Card>
        <CardBody className="p-4">
          <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
            <div className="flex items-center gap-3 w-full sm:w-auto">
              <div className="relative w-full sm:w-64">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                <Input 
                  placeholder="Rechercher employé..." 
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-9"
                />
              </div>
              <Button variant="outline" iconLeft={<Filter size={16} />}>Filtres</Button>
            </div>
            <Button variant="primary" iconLeft={<Plus size={16} />} onClick={onNewDemande}>
              Nouvelle Demande
            </Button>
          </div>
        </CardBody>
      </Card>

      {/* Tableau des Demandes */}
      <Card>
        <CardBody className="p-0">
          {filteredDemandes.length === 0 ? (
            <EmptyState 
              icon={FileText}
              title="Aucune demande trouvée" 
              description="Il n'y a aucune demande de congé correspondant à vos critères."
            />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Réf.</Th>
                  <Th>Employé</Th>
                  <Th>Type</Th>
                  <Th>Début</Th>
                  <Th>Fin</Th>
                  <Th>Durée</Th>
                  <Th>Statut</Th>
                  <Th className="text-right">Actions</Th>
                </Tr>
              </Thead>
              <Tbody>
                {filteredDemandes.map((demande) => {
                  const employeName = `${demande.prenom || ''} ${demande.nom || ''}`.trim();
                  return (
                    <Tr 
                      key={demande.idDemandeConge} 
                      className="cursor-pointer hover:bg-[var(--color-surface-hover)]"
                      onClick={() => setSelectedDemande(demande)}
                    >
                      <Td className="font-data text-xs text-[var(--color-fg-subtle)]">#{demande.idDemandeConge}</Td>
                      <Td className="font-semibold text-[var(--color-fg)]">{employeName || `Employé ${demande.idEmploye}`}</Td>
                      <Td><Badge variant="brand">{demande.codeTypeConge || 'ANNUEL'}</Badge></Td>
                      <Td className="font-data">{formatDate(demande.dateDebut)}</Td>
                      <Td className="font-data">{formatDate(demande.dateFin)}</Td>
                      <Td className="font-data">{demande.nbJoursDecomptes} j</Td>
                      <Td><Badge variant={getStatusBadgeVariant(demande.codeStatutDemande)} dot>{demande.codeStatutDemande}</Badge></Td>
                      <Td className="text-right">
                        <Button 
                          variant="ghost" 
                          size="xs" 
                          onClick={(e) => { e.stopPropagation(); setSelectedDemande(demande); }}
                        >
                          Voir
                        </Button>
                      </Td>
                    </Tr>
                  );
                })}
              </Tbody>
            </Table>
          )}
        </CardBody>
      </Card>

      {/* Modal Détail Demande */}
      {selectedDemande && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none max-w-lg w-full shadow-2xl overflow-hidden font-sans">
            <div className="flex items-center justify-between p-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                <Calendar className="w-4 h-4 text-[var(--color-brand-500)]" />
                Détail de la demande #{selectedDemande.idDemandeConge}
              </h4>
              <button onClick={() => setSelectedDemande(null)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-bold">
                ×
              </button>
            </div>
            <div className="p-6 space-y-6">
              <div className="flex items-center justify-between pb-4 border-b border-[var(--color-border-subtle)]">
                <div>
                  <h3 className="text-lg font-bold text-[var(--color-fg)]">
                    {`${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}`.trim() || `Employé ${selectedDemande.idEmploye}`}
                  </h3>
                  <p className="text-sm text-[var(--color-fg-muted)]">Type: {selectedDemande.codeTypeConge || 'ANNUEL'}</p>
                </div>
                <Badge variant={getStatusBadgeVariant(selectedDemande.codeStatutDemande)} dot>
                  {selectedDemande.codeStatutDemande}
                </Badge>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-1">
                  <span className="text-xs font-bold uppercase text-[var(--color-fg-muted)]">Date de début</span>
                  <p className="font-data text-sm">{formatDate(selectedDemande.dateDebut)}</p>
                </div>
                <div className="space-y-1">
                  <span className="text-xs font-bold uppercase text-[var(--color-fg-muted)]">Date de fin</span>
                  <p className="font-data text-sm">{formatDate(selectedDemande.dateFin)}</p>
                </div>
                <div className="space-y-1">
                  <span className="text-xs font-bold uppercase text-[var(--color-fg-muted)]">Durée estimée</span>
                  <p className="font-data text-sm">{selectedDemande.nbJoursDecomptes} jours</p>
                </div>
                <div className="space-y-1">
                  <span className="text-xs font-bold uppercase text-[var(--color-fg-muted)]">Date de demande</span>
                  <p className="font-data text-sm">{formatDate(selectedDemande.dateDemande)}</p>
                </div>
              </div>

              {selectedDemande.motif && (
                <div className="space-y-2 pt-4 border-t border-[var(--color-border-subtle)]">
                  <span className="text-xs font-bold uppercase text-[var(--color-fg-muted)]">Motif / Commentaire</span>
                  <p className="text-sm bg-[var(--color-bg-subtle)] p-3 rounded-none text-[var(--color-fg)]">
                    {selectedDemande.motif}
                  </p>
                </div>
              )}
            </div>
            <div className="flex items-center justify-end p-4 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              <Button variant="outline" size="sm" onClick={() => setSelectedDemande(null)}>
                Fermer
              </Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
