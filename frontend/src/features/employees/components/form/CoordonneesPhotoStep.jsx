import { Upload, Camera, Trash2, Phone } from 'lucide-react';
import Input, { Textarea, AutocompleteInput } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Loading from '@/components/ui/feedback/Loading';
import AuthenticatedImage from '@/components/ui/data-display/AuthenticatedImage';

export default function CoordonneesPhotoStep({
    formData,
    set,
    villesStandards,
    uploadingPhoto,
    handlePhotoSelect,
}) {
    return (
        <div className="space-y-4">
            {/* Zone Téléversement Photo */}
            <Card padding className="bg-[var(--color-bg-subtle)]/60">
                <div className="flex items-center justify-between pb-2 mb-3 border-b border-[var(--color-border-subtle)]">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Camera size={14} className="text-brand-500" />
                        Photo de profil
                    </label>
                    <Badge variant="default" size="xs">Optionnel</Badge>
                </div>
                <div className="flex items-center gap-6">
                    <div className="w-20 h-20 bg-[var(--color-surface)] border-2 border-dashed border-[var(--color-border)] flex flex-col items-center justify-center relative overflow-hidden flex-shrink-0">
                        <AuthenticatedImage
                            src={formData.photoUrl}
                            alt="Aperçu de la photo"
                            className="w-full h-full object-cover"
                            fallback={(
                                <div className="text-center text-[var(--color-fg-subtle)]">
                                    <Camera size={24} className="mx-auto mb-1" />
                                    <span className="text-[9px] uppercase font-semibold">Aucune</span>
                                </div>
                            )}
                        />
                        {uploadingPhoto && (
                            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                                <Loading type="spinner" color="brand" size="md" />
                            </div>
                        )}
                    </div>
                    <div className="flex-1 space-y-2">
                        <div className="flex items-center gap-3">
                            <label className="inline-flex items-center gap-2 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider bg-[var(--color-brand-500)] hover:bg-[var(--color-brand-600)] text-white rounded-none cursor-pointer transition-colors">
                                <Upload size={14} />
                                {uploadingPhoto ? 'Téléversement...' : 'Parcourir un fichier'}
                                <input 
                                    type="file" 
                                    accept="image/png, image/jpeg, image/webp" 
                                    onChange={handlePhotoSelect} 
                                    disabled={uploadingPhoto} 
                                    className="hidden" 
                                />
                            </label>
                            {formData.photoUrl && (
                                <Button 
                                    type="button" 
                                    variant="ghost" 
                                    size="xs"
                                    onClick={() => set('photoUrl', '')} 
                                    className="text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                    iconLeft={<Trash2 size={13} />}
                                >
                                    Supprimer
                                </Button>
                            )}
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)]">Formats acceptés : PNG, JPG, WEBP. Taille max : 5 Mo.</p>
                    </div>
                </div>
            </Card>

            <Card padding className="space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Phone size={14} className="text-brand-500" />
                        Coordonnées & Résidence
                    </h3>
                    <Badge variant="primary" size="xs">Obligatoire</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="md:col-span-2">
                        <Textarea label="Adresse Domicile" required rows={2} value={formData.adresse} onChange={e => set('adresse', e.target.value)} placeholder="Adresse complète" />
                    </div>
                    <AutocompleteInput 
                        label="Ville" 
                        value={formData.ville} 
                        onChange={e => set('ville', e.target.value)} 
                        placeholder="Ex: Antananarivo, Toamasina..."
                        options={villesStandards}
                        listId="coord-villes-list"
                    />
                    <Input label="Téléphone" required value={formData.telephone} onChange={e => set('telephone', e.target.value)} placeholder="Ex: 034 12 345 67" />
                    <Input label="Email Professionnel / Personnel" type="email" value={formData.email} onChange={e => set('email', e.target.value)} placeholder="email@domaine.mg" />
                </div>
            </Card>
        </div>
    );
}
