export default function Footer() {
    return (
        <footer className="h-7 shrink-0 border-t border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex items-center justify-center px-4">
            <p className="text-[11px] text-[var(--color-fg-subtle)]">
                © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
            </p>
        </footer>
    );
}