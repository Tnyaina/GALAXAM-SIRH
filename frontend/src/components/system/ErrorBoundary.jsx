import React from 'react';
import { AlertOctagon, RotateCcw, Home } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/**
 * ErrorBoundary — Composant global et local de capture des exceptions de rendu.
 * Empêche les écrans blancs non gérés et propose des actions de récupération immédiates.
 */
class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            hasError: false,
            error: null,
            errorInfo: null,
            showDetails: false
        };
    }

    static getDerivedStateFromError(error) {
        return { hasError: true, error };
    }

    componentDidCatch(error, errorInfo) {
        this.setState({ errorInfo });
        if (this.props.onError) {
            this.props.onError(error, errorInfo);
        }
        console.error('ErrorBoundary caught an unhandled error:', error, errorInfo);
    }

    handleReset = () => {
        this.setState({
            hasError: false,
            error: null,
            errorInfo: null,
            showDetails: false
        });
        if (this.props.onReset) {
            this.props.onReset();
        }
    };

    handleReload = () => {
        window.location.reload();
    };

    handleGoHome = () => {
        window.location.href = '/';
    };

    render() {
        if (this.state.hasError) {
            if (this.props.fallback) {
                if (typeof this.props.fallback === 'function') {
                    return this.props.fallback({
                        error: this.state.error,
                        resetError: this.handleReset
                    });
                }
                return this.props.fallback;
            }

            return (
                <div
                    role="alert"
                    aria-live="assertive"
                    className="min-h-screen w-full flex items-center justify-center bg-[var(--color-bg)] p-4 md:p-8"
                >
                    <div className="max-w-lg w-full bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xl p-8 rounded-none text-center">
                        <div className="w-16 h-16 border-2 border-[var(--color-danger-500)] bg-[var(--color-danger-50)] text-[var(--color-danger-600)] flex items-center justify-center mx-auto mb-6">
                            <AlertOctagon size={32} strokeWidth={1.75} />
                        </div>

                        <p className="text-xs font-bold uppercase tracking-widest text-[var(--color-danger-500)] mb-2 font-mono">
                            Erreur d'exécution
                        </p>

                        <h1 className="text-xl font-bold text-[var(--color-fg)] mb-3">
                            Une erreur inattendue est survenue
                        </h1>

                        <p className="text-sm text-[var(--color-fg-muted)] mb-6 leading-relaxed">
                            L'application a rencontré un dysfonctionnement imprévu dans cette vue.
                            Vos données enregistrées sur le serveur restent intactes.
                        </p>

                        <div className="flex flex-wrap items-center justify-center gap-3 mb-6">
                            <Button
                                variant="primary"
                                size="sm"
                                iconLeft={<RotateCcw size={15} />}
                                onClick={this.handleReload}
                            >
                                Recharger l'application
                            </Button>
                            <Button
                                variant="outline"
                                size="sm"
                                iconLeft={<Home size={15} />}
                                onClick={this.handleGoHome}
                            >
                                Retour à l'accueil
                            </Button>
                        </div>

                        {import.meta.env?.DEV && this.state.error && (
                            <div className="text-left mt-6 border-t border-[var(--color-border-subtle)] pt-4">
                                <button
                                    type="button"
                                    onClick={() => this.setState((prev) => ({ showDetails: !prev.showDetails }))}
                                    className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] underline font-medium focus:outline-none"
                                >
                                    {this.state.showDetails ? 'Masquer les détails techniques' : 'Afficher les détails techniques (Dev)'}
                                </button>
                                {this.state.showDetails && (
                                    <div className="mt-3 p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-left overflow-auto max-h-48 text-xs font-mono text-[var(--color-danger-600)]">
                                        <div className="font-bold mb-1">{this.state.error.toString()}</div>
                                        <pre className="text-[11px] text-[var(--color-fg-muted)] whitespace-pre-wrap">
                                            {this.state.errorInfo?.componentStack || this.state.error.stack}
                                        </pre>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
