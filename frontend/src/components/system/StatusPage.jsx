import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { useAuth } from '@/context/AuthContext'; // Import du hook Auth

const tones = {
    neutral: 'text-[var(--color-fg)] border-[var(--color-border)] bg-[var(--color-bg-subtle)]',
    danger:  'text-[var(--color-danger-600)] border-[var(--color-danger-500)] bg-[var(--color-danger-500)]/8',
    warning: 'text-[var(--color-warning-600)] border-[var(--color-warning-500)] bg-[var(--color-warning-500)]/8',
    info:    'text-[var(--color-info-600)] border-[var(--color-info-500)] bg-[var(--color-info-500)]/8',
};

const codeTones = {
    neutral: 'text-[var(--color-fg-muted)]',
    danger:  'text-[var(--color-danger-500)]',
    warning: 'text-[var(--color-warning-500)]',
    info:    'text-[var(--color-info-500)]',
};

export default function StatusPage({
    code,
    icon: Icon,
    title,
    description,
    tone = 'neutral',
    actionLabel,
    actionHref,
    actionIcon = <ArrowLeft size={16} />,
}) {
    const navigate = useNavigate();
    const { isAuthenticated } = useAuth(); // Récupération de l'état d'authentification

    const iconTone = tones[tone] ?? tones.neutral;
    const codeTone = codeTones[tone] ?? codeTones.neutral;

    // Détermination dynamique de la destination et du texte si non fournis
    const defaultHref = isAuthenticated ? '/dashboard' : '/';
    const targetHref = actionHref ?? defaultHref;
    const defaultLabel = isAuthenticated ? "Retour au tableau de bord" : "Retour à l'accueil";
    const label = actionLabel ?? defaultLabel;

    const handleAction = () => {
        if (targetHref === -1) {
            navigate(-1); // Permet de faire un retour arrière dans l'historique si désiré
        } else {
            navigate(targetHref);
        }
    };

    return (
        <main id="main-content" tabIndex={-1} className="flex-1 w-full min-h-full h-full flex items-center justify-center bg-[var(--color-bg)] px-4 py-8 focus:outline-none">
            <div className="max-w-md w-full bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm p-10 text-center">
                {Icon && (
                    <div className={`w-16 h-16 border-2 flex items-center justify-center mx-auto mb-6 ${iconTone}`}>
                        <Icon size={30} strokeWidth={1.75} />
                    </div>
                )}

                {code && (
                    <p className={`text-xs font-bold uppercase tracking-widest mb-2 ${codeTone}`}>
                        Erreur {code}
                    </p>
                )}

                <h1 className="text-xl font-semibold text-[var(--color-fg)] mb-2">{title}</h1>

                {description && (
                    <p className="text-sm text-[var(--color-fg-muted)] mb-8 leading-relaxed">{description}</p>
                )}

                <div className="flex justify-center">
                    <Button
                        variant="primary"
                        size="md"
                        iconLeft={actionIcon}
                        onClick={handleAction}
                    >
                        {label}
                    </Button>
                </div>
            </div>
        </main>
    );
}