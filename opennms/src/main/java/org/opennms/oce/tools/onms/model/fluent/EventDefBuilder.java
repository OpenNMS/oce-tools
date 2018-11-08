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

package org.opennms.oce.tools.onms.model.fluent;

import java.util.Objects;
import java.util.regex.Pattern;

import org.opennms.oce.tools.onms.model.api.EventDefinition;
import org.opennms.oce.tools.onms.model.api.EventType;
import org.opennms.oce.tools.onms.model.matchers.EventMatcher;
import org.opennms.oce.tools.onms.model.matchers.SnmpTrapEventMatcher;
import org.opennms.oce.tools.onms.model.matchers.SyslogEventSubstringMatcher;
import org.opennms.oce.tools.onms.model.matchers.SyslogEventRegexMatcher;
import org.snmp4j.smi.OID;

public class EventDefBuilder {
    private String uei;
    private EventType type;
    private String matchSyslogSubstring;
    private Pattern matchSyslogMessage;

    private boolean snmpMatch = false;
    private OID snmpEnterprise;
    private Integer snmpGeneric;
    private Integer snmpSpecific;

    public EventDefBuilder withUEI(String uei) {
        this.uei = uei;
        return this;
    }

    public EventDefBuilder withType(EventType type) {
        this.type = type;
        return this;
    }

    public EventDefBuilder matchSyslogMessage(Pattern matchSyslogMessage) {
        this.matchSyslogMessage = matchSyslogMessage;
        return this;
    }

    public EventDefBuilder matchTrap(OID enterprise, Integer generic, Integer specific) {
        this.snmpEnterprise = enterprise;
        this.snmpGeneric = generic;
        this.snmpSpecific = specific;
        snmpMatch = true;
        return this;
    }

    public EventDefinition build() {
        Objects.requireNonNull(uei, "uei");
        Objects.requireNonNull(type, "type");

        final EventMatcher matcher;
        if (snmpMatch) {
            matcher = new SnmpTrapEventMatcher(snmpEnterprise, snmpGeneric, snmpSpecific);
        } else if (matchSyslogSubstring != null) {
            matcher = new SyslogEventSubstringMatcher(matchSyslogSubstring);
        } else if (matchSyslogMessage != null) {
            matcher = new SyslogEventRegexMatcher(matchSyslogMessage);
        } else {
            throw new IllegalArgumentException("One match is required.");
        }

        return new EventDefinition() {
            @Override
            public EventType getEventType() {
                return type;
            }

            @Override
            public String getUEI() {
                return uei;
            }

            @Override
            public EventMatcher getMatcher() {
                return matcher;
            }
        };
    }


}
