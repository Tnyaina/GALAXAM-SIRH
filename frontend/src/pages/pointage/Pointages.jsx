import { useState } from 'react';
import { Clock, Calendar, UserCheck, Search, Filter } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';

export default function Pointages() {
    const [searchTerm, setSearchTerm] = useState('');

    // Données de démonstration pour les pointages
    const mockPointages = [
        { id: 1, matricule: 'EMP-0012', nom: 'Andriamanitra Jean', date: '14 août 2026', entree: '08:00', sortie: '17:02', statut: 'Present' },
        { id: 2, matricule: 'EMP-0045', nom: 'Rabe Harilala', date: '14 août 2026', entree: '08:05', sortie: '17:00', statut: 'Present' },
        { id: 3, matricule: 'EMP-0098', nom: 'Rakoto Pierre', date: '14 août 2026', entree: '08:15', sortie: '17:05', statut: 'Retard' },
        { id: 4, matricule: 'EMP-0120', nom: 'Razafy Marie', date: '14 août 2026', entree: '—', sortie: '—', statut: 'Absent' },
        { id: 5, matricule: 'EMP-0131', nom: 'Rasoa Celine', date: '14 août 2026', entree: '07:55', sortie: '—', statut: 'En cours' }
    ];

    const getStatutBadge = (statut) => {
        switch (statut) {
            case 'Present': return <Badge variant="success">Présent</Badge>;
            case 'Retard': return <Badge variant="warning">Retard</Badge>;
            case 'Absent': return <Badge variant="danger">Absent</Badge>;
            case 'En cours': return <Badge variant="primary">En cours</Badge>;
            default: return <Badge variant="neutral">{statut}</Badge>;
        }
    };

    const filteredPointages = mockPointages.filter(p =>
        p.nom.toLowerCase().includes(searchTerm.toLowerCase()) ||
        p.matricule.toLowerCase().includes(searchTerm.toLowerCase())
    );

    return (
        <div className="space-y-6">
            {/* Header */}
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                <div>
                    <h1 className="text-2xl font-bold tracking-tight text-[var(--color-fg)]">Gestion des Pointages</h1>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                        Consultez et validez les heures de présence de l'équipe logistique.
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <Button variant="outline" size="sm" className="flex items-center gap-1.5">
                        <Calendar size={14} /> Exporter
                    </Button>
                    <Button variant="primary" size="sm" className="flex items-center gap-1.5">
                        <Clock size={14} /> Nouveau Pointage
                    </Button>
                </div>
            </div>

            {/* Statistiques rapides */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card shadow="sm" bordered>
                    <CardBody className="flex items-center gap-4 py-4">
                        <div className="p-3 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)]">
                            <UserCheck size={20} />
                        </div>
                        <div>
                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Présents</p>
                            <p className="text-xl font-bold text-[var(--color-fg)] font-data mt-0.5">3 / 5</p>
                        </div>
                    </CardBody>
                </Card>

                <Card shadow="sm" bordered>
                    <CardBody className="flex items-center gap-4 py-4">
                        <div className="p-3 bg-green-500/10 text-green-500">
                            <Clock size={20} />
                        </div>
                        <div>
                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">À l'heure</p>
                            <p className="text-xl font-bold text-[var(--color-fg)] font-data mt-0.5">2</p>
                        </div>
                    </CardBody>
                </Card>

                <Card shadow="sm" bordered>
                    <CardBody className="flex items-center gap-4 py-4">
                        <div className="p-3 bg-amber-500/10 text-amber-500">
                            <Clock size={20} />
                        </div>
                        <div>
                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Retards</p>
                            <p className="text-xl font-bold text-[var(--color-fg)] font-data mt-0.5">1</p>
                        </div>
                    </CardBody>
                </Card>

                <Card shadow="sm" bordered>
                    <CardBody className="flex items-center gap-4 py-4">
                        <div className="p-3 bg-red-500/10 text-red-500">
                            <UserCheck size={20} />
                        </div>
                        <div>
                            <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Absents</p>
                            <p className="text-xl font-bold text-[var(--color-fg)] font-data mt-0.5">1</p>
                        </div>
                    </CardBody>
                </Card>
            </div>

            {/* Liste des Pointages */}
            <Card shadow="sm" bordered>
                <CardHeader className="border-b border-[var(--color-border-subtle)] pb-4 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <CardTitle className="text-sm font-bold uppercase tracking-wider">
                        Registre de présence - Aujourd'hui
                    </CardTitle>
                    <div className="flex w-full sm:w-auto items-center gap-2">
                        <div className="relative flex-1 sm:w-64">
                            <Input
                                type="text"
                                placeholder="Rechercher un employé..."
                                value={searchTerm}
                                onChange={(e) => setSearchTerm(e.target.value)}
                                className="pl-8"
                                name="search"
                            />
                            <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-subtle)]" />
                        </div>
                        <Button variant="outline" size="sm" className="px-2.5">
                            <Filter size={14} />
                        </Button>
                    </div>
                </CardHeader>
                <CardBody className="p-0">
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Matricule</Th>
                                <Th>Employé</Th>
                                <Th>Date</Th>
                                <Th>Entrée</Th>
                                <Th>Sortie</Th>
                                <Th>Statut</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {filteredPointages.length > 0 ? (
                                filteredPointages.map((p) => (
                                    <Tr key={p.id}>
                                        <Td className="font-data font-bold">{p.matricule}</Td>
                                        <Td className="font-semibold">{p.nom}</Td>
                                        <Td>{p.date}</Td>
                                        <Td className="font-data">{p.entree}</Td>
                                        <Td className="font-data">{p.sortie}</Td>
                                        <Td>{getStatutBadge(p.statut)}</Td>
                                    </Tr>
                                ))
                            ) : (
                                <Tr>
                                    <Td colSpan={6} className="text-center py-8 text-[var(--color-fg-muted)]">
                                        Aucun pointage trouvé.
                                    </Td>
                                </Tr>
                            )}
                        </Tbody>
                    </Table>
                </CardBody>
            </Card>
        </div>
    );
}
