import { lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ArrowRight, CheckCircle2, ClipboardList, GitBranch, ListChecks, Settings2 } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

const PayrollConfigurationTab = lazy(() => import('@/features/payroll/components/tabs/PayrollConfigurationTab'));
const PayrollRulesReadOnly = lazy(() => import('@/features/payroll/components/tabs/PayrollRulesReadOnly'));

const STEPS = [
    { id: 'rubriques', number: '01', title: 'Définir le traitement', subtitle: 'Rubriques de calcul', icon: Settings2,
        description: 'Calcul, intervention dans la paie, assiettes et période d’application.' },
    { id: 'types', number: '02', title: 'Rendre la saisie possible', subtitle: 'Types de variables', icon: GitBranch,
        description: 'Élément saisissable et rubrique de calcul associée.' },
    { id: 'regles', number: '03', title: 'Vérifier la liaison', subtitle: 'Règles appliquées', icon: ListChecks,
        description: 'Lecture des correspondances et du traitement configuré.' },
];

export default function PayrollConfigurationPage() {
    const [params, setParams] = useSearchParams();
    const active = STEPS.some(step => step.id === params.get('vue')) ? params.get('vue') : 'rubriques';
    const activeStep = STEPS.find(step => step.id === active);
    const changeStep = id => setParams(previous => {
        const next = new URLSearchParams(previous);
        next.set('vue', id);
        return next;
    });

    return <div className="max-w-7xl 2xl:max-w-[1600px] mx-auto w-full space-y-6 pb-10">
        <SEO title="Configuration de paie | GALAXAM GRH" description="Rubriques, types de variables et règles appliquées dans la paie." />
        <PageHeader title="Configuration de paie" subtitle="Définissez le traitement, reliez la saisie, puis vérifiez la règle appliquée."
            icon={ClipboardList} backLink="/paie" backLabel="Centre de paie" />

        <section aria-label="Parcours de configuration" className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
            <div className="px-5 py-4 border-b border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-2">
                <div><h2 className="text-base font-semibold">Parcours de configuration</h2>
                    <p className="text-sm text-[var(--color-fg-muted)] mt-1">Chaque type de variable utilise une rubrique ; contrôlez leur liaison avant la prochaine paie.</p></div>
                <span className="text-xs font-semibold text-[var(--color-brand-500)]">{activeStep.number} / 03</span>
            </div>
            <nav aria-label="Choisir une étape" className="grid grid-cols-1 md:grid-cols-3">
                {STEPS.map((step, index) => {
                    const Icon = step.icon;
                    const selected = active === step.id;
                    return <button key={step.id} type="button" onClick={() => changeStep(step.id)} aria-current={selected ? 'step' : undefined}
                        className={`text-left min-h-[112px] p-5 flex gap-4 border-b md:border-b-0 md:border-r last:border-0 border-[var(--color-border-subtle)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)] transition-colors ${selected ? 'bg-[var(--color-bg-subtle)] shadow-[inset_0_3px_0_var(--color-brand-500)]' : 'hover:bg-[var(--color-bg-subtle)]'}`}>
                        <span className={`w-10 h-10 shrink-0 flex items-center justify-center border ${selected ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]' : 'border-[var(--color-border-subtle)] text-[var(--color-brand-500)]'}`}><Icon size={19} /></span>
                        <span className="min-w-0 flex-1"><span className="block text-[11px] font-semibold tracking-wider text-[var(--color-fg-muted)]">ÉTAPE {step.number}</span>
                            <span className="block font-semibold text-sm mt-1">{step.title}</span>
                            <span className="block text-xs text-[var(--color-fg-muted)] mt-1">{step.subtitle}</span></span>
                        {selected ? <CheckCircle2 size={17} className="shrink-0 text-[var(--color-brand-500)]" /> : index < 2 ? <ArrowRight size={16} className="shrink-0 text-[var(--color-fg-muted)]" /> : null}
                    </button>;
                })}
            </nav>
        </section>

        <section aria-labelledby="configuration-workspace-title" className="space-y-5">
            <div className="flex items-start gap-4"><span className="text-3xl font-semibold text-[var(--color-brand-500)] leading-none">{activeStep.number}</span>
                <div><h2 id="configuration-workspace-title" className="text-xl font-semibold">{activeStep.subtitle}</h2>
                    <p className="text-sm text-[var(--color-fg-muted)] mt-1">{activeStep.description}</p></div></div>
            <Suspense fallback={<SkeletonTable rows={6} columns={3} />}>
                {active === 'rubriques' && <PayrollConfigurationTab key="rubriques" domaine="rubriques" embedded />}
                {active === 'types' && <PayrollConfigurationTab key="types" domaine="types" embedded />}
                {active === 'regles' && <PayrollRulesReadOnly />}
            </Suspense>
        </section>
    </div>;
}
