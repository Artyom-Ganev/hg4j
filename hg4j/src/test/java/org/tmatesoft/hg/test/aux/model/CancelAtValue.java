package org.tmatesoft.hg.test.aux.model;

/**
 *
 */
public class CancelAtValue {

    protected final CancelImpl cancelImpl = new CancelImpl();
    private final int stopValue;
    private int lastSeen;

    protected CancelAtValue(int value) {
        stopValue = value;
    }

    public int getStopValue() {
        return stopValue;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public CancelImpl getCancelImpl() {
        return cancelImpl;
    }

    void nextValue(int value) {
        lastSeen = value;
        if (value == stopValue) {
            cancelImpl.stop();
        }
    }
}
