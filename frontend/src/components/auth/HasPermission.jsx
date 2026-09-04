import React from 'react';
import { useAuth } from '@/context/AuthContext';

/**
 * Composant déclaratif de contrôle d'accès basé sur les permissions et rôles.
 * 
 * Exemples d'utilisation :
 * 
 * 1. Permission unique :
 * <HasPermission authority="PAIE_READ">
 *    <SalaireWidget />
 * </HasPermission>
 * 
 * 2. Avec un fallback personnalisé :
 * <HasPermission authority="EMPLOYE_WRITE" fallback={<span>Lecture seule</span>}>
 *    <BoutonModifier />
 * </HasPermission>
 * 
 * 3. Au moins une permission requise parmi plusieurs :
 * <HasPermission any={['PAIE_READ', 'PAIE_WRITE']}>
 *    <SectionPaie />
 * </HasPermission>
 * 
 * 4. Toutes les permissions requises :
 * <HasPermission all={['EMPLOYE_WRITE', 'EMPLOYE_VALIDATE']}>
 *    <BoutonValidationDefinitive />
 * </HasPermission>
 * 
 * 5. Contrôle par rôle :
 * <HasPermission role="ROLE_ADMIN">
 *    <ParametresSysteme />
 * </HasPermission>
 */
export const HasPermission = ({
    authority,
    permission,
    any,
    all,
    role,
    fallback = null,
    children,
}) => {
    const { user, hasPermission, hasAnyPermission, hasAllPermissions, hasRole } = useAuth();

    if (!user) {
        return fallback;
    }

    // ROLE_ADMIN a tous les droits par défaut
    if (user.role === 'ROLE_ADMIN') {
        return <>{children}</>;
    }

    // Vérification de rôle spécifique
    if (role && !hasRole(role)) {
        return fallback;
    }

    // Vérification de permission unique (authority ou permission)
    const singlePerm = authority || permission;
    if (singlePerm && !hasPermission(singlePerm)) {
        return fallback;
    }

    // Vérification 'any' (au moins une)
    if (Array.isArray(any) && any.length > 0 && !hasAnyPermission(any)) {
        return fallback;
    }

    // Vérification 'all' (toutes requises)
    if (Array.isArray(all) && all.length > 0 && !hasAllPermissions(all)) {
        return fallback;
    }

    return <>{children}</>;
};

export default HasPermission;
