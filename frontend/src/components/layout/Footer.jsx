export default function Footer() {

    return (
        <footer className="h-10 shrink-0 border-t border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 flex items-center justify-center px-6">
            <p className="text-xs text-ink-400 dark:text-ink-500">
                © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
            </p>
        </footer>
    );
}
