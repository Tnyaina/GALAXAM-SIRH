/** En-tête commun pour les listes de travail : rôle, volume et aide de lecture. */
export default function DataSectionHeader({ title, description, count, countLabel = 'éléments', tone = 'brand', children }) {
    return (
        <div className={`ui-data-section-header ui-data-section-header--${tone}`}>
            <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                    <h2 className="ui-section-title">{title}</h2>
                    {count !== undefined && count !== null && (
                        <span className="ui-data-section-count">{count} {countLabel}</span>
                    )}
                </div>
                {description && <p className="ui-supporting-text">{description}</p>}
            </div>
            {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
        </div>
    );
}
