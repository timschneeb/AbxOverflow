package android.os;

public final class ServiceManager {
    /**
     * Returns a reference to a service with the given name.
     *
     * @param name the name of the service to get
     * @return a reference to the service, or <code>null</code> if the service doesn't exist
     */
    public static IBinder getService(String name) { throw new UnsupportedOperationException("STUB"); }

    /**
     * Return a list of all currently running services.
     * @return an array of all currently running services, or <code>null</code> in
     * case of an exception
     */
    public static String[] listServices() { throw new UnsupportedOperationException("STUB"); }
}