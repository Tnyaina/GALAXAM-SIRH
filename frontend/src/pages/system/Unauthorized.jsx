import { ShieldAlert } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function Unauthorized() {
    return (
        <StatusPage
            code="403"
            icon={ShieldAlert}
            title="Accès refusé (403)"
            description="Vos droits d'accès actuels ne vous permettent pas d'accéder à cette page ou d'effectuer cette opération."
            tone="danger"
            actionLabel="Retour au tableau de bord"
            actionHref="/dashboard"
        />
    );
}
