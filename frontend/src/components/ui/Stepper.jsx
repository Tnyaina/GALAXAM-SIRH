import { Check } from 'lucide-react';

/**
 * Stepper — fil d'ariane horizontal pour un flux multi-étapes.
 * steps: [{ id, label }]  ·  currentStep: index 0-based
 */
export default function Stepper({ steps, currentStep, onStepClick }) {
    return (
        <div className="flex items-center px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-ink-50 dark:bg-ink-950">
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
                                className={`w-7 h-7 flex items-center justify-center text-xs font-bold shrink-0 border transition-colors ${
                                    isDone
                                        ? 'bg-brand-500 border-brand-500 text-white'
                                        : isActive
                                            ? 'border-brand-500 text-brand-500 bg-white dark:bg-ink-900'
                                            : 'border-ink-200 dark:border-ink-700 text-ink-400'
                                }`}
                            >
                                {isDone ? <Check size={14} /> : i + 1}
                            </span>
                            <span
                                className={`text-xs font-semibold whitespace-nowrap ${
                                    isActive
                                        ? 'text-ink-800 dark:text-white'
                                        : isDone
                                            ? 'text-ink-600 dark:text-ink-300'
                                            : 'text-ink-400'
                                }`}
                            >
                                {step.label}
                            </span>
                        </button>

                        {i < steps.length - 1 && (
                            <div
                                className={`flex-1 h-px mx-3 ${
                                    isDone ? 'bg-brand-500' : 'bg-ink-200 dark:bg-ink-700'
                                }`}
                            />
                        )}
                    </div>
                );
            })}
        </div>
    );
}