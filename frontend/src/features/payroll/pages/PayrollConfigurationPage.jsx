import { Suspense } from 'react';
import SEO from '@/components/system/SEO';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import PayrollEngineMap from '@/features/payroll/components/PayrollEngineMap';

export default function PayrollConfigurationPage() {
    return (
        <div className="max-w-[1600px] mx-auto w-full pb-10">
            <SEO
                title="Configuration de paie | GALAXAM GRH"
                description="Traitements, rubriques et types de variables du moteur de paie."
            />
            <Suspense fallback={<SkeletonTable rows={8} columns={5} />}>
                <PayrollEngineMap />
            </Suspense>
        </div>
    );
}
