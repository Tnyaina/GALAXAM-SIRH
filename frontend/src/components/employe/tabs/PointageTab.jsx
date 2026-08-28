import { Clock, ShieldCheck, UserCheck } from 'lucide-react';
import Card, { CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';

export default function PointageTab({ _idEmploye }) {
    return (
        <div className="space-y-6">
            <Card>
                <CardHeader>
                    <div className="flex items-center justify-between w-full">
                        <CardTitle className="flex items-center gap-2">
                            <Clock size={18} className="text-[var(--color-brand-500)]" />
                            Régime de Présence & Paramètres de Pointage
                        </CardTitle>
                        <Badge variant="brand" size="sm" className="font-bold">
                            Base Contractuelle : 40h / semaine
                        </Badge>
                    </div>
                </CardHeader>
                <CardBody className="space-y-4">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                                <Clock size={14} className="text-[var(--color-brand-500)]" />
                                Horaires de Référence
                            </p>
                            <p className="text-sm font-bold text-[var(--color-fg)]">
                                08h00 - 12h00 | 13h00 - 17h00
                            </p>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                Lundi au Vendredi (8.00h / jour)
                            </p>
                        </div>

                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                                <ShieldCheck size={14} className="text-amber-500" />
                                Tolérance & Retards
                            </p>
                            <p className="text-sm font-bold text-[var(--color-fg)]">
                                0 Tolérance (Calcul strict à la minute)
                            </p>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                Compensation automatique par les heures sup de la semaine
                            </p>
                        </div>

                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                            <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                                <UserCheck size={14} className="text-indigo-500" />
                                Période de Paie Active
                            </p>
                            <p className="text-sm font-bold text-[var(--color-fg)]">
                                Synchronisée au calendrier mensuel
                            </p>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                Configurable dans le menu Périodes de Paie
                            </p>
                        </div>
                    </div>
                </CardBody>
            </Card>
        </div>
    );
}
