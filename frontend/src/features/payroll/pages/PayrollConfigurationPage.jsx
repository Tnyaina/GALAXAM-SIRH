import { lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Settings2 } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

const PayrollConfigurationTab = lazy(() => import('@/features/payroll/components/tabs/PayrollConfigurationTab'));
const PayrollEngineMap = lazy(() => import('@/features/payroll/components/PayrollEngineMap'));

export default function PayrollConfigurationPage() {
    const navigate = useNavigate();
    const [params] = useSearchParams();
    const editor = ['rubriques', 'types'].includes(params.get('vue')) ? params.get('vue') : null;
    return <div className="max-w-[1600px] mx-auto w-full space-y-5 pb-10">
        <SEO title="Configuration de paie | GALAXAM GRH" description="Traitements, rubriques et types de variables du moteur de paie." />
        <PageHeader title="Configuration de paie" subtitle="Type de variable → rubrique → mouvement approuvé → calcul du bulletin."
            icon={Settings2} backLink="/paie" backLabel="Centre de paie"
            breadcrumbs={[
                { label: 'Tableau de bord', href: '/dashboard' },
                { label: 'Centre de paie', href: '/paie' },
                { label: 'Configuration' }
            ]}
            actions={editor && <Button variant="outline" className="min-h-[44px]" iconLeft={<ArrowLeft size={16} />}
                onClick={() => navigate(`/paie/configuration?mode=${editor}`)}>Retour au parcours</Button>} />
        <Suspense fallback={<SkeletonTable rows={8} columns={5} />}>
            {editor ? <PayrollConfigurationTab key={editor} domaine={editor} embedded /> : <PayrollEngineMap />}
        </Suspense>
    </div>;
}
