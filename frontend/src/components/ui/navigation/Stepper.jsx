import { Check } from 'lucide-react';

/**
 * Stepper — fil d'ariane horizontal pour un flux multi-étapes.
 * steps: [{ id, label }]  ·  currentStep: index 0-based
 */
export default function Stepper({ steps, currentStep, onStepClick }) {
    return (
        <div className="flex items-center px-6 py-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] rounded-none">
            {steps.map((step, i) => {
                const isDone = i < currentStep;
                const isActive = i === currentStep;
                const clickable = isDone && typeof onStepClick === 'function';

                return (
                    <div key={step.id} className="flex items-center flex-1 last:flex-none">
                        <button
                            type="button"
                            disabled={!clickable}
                            onClick={() => clickable && onStepClick(i)}
                            className={`flex items-center gap-2 ${clickable ? 'cursor-pointer' : 'cursor-default'}`}
                        >
                            <span
                                className={`w-7 h-7 flex items-center justify-center text-xs font-bold shrink-0 border transition-colors rounded-none font-data ${
                                    isDone
                                        ? 'bg-[var(--color-brand-500)] border-[var(--color-brand-500)] text-white'
                                        : isActive
                                            ? 'border-[var(--color-brand-500)] text-[var(--color-brand-500)] bg-[var(--color-surface)]'
                                            : 'border-[var(--color-border)] text-[var(--color-fg-subtle)]'
                                }`}
                            >
                                {isDone ? <Check size={14} /> : i + 1}
                            </span>
                            <span
                                className={`text-xs font-semibold whitespace-nowrap uppercase tracking-wider ${
                                    isActive
                                        ? 'text-[var(--color-fg)]'
                                        : isDone
                                            ? 'text-[var(--color-fg-muted)]'
                                            : 'text-[var(--color-fg-subtle)]'
                                }`}
                            >
                                {step.label}
                            </span>
                        </button>

                        {i < steps.length - 1 && (
                            <div
                                className={`flex-1 h-px mx-3 ${
                                    isDone ? 'bg-[var(--color-brand-500)]' : 'bg-[var(--color-border-subtle)]'
                                }`}
                            />
                        )}
                    </div>
                );
            })}
        </div>
    );
}