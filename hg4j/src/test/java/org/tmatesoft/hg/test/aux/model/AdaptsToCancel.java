package org.tmatesoft.hg.test.aux.model;

import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.CancelSupport;

/**
 *
 */
public class AdaptsToCancel extends BaseCancel implements Adaptable {

    public AdaptsToCancel(int limit) {
        super(limit);
    }

    public <T> T getAdapter(Class<T> adapterClass) {
        if (adapterClass == CancelSupport.class) {
            return adapterClass.cast(cancelImpl);
        }
        return null;
    }
}