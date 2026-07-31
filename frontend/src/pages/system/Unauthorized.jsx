import { ShieldAlert } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function Unauthorized() {
    return (
        <StatusPage
            code="401"
            icon={ShieldAlert}
            title="Accès refusé"
            description="Vous ne disposez pas des permissions requises (rôle administrateur) pour accéder à cette ressource."
            tone="danger"
            actionLabel="Retour au tableau de bord"
            actionHref="/dashboard"
        />
    );
}
