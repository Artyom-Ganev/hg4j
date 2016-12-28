package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;

/**
 *
 */
public class ImplementsCancel extends BaseCancel implements CancelSupport {

    public ImplementsCancel(int limit) {
        super(limit);
    }

    public void checkCancelled() throws CancelledException {
        cancelImpl.checkCancelled();
    }
}