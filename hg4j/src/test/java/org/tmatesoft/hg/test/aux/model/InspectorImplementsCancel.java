package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;

/**
 *
 */
public class InspectorImplementsCancel extends CancelAtValue implements HgChangelog.Inspector, CancelSupport {

    public InspectorImplementsCancel(int limit) {
        super(limit);
    }

    public void next(int revisionNumber, Nodeid nodeid, HgChangelog.RawChangeset cset) {
        nextValue(revisionNumber);
    }

    public void checkCancelled() throws CancelledException {
        cancelImpl.checkCancelled();
    }
}
