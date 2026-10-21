import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Eye, EyeOff, Mail } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import galaxamLogo from '@/assets/logo/galaxam-logo.png';
import SEO from '@/components/system/SEO';
import DesktopConnectionChooser from '@/features/desktop/DesktopConnectionChooser';

export default function Login() {
    // Contexte et navigation
    const navigate = useNavigate();
    const { login, isAuthenticated, user, getDefaultHomeRoute } = useAuth();

    // État local
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);
    const [showConnectionChooser, setShowConnectionChooser] = useState(false);
    const isDesktop = typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Effets
    useEffect(() => {
        if (isAuthenticated && user) {
            navigate(getDefaultHomeRoute(), { replace: true });
        }
    }, [isAuthenticated, user, getDefaultHomeRoute, navigate]);

    // Actions
    const handleEmailChange = (e) => {
        if (error) setError('');
        setEmail(e.target.value);
    };

    const handlePasswordChange = (e) => {
        if (error) setError('');
        setPassword(e.target.value);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            const result = await login(email, password);

            if (!result.success) {
                setError(result.message || 'Identifiants incorrects.');
            }
        } catch {
            setError('Une erreur inattendue est survenue. Veuillez réessayer.');
        } finally {
            setLoading(false);
        }
    };

    // Rendu
    return (
        <main id="main-content" tabIndex={-1} className="min-h-screen flex flex-col items-center justify-center bg-[var(--color-bg)] px-4 py-12 focus:outline-none">
            <SEO title="Connexion" description="Connectez-vous à l'intranet RH de Galaxam." />
            <div className="w-full max-w-md">
                <Card shadow="sm" bordered className="border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <CardBody className="px-8 py-10 sm:px-10 sm:pb-12">
                        {/* Logo et Nom de l'application */}
                        <div className="flex flex-col items-center gap-4">
                            <img
                                src={galaxamLogo}
                                alt="Galaxam Logistics"
                                className="h-20 w-auto object-contain"
                                loading="eager"
                                fetchPriority="high"
                                decoding="async"
                            />
                            <div className="text-center flex flex-col items-center justify-center -mt-2">
                                <span className="text-[26px] font-black tracking-tighter text-[var(--color-fg)] uppercase leading-none block">
                                    Galaxam
                                </span>
                                <span className="text-sm font-light tracking-[0.25em] text-[var(--color-fg-muted)] uppercase mt-0.5 ml-1">
                                    Logistics
                                </span>
                            </div>
                        </div>

                        {/* Séparateur */}
                        <div className="border-t border-[var(--color-border-subtle)] my-6" />

                        <div className="mb-6 text-center space-y-1">
                            <h1 className="text-xl font-bold text-[var(--color-fg)]">Connexion</h1>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Connectez-vous pour accéder à votre espace
                            </p>
                        </div>

                        {error && (
                            <div className="mb-5 animate-in fade-in duration-200">
                                <Alert 
                                    type="error" 
                                    title="Échec de la connexion"
                                    onClose={() => setError('')}
                                >
                                    {error}
                                </Alert>
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
                            <Input
                                label="Adresse e-mail"
                                type="email"
                                name="email"
                                placeholder="vous@entreprise.com"
                                icon={<Mail size={16} />}
                                value={email}
                                onChange={handleEmailChange}
                                autoComplete="email"
                                required
                            />

                            <Input
                                label="Mot de passe"
                                type={showPassword ? 'text' : 'password'}
                                name="password"
                                placeholder="Saisissez votre mot de passe"
                                icon={<Lock size={16} />}
                                iconRight={
                                    <button
                                        type="button"
                                        onClick={() => setShowPassword((v) => !v)}
                                        className="min-w-[44px] min-h-[44px] -mr-3 flex items-center justify-center text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] focus:outline-none cursor-pointer"
                                        aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                                    >
                                        {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                                    </button>
                                }
                                value={password}
                                onChange={handlePasswordChange}
                                autoComplete="current-password"
                                required
                            />


                            <Button
                                type="submit"
                                variant="primary"
                                size="lg"
                                loading={loading}
                                disabled={loading}
                                className="w-full mt-3"
                            >
                                Se connecter
                            </Button>
                        </form>
                        {isDesktop && (
                            <div className="mt-5 border-t border-[var(--color-border-subtle)] pt-4 text-center">
                                <button
                                    type="button"
                                    onClick={() => setShowConnectionChooser(true)}
                                    className="min-h-10 rounded px-3 text-sm font-medium text-[var(--color-brand-600)] underline-offset-4 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]"
                                >
                                    Choisir une base ou un serveur
                                </button>
                            </div>
                        )}
                    </CardBody>
                </Card>

                {/* Footer avec contraste accessible */}
                <p className="text-center text-xs text-[var(--color-fg-muted)] mt-6">
                    © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
                </p>
            </div>
            {showConnectionChooser && <DesktopConnectionChooser onClose={() => setShowConnectionChooser(false)} />}
        </main>
    );
}
