package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;

/**
 *
 */
public class CancelImpl implements CancelSupport {

    private boolean shallStop = false;

    public void stop() {
        shallStop = true;
    }

    public void checkCancelled() throws CancelledException {
        if (shallStop) {
            throw new CancelledException();
        }
    }
}
