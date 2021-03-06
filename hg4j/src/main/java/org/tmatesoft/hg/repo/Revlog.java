/*
 * Copyright (c) 2010-2013 TMate Software Ltd
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For information on how to redistribute this software under
 * the terms of a license other than GNU General Public License
 * contact TMate Software at support@hg4j.com
 */
package org.tmatesoft.hg.repo;

import static org.tmatesoft.hg.repo.HgRepository.*;
import static org.tmatesoft.hg.util.LogFacility.Severity.Warn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.internal.DataAccess;
import org.tmatesoft.hg.internal.Experimental;
import org.tmatesoft.hg.internal.IntMap;
import org.tmatesoft.hg.internal.Preview;
import org.tmatesoft.hg.internal.RevisionLookup;
import org.tmatesoft.hg.internal.RevlogDelegate;
import org.tmatesoft.hg.internal.RevlogStream;
import org.tmatesoft.hg.util.Adaptable;
import org.tmatesoft.hg.util.ByteChannel;
import org.tmatesoft.hg.util.CancelSupport;
import org.tmatesoft.hg.util.CancelledException;
import org.tmatesoft.hg.util.LogFacility;
import org.tmatesoft.hg.util.ProgressSupport;


/**
 * Base class for all Mercurial entities that are serialized in a so called revlog format (changelog, manifest, data files).
 * 
 * Implementation note:
 * Hides actual actual revlog stream implementation and its access methods (i.e. RevlogStream.Inspector), iow shall not expose anything internal
 * in public methods.
 *   
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
abstract class Revlog {

	private final HgRepository repo;
	protected final RevlogStream content;
	protected final boolean useRevisionLookup;
	protected RevisionLookup revisionLookup;
	private final RevlogStream.Observer revisionLookupCleaner;

	protected Revlog(HgRepository hgRepo, RevlogStream contentStream, boolean needRevisionLookup) {
		if (hgRepo == null) {
			throw new IllegalArgumentException();
		}
		if (contentStream == null) {
			throw new IllegalArgumentException();
		}
		repo = hgRepo;
		content = contentStream;
		useRevisionLookup = needRevisionLookup;
		if (needRevisionLookup) {
			revisionLookupCleaner = new RevlogStream.Observer() {
				
				public void reloaded(RevlogStream src) {
					revisionLookup = null;
				}
			};
		} else {
			revisionLookupCleaner = null;
		}
	}
	
	// invalid Revlog
	protected Revlog(HgRepository hgRepo) {
		repo = hgRepo;
		content = null;
		useRevisionLookup = false;
		revisionLookupCleaner = null;
	}

	public final HgRepository getRepo() {
		return repo;
	}

	/**
	 * @return total number of revisions kept in this revlog
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final int getRevisionCount() throws HgRuntimeException {
		return content.revisionCount();
	}
	
	/**
	 * @return index of last known revision, a.k.a. {@link HgRepository#TIP}, or {@link HgRepository#NO_REVISION} if revlog is empty
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final int getLastRevision() throws HgRuntimeException {
		// although old code gives correct result when revlog is empty (NO_REVISION deliberately == -1), 
		// it's still better to be explicit
		int revCount = content.revisionCount();
		return revCount == 0 ? NO_REVISION : revCount - 1;
	}

	/**
	 * Map revision index to unique revision identifier (nodeid).
	 *  
	 * @param revisionIndex index of the entry in this revlog, may be {@link HgRepository#TIP}
	 * @return revision nodeid of the entry
	 * 
	 * @throws HgInvalidRevisionException if any supplied revision doesn't identify revision from this revlog. <em>Runtime exception</em>
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final Nodeid getRevision(int revisionIndex) throws HgRuntimeException {
		// XXX cache nodeids? Rather, if context.getCache(this).getRevisionMap(create == false) != null, use it
		return Nodeid.fromBinary(content.nodeid(revisionIndex), 0);
	}
	
	/**
	 * Effective alternative to map few revision indexes to corresponding nodeids at once.
	 * <p>Note, there are few aspects to be careful about when using this method<ul>
	 * <li>ordering of the revisions in the return list is unspecified, it's likely won't match that of the method argument
	 * <li>supplied array get modified (sorted)</ul>
	 * @return list of mapped revisions in no particular order
	 * @throws HgInvalidRevisionException if any supplied revision doesn't identify revision from this revlog. <em>Runtime exception</em>
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final List<Nodeid> getRevisions(int... revisions) throws HgRuntimeException {
		ArrayList<Nodeid> rv = new ArrayList<Nodeid>(revisions.length);
		Arrays.sort(revisions);
		getRevisionsInternal(rv, revisions);
		return rv;
	}
	
	/*package-local*/ void getRevisionsInternal(final List<Nodeid> retVal, int[] sortedRevs) throws HgRuntimeException {
		// once I have getRevisionMap and may find out whether it is avalable from cache,
		// may use it, perhaps only for small number of revisions
		content.iterate(sortedRevs, false, new RevlogStream.Inspector() {
			
			public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) {
				retVal.add(Nodeid.fromBinary(nodeid, 0));
			}
		});
	}

	/**
	 * Get local index of the specified revision.
	 * If unsure, use {@link #isKnown(Nodeid)} to find out whether nodeid belongs to this revlog.
	 * 
	 * For occasional queries, this method works with decent performance, despite its O(n/2) approach.
	 * Alternatively, if you need to perform multiple queries (e.g. at least 15-20), {@link HgRevisionMap} may come handy.
	 * 
	 * @param nid revision to look up 
	 * @return revision local index in this revlog
	 * @throws HgInvalidRevisionException if revision was not found in this revlog. <em>Runtime exception</em>
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final int getRevisionIndex(Nodeid nid) throws HgRuntimeException {
		final int revision = doFindWithCache(nid);
		if (revision == BAD_REVISION) {
			// using toString() to identify revlog. HgDataFile.toString includes path, HgManifest and HgChangelog instances 
			// are fine with default (class name)
			// Perhaps, more tailored description method would be suitable here
			throw new HgInvalidRevisionException(String.format("Can't find revision %s in %s", nid.shortNotation(), this), nid, null);
		}
		return revision;
	}
	
	private int doFindWithCache(Nodeid nid) throws HgRuntimeException {
		if (useRevisionLookup) {
			if (revisionLookup == null || content.shallDropDerivedCaches()) {
				content.detach(revisionLookupCleaner);
				setRevisionLookup(RevisionLookup.createFor(content));
			}
			return revisionLookup.findIndex(nid);
		} else {
			return content.findRevisionIndex(nid);
		}
	}
	
	/**
	 * use selected helper for revision lookup, register appropriate listeners to clear cache on revlog changes
	 * @param rl not <code>null</code>
	 */
	protected void setRevisionLookup(RevisionLookup rl) {
		assert rl != null;
		revisionLookup = rl;
		content.attach(revisionLookupCleaner);
	}
	
	/**
	 * Note, {@link Nodeid#NULL} nodeid is not reported as known in any revlog.
	 * 
	 * @param nodeid
	 * @return <code>true</code> if revision is part of this revlog
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	public final boolean isKnown(Nodeid nodeid) throws HgRuntimeException {
		final int rn = doFindWithCache(nodeid);
		if (BAD_REVISION == rn) {
			return false;
		}
		if (rn < 0 || rn >= content.revisionCount()) {
			// Sanity check
			throw new HgInvalidStateException(String.format("Revision index %d found for nodeid %s is not from the range [0..%d]", rn, nodeid.shortNotation(), content.revisionCount()-1));
		}
		return true;
	}

	/**
	 * Access to revision data as is, equivalent to <code>rawContent(getRevisionIndex(nodeid), sink)</code>
	 * 
	 * @param nodeid revision to retrieve
	 * @param sink data destination
	 * 
	 * @see #rawContent(int, ByteChannel)
	 * 
	 * @throws CancelledException if content retrieval operation was cancelled
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog. <em>Runtime exception</em>
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	protected void rawContent(Nodeid nodeid, ByteChannel sink) throws CancelledException, HgRuntimeException {
		rawContent(getRevisionIndex(nodeid), sink);
	}
	
	/**
	 * Access to revision data as is (decompressed, but otherwise unprocessed, i.e. not parsed for e.g. changeset or manifest entries).
	 *  
	 * @param revisionIndex index of this revlog change (not a changelog revision index), non-negative. From predefined constants, only {@link HgRepository#TIP} makes sense.
	 * @param sink data destination
	 * 
	 * @throws CancelledException if content retrieval operation was cancelled
	 * @throws HgInvalidRevisionException if supplied argument doesn't represent revision index in this revlog. <em>Runtime exception</em>
	 * @throws HgInvalidControlFileException if failed to access revlog index/data entry. <em>Runtime exception</em>
	 * @throws HgRuntimeException subclass thereof to indicate other issues with the library. <em>Runtime exception</em>
	 */
	protected void rawContent(int revisionIndex, ByteChannel sink) throws CancelledException, HgRuntimeException {
		if (sink == null) {
			throw new IllegalArgumentException();
		}
		try {
			ContentPipe insp = new ContentPipe(sink, 0, repo.getSessionContext().getLog());
			insp.checkCancelled();
			content.iterate(revisionIndex, revisionIndex, true, insp);
			insp.checkFailed();
		} catch (IOException ex) {
			HgInvalidControlFileException e = new HgInvalidControlFileException(String.format("Access to revision %d content failed", revisionIndex), ex, null);
			e.setRevisionIndex(revisionIndex);
			// TODO [post 1.1] e.setFileName(content.getIndexFile() or this.getHumanFriendlyPath()) - shall decide whether 
			// protected abstract getHFPath() with impl in HgDataFile, HgManifest and HgChangelog or path is data of either Revlog or RevlogStream
			// Do the same (add file name) below
			throw e;
		} catch (HgInvalidControlFileException ex) {
			throw ex.isRevisionIndexSet() ? ex : ex.setRevisionIndex(revisionIndex);
		}
	}

	/**
	 * Fills supplied arguments with information about revision parents.
	 * 
	 * @param revision - revision to query parents, or {@link HgRepository#TIP}
	 * @param parentRevisions - int[2] to get local revision numbers of parents (e.g. {6, -1}), {@link HgRepository#NO_REVISION} indicates parent not set
	 * @param parent1 - byte[20] or null, if parent's nodeid is not needed
	 * @param parent2 - byte[20] or null, if second parent's nodeid is not needed
	 * @throws IllegalArgumentException if passed arrays can't fit requested data
	 * @throws HgRuntimeException subclass thereof to indicate issues with the library. <em>Runtime exception</em>
	 */
	public void parents(int revision, int[] parentRevisions, byte[] parent1, byte[] parent2) throws HgRuntimeException, IllegalArgumentException {
		if (revision != TIP && !(revision >= 0 && revision < content.revisionCount())) {
			throw new HgInvalidRevisionException(revision);
		}
		if (parentRevisions == null || parentRevisions.length < 2) {
			throw new IllegalArgumentException(String.valueOf(parentRevisions));
		}
		if (parent1 != null && parent1.length < 20) {
			throw new IllegalArgumentException(parent1.toString());
		}
		if (parent2 != null && parent2.length < 20) {
			throw new IllegalArgumentException(parent2.toString());
		}
		class ParentCollector implements RevlogStream.Inspector {
			public int p1 = -1;
			public int p2 = -1;
			public byte[] nodeid;
			
			public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess da) {
				p1 = parent1Revision;
				p2 = parent2Revision;
				this.nodeid = new byte[20];
				// nodeid arg now comes in 32 byte from (as in file format description), however upper 12 bytes are zeros.
				System.arraycopy(nodeid, nodeid.length > 20 ? nodeid.length - 20 : 0, this.nodeid, 0, 20);
			}
		};
		ParentCollector pc = new ParentCollector();
		content.iterate(revision, revision, false, pc);
		// although next code looks odd (NO_REVISION *is* -1), it's safer to be explicit
		parentRevisions[0] = pc.p1 == -1 ? NO_REVISION : pc.p1;
		parentRevisions[1] = pc.p2 == -1 ? NO_REVISION : pc.p2;
		if (parent1 != null) {
			if (parentRevisions[0] == NO_REVISION) {
				Arrays.fill(parent1, 0, 20, (byte) 0);
			} else {
				content.iterate(parentRevisions[0], parentRevisions[0], false, pc);
				System.arraycopy(pc.nodeid, 0, parent1, 0, 20);
			}
		}
		if (parent2 != null) {
			if (parentRevisions[1] == NO_REVISION) {
				Arrays.fill(parent2, 0, 20, (byte) 0);
			} else {
				content.iterate(parentRevisions[1], parentRevisions[1], false, pc);
				System.arraycopy(pc.nodeid, 0, parent2, 0, 20);
			}
		}
	}

	/**
	 * EXPERIMENTAL CODE, DO NOT USE
	 * 
	 * Alternative revlog iteration
	 * There's an implicit ordering of inspectors. Less inspector needs, earlier its invocation occurs.
	 * E.g. ParentInspector goes after RevisionInspector. LinkRevisionInspector goes before RevisionInspector
	 * 
	 * @param start
	 * @param end
	 * @param inspector
	 * @throws HgRuntimeException subclass thereof to indicate issues with the library. <em>Runtime exception</em>
	 */
	@Experimental
	public final void indexWalk(int start, int end, final Revlog.Inspector inspector) throws HgRuntimeException { 
		int lastRev = getLastRevision();
		final int _start = start == TIP ? lastRev : start;
		if (end == TIP) {
			end = lastRev;
		}
		final RevisionInspector revisionInsp = Adaptable.Factory.getAdapter(inspector, RevisionInspector.class, null);
		final ParentInspector parentInsp = Adaptable.Factory.getAdapter(inspector, ParentInspector.class, null);
		final LinkRevisionInspector linkRevInspector = Adaptable.Factory.getAdapter(inspector, LinkRevisionInspector.class, null);
		
		// instantiate implementors in reverse order
		RevlogDelegate head = parentInsp == null ? null : new ParentDelegate(parentInsp, null, _start, end);
		if (revisionInsp != null) {
			head = new RevisionDelegate(revisionInsp, head);
		}
		if (linkRevInspector != null) {
			head = new LinkRevDelegate(linkRevInspector, head);
		}
		// first to get notified is created last

		assert head != null; // we know all subclasses of Revlog.Inspector
		head.walk(getRepo(), content, _start, end);
	}
	
	/**
	 * MARKER 
	 */
	@Experimental
	public interface Inspector {
	}

	@Experimental
	public interface RevisionInspector extends Inspector {
		void next(int revisionIndex, Nodeid revision, int linkedRevisionIndex) throws HgRuntimeException;
	}

	@Experimental
	public interface LinkRevisionInspector extends Inspector {
		void next(int revisionIndex, int linkedRevisionIndex) throws HgRuntimeException;
	}

	@Experimental
	public interface ParentInspector extends Inspector {
		// XXX document whether parentX is -1 or a constant (BAD_REVISION? or dedicated?)
		void next(int revisionIndex, Nodeid revision, int parent1, int parent2, Nodeid nidParent1, Nodeid nidParent2) throws HgRuntimeException;
	}
	
	protected HgParentChildMap<? extends Revlog> getParentWalker() throws HgRuntimeException {
		HgParentChildMap<Revlog> pw = new HgParentChildMap<Revlog>(this);
		pw.init();
		return pw;
	}
	
	/*
	 * class with cancel and few other exceptions support. 
	 * TODO [post-1.1] consider general superclass to share with e.g. HgManifestCommand.Mediator
	 */
	protected abstract static class ErrorHandlingInspector implements RevlogStream.Inspector, CancelSupport {
		private Exception failure;
		private CancelSupport cancelSupport;
		
		protected void setCancelSupport(CancelSupport cs) {
			assert cancelSupport == null; // no reason to set it twice
			cancelSupport = cs;
		}

		protected void recordFailure(Exception ex) {
			assert failure == null;
			failure = ex;
		}

		public void checkFailed() throws HgRuntimeException, IOException, CancelledException {
			if (failure == null) {
				return;
			}
			if (failure instanceof IOException) {
				throw (IOException) failure;
			}
			if (failure instanceof CancelledException) {
				throw (CancelledException) failure;
			}
			if (failure instanceof HgRuntimeException) {
				throw (HgRuntimeException) failure;
			}
			throw new HgInvalidStateException(failure.toString());
		}

		public void checkCancelled() throws CancelledException {
			if (cancelSupport != null) {
				cancelSupport.checkCancelled();
			}
		}
	}

	protected static class ContentPipe extends ErrorHandlingInspector implements RevlogStream.Inspector, CancelSupport {
		private final ByteChannel sink;
		private final int offset;
		private final LogFacility logFacility;

		/**
		 * @param _sink - cannot be <code>null</code>
		 * @param seekOffset - when positive, orders to pipe bytes to the sink starting from specified offset, not from the first byte available in DataAccess
		 * @param log optional facility to put warnings/debug messages into, may be null.
		 */
		public ContentPipe(ByteChannel _sink, int seekOffset, LogFacility log) {
			assert _sink != null;
			sink = _sink;
			setCancelSupport(CancelSupport.Factory.get(_sink));
			offset = seekOffset;
			logFacility = log;
		}
		
		protected void prepare(int revisionNumber, DataAccess da) throws IOException {
			if (offset > 0) { // save few useless reset/rewind operations
				da.seek(offset);
			}
		}

		public void next(int revisionNumber, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess da) {
			try {
				prepare(revisionNumber, da); // XXX perhaps, prepare shall return DA (sliced, if needed)
				final ProgressSupport progressSupport = ProgressSupport.Factory.get(sink);
				ByteBuffer buf = ByteBuffer.allocate(actualLen > 8192 ? 8192 : actualLen);
				Preview p = Adaptable.Factory.getAdapter(sink, Preview.class, null);
				if (p != null) {
					progressSupport.start(2 * da.length());
					while (!da.isEmpty()) {
						checkCancelled();
						da.readBytes(buf);
						p.preview(buf);
						buf.clear();
					}
					da.reset();
					prepare(revisionNumber, da);
					progressSupport.worked(da.length());
					buf.clear();
				} else {
					progressSupport.start(da.length());
				}
				while (!da.isEmpty()) {
					checkCancelled();
					da.readBytes(buf);
					buf.flip(); // post: position == 0
					// XXX I may not rely on returned number of bytes but track change in buf position instead.
					
					int consumed = sink.write(buf);
					if ((consumed == 0 || consumed != buf.position()) && logFacility != null) {
						logFacility.dump(getClass(), Warn, "Bad data sink when reading revision %d. Reported %d bytes consumed, byt actually read %d", revisionNumber, consumed, buf.position());
					}
					if (buf.position() == 0) {
						throw new HgInvalidStateException("Bad sink implementation (consumes no bytes) results in endless loop");
					}
					buf.compact(); // ensure (a) there's space for new (b) data starts at 0
					progressSupport.worked(consumed);
				}
				progressSupport.done(); // XXX shall specify whether #done() is invoked always or only if completed successfully.
			} catch (IOException ex) {
				recordFailure(ex);
			} catch (CancelledException ex) {
				recordFailure(ex);
			}
		}
	}


	private static final class LinkRevDelegate extends RevlogDelegate {
		private final LinkRevisionInspector insp;

		public LinkRevDelegate(LinkRevisionInspector inspector, RevlogDelegate next) {
			super(next);
			assert inspector != null;
			insp = inspector;
		}
		@Override
		public void next(int revisionIndex, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) throws HgRuntimeException {
			insp.next(revisionIndex, linkRevision);
			super.next(revisionIndex, actualLen, baseRevision, linkRevision, parent1Revision, parent2Revision, nodeid, data);
		}
	}
	
	private static final class RevisionDelegate extends RevlogDelegate {
		private final RevisionInspector insp;
		public RevisionDelegate(RevisionInspector inspector, RevlogDelegate next) {
			super(next);
			assert inspector != null;
			insp = inspector;
		}
		@Override
		public void next(int revisionIndex, int actualLen, int baseRevision, int linkRevision, int parent1Revision, int parent2Revision, byte[] nodeid, DataAccess data) throws HgRuntimeException {
			Nodeid nid = getRevision(nodeid);
			insp.next(revisionIndex, nid, linkRevision);
			super.next(revisionIndex, actualLen, baseRevision, linkRevision, parent1Revision, parent2Revision, nodeid, data);
		}
	}
	
	private static class ParentDelegate extends RevlogDelegate {
		private final ParentInspector insp;
		private int i = 0;
		private final Nodeid[] allRevisions;
		// next are to build set of parent indexes that are not part of the range iteration
		// i.e. those parents we need to read separately. See Issue 31 for details.
		private final int[]      firstParentIndexes;
		private final int[]     secondParentIndexes;
		private final IntMap<Nodeid> missingParents;
		private final int start; 

		public ParentDelegate(ParentInspector inspector, RevlogDelegate next, int _start, int end) {
			super(next);
			insp = inspector;
			start = _start;
			allRevisions = new Nodeid[end - _start + 1];
			firstParentIndexes = _start == 0 ? null : new int[allRevisions.length];
			secondParentIndexes = _start == 0 ? null : new int[allRevisions.length];
			missingParents = _start == 0 ? null : new IntMap<Nodeid>(16);
		}
		@Override
		public void next(int revisionIndex, int actualLen, int baseRevision, int linkRevision, int parent1RevIndex, int parent2RevIndex, byte[] nodeid, DataAccess data) throws HgRuntimeException {
			allRevisions[i] = getRevision(nodeid);
			if (start > 0) {
				// there are chances we don't know parents here,
				// postpone parent dispatching for later, now just collect what's missing
				firstParentIndexes[i] = parent1RevIndex;
				secondParentIndexes[i] = parent2RevIndex;
				if (parent1RevIndex < start && parent1RevIndex >= 0) {
					missingParents.put(parent1RevIndex, null);
				}
				if (parent2RevIndex < start && parent2RevIndex >= 0) {
					missingParents.put(parent2RevIndex, null);
				}
			} else {
				// we iterate from the very beginning, got every index we'll need
				Nodeid p1 = parent1RevIndex == -1 ? Nodeid.NULL : allRevisions[parent1RevIndex];
				Nodeid p2 = parent2RevIndex == -1 ? Nodeid.NULL : allRevisions[parent2RevIndex];
				insp.next(revisionIndex, allRevisions[i], parent1RevIndex, parent2RevIndex, p1, p2);
			}
			i++;
			super.next(revisionIndex, actualLen, baseRevision, linkRevision, parent1RevIndex, parent2RevIndex, nodeid, data);
		}

		@Override
		protected void postWalk(HgRepository hgRepo) {
			if (start > 0) {
				complete(hgRepo);
			}
			super.postWalk(hgRepo);
		}

		private void complete(HgRepository repo) {
			if (missingParents.size() > 0) {
				// it's possible to get empty missingParents when _start > 0 e.g. when n-th file revision
				// is a copy of another file and hence got -1,-1 parents in this revlog, and we indexWalk(n,n)
				for (int k = missingParents.firstKey(), l = missingParents.lastKey(); k <= l; k++) {
					// TODO [post-1.1] int[] IntMap#keys() or even sort of iterator that can modify values
					if (missingParents.containsKey(k)) {
						Nodeid nid = repo.getChangelog().getRevision(k);
						missingParents.put(k, nid);
					}
				}
			}

			for (int i = 0, revNum = start; i < allRevisions.length; i++, revNum++) {
				int riP1 = firstParentIndexes[i];
				int riP2 = secondParentIndexes[i];
				Nodeid p1, p2;
				p1 = p2 = Nodeid.NULL;
				if (riP1 >= start) {
					// p1 of revNum's revision is out of iterated range
					// (don't check for riP1<end as I assume parents come prior to children in the changelog)
					p1 = allRevisions[riP1 - start];
				} else if (riP1 != -1) {
					assert riP1 >=0 && riP1 < start;
					p1 = missingParents.get(riP1);
					assert p1 != null;
				}
				// same for Pp2
				if (riP2 >= start) {
					p2 = allRevisions[riP2 - start];
				} else if (riP2 != -1) {
					assert riP2 >= 0 && riP2 < start;
					p2 = missingParents.get(riP2);
					assert p2 != null;
				}
				insp.next(revNum, allRevisions[i], riP1, riP2, p1, p2);
			}
		}
	}
}
