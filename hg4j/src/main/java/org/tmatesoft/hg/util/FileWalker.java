/*
 * Copyright (c) 2011-2013 TMate Software Ltd
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
package org.tmatesoft.hg.util;

import java.io.File;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import org.tmatesoft.hg.core.SessionContext;
import org.tmatesoft.hg.internal.Internals;

/**
 * Implementation of {@link FileIterator} using regular {@link java.io.File}
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class FileWalker implements FileIterator {

	private final File startDir;
	private final Path.Source pathHelper;
	private final LinkedList<File> dirQueue;
	private final LinkedList<File> fileQueue;
	private final Path.Matcher scope;
	private final boolean execCap, linkCap;
	private final SessionContext sessionContext;
	private RegularFileInfo nextFile;
	private Path nextPath;

	public FileWalker(SessionContext ctx, File dir, Path.Source pathFactory) {
		this(ctx, dir, pathFactory, null);
	}
	
	/**
	 * @see FileWalker#FileWalker(SessionContext, File, Path.Source, Matcher)
	 */
	public FileWalker(SessionContext.Source ctxSource, File dir, Path.Source pathFactory, Path.Matcher scopeMatcher) {
		this(ctxSource.getSessionContext(), dir, pathFactory, scopeMatcher);
	}

	/**
	 * Implementation of {@link FileIterator} with regular {@link java.io.File}.
	 * 
	 * @param dir directory to start at, not <code>null</code>
	 * @param pathFactory factory to create {@link Path} instances, not <code>null</code>
	 * @param scopeMatcher - this matcher shall be capable to tell not only files of interest, but
	 * also whether directories shall be traversed or not (Paths it gets in {@link Path.Matcher#accept(Path)} may 
	 * point to directories); may be <code>null</code>
	 */
	public FileWalker(SessionContext ctx, File dir, Path.Source pathFactory, Path.Matcher scopeMatcher) {
		sessionContext = ctx;
		startDir = dir;
		pathHelper = pathFactory;
		dirQueue = new LinkedList<File>();
		fileQueue = new LinkedList<File>();
		scope = scopeMatcher;
		execCap = Internals.checkSupportsExecutables(startDir);
		linkCap = Internals.checkSupportsSymlinks(startDir);
		reset();
	}

	public void reset() {
		fileQueue.clear();
		dirQueue.clear();
		dirQueue.add(startDir);
		nextFile = new RegularFileInfo(sessionContext, supportsExecFlag(), supportsLinkFlag());
		nextPath = null;
	}
	
	public boolean hasNext() {
		return fill();
	}

	public void next() {
		if (!fill()) {
			throw new NoSuchElementException();
		}
		File next = fileQueue.removeFirst();
		nextFile.init(next);
		nextPath = pathHelper.path(next.getPath());
	}

	public Path name() {
		return nextPath;
	}
	
	public FileInfo file() {
		return nextFile;
	}
	
	public boolean inScope(Path file) {
		/* by default, no limits, all files are of interest */
		return scope == null ? true : scope.accept(file); 
	}
	
	public boolean supportsExecFlag() {
		return execCap;
	}
	
	public boolean supportsLinkFlag() {
		return linkCap;
	}
		
	// returns non-null
	private File[] listFiles(File f) {
		// in case we need to solve os-related file issues (mac with some encodings?)
		File[] rv = f.listFiles();
		// there are chances directory we query files for is missing (deleted), just treat it as empty
		return rv == null ? new File[0] : rv;
	}

	// return true when fill added any elements to fileQueue. 
	private boolean fill() {
		while (fileQueue.isEmpty()) {
			if (dirQueue.isEmpty()) {
				return false;
			}
			while (!dirQueue.isEmpty()) {
				File dir = dirQueue.removeFirst();
				for (File f : listFiles(dir)) {
					final boolean isDir = f.isDirectory();
					Path path = pathHelper.path(isDir ? ensureTrailingSlash(f.getPath()) : f.getPath());
					if (!inScope(path)) {
						continue;
					}
					if (isDir) {
						// do not dive into <repo>/.hg and
						// if there's .hg/ under f/, it's a nested repository, which shall not be walked into
						if (".hg".equals(f.getName()) || new File(f, ".hg").isDirectory()) {
							continue;
						} else {
							dirQueue.addLast(f);
						}
					} else {
						fileQueue.addLast(f);
					}
				}
				break;
			}
		}
		return !fileQueue.isEmpty();
	}
	
	private static String ensureTrailingSlash(String dirName) {
		if (dirName.length() > 0) {
			char last = dirName.charAt(dirName.length() - 1);
			if (last == '/' || last == File.separatorChar) {
				return dirName;
			}
			// if path already has platform-specific separator (which, BTW, it shall, according to File#getPath), 
			// add similar, otherwise use our default.
			return dirName.indexOf(File.separatorChar) != -1 ? dirName.concat(File.separator) : dirName.concat("/");
		}
		return dirName;
	}
}
