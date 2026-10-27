import { useContext } from 'react';
import { MenuContext } from '@/context/MenuContext';

export {
    MENU_CHANGED_EVENT,
    notifyMenuChanged,
    clearMenuCache,
    readMenuCache,
    writeMenuCache
} from '@/utils/menuEvents';

export function useMenu() {
    const context = useContext(MenuContext);
    if (!context) {
        throw new Error('useMenu doit être utilisé à l\'intérieur d\'un MenuProvider');
    }
    return context;
}

export default useMenu;
