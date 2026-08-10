import { WifiOff } from 'lucide-react';
import StatusPage from '@/components/system/StatusPage';

export default function NoConnection() {
    return (
        <StatusPage
            icon={WifiOff}
            title="Connexion perdue"
            description="Vérifiez votre connexion internet, puis réessayez."
            tone="warning"
            actionLabel="Réessayer"
            actionHref="/"
        />
    );
}
