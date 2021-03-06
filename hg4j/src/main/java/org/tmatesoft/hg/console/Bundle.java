/*
 * Copyright (c) 2011-2012 TMate Software Ltd
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
package org.tmatesoft.hg.console;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;

import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.core.Nodeid;
import org.tmatesoft.hg.repo.HgBundle;
import org.tmatesoft.hg.repo.HgChangelog;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.repo.HgRepository;
import org.tmatesoft.hg.repo.HgBundle.GroupElement;
import org.tmatesoft.hg.repo.HgBundle.Inspector;
import org.tmatesoft.hg.repo.HgChangelog.RawChangeset;
import org.tmatesoft.hg.repo.HgRuntimeException;


/**
 * WORK IN PROGRESS, DO NOT USE
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class Bundle {
	public static void main(String[] args) throws Exception {
		Options cmdLineOpts = Options.parse(args, Collections.<String>emptySet());
		final HgRepository hgRepo = cmdLineOpts.findRepository();
		if (hgRepo.isInvalid()) {
			System.err.printf("Can't find repository in: %s\n", hgRepo.getLocation());
			return;
		}
		File bundleFile = new File("/temp/hg/hg-bundle-cpython.tmp");
		HgBundle hgBundle = new HgLookup().loadBundle(bundleFile);
		hgBundle.inspectFiles(new Dump());
		if (Boolean.parseBoolean("true")) {
			return;
		}
		/* pass -R <path-to-repo-with-less-revisions-than-bundle>, e.g. for bundle with tip=168 and -R \temp\hg4j-50 with tip:159
		+Changeset {User: ..., Comment: Integer ....}
		+Changeset {User: ..., Comment: Approach with ...}
		-Changeset {User: ..., Comment: Correct project name...}
		-Changeset {User: ..., Comment: Record possible...}
		*/
		hgBundle.changes(hgRepo, new HgChangelog.Inspector() {
			private final HgChangelog changelog = hgRepo.getChangelog();
			
			public void next(int revisionNumber, Nodeid nodeid, RawChangeset cset) throws HgRuntimeException {
				if (changelog.isKnown(nodeid)) {
					System.out.print("+");
				} else {
					System.out.print("-");
				}
				System.out.printf("%d:%s\n%s\n", revisionNumber, nodeid.shortNotation(), cset.toString());
			}
		});
	}

/*
 *  TODO EXPLAIN why DataAccess.java on merge from branch has P2 set, and P1 is NULL
 *  
 *  excerpt from dump('hg-bundle-00') output (node, p1, p2, cs):
 src/org/tmatesoft/hg/internal/DataAccess.java
  186af94a2a7ddb34190e63ce556d0fa4dd24add2 0000000000000000000000000000000000000000 0000000000000000000000000000000000000000 6f1b88693d48422e98c3eaaa8428ffd4d4d98ca7; patches:1
  be8d0fdc4ff268bf5eb0a9120282ce6e63de1606 186af94a2a7ddb34190e63ce556d0fa4dd24add2 0000000000000000000000000000000000000000 a3a2e5deb320d7412ccbb59bdc44668d445bc4c4; patches:2
  333d7bbd4a80a5d6fb4b44e54e39e290f50dc7f8 be8d0fdc4ff268bf5eb0a9120282ce6e63de1606 0000000000000000000000000000000000000000 e93101b97e4ab0a3f3402ec0e80b6e559237c7c8; patches:1
  56e4523cb8b42630daf70511d73d29e0b375dfa5 0000000000000000000000000000000000000000 333d7bbd4a80a5d6fb4b44e54e39e290f50dc7f8 d5268ca7715b8d96204fc62abc632e8f55761547; patches:6
  f85b6d7ed3cc4b7c6f99444eb0a41b58793cc900 56e4523cb8b42630daf70511d73d29e0b375dfa5 0000000000000000000000000000000000000000 b413b16d10a50cc027f4c38e4df5a9fedd618a79; patches:4
	  
  RevlogDump for the file says:
  Index    Offset      Flags     Packed     Actual   Base Rev   Link Rev  Parent1  Parent2     nodeid
   0:    4295032832      0       1109       2465          0         74       -1       -1     186af94a2a7ddb34190e63ce556d0fa4dd24add2
   1:          1109      0         70       2364          0        102        0       -1     be8d0fdc4ff268bf5eb0a9120282ce6e63de1606
   2:          1179      0         63       2365          0        122        1       -1     333d7bbd4a80a5d6fb4b44e54e39e290f50dc7f8
   3:          1242      0        801       3765          0        157       -1        2     56e4523cb8b42630daf70511d73d29e0b375dfa5
   4:          2043      0        130       3658          0        158        3       -1     f85b6d7ed3cc4b7c6f99444eb0a41b58793cc900

  Excerpt from changelog dump:
  155:         30541      0        155        195        155        155      154       -1     a4ec5e08701771b96057522188b16ed289e9e8fe
  156:         30696      0        154        186        155        156      155       -1     643ddec3be36246fc052cf22ece503fa60cafe22
  157:         30850      0        478       1422        155        157      156       53     d5268ca7715b8d96204fc62abc632e8f55761547
  158:         31328      0        247        665        155        158      157       -1     b413b16d10a50cc027f4c38e4df5a9fedd618a79
			   

 */

	public static void dump(HgBundle hgBundle) throws HgException, HgRuntimeException {
		Dump dump = new Dump();
		hgBundle.inspectAll(dump);
		System.out.println("Total files:" + dump.names.size());
		for (String s : dump.names) {
			System.out.println(s);
		}
	}

	public static class Dump implements Inspector {
		public final LinkedList<String> names = new LinkedList<String>();

		public void changelogStart() {
			System.out.println("Changelog group");
		}

		public void changelogEnd() {
		}

		public void manifestStart() {
			System.out.println("Manifest group");
		}

		public void manifestEnd() {
		}

		public void fileStart(String name) {
			names.add(name);
			System.out.println(name);
		}

		public void fileEnd(String name) {
		}

		public boolean element(GroupElement ge) {
			System.out.printf("  %s\n", ge.toString());
			return true;
		}
	}
}
