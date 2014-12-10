package test;

public interface Stoppable {
    public default void stop() { Thread.currentThread().interrupt(); }
}
