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

package org.opennms.oce.tools.onms;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.model.api.SyslogEvent;
import org.opennms.oce.tools.onms.model.v1.SnmpTrapAlarmDefinitions;
import org.opennms.oce.tools.onms.model.v1.SyslogAlarmDefinitions;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

public class EventCoverageAudit {

    protected static final Pattern CISCO_SYSLOG_TAG_PATTERN = Pattern.compile(".*(%\\S+)\\s*:.*");

    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final ESDataProvider esDataProvider;
    private final boolean noFail;

    public static class Builder {
        private ZonedDateTime start;
        private ZonedDateTime end;
        private ESDataProvider esDataProvider;
        private boolean noFail = true;

        public Builder withStart(ZonedDateTime start) {
            this.start = start;
            return this;
        }

        public Builder withEnd(ZonedDateTime end) {
            this.end = end;
            return this;
        }

        public Builder withDataProvider(ESDataProvider esDataProvider) {
            this.esDataProvider = esDataProvider;
            return this;
        }

        public Builder withNoFail(boolean noFail) {
            this.noFail = noFail;
            return this;
        }

        public EventCoverageAudit build() {
            Objects.requireNonNull(start, "start is required");
            Objects.requireNonNull(end, "end is required");
            Objects.requireNonNull(esDataProvider, "data provider is required");
            return new EventCoverageAudit(this);
        }
    }

    public EventCoverageAudit(Builder builder) {
        this.esDataProvider = builder.esDataProvider;
        this.start = builder.start;
        this.end = builder.end;
        this.noFail = builder.noFail;
    }

    /**
     * Validate that we have a matching event definition for every event found in ES.
     *
     * @throws IOException if an error occurs getting data out of ES
     */
    public void verifyMatchingEventDefinitions() throws IOException {
        MetricRegistry metrics = new MetricRegistry();
        Counter syslogsMatched = metrics.counter("syslogMatched");
        Counter syslogsUnmatched = metrics.counter("syslogsUnmatched");
        Counter trapsMatched = metrics.counter("trapsMatched");
        Counter trapsUnmatched = metrics.counter("trapsUnmatched");

        Map<String, Integer> syslogMessagesByCiscoTag = new LinkedHashMap<>();
        Map<String, String> lastEventIdForSyslogMessageWithCiscoTag = new LinkedHashMap<>();
        Set<String> untaggedSyslogMessages = new LinkedHashSet<>();

        Map<String, Integer> trapsByTrapTypeOid = new LinkedHashMap<>();
        Map<String, String> lastEventIdForTrapWithTypeOid = new LinkedHashMap<>();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(5, TimeUnit.SECONDS);

        esDataProvider.getSyslogRecordsInRange(start, end, records -> {
            for (EventRecord r : records) {
                final SyslogEvent s = new SyslogEvent() {
                    @Override
                    public String getEventId() {
                        return r.getEventId();
                    }

                    @Override
                    public String getMessage() {
                        return r.getDetailedDescription();
                    }
                };

                boolean matched = SyslogAlarmDefinitions.DEFS.stream().anyMatch(def -> def.matches(s));
                if (!matched && s.getMessage().contains("Cleared due to ")) {
                    // The syslog message has been mangled by CPN, ignore it
                   continue;
                }

                if (matched) {
                    syslogsMatched.inc();
                } else {
                    syslogsUnmatched.inc();
                    final Matcher m = CISCO_SYSLOG_TAG_PATTERN.matcher(s.getMessage());
                    if (m.matches()) {
                        syslogMessagesByCiscoTag.compute(m.group(1), (key,oldValue) -> {
                            if (oldValue == null) {
                                return 1;
                            } else {
                                return oldValue+1;
                            }
                        });
                        lastEventIdForSyslogMessageWithCiscoTag.put(m.group(1), s.getEventId());
                    } else {
                        untaggedSyslogMessages.add(s.getMessage() + String.format(" (id=%s)", s.getEventId()));
                    }
                }

                if (!noFail && !matched) {
                    throw new RuntimeException(String.format("No match for syslog event with id: %s and message: %s",
                            r.getEventId(), s.getMessage()));
                }
            }
        });
        esDataProvider.getTrapRecordsInRange(start, end, records -> {
            for (TrapRecord r : records) {
                final boolean matched = SnmpTrapAlarmDefinitions.DEFS.stream().anyMatch(def -> def.matches(r));
                if (matched) {
                    trapsMatched.inc();
                } else {
                    trapsUnmatched.inc();
                    trapsByTrapTypeOid.compute(r.getTrapTypeOid(), (key,oldValue) -> {
                        if (oldValue == null) {
                            return 1;
                        } else {
                            return oldValue+1;
                        }
                    });
                    lastEventIdForTrapWithTypeOid.put(r.getTrapTypeOid(), r.getEventId());
                }

                if (!noFail && !matched) {
                    throw new RuntimeException(String.format("No match for trap event with id: %s and trap type: %s",
                            r.getEventId(), r.getTrapTypeOid()));
                }
            }
        });

        // Sort the maps
        Map<String, Integer> sortedSslogMessagesByCiscoTag = syslogMessagesByCiscoTag.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));
        Map<String, Integer> sortedTrapsByTrapTypeOid = trapsByTrapTypeOid.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> -e.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));

        // One last report
        reporter.stop();
        reporter.report();

        long totalSyslogs = syslogsMatched.getCount() + syslogsUnmatched.getCount();
        System.out.printf("Syslog coverage: %.2f\n", syslogsMatched.getCount() / (double)totalSyslogs * 100d);

        long totalTraps = trapsMatched.getCount() + trapsUnmatched.getCount();
        System.out.printf("Trap coverage: %.2f\n", trapsMatched.getCount() / (double)totalTraps * 100d);

        if (sortedSslogMessagesByCiscoTag.size() > 0) {
            System.out.println("## Syslog Messages");
            sortedSslogMessagesByCiscoTag.forEach((key, value) -> System.out.printf("1. %s (count=%d, lastId=%s)\n", key, value, lastEventIdForSyslogMessageWithCiscoTag.get(key)));
        }

        if (untaggedSyslogMessages.size() > 0) {
            System.out.println("### Untagged Syslog Messages");
            untaggedSyslogMessages.stream()
                    // Limit these to avoid flooding the console
                    .limit(100)
                    .forEach((key) -> System.out.printf("1. %s\n", key));
        }

        if (sortedTrapsByTrapTypeOid.size() > 0) {
            System.out.println();
            System.out.println("## SNMP Traps");
            sortedTrapsByTrapTypeOid.forEach((key, value) -> System.out.printf("1. %s (count=%d, lastId=%s)\n", key, value, lastEventIdForTrapWithTypeOid.get(key)));
        }
    }
}
