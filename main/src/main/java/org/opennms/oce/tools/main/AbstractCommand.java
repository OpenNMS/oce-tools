/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2018 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2018 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.oce.tools.main;

import java.util.Objects;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCommand implements Command {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCommand.class);

    private final String name;

    @Option(name="--help", aliases={"-h"}, usage="show this message", help=true)
    private boolean help = false;

    public AbstractCommand(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public abstract void doExec(Context context) throws Exception;

    @Override
    public void execute(Context context) throws Exception {
        if (help) {
            printUsage();
            return;
        }
        doExec(context);
    }

    private void printUsage() {
        System.out.printf("cpn-tools %s (options)\n", name);
        final CmdLineParser parser = new CmdLineParser(this);
        parser.printUsage(System.out);
    }

}
