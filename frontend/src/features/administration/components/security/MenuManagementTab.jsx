import { useState, useEffect } from 'react';
import { Search, Compass, Info } from 'lucide-react';
import referentielService from '@/features/administration/api/referentielService';
import { getMenuIcon } from '@/utils/iconRegistry';
import Card from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';

export default function MenuManagementTab() {
    // État local : consultation des menus et recherche
    const [menus, setMenus] = useState([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await referentielService.getMenusFlat();
            setMenus(data);
        } catch (err) {
            setError("Erreur lors du chargement des menus. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const getOrderedMenus = () => {
        const parents = menus.filter(m => !m.idPere || m.niveau === 1);
        parents.sort((a, b) => (a.rang || 0) - (b.rang || 0));

        const result = [];
        parents.forEach(parent => {
            result.push(parent);
            const children = menus.filter(m => m.idPere === parent.idMenu);
            children.sort((a, b) => (a.rang || 0) - (b.rang || 0));
            result.push(...children);
        });

        const processedIds = result.map(r => r.idMenu);
        const remaining = menus.filter(m => !processedIds.includes(m.idMenu));

        return [...result, ...remaining];
    };

    const orderedMenus = getOrderedMenus();

    const filteredMenus = orderedMenus.filter(m =>
        m.idMenu.toLowerCase().includes(search.toLowerCase()) ||
        m.libelle.toLowerCase().includes(search.toLowerCase())
    );

    if (loading && menus.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={8} columns={6} />
            </div>
        );
    }

    return (
        <div className="space-y-4">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Note informative Mode Consultation */}
            <div className="flex items-center gap-2.5 p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-xs text-[var(--color-fg-muted)]">
                <Info size={16} className="text-[var(--color-brand-500)] shrink-0" />
                <span>
                    Cette page est en <strong>consultation seule</strong>. Les rubriques et sous-menus de navigation sont intégrés à l'architecture du système et aux routes de l'application. L'attribution des accès aux menus par rôle s'effectue depuis l'onglet <strong>Rôles & permissions</strong>.
                </span>
            </div>

            {/* Barre d'outils */}
            <Card className="overflow-hidden p-0" shadow="none">
                <div className="flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center p-3.5 border-b border-[var(--color-border-subtle)]">
                    <div className="flex-1 max-w-md">
                        <Input
                            aria-label="Rechercher"
                            placeholder="Rechercher par code ou libellé…"
                            value={search}
                            onChange={event => setSearch(event.target.value)}
                            iconLeft={<Search size={16} />}
                        />
                    </div>
                    <div className="text-xs text-[var(--color-fg-muted)]">
                        <strong>{filteredMenus.length}</strong> menu{filteredMenus.length > 1 ? 's' : ''} référencé{filteredMenus.length > 1 ? 's' : ''}
                    </div>
                </div>

                {/* Tableau de consultation des menus */}
                <Table bordered={false}>
                    <Thead>
                        <Th className="text-xs py-3">Code / Identifiant</Th>
                        <Th className="text-xs py-3">Libellé (Hiérarchie)</Th>
                        <Th className="text-xs py-3">Icône Visuelle</Th>
                        <Th className="text-xs py-3">Chemin (Route)</Th>
                        <Th className="text-xs py-3">Position / Ordre</Th>
                        <Th className="text-xs py-3">Menu Père</Th>
                    </Thead>
                    <Tbody>
                        {filteredMenus.length > 0 ? (
                            filteredMenus.map((menu) => {
                                const isChild = menu.niveau > 1 || menu.idPere;
                                const IconComponent = getMenuIcon(menu.icone);
                                return (
                                    <Tr key={menu.idMenu}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)] py-3 text-sm">{menu.idMenu}</Td>
                                        <Td className={`py-3 text-sm ${isChild ? 'pl-8' : ''}`}>
                                            {isChild ? (
                                                <span className="inline-flex items-center gap-2 text-[var(--color-fg-muted)]">
                                                    <span className="text-[var(--color-fg-subtle)] font-bold">↳</span>
                                                    {menu.libelle}
                                                </span>
                                            ) : (
                                                <span className="font-bold text-[var(--color-fg)]">
                                                    {menu.libelle}
                                                </span>
                                            )}
                                        </Td>
                                        <Td className="py-3">
                                            <span className="inline-flex items-center gap-2 font-mono text-xs text-[var(--color-fg)] bg-[var(--color-bg-subtle)] px-2.5 py-1 rounded-none">
                                                <IconComponent size={14} className="text-[var(--color-brand-500)] shrink-0" />
                                                {menu.icone || 'Circle'}
                                            </span>
                                        </Td>
                                        <Td isData muted className="text-xs py-3">{menu.href || '-'}</Td>
                                        <Td className="py-3">
                                            <Badge variant={isChild ? 'default' : 'primary'} size="sm">
                                                {isChild ? `Sous-menu / Rang ${menu.rang}` : `Racine / Rang ${menu.rang}`}
                                            </Badge>
                                        </Td>
                                        <Td className="py-3">
                                            {menu.idPere ? (
                                                <Badge variant="primary" size="sm">{menu.idPere}</Badge>
                                            ) : (
                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                            )}
                                        </Td>
                                    </Tr>
                                );
                            })
                        ) : (
                            <Tr>
                                <Td colSpan={6} className="p-0">
                                    <EmptyState
                                        icon={Compass}
                                        title="Aucun menu trouvé"
                                        description="Aucun menu ne correspond à votre recherche."
                                    />
                                </Td>
                            </Tr>
                        )}
                    </Tbody>
                </Table>
            </Card>
        </div>
    );
}
