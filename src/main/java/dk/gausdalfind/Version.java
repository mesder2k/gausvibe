package dk.gausdalfind;

/**
 * Build version of the running artifact.
 *
 * The value comes from the jar manifest (Implementation-Version), which
 * scripts/build-artifact.sh sets to the git commit count. Falls back to
 * "dev" when running from target/classes without a packaged jar.
 */
public final class Version {

    private static final String FALLBACK = "dev";

    private Version() {
    }

    public static String get() {
        String v = Version.class.getPackage().getImplementationVersion();
        return v != null ? v : FALLBACK;
    }
}
