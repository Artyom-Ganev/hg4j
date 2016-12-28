package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.core.HgCallbackTargetException;
import org.tmatesoft.hg.core.HgChangeset;
import org.tmatesoft.hg.core.HgChangesetHandler;

/**
 *
 */
public class BaseCancel extends CancelAtValue implements HgChangesetHandler {

    public BaseCancel(int limit) {
        super(limit);
    }

    public void cset(HgChangeset changeset) throws HgCallbackTargetException {
        nextValue(changeset.getRevisionIndex());
    }
}
