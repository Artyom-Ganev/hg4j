package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.CancelSupport;

/**
 *
 */
public class InspectorImplementsAdaptable extends CancelAtValue implements HgChangelog.Inspector, Adaptable {

    public InspectorImplementsAdaptable(int limit) {
        super(limit);
    }

    public void next(int revisionNumber, Nodeid nodeid, HgChangelog.RawChangeset cset) {
        nextValue(revisionNumber);
    }

    public <T> T getAdapter(Class<T> adapterClass) {
        if (CancelSupport.class == adapterClass) {
            return adapterClass.cast(cancelImpl);
        }
        return null;
    }
}