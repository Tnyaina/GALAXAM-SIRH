import { Upload, Camera } from 'lucide-react';
import Input, { Textarea, Select } from '@/components/ui/Input';
import { getFullPhotoUrl } from '@/utils/fileUtils';

export default function CoordonneesPhotoStep({
    formData,
    set,
    villesStandards,
    uploadingPhoto,
    handlePhotoSelect,
}) {
    return (
        <div className="space-y-6">
            {/* Zone Téléversement Photo */}
            <div className="bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 p-4">
                <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50 block mb-2">Photo de l'employé</label>
                <div className="flex items-center gap-6">
                    <div className="w-20 h-20 bg-white dark:bg-ink-900 border-2 border-dashed border-ink-200 dark:border-ink-700 flex flex-col items-center justify-center relative overflow-hidden flex-shrink-0">
                        {formData.photoUrl ? (
                            <img src={getFullPhotoUrl(formData.photoUrl)} alt="Aperçu" className="w-full h-full object-cover" />
                        ) : (
                            <div className="text-center text-ink-300">
                                <Camera size={24} className="mx-auto mb-1" />
                                <span className="text-[9px] uppercase font-semibold">Aucune</span>
                            </div>
                        )}
                        {uploadingPhoto && (
                            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                            </div>
                        )}
                    </div>
                    <div className="flex-1 space-y-2">
                        <div className="flex items-center gap-3">
                            <label className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold uppercase tracking-wider bg-brand-500 hover:bg-brand-600 text-white rounded-sm cursor-pointer transition-colors">
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
                                <button 
                                    type="button" 
                                    onClick={() => set('photoUrl', '')} 
                                    className="text-xs font-semibold text-danger-500 hover:underline cursor-pointer"
                                >
                                    Supprimer
                                </button>
                            )}
                        </div>
                        <p className="text-[11px] text-ink-400">Formats acceptés : PNG, JPG, WEBP. Taille max : 5 Mo.</p>
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="md:col-span-2">
                    <Textarea label="Adresse Domicile" required rows={2} value={formData.adresse} onChange={e => set('adresse', e.target.value)} placeholder="Adresse complète" />
                </div>
                <Select label="Ville" value={formData.ville} onChange={e => set('ville', e.target.value)}>
                    {villesStandards.map(v => <option key={v} value={v}>{v}</option>)}
                </Select>
                <Input label="Téléphone" required value={formData.telephone} onChange={e => set('telephone', e.target.value)} placeholder="Ex: 034 12 345 67" />
                <Input label="Email" type="email" value={formData.email} onChange={e => set('email', e.target.value)} placeholder="email@domaine.mg" />
            </div>
        </div>
    );
}
