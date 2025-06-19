package measurements.support;

/**
 * Global padding constant used across the project.
 * Each long occupies 8 bytes, so {@code PADDING} longs reserve
 * {@code 8 * PADDING} bytes to avoid false sharing.
 */
public final class Padding {
    public static final int PADDING = 8;
}
