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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.kohsuke.args4j.Option;
import org.opennms.core.xml.JaxbUtils;
import org.opennms.netmgt.config.syslogd.SyslogdConfigurationGroup;
import org.opennms.netmgt.config.syslogd.UeiMatch;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.syslogd.ByteBufferParser;
import org.opennms.netmgt.syslogd.ConvertToEvent;
import org.opennms.netmgt.syslogd.RadixTreeSyslogParser;
import org.opennms.netmgt.syslogd.SyslogMessage;
import org.opennms.oce.integrations.opennms.config.CiscoSyslogMatchExtension;
import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;

public class CpnOnmsSyslogAudit extends AbstractCommand {
    public static final String NAME = "cpn-opennms-syslog-audit";

    @Option(name = "--from", aliases = {"-f"}, usage = "From date i.e. Oct 28 2018")
    private String from;

    @Option(name = "--to", aliases = {"-t"}, usage = "To date i.e. Oct 29 2018")
    private String to;

    @Option(name = "--count", aliases = {"-c"}, usage = "Set to print only the count")
    private boolean countOnly = false;

    private final ByteBufferParser<SyslogMessage> parser = RadixTreeSyslogParser.getRadixParser();

    private final List<UeiMatch> ueiMatches = new ArrayList<>();

    private final Method matchRegex = ConvertToEvent.class.getDeclaredMethod("matchRegex", String.class,
            UeiMatch.class, EventBuilder.class, String.class);

    private long failedHeaderParsingCount;

    private long failedBodyParsingCount;

    private long processedCount;

    public CpnOnmsSyslogAudit() throws NoSuchMethodException {
        super(NAME);
        matchRegex.setAccessible(true);
    }

    @Override
    public void doExec(Context context) throws Exception {
        ESDataProvider esDataProvider = new ESDataProvider(context.getEsClient());
        ueiMatches.addAll(JaxbUtils.unmarshal(SyslogdConfigurationGroup.class,
                CiscoSyslogMatchExtension.class.getClassLoader()
                        .getResourceAsStream("syslog/Cisco.ext.syslog.xml")).getUeiMatches());
        CommandUtils.DateRange range = CommandUtils.parseDateRange(from, to);
        esDataProvider.getSyslogRecordsInRange(range.getStart(), range.getEnd(), this::consumeSyslogBatch);

        System.out.println("Processed " + processedCount + " syslog messages");

        if (countOnly || !(failedHeaderParsingCount == 0 && failedBodyParsingCount == 0)) {
            System.out.println("Header fail: " + failedHeaderParsingCount);
            System.out.println("Body fail: " + failedBodyParsingCount);
        } else {
            System.out.println("All messages parsed correctly");
        }
    }

    private void consumeSyslogBatch(List<EventRecord> batchOfSyslogs) {
        batchOfSyslogs.stream()
                .filter(event -> !EventUtils.isClear(event))
                .forEach(syslog -> {
                    processedCount++;
                    String detailedDescription = syslog.getDetailedDescription();
                    ByteBuffer bytesToParse = ByteBuffer.wrap(detailedDescription.getBytes());
                    CompletableFuture<SyslogMessage> parseFuture = parser.parse(bytesToParse);
                    Optional<SyslogMessage> parsedSyslogOpt = Optional.empty();

                    try {
                        parsedSyslogOpt = Optional.of(parseFuture.get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }

                    SyslogMessage parsedSyslog = parsedSyslogOpt.orElseThrow(() -> 
                            new RuntimeException("Parser failed to return a parsed syslog message"));
                    String parsedMessage = parsedSyslog.getMessage();

                    if (!passedHeaderParsing(parsedMessage)) {
                        failedHeaderParsingCount++;

                        if (!countOnly) {
                            System.out.println("FAILED to parse header");
                            System.out.println("Input:\n" + detailedDescription);
                            System.out.println("Result:\n" + parsedSyslog + "\n");
                        }
                    }

                    try {
                        if (!passedBodyParsing(parsedMessage)) {
                            failedBodyParsingCount++;

                            if (!countOnly) {
                                System.out.println("FAILED to parse body");
                                System.out.println("Input:\n" + detailedDescription);
                                System.out.println("Result:\n" + parsedSyslog + "\n");
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    private boolean passedHeaderParsing(String syslogMessage) {
        // We expect all message to begin with a mnemonic in the form of %Blah so we can simply check for the % here
        return syslogMessage.startsWith("%");
    }

    private boolean passedBodyParsing(String syslogMessage) throws InvocationTargetException, IllegalAccessException {
        for (UeiMatch ueiMatch : ueiMatches) {
            if ((boolean) matchRegex.invoke(ConvertToEvent.class, syslogMessage, ueiMatch, new EventBuilder(),
                    "discarduei")) {
                return true;
            }
        }

        return false;
    }
}
