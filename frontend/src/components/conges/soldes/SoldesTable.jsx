import { Link } from 'react-router-dom';
import { ArrowUpDown, ArrowUp, ArrowDown, ExternalLink } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';

const formatDate = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

export default function SoldesTable({
  ganttData,
  paginatedTableData,
  totalTableItems,
  tableSortField,
  tableSortOrder,
  handleTableSort,
  tablePage,
  setTablePage,
  tablePageSize,
  setTablePageSize
}) {
  const { hasPermission } = useAuth();
  const canViewEmploye = hasPermission('EMPLOYE_READ');
  const renderSortIcon = (field) => {
    if (tableSortField !== field) return <ArrowUpDown size={13} className="text-[var(--color-fg-subtle)] opacity-40 ml-1 inline-block" />;
    return tableSortOrder === 'asc'
      ? <ArrowUp size={13} className="text-[var(--color-brand-500)] ml-1 inline-block font-bold" />
      : <ArrowDown size={13} className="text-[var(--color-brand-500)] ml-1 inline-block font-bold" />;
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Dernières absences du mois</CardTitle>
      </CardHeader>
      <CardBody className="p-0">
        {ganttData.length === 0 ? (
          <EmptyState title="Aucune donnée" description="Aucune absence validée ou en attente ce mois-ci." />
        ) : (
          <Table>
            <Thead>
              <Tr>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('employe')}>
                  Employé {renderSortIcon('employe')}
                </Th>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('type')}>
                  Type {renderSortIcon('type')}
                </Th>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('originalStart')}>
                  Du {renderSortIcon('originalStart')}
                </Th>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('originalEnd')}>
                  Au {renderSortIcon('originalEnd')}
                </Th>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('dureeJours')}>
                  Jours {renderSortIcon('dureeJours')}
                </Th>
                <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('statut')}>
                  Statut {renderSortIcon('statut')}
                </Th>
              </Tr>
            </Thead>
            <Tbody>
              {paginatedTableData.map((item, idx) => (
                <Tr key={idx}>
                  <Td className="font-semibold text-[var(--color-fg)]">
                    {item.idEmploye && canViewEmploye ? (
                      <Link
                        to={`/employes/${item.idEmploye}?tab=conges`}
                        className="hover:text-[var(--color-brand-500)] hover:underline inline-flex items-center gap-1 font-semibold text-[var(--color-fg)]"
                        title={`Ouvrir la fiche de ${item.employe} (onglet Congés)`}
                      >
                        {item.employe}
                        <ExternalLink size={12} className="text-[var(--color-fg-subtle)] hover:text-[var(--color-brand-500)]" />
                      </Link>
                    ) : (
                      item.employe
                    )}
                  </Td>
                  <Td><Badge variant="brand">{item.type}</Badge></Td>
                  <Td className="font-data">{formatDate(item.originalStart)}</Td>
                  <Td className="font-data">{formatDate(item.originalEnd)}</Td>
                  <Td className="font-data">{item.dureeJours}</Td>
                  <Td>
                    <Badge variant={item.statut === 'VALIDE' ? 'success' : 'warning'} dot>
                      {item.statut === 'VALIDE' ? 'Validé' : 'En attente'}
                    </Badge>
                  </Td>
                </Tr>
              ))}
            </Tbody>
          </Table>
        )}
      </CardBody>

      {/* Pagination du tableau récapitulatif */}
      {totalTableItems > 0 && (
        <Pagination
          currentPage={tablePage}
          totalItems={totalTableItems}
          pageSize={tablePageSize}
          pageSizeOptions={[5, 10, 25, 50]}
          onPageChange={setTablePage}
          onPageSizeChange={(size) => {
            setTablePageSize(size);
            setTablePage(1);
          }}
        />
      )}
    </Card>
  );
}
