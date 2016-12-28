package org.tmatesoft.hg.test.aux.model;

import org.junit.Assert;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgDataFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class ParentInspectorCheck implements HgDataFile.ParentInspector {
    private int i, c;
    private Nodeid[] all;
    private final int start;

    public ParentInspectorCheck(int start, int total) {
        this.start = start;
        i = start; // revision index being iterated
        c = 0; // index/counter of visited revisions
        all = new Nodeid[total];
    }

    public void next(int localRevision, Nodeid revision, int parent1, int parent2, Nodeid nidParent1, Nodeid nidParent2) {
        assertEquals(i++, localRevision);
        all[c++] = revision;
        assertNotNull(revision);
        assertFalse(localRevision == 0 && (parent1 != -1 || parent2 != -1));
        assertFalse(localRevision > 0 && parent1 == -1 && parent2 == -1);
        if (parent1 != -1) {
            assertNotNull(nidParent1);
            if (parent1 >= start) {
                // deliberately ==, not asserEquals to ensure same instance
                Assert.assertTrue(nidParent1 == all[parent1 - start]);
            }
        }
        if (parent2 != -1) {
            assertNotNull(nidParent2);
            if (parent2 >= start) {
                Assert.assertTrue(nidParent2 == all[parent2 - start]);
            }
        }
    }
}
