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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.opennms.oce.tools.onms.model.api.AlarmDefinition;
import org.opennms.oce.tools.onms.model.api.EventDefinition;
import org.opennms.oce.tools.onms.model.matchers.EventMatcherVisitor;
import org.opennms.oce.tools.onms.model.matchers.SnmpTrapEventMatcher;
import org.opennms.oce.tools.onms.model.matchers.SyslogEventRegexMatcher;
import org.opennms.oce.tools.onms.model.matchers.SyslogEventSubstringMatcher;
import org.opennms.oce.tools.onms.model.v1.SnmpTrapAlarmDefinitions;
import org.opennms.oce.tools.onms.model.v1.SyslogAlarmDefinitions;
import org.snmp4j.smi.OID;

public class KemConfigGenerator {

    public void generateKemConfig() {
        final List<AlarmDefinition> allAlarmDefs = new ArrayList<>();
        allAlarmDefs.addAll(SnmpTrapAlarmDefinitions.DEFS);
        allAlarmDefs.addAll(SyslogAlarmDefinitions.DEFS);

        final Set<IncludeTrapEntry> trapIncludes = new LinkedHashSet<>();
        final Set<IncludeSyslog> includeSyslogsContaining = new LinkedHashSet<>();
        final Set<IncludeSyslog> includeSyslogsMatching = new LinkedHashSet<>();

        for (AlarmDefinition alarmDef : allAlarmDefs) {
            for (EventDefinition eventDef : alarmDef.getEventDefinitions()) {
                eventDef.getMatcher().visit(new EventMatcherVisitor() {
                    @Override
                    public void visitSnmpTrapEventMatcher(SnmpTrapEventMatcher m) {
                        trapIncludes.add(new IncludeTrapEntry(eventDef.getUEI(), m.getEnterprise(), m.getGeneric(), m.getSpecific()));
                    }

                    @Override
                    public void visitSyslogEventSubstringMatcher(SyslogEventSubstringMatcher m) {
                        includeSyslogsContaining.add(new IncludeSyslog(eventDef.getUEI(), m.getSubstring()));
                    }

                    @Override
                    public void visitSyslogEventRegexMatcher(SyslogEventRegexMatcher m) {
                        includeSyslogsMatching.add(new IncludeSyslog(eventDef.getUEI(), m.getPattern().toString()));
                    }
                });
            }
        }

        System.out.println("traps:\n" +
                "  enabled: true\n" +
                "  location-override: Default\n" +
                "  source-topic: OpenNMS.Sink.Trap\n" +
                "  target-topic: OpenNMS.Sink.Trap\n" +
                "  include-traps-with:");
        for (IncludeTrapEntry trapInclude : trapIncludes) {
            System.out.printf("%s", trapInclude);
        }

        System.out.println("syslog:\n" +
                "  enabled: true\n" +
                "  location-override: Default\n" +
                "  source-topic: OpenNMS.Sink.Syslog\n" +
                "  target-topic: OpenNMS.Sink.Syslog\n" +
                "  exclude-messages-containing:\n" +
                "    - \"%ASA-\"\n" +
                "  include-messages-containing:");
        for (IncludeSyslog syslogInclude : includeSyslogsContaining) {
            System.out.printf("%s", syslogInclude);
        }
        System.out.println("  include-messages-matching:");
        for (IncludeSyslog syslogInclude : includeSyslogsMatching) {
            System.out.printf("%s", syslogInclude);
        }
    }

    private static class IncludeSyslog {
        private final String uei;
        private final String match;

        public IncludeSyslog(String uei, String match) {
            this.uei = Objects.requireNonNull(uei);
            this.match = Objects.requireNonNull(match);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("    # ");
            sb.append(uei);
            sb.append("\n    - '");
            sb.append(match);
            sb.append("'\n");
            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncludeSyslog that = (IncludeSyslog) o;
            return Objects.equals(uei, that.uei) &&
                    Objects.equals(match, that.match);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uei, match);
        }
    }

    private static class IncludeTrapEntry {
        private final String uei;
        private final OID enterprise;
        private final Integer generic;
        private final Integer specific;

        public IncludeTrapEntry(String uei, OID enterprise, Integer generic, Integer specific) {
            this.uei = Objects.requireNonNull(uei);
            this.enterprise = enterprise;
            this.generic = generic;
            this.specific = specific;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("    # ");
            sb.append(uei);
            sb.append("\n    - ");

            boolean needsPrefix = false;

            if (enterprise != null) {
                sb.append("enterprise: ");
                sb.append(enterprise);
                sb.append("\n");
                needsPrefix = true;
            }

            if (generic != null) {
                if (needsPrefix) {
                    sb.append("      ");
                }
                sb.append("generic: ");
                sb.append(generic);
                sb.append("\n");
            }

            if (specific != null) {
                if (needsPrefix) {
                    sb.append("      ");
                }
                sb.append("specific: ");
                sb.append(specific);
                sb.append("\n");
            }

            return sb.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncludeTrapEntry that = (IncludeTrapEntry) o;
            return Objects.equals(uei, that.uei) &&
                    Objects.equals(enterprise, that.enterprise) &&
                    Objects.equals(generic, that.generic) &&
                    Objects.equals(specific, that.specific);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uei, enterprise, generic, specific);
        }
    }



}
