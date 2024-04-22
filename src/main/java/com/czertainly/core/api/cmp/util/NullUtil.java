package com.czertainly.core.api.cmp.util;

import java.util.function.Supplier;

public class NullUtil {
    /**
     * compute a default value if value is <code>null</code>
     * @param <T> value type
     * @param value value to check for <code>null</code>
     * @param defaultSupplier funtion to call if value is <code>null</code>
     * @return value or result of defaultSupplier
     */
    public static <T> T computeDefaultIfNull(final T value, final Supplier<T> defaultSupplier) {
        return value == null ? defaultSupplier.get() : value;
    }

    /**
     * provide a default value if if value is <code>null</code>
     * @param <T> value type
     * @param value value to check for <code>null</code>
     * @param defaultValue value to use if provided value is <code>null</code>
     * @return value or defaultValue
     */
    public static <T> T defaultIfNull(final T value, final T defaultValue) {
        return value != null ? value : defaultValue;
    }

    public static <T> T computeValueIfNotNull(final T value, final Supplier<T> defaultSupplier) {
        return value == null ? null : defaultSupplier.get();
    }
}
