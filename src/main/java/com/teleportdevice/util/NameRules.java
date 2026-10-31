package com.teleportdevice.util;

import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Reglas de nombre de ubicación. Sin dependencias de Minecraft, para poder probarlas de forma aislada. */
public final class NameRules {
    public static final int MAX_LENGTH = 32;

    /** \p{C}: control, formato (p. ej. U+202E), sustitutos y no asignados. \u00A7 = '§' (códigos de formato de Minecraft). */
    private static final Pattern FORBIDDEN = Pattern.compile("[\\p{C}\\u00A7]");
    private static final Pattern SPACES = Pattern.compile("[\\s\\p{Z}]+");

    private NameRules() {}

    /** @return el nombre saneado, o null si queda vacío o supera {@link #MAX_LENGTH} caracteres. */
    @Nullable
    public static String sanitize(@Nullable String raw) {
        if (raw == null) return null;
        String s = FORBIDDEN.matcher(raw).replaceAll("");
        s = SPACES.matcher(s).replaceAll(" ").trim();
        return (s.isEmpty() || s.length() > MAX_LENGTH) ? null : s;
    }
}
