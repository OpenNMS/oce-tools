/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2019 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2019 The OpenNMS Group, Inc.
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

import java.nio.file.Paths;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.NodeAndFactsGenerator;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.dsmapping.DSMapper;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.onms.client.EventClient;

public class DSMapCommand extends AbstractCommand {
    public static final String NAME = "dsmap";

    @Option(name = "--cpn-dir", aliases = {"-c"}, usage = "TODO")
    private String cpnDir;

    @Option(name = "--onms-dir", aliases = {"-n"}, usage = "TODO")
    private String onmsDir;

    @Option(name = "--output-dir", aliases = {"-o"}, usage = "TODO")
    private String outputDir;

    // TODO: Temporary
    @Option(name = "--from", aliases = {"-f"}, usage = "From date i.e. Oct 28 2018")
    private String from;

    // TODO: Temporary
    @Option(name = "--to", aliases = {"-t"}, usage = "To date i.e. Oct 29 2018")
    private String to;

    public DSMapCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        ESClient esClient = context.getEsClient();
        ESDataProvider esDataProvider = new ESDataProvider(esClient);
        EventClient eventClient = new EventClient(esClient);
        DSMapper dsMapper = new DSMapper(esDataProvider, eventClient, Paths.get(cpnDir), Paths.get(onmsDir),
                Paths.get(outputDir), NodeAndFactsGenerator::newBuilder);
        
        // TODO: Temporary hack to explicitly set the date range rather than derive from xml
        CommandUtils.DateRange range = CommandUtils.parseDateRange(from, to);
        dsMapper.overrideStart(range.getStart());
        dsMapper.overrideEnd(range.getEnd());
        dsMapper.run();
    }
}
