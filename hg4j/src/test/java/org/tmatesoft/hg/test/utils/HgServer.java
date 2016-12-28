/*
 * Copyright (c) 2013 TMate Software Ltd
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
package org.tmatesoft.hg.test.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps hg server
 *
 * @author Artem Tikhomirov
 * @author TMate Software Ltd.
 */
public class HgServer {
    private Process serverProcess;
    private boolean publish = true;

    public HgServer publishing(boolean pub) {
        publish = pub;
        return this;
    }

    public HgServer start(File dir) throws IOException, InterruptedException {
        if (serverProcess != null) {
            stop();
        }
        List<String> cmdline = new ArrayList<>();
        cmdline.add("hg");
        cmdline.add("--config");
        cmdline.add("web.allow_push=*");
        cmdline.add("--config");
        cmdline.add("web.push_ssl=False");
        cmdline.add("--config");
        cmdline.add("server.validate=True");
        cmdline.add("--config");
        cmdline.add(String.format("web.port=%d", port()));
        if (!publish) {
            cmdline.add("--config");
            cmdline.add("phases.publish=False");
        }
        cmdline.add("serve");
        serverProcess = new ProcessBuilder(cmdline).directory(dir).start();
        //TODO: Thread.sleep should not be used in tests
        Thread.sleep(500);
        return this;
    }

    public URL getURL() throws MalformedURLException {
        return new URL(String.format("http://localhost:%d/", port()));
    }

    public void stop() {
        if (serverProcess == null) {
            return;
        }
        // if Process#destroy() doesn't perform well with scripts and child processes
        // may need to write server pid to a file and send a kill <pid> here
        serverProcess.destroy();
        serverProcess = null;
    }

    private int port() {
        return 9090;
    }
}