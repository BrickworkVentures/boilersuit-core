package ch.brickwork.bsuit.globals;

/**
 * Holds the objects that are identical during the runtime of an instance of the application
 */
public final class BoilerSuitGlobals {

    private static IBoilersuitApplicationContext applicationContext;

    static {
        bootstrap();
    }

    private static void bootstrap() {
        applicationContext = new DefaultBoilersuitApplicationContext();
    }

    public static IBoilersuitApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
