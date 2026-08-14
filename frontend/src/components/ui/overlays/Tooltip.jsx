import { useState } from 'react';

export function Tooltip({ children, content, position = 'top' }) {
    const [visible, setVisible] = useState(false);

    const positionClasses = {
        top: 'bottom-full left-1/2 -translate-x-1/2 mb-2',
        bottom: 'top-full left-1/2 -translate-x-1/2 mt-2',
        left: 'right-full top-1/2 -translate-y-1/2 mr-2',
        right: 'left-full top-1/2 -translate-y-1/2 ml-2',
    };

    return (
        <div
            className="relative inline-flex"
            onMouseEnter={() => setVisible(true)}
            onMouseLeave={() => setVisible(false)}
            onFocus={() => setVisible(true)}
            onBlur={() => setVisible(false)}
        >
            {children}
            {visible && content && (
                <div
                    role="tooltip"
                    className={`
            absolute z-50 whitespace-nowrap px-2.5 py-1 text-[11px] font-medium
            bg-[var(--color-fg)] text-[var(--color-bg)] rounded-none shadow-md
            pointer-events-none transition-opacity duration-150
            ${positionClasses[position] || positionClasses.top}
          `}
                >
                    {content}
                </div>
            )}
        </div>
    );
}

export default Tooltip;
