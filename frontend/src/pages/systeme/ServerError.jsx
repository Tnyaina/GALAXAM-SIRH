import { ServerCrash } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function ServerError() {
    return (
        <StatusPage
            code="500"
            icon={ServerCrash}
            title="Erreur serveur"
            description="Une erreur inattendue est survenue de notre côté. Notre équipe a été notifiée, merci de réessayer dans un instant."
            tone="danger"
            actionLabel="Réessayer"
            actionHref="/"
        />
    );
}
