package mg.galaxam.sirh.api.dto.common;

import java.util.List;

public record ActionCapability(boolean allowed, List<String> reasonCodes) {
    public ActionCapability {
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
    }

    public static ActionCapability permit() {
        return new ActionCapability(true, List.of());
    }

    public static ActionCapability denied(String reasonCode) {
        return new ActionCapability(false, List.of(reasonCode));
    }
}
