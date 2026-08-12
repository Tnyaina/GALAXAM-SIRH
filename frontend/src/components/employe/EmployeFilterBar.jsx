import { Search } from 'lucide-react';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';

export default function EmployeFilterBar({
    searchQuery = '',
    onSearchChange,
    statusFilter = 'ALL',
    onStatusFilterChange,
}) {
    return (
        <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 border border-ink-150 dark:border-ink-700 shadow-sm">
            <div className="flex-1 max-w-md">
                <Input
                    type="text"
                    value={searchQuery}
                    onChange={(e) => onSearchChange(e.target.value)}
                    placeholder="Rechercher par nom, matricule, CIN..."
                    icon={<Search size={18} />}
                />
            </div>

            <div className="flex items-center gap-1 bg-ink-50 dark:bg-ink-950 border border-ink-200 dark:border-ink-700 p-1">
                {[
                    { key: 'ALL', label: 'Tous' },
                    { key: 'ACTIVE', label: 'Actifs' },
                    { key: 'INACTIVE', label: 'Inactifs' },
                ].map(f => (
                    <Button
                        key={f.key}
                        size="xs"
                        variant={statusFilter === f.key ? 'primary' : 'ghost'}
                        onClick={() => onStatusFilterChange(f.key)}
                    >
                        {f.label}
                    </Button>
                ))}
            </div>
        </div>
    );
}
