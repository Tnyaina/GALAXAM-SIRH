import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import Button from '@/components/ui/Button';

/**
 * StatusPage — socle commun pour les pages système (404, 401, 500, maintenance...).
 * Garde un rendu carré et sobre, cohérent avec Button/Input/Card (pas d'arrondi).
 *
 * Usage :
 *   <StatusPage code="404" icon={HelpCircle} title="Page introuvable" ... />
 */
const tones = {
    neutral: 'text-[#2d2d2d] border-[#2d2d2d] bg-[#f5f5f5]',
    danger:  'text-[#c0392b] border-[#c0392b] bg-red-50',
    warning: 'text-[#b45309] border-[#b45309] bg-amber-50',
    info:    'text-[#1A82B1] border-[#1A82B1] bg-[#eaf6fa]',
};

const codeTones = {
    neutral: 'text-[#2d2d2d]',
    danger:  'text-[#c0392b]',
    warning: 'text-[#b45309]',
    info:    'text-[#1A82B1]',
};

export default function StatusPage({
    code,
    icon: Icon,
    title,
    description,
    tone = 'neutral',
    actionLabel = "Retour à l'accueil",
    actionHref = '/',
    actionIcon = <ArrowLeft size={16} />,
}) {
    const navigate = useNavigate();
    const iconTone = tones[tone] ?? tones.neutral;
    const codeTone = codeTones[tone] ?? codeTones.neutral;

    return (
        <div className="min-h-screen flex items-center justify-center bg-[#f7f7f7] px-4">
            <div className="max-w-md w-full bg-white border border-[#e5e5e5] shadow-sm p-10 text-center">
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

                <h1 className="text-xl font-semibold text-[#2d2d2d] mb-2">{title}</h1>

                {description && (
                    <p className="text-sm text-[#888] mb-8 leading-relaxed">{description}</p>
                )}

                <div className="flex justify-center">
                    <Button
                        variant="primary"
                        size="md"
                        iconLeft={actionIcon}
                        onClick={() => navigate(actionHref)}
                    >
                        {actionLabel}
                    </Button>
                </div>
            </div>
        </div>
    );
}
