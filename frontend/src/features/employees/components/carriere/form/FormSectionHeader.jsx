export default function FormSectionHeader({ step, icon: Icon, title }) {
    return (
        <div className="flex items-center gap-2">
            <span className="flex items-center justify-center w-5 h-5 rounded-none bg-brand-500/10 text-brand-500 text-[10px] font-bold shrink-0">
                {step}
            </span>
            <h3 className="flex items-center gap-1.5 text-xs font-bold text-ink-600 dark:text-ink-300 uppercase tracking-wider">
                {Icon && <Icon size={13} className="text-brand-500" />}
                {title}
            </h3>
        </div>
    );
}
