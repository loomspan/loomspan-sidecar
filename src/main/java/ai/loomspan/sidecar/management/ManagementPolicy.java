package ai.loomspan.sidecar.management;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ManagementPolicy {
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+");

    private ManagementPolicy() {}

    public static String email(String supplied) {
        if (supplied == null) throw new IllegalArgumentException("Invalid email address");
        String normalized = supplied.strip().toLowerCase(Locale.ROOT);
        String local = normalized.substring(0, Math.max(0, normalized.indexOf('@')));
        if (normalized.length() > 254 || !ADDRESS.matcher(normalized).matches()
                || local.startsWith(".") || local.endsWith(".") || local.contains(".."))
            throw new IllegalArgumentException("Invalid email address");
        return normalized;
    }

    public static void password(String value) {
        if (value == null) throw new IllegalArgumentException("Invalid password");
        int length = value.codePointCount(0, value.length());
        if (length < 15 || length > 128 || value.getBytes(StandardCharsets.UTF_8).length > 512)
            throw new IllegalArgumentException("Invalid password");
        boolean upper = false, lower = false, digit = false, special = false;
        for (int point : value.codePoints().toArray()) {
            upper |= Character.isUpperCase(point);
            lower |= Character.isLowerCase(point);
            digit |= Character.isDigit(point);
            int type = Character.getType(point);
            special |= !Character.isWhitespace(point) && !Character.isISOControl(point)
                    && (type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION || type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL);
        }
        if (!(upper && lower && digit && special)) throw new IllegalArgumentException("Invalid password");
    }
}
