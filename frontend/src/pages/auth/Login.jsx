import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Lock, Eye, EyeOff, User } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';

import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import Alert from '@/components/ui/Alert';
import { Card, CardBody } from '@/components/ui/Card';
import galaxamLogo from '@/assets/logo/galaxam-logo.png';

export default function Login() {
    const navigate = useNavigate();
    const { login, isAuthenticated } = useAuth();

    const [identifiant, setIdentifiant] = useState("");
    const [password, setPassword] = useState('');
    const [showPassword, setShowPassword] = useState(false);

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        if (isAuthenticated) {
            navigate('/dashboard', { replace: true });
        }
    }, [isAuthenticated, navigate]);

    // Effacer l'erreur dès que l'utilisateur modifie sa saisie
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

            if (result.success) {
                navigate('/dashboard', { replace: true });
            } else {
                setError(result.message || 'Identifiants incorrects.');
            }
        } catch {
            setError('Une erreur inattendue est survenue. Veuillez reessayer.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="min-h-screen flex flex-col items-center justify-center bg-[#f7f7f7] px-4 py-12">
            <div className="w-full max-w-md">
                <Card shadow="sm" bordered className="border-[#e5e5e5]">
                    <CardBody className="px-10 py-10">
                        {/* Logo */}
                        <div className="flex justify-center">
                            <img
                                src={galaxamLogo}
                                alt="Galaxam Logistics"
                                className="h-20 w-auto object-contain"
                                loading="eager"
                                fetchPriority="high"
                                decoding="async"
                            />
                        </div>

                        {/* Séparateur */}
                        <div className="border-t border-[#e5e5e5] my-6" />

                        <div className="mb-6 text-center">
                            <h1 className="text-lg font-semibold text-[#2d2d2d]">Connexion</h1>
                            <p className="text-sm text-[#888] mt-1">
                                Connectez-vous pour accéder à votre espace
                            </p>
                        </div>

                        {error && (
                            <div className="mb-5">
                                <Alert type="error" title="Erreur">{error}</Alert>
                            </div>
                        )}

                        <form onSubmit={handleSubmit} className="flex flex-col gap-5">
                            <Input
                                label="Identifiant"
                                type="text"
                                name="identifiant"
                                placeholder="Votre identifiant"
                                icon={<User size={17} />}
                                value={identifiant}
                                onChange={handleIdentifiantChange}
                                autoComplete="username"
                                error={!!error} // Signalement visuel d'erreur sur le champ
                                required
                            />

                            <Input
                                label="Mot de passe"
                                type={showPassword ? 'text' : 'password'}
                                name="password"
                                placeholder="••••••••"
                                icon={<Lock size={17} />}
                                iconRight={
                                    <button
                                        type="button"
                                        onClick={() => setShowPassword((v) => !v)}
                                        // tabIndex={-1} retiré pour permettre la navigation clavier (Accessibilité)
                                        className="pointer-events-auto text-[#888] hover:text-[#2d2d2d] focus:outline-none focus:text-[#2d2d2d] transition-colors"
                                        aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'}
                                    >
                                        {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                                    </button>
                                }
                                value={password}
                                onChange={handlePasswordChange}
                                autoComplete="current-password"
                                error={!!error} // Signalement visuel d'erreur sur le champ
                                required
                            />

                            <div className="flex items-center justify-end -mt-1">
                                <a
                                    href="/mot-de-passe-oublie"
                                    className="text-xs font-semibold text-[#1A82B1] hover:underline focus:outline-none focus:underline"
                                >
                                    Mot de passe oublié ?
                                </a>
                            </div>

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

                <p className="text-center text-xs text-[#aaa] mt-6">
                    © {new Date().getFullYear()} Galaxam Logistics. Tous droits réservés.
                </p>
            </div>
        </div>
    );
}