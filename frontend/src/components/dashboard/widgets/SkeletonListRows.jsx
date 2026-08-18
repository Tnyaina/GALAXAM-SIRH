import { Skeleton } from '@/components/ui/feedback/Skeleton';

export default function SkeletonListRows({ count = 3 }) {
    return (
        <div className="divide-y divide-[var(--color-border-subtle)] py-1">
            {Array.from({ length: count }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 py-3 px-1">
                    <Skeleton className="w-2 h-2 rounded-none shrink-0" />
                    <div className="flex-1 space-y-1 min-w-0">
                        <Skeleton className="h-3.5 w-1/3" />
                        <Skeleton className="h-2.5 w-1/2" />
                    </div>
                    <Skeleton className="h-5 w-14 shrink-0" />
                </div>
            ))}
        </div>
    );
}
