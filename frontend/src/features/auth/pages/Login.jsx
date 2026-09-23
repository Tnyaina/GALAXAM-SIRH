import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Eye, EyeOff, User } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import galaxamLogo from '@/assets/logo/galaxam-logo.png';
import SEO from '@/components/system/SEO';

export default function Login() {
    // Contexte et navigation
    const navigate = useNavigate();
    const { login, isAuthenticated, user, hasPermission } = useAuth();

    // État local
    const [identifiant, setIdentifiant] = useState('');
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    // Effets
    useEffect(() => {
        if (isAuthenticated && user) {
            const redirectPath = hasPermission('EMPLOYE_READ')
                ? '/dashboard'
                : (hasPermission('POINTAGE_READ')
                    ? '/pointages/saisie'
                    : (hasPermission('CONGE_READ') ? '/conges' : '/dashboard'));
            navigate(redirectPath, { replace: true });
        }
    }, [isAuthenticated, user, hasPermission, navigate]);

    // Actions
    const handleIdentifiantChange = (e) => {
        if (error) setError('');
        setIdentifiant(e.target.value);
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
            const result = await login(identifiant, password);

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
                    <CardBody className="px-8 py-10 sm:px-10">
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
                                label="Identifiant"
                                type="text"
                                name="identifiant"
                                placeholder="Votre identifiant (ex: admin)"
                                icon={<User size={16} />}
                                value={identifiant}
                                onChange={handleIdentifiantChange}
                                autoComplete="username"
                                required
                            />

                            <Input
                                label="Mot de passe"
                                type={showPassword ? 'text' : 'password'}
                                name="password"
                                placeholder="••••••••"
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
                                className="w-full mt-2"
                            >
                                Se connecter
                            </Button>
                        </form>
                    </CardBody>
                </Card>

                {/* Footer avec contraste accessible */}
                <p className="text-center text-xs text-[var(--color-fg-muted)] mt-6">
                    © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
                </p>
            </div>
        </main>
    );
}
