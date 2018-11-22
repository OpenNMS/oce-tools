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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opennms.oce.tools.cpn.ESDataProvider;
import org.opennms.oce.tools.cpn.EventUtils;
import org.opennms.oce.tools.cpn.model.EventRecord;
import org.opennms.oce.tools.cpn.model.TrapRecord;
import org.opennms.oce.tools.onms.client.ESEventDTO;
import org.opennms.oce.tools.onms.match.CpnToOnmsEventMatcher;

public class MatchCpnToOnmsEventsAudit {

    private final CpnToOnmsEventMatcher cpnToOnmsEventMatcher;
    private final Map<String,Long> matchedMessagesByHost = new LinkedHashMap<>();
    private final Map<String,Long> unmatchedMessagesByHost = new LinkedHashMap<>();
    private final Map<String,Long> matchedTrapsByType = new LinkedHashMap<>();
    private final Map<String,Long> unmatchedTrapsByType = new LinkedHashMap<>();

    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final ESDataProvider esDataProvider;

    public static class Builder {
        private ZonedDateTime start;
        private ZonedDateTime end;
        private ESDataProvider esDataProvider;

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

        public MatchCpnToOnmsEventsAudit build() {
            Objects.requireNonNull(start, "start is required");
            Objects.requireNonNull(end, "end is required");
            Objects.requireNonNull(esDataProvider, "data provider is required");
            return new MatchCpnToOnmsEventsAudit(this);
        }
    }

    public MatchCpnToOnmsEventsAudit(Builder builder) {
        this.esDataProvider = builder.esDataProvider;
        this.start = builder.start;
        this.end = builder.end;
        this.cpnToOnmsEventMatcher = new CpnToOnmsEventMatcher(esDataProvider.getEsClient().getJestClient());
    }

    public void match() throws IOException {
        esDataProvider.getSyslogRecordsInRange(start, end, records -> {
            for (EventRecord r : records) {
                if (EventUtils.isClear(r)) {
                    System.out.println("Skipping clear.");
                    return;
                }
                try {
                    Optional<ESEventDTO> event = cpnToOnmsEventMatcher.matchCpnSyslogToOnmsSyslog(r);
                    if (event.isPresent()) {
                        logMatchForLocation(r.getLocation());
                    } else {
                        logMismatchForLocation(r.getLocation());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        esDataProvider.getTrapRecordsInRange(start, end, records -> {
            for (TrapRecord r : records) {
                if (r.getTrapTypeOid() == null || "N/A".equals(r.getTrapTypeOid())) {
                    System.out.println("Skipping trap without type oid");
                    continue;
                }
                if (".1.3.6.1.6.3.1.1.5.5".equals(r.getTrapTypeOid())) {
                    // Silently skip authenticationFailure traps - there are alot of these
                    continue;
                }
                if (EventUtils.isClear(r)) {
                    System.out.println("Skipping clear.");
                    return;
                }
                try {
                    Optional<ESEventDTO> event = cpnToOnmsEventMatcher.matchCpnTrapToOnmsTrap(r);
                    if (event.isPresent()) {
                        logMatchForLocation(r.getLocation());
                        logMatchForTrapType(r.getTrapTypeOid());
                    } else {
                        logMismatchForLocation(r.getLocation());
                        logMismatchForTrapType(r.getTrapTypeOid());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        System.out.println("\n\nResult summary:");
        System.out.println("\nMatches by host:");
        matchedMessagesByHost.forEach((k,v) -> System.out.printf("%s: %d\n", k, v));
        System.out.println("\nMismatches by host:");
        unmatchedMessagesByHost.forEach((k,v) -> System.out.printf("%s: %d\n", k, v));
        System.out.println("\nTraps matched by type:");
        matchedTrapsByType.forEach((k,v) -> System.out.printf("%s: %d\n", k, v));
        System.out.println("\nTraps mismatched by type:");
        unmatchedTrapsByType.forEach((k,v) -> System.out.printf("%s: %d\n", k, v));
    }

    private void logMatchForLocation(String location) {
        matchedMessagesByHost.compute(EventUtils.getNodeLabelFromLocation(location), (k,v) -> {
            if (v == null) {
                return 1L;
            } else {
                return v+1;
            }});
    }

    private void logMismatchForLocation(String location) {
        unmatchedMessagesByHost.compute(EventUtils.getNodeLabelFromLocation(location), (k,v) -> {
            if (v == null) {
                return 1L;
            } else {
                return v+1;
            }});
    }

    private void logMismatchForTrapType(String trapTypeOid) {
        unmatchedTrapsByType.compute(trapTypeOid, (k,v) -> {
            if (v == null) {
                return 1L;
            } else {
                return v+1;
            }});
    }

    private void logMatchForTrapType(String trapTypeOid) {
        matchedTrapsByType.compute(trapTypeOid, (k,v) -> {
            if (v == null) {
                return 1L;
            } else {
                return v+1;
            }});
    }
}
