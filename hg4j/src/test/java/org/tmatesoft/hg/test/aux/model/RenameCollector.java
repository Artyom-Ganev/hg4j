package org.tmatesoft.hg.test.aux.model;

import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.hg.core.HgFileRenameHandlerMixin;
import org.tmatesoft.hg.core.HgFileRevision;
import org.tmatesoft.hg.internal.AdapterPlug;
import org.tmatesoft.hg.util.Pair;

/**
 *
 */
public class RenameCollector implements HgFileRenameHandlerMixin {

    public boolean copyReported = false;
    public List<Pair<HgFileRevision, HgFileRevision>> renames = new LinkedList<>();

    public RenameCollector() {
    }

    public RenameCollector(AdapterPlug ap) {
        ap.attachAdapter(HgFileRenameHandlerMixin.class, this);
    }

    public void copy(HgFileRevision from, HgFileRevision to) {
        copyReported = true;
        renames.add(new Pair<>(from, to));
    }
}