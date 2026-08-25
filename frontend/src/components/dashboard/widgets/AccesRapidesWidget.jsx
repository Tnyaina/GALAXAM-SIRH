import { Link } from 'react-router-dom';
import { Calendar, Users, Clock, ChevronRight } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';

export default function AccesRapidesWidget() {
    return (
        <Card>
            <CardHeader>
                <CardTitle>Accès Rapides SIRH</CardTitle>
            </CardHeader>
            <CardBody className="space-y-2">
                {[
                    { to: '/conges', label: 'Gestion des Congés & Absences', icon: Calendar },
                    { to: '/employes', label: 'Gestion du Personnel & Fiches', icon: Users },
                    { to: '/pointages/saisie', label: 'Pointage & Temps de travail', icon: Clock },
                ].map(({ to, label, icon: Icon }) => (
                    <Link
                        key={to}
                        to={to}
                        className="flex items-center justify-between py-2.5 px-3 bg-[var(--color-bg-subtle)] hover:bg-[var(--color-surface-hover)] border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] text-xs font-semibold text-[var(--color-fg)]"
                    >
                        <span className="flex items-center gap-2">
                            <Icon size={14} className="text-[var(--color-brand-500)]" />
                            {label}
                        </span>
                        <ChevronRight size={13} className="text-[var(--color-fg-subtle)]" />
                    </Link>
                ))}
            </CardBody>
        </Card>
    );
}
