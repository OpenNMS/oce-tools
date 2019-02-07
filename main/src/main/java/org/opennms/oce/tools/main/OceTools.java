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

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.SubCommand;
import org.kohsuke.args4j.spi.SubCommandHandler;
import org.kohsuke.args4j.spi.SubCommands;
import org.opennms.oce.tools.es.ESClient;
import org.opennms.oce.tools.es.ESConfiguration;
import org.opennms.oce.tools.es.ESConfigurationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class OceTools {

    private static final Logger LOG = LoggerFactory.getLogger(OceTools.class);

    private static final List<String> availableSubCommands = Lists.newArrayList(
            CpnCsvImportCommand.NAME,
            CpnOceExportCommand.NAME,
            CpnOnmsEventDefAuditCommand.NAME,
            CpnOnmsEventHandlingAuditCommand.NAME,
            CpnOnmsEventMatAuditCommand.NAME,
            CpnOnmsSituationMatAuditCommand.NAME,
            GenerateKemConfigCommand.NAME,
            CpnReportCommand.NAME,
            CpnOnmsSyslogAudit.NAME,
            OnmsExportCommand.NAME,
            TSAuditCommand.NAME,
            DSMapCommand.NAME,
            TicketDiagCommand.NAME
    );

    @Option(name = "--es-config", usage = "elasticsearch configuration", metaVar = "es-yaml-config")
    private File esConfigFile = Paths.get(System.getProperty("user.home"), ".oce", "es-config.yaml").toFile();

    @Option(name="--help", aliases={"-h"}, usage="show this message", help=true)
    private boolean help = false;

    @Argument(handler=SubCommandHandler.class, required=true, metaVar = "command")
    @SubCommands({
            @SubCommand(name=CpnCsvImportCommand.NAME, impl=CpnCsvImportCommand.class),
            @SubCommand(name=CpnOceExportCommand.NAME, impl=CpnOceExportCommand.class),
            @SubCommand(name=CpnOnmsEventDefAuditCommand.NAME, impl=CpnOnmsEventDefAuditCommand.class),
            @SubCommand(name=CpnOnmsEventHandlingAuditCommand.NAME, impl=CpnOnmsEventHandlingAuditCommand.class),
            @SubCommand(name=CpnOnmsEventMatAuditCommand.NAME, impl=CpnOnmsEventMatAuditCommand.class),
            @SubCommand(name=CpnOnmsSituationMatAuditCommand.NAME, impl=CpnOnmsSituationMatAuditCommand.class),
            @SubCommand(name=GenerateKemConfigCommand.NAME, impl=GenerateKemConfigCommand.class),
            @SubCommand(name=CpnReportCommand.NAME, impl=CpnReportCommand.class),
            @SubCommand(name=CpnOnmsSyslogAudit.NAME, impl=CpnOnmsSyslogAudit.class),
            @SubCommand(name=OnmsExportCommand.NAME, impl=OnmsExportCommand.class),
            @SubCommand(name=TSAuditCommand.NAME, impl=TSAuditCommand.class),
            @SubCommand(name=DSMapCommand.NAME, impl=DSMapCommand.class),
            @SubCommand(name=TicketDiagCommand.NAME, impl=TicketDiagCommand.class),
    })
    private Command cmd;

    public static void main(String[] args) {
        final OceTools oceTools = new OceTools();
        final CmdLineParser parser = new CmdLineParser(oceTools);
        try {
            parser.parseArgument(args);
            if (oceTools.help) {
                oceTools.printUsage(parser);
                return;
            }
            oceTools.doMain();
        } catch (CmdLineException e) {
            LOG.error("Invalid command line options.", e);
            oceTools.printUsage(parser);
            System.exit(1);
        } catch (Exception e) {
            LOG.error("Execution failed.", e);
            System.exit(1);
        }
    }

    private void doMain() throws Exception {
        final ESConfiguration esConfiguration;
        try {
            final ESConfigurationDao esConfigurationDao = new ESConfigurationDao(esConfigFile);
            esConfiguration = esConfigurationDao.getConfig();
        } catch(Exception e) {
            throw new Exception(String.format("Failed to load Elasticsearch configuration from %s.", esConfigFile), e);
        }
        final ESClient esClient = new ESClient(esConfiguration.getFirstCluster());
        final Context context = new Context(esClient);
        cmd.execute(context);
    }

    private void printUsage(CmdLineParser parser) {
        System.out.println("cpn-tools <command>");
        parser.printUsage(System.out);
        System.out.println("Available commands are: ");
        for (String subCommand : availableSubCommands) {
            System.out.printf("\t%s\n", subCommand);
        }
    }

}
