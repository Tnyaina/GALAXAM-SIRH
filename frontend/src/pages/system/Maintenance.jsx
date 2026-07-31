import { Wrench } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function Maintenance() {
    return (
        <StatusPage
            code="503"
            icon={Wrench}
            title="Maintenance en cours"
            description="Le service est temporairement indisponible le temps d'une mise à jour. Merci de réessayer dans quelques minutes."
            tone="warning"
            actionLabel="Actualiser la page"
            actionHref="/"
        />
    );
}
