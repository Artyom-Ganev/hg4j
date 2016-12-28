package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.util.ProgressSupport;

/**
 *
 */
public class PS implements ProgressSupport {

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private int units;
    private int worked;
    private boolean done = false;

    public void start(int totalUnits) {
        units = totalUnits;
    }

    public int getWorked() {
        return worked;
    }

    public void worked(int wu) {
        worked += wu;
    }

    public void done() {
        done = true;
    }

    public boolean isDone() {
        return done;
    }
}
