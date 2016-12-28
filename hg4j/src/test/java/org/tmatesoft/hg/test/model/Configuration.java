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
package org.tmatesoft.hg.test.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.tmatesoft.hg.core.HgException;
import org.tmatesoft.hg.repo.HgLookup;
import org.tmatesoft.hg.repo.HgRemoteRepository;
import org.tmatesoft.hg.repo.HgRepository;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class Configuration {

    private static final String TESTS_REPO_PATH = "/src/test/repos/";
    private static final String TESTS_TMP_PATH = "/src/test/tests-tmp/";
    private static final String TESTS_RESOURCES_PATH = "/src/test/resources/";
    private static final String TESTS_REMOTE_PATH = "http://hg.serpentine.com/tutorial/hello";
    private static final Configuration INSTANCE = new Configuration();
    private final String userDir = System.getProperty("user.dir");
    private final HgLookup lookup;
    private File root;
    private File tempDir;
    private List<String> remoteServers;
    private File testDataDir;
    private File testHgrc;

    private Configuration() {
        lookup = new HgLookup();
    }

    public static Configuration get() {
        return INSTANCE;
    }

    public HgRepository own() throws Exception {
        return lookup.detectFromWorkingDir();
    }

    // fails if repo not found
    public HgRepository find(String key) throws HgException {
        HgRepository rv = lookup.detect(new File(getRoot(), key));
        assertNotNull(rv);
        assertFalse(rv.isInvalid());
        return rv;
    }

    // easy override for manual test runs
    public void remoteServers(String... keys) {
        remoteServers = Arrays.asList(keys);
    }

    public List<HgRemoteRepository> allRemote() throws Exception {
        if (remoteServers == null) {
            remoteServers = Arrays.asList(TESTS_REMOTE_PATH.split("\\s"));
        }
        ArrayList<HgRemoteRepository> rv = new ArrayList<>(remoteServers.size());
        for (String key : remoteServers) {
            rv.add(lookup.detectRemote(key, null));
        }
        return rv;
    }

    /**
     * @return temporary directory to use in tests, may be configured from outside
     */
    public File getTempDir() {
        if (tempDir == null) {
            tempDir = new File(userDir + TESTS_TMP_PATH);
        }
        return tempDir;
    }

    /**
     * @return location with various files used in tests
     */
    public File getTestDataDir() {
        if (testDataDir == null) {
            testDataDir = new File(userDir + TESTS_RESOURCES_PATH);
        }
        return testDataDir;
    }

    private File getRoot() {
        if (root == null) {
            root = new File(userDir + TESTS_REPO_PATH);
            assertTrue(root.exists());
        }
        return root;
    }
}
