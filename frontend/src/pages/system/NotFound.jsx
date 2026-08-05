import { HelpCircle } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function NotFound() {
    return (
        <StatusPage
            code="404"
            icon={HelpCircle}
            title="Page introuvable"
            description="La page que vous recherchez n'existe pas ou a été déplacée."
            tone="neutral"
            actionLabel="Retour à l'accueil"
            actionHref="/"
        />
    );
}
