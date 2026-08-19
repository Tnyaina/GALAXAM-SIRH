/**
 * Skeleton — Composants d'attente de chargement (Skeletons).
 * Alignés sur les tokens dynamiques du Design System Galaxam (pas d'arrondi, surfaces sémantiques).
 */

export function Skeleton({ className = '', ...props }) {
  return (
    <div
      className={` bg-[var(--color-bg-subtle)] rounded-none ${className}`}
      {...props}
    />
  );
}

export function SkeletonAvatar({ size = 'md', className = '' }) {
  const sizes = {
    xs: 'w-6 h-6',
    sm: 'w-8 h-8',
    md: 'w-10 h-10',
    lg: 'w-12 h-12',
    xl: 'w-16 h-16',
  };

  return (
    <Skeleton className={`rounded-none shrink-0 ${sizes[size] ?? sizes.md} ${className}`} />
  );
}

export function SkeletonButton({ size = 'md', className = '' }) {
  const sizes = {
    xs: 'h-7 w-20',
    sm: 'h-8 w-24',
    md: 'h-10 w-28',
    lg: 'h-12 w-36',
  };

  return <Skeleton className={`${sizes[size] ?? sizes.md} ${className}`} />;
}

export function SkeletonInput({ hasLabel = true, className = '' }) {
  return (
    <div className={`space-y-1.5 ${className}`}>
      {hasLabel && <Skeleton className="h-3 w-20" />}
      <Skeleton className="h-10 w-full" />
    </div>
  );
}

export function SkeletonCard({ className = '' }) {
  return (
    <div className={`bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 space-y-4 rounded-none ${className}`}>
      <div className="flex items-center gap-3">
        <Skeleton className="w-10 h-10 shrink-0" />
        <div className="space-y-2 flex-1 min-w-0">
          <Skeleton className="h-4 w-1/3" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      </div>
      <Skeleton className="h-3 w-full" />
      <Skeleton className="h-3 w-4/5" />
      <Skeleton className="h-3 w-2/3" />
    </div>
  );
}

export function SkeletonStatCard({ className = '' }) {
  return (
    <div className={`bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 rounded-none space-y-3 ${className}`}>
      <div className="flex items-start justify-between">
        <div className="space-y-2 flex-1">
          <Skeleton className="h-3 w-24" />
          <Skeleton className="h-8 w-32" />
        </div>
        <Skeleton className="w-11 h-11 shrink-0" />
      </div>
      <Skeleton className="h-3 w-20" />
    </div>
  );
}

export function SkeletonTable({ rows = 5, columns = 4 }) {
  return (
    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none overflow-hidden">
      {/* Header fictif */}
      <div className="flex items-center gap-4 px-4 py-3 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)]">
        {Array.from({ length: columns }).map((_, i) => (
          <Skeleton key={i} className="h-3 flex-1" />
        ))}
      </div>

      {/* Lignes du tableau */}
      <div className="divide-y divide-[var(--color-border-subtle)]">
        {Array.from({ length: rows }).map((_, i) => (
          <div key={i} className="flex items-center gap-4 px-4 py-3.5">
            <SkeletonAvatar size="sm" />
            <div className="flex-1 space-y-1.5">
              <Skeleton className="h-3.5 w-2/5" />
              <Skeleton className="h-2.5 w-1/4" />
            </div>
            <Skeleton className="h-3 w-20" />
            <Skeleton className="h-5 w-16" />
          </div>
        ))}
      </div>
    </div>
  );
}

export function SkeletonForm({ fields = 4 }) {
  return (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 space-y-5 rounded-none">
      <Skeleton className="h-5 w-1/4 mb-2" />
      <div className="grid sm:grid-cols-2 gap-5">
        {Array.from({ length: fields }).map((_, i) => (
          <SkeletonInput key={i} />
        ))}
      </div>
      <div className="flex justify-end gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
        <SkeletonButton size="sm" />
        <SkeletonButton size="sm" />
      </div>
    </div>
  );
}