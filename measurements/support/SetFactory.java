package measurements.support;

public abstract class SetFactory<K> {
    public abstract SetInterface<K> newSet(final Integer param1,final Integer param2);
    public abstract String getName();
}