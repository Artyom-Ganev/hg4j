package org.tmatesoft.hg.test.aux.model;

import java.util.LinkedList;
import java.util.List;

import org.junit.Assert;
import org.tmatesoft.hg.core.HgCallbackTargetException;
import org.tmatesoft.hg.core.HgChangeset;
import org.tmatesoft.hg.core.HgChangesetHandler;
import org.tmatesoft.hg.core.HgFileRevision;
import org.tmatesoft.hg.core.HgLogCommand;

/**
 *
 */
public class CollectWithRenameHandler extends HgLogCommand.CollectHandler implements HgChangesetHandler.WithCopyHistory {

    public final RenameCollector rh = new RenameCollector();
    public List<HgChangeset> lastChangesetReportedAtRename = new LinkedList<>();

    public void copy(HgFileRevision from, HgFileRevision to) throws HgCallbackTargetException {
        Assert.assertTrue("Renames couldn't be reported prior to any change", getChanges().size() > 0);
        HgChangeset lastKnown = getChanges().get(getChanges().size() - 1);
        lastChangesetReportedAtRename.add(lastKnown);
        rh.copy(from, to);
    }
}