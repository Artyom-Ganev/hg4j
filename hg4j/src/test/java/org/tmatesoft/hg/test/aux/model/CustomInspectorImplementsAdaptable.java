package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgManifest;
import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.Path;

/**
 *
 */
public class CustomInspectorImplementsAdaptable extends CancelAtValue implements HgManifest.Inspector, Adaptable {

    public CustomInspectorImplementsAdaptable(int limit) {
        super(limit);
    }

    public boolean begin(int mainfestRevision, Nodeid nid, int changelogRevision) {
        nextValue(getLastSeen() + 1);
        return true;
    }

    public boolean end(int manifestRevision) {
        return true;
    }

    public <T> T getAdapter(Class<T> adapterClass) {
        if (CancelSupport.class == adapterClass) {
            return adapterClass.cast(cancelImpl);
        }
        return null;
    }

    public boolean next(Nodeid nid, Path fname, HgManifest.Flags flags) {
        return true;
    }
}
