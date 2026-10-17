import { useEffect, useState } from 'react';
import { ArrowLeft, UserMinus } from 'lucide-react';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';

/** Page de travail du dossier ; conserve la même fiche métier que l'ancienne modale. */
export default function DossierSortieWorkspaceShell({ isOpen, onClose, title, children, footer, isDirty }) {
    const [confirmLeave, setConfirmLeave] = useState(false);

    useEffect(() => {
        if (!isOpen || !isDirty) return undefined;
        const warn = (event) => {
            event.preventDefault();
            event.returnValue = '';
        };
        window.addEventListener('beforeunload', warn);
        return () => window.removeEventListener('beforeunload', warn);
    }, [isOpen, isDirty]);

    if (!isOpen) return null;

    return (
        <div className="dossier-stc-workspace w-full space-y-4 pb-8">
            <PageHeader
                title={title}
                subtitle="Préparation, calcul et suivi du départ"
                icon={UserMinus}
                breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Dossiers de sortie', href: '/paie/dossiers-sortie' }, { label: 'Dossier individuel' }]}
                actions={<Button type="button" variant="outline" size="sm" iconLeft={<ArrowLeft size={15} />}
                    onClick={() => isDirty ? setConfirmLeave(true) : onClose?.()}>
                    Retour aux dossiers
                </Button>}
            />
            <Card as="main" shadow="none" className="p-4 sm:p-5">
                {children}
                {footer && (
                    <div className="mt-8">
                        {footer}
                    </div>
                )}
            </Card>
            <ConfirmDialog isOpen={confirmLeave} onClose={() => setConfirmLeave(false)}
                onConfirm={() => { setConfirmLeave(false); onClose?.(); }}
                title="Quitter sans enregistrer"
                message="Les modifications de préparation non enregistrées seront perdues."
                confirmLabel="Quitter sans enregistrer" variant="warning" />
        </div>
    );
}
