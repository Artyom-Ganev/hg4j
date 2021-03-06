/*
 * Copyright (c) 2010-2012 TMate Software Ltd
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

import org.tmatesoft.hg.internal.Callback;
import org.tmatesoft.hg.util.Path;

/**
 * Callback to get file status information
 * 
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
@Callback
public interface HgStatusInspector {
	void modified(Path fname);
	void added(Path fname);
	/**
	 * This method is invoked for files that we added as a result of a copy/move operation, and it's the sole
	 * method invoked in this case, that is {@link #added(Path)} method is NOT invoked along with it. 
	 * Note, however, {@link #removed(Path)} IS invoked for the removed file in all cases, regardless whether it's a mere rename or not.
	 * <p>The reason why it's not symmetrical ({@link #copied(Path, Path)} and {@link #removed(Path)} but not {@link #added(Path)}) is that Mercurial 
	 * does it this way ('copy' is just an extra attribute for Added file), and we try to stay as close as possible here.  
	 * <p>If copied files of no interest, it is implementation responsibility to delegate to <code>this.added(fnameAdded)</code>
	 */
	void copied(Path fnameOrigin, Path fnameAdded);
	void removed(Path fname);
	void clean(Path fname);
	/**
	 * Reports file tracked by Mercurial, but not available in file system any more, aka deleted. 
	 */
	void missing(Path fname); // 
	void unknown(Path fname); // not tracked
	void ignored(Path fname);
	/**
	 * Reports a single file error during status collecting operation. It's up to client to treat the whole operation as successful or not.
	 * The error reported is otherwise not critical for the status operation.
	 *  
	 * @param fname origin of the error
	 * @param ex describes an error occurred while accessing the file, never <code>null</code>
	 */
	void invalid(Path fname, Exception ex);
}