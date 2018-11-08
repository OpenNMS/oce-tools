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

package org.opennms.oce.tools.onms.model.matchers;

import java.util.Objects;

import org.opennms.oce.tools.common.TrapEvent;
import org.opennms.oce.tools.onms.model.api.SyslogEvent;
import org.snmp4j.smi.OID;

import com.google.common.base.Preconditions;

public class SnmpTrapEventMatcher implements EventMatcher {
    private final OID enterprise;
    private final Integer generic;
    private final Integer specific;

    public SnmpTrapEventMatcher(OID enterprise, Integer generic, Integer specific) {
        this.enterprise = enterprise;
        this.generic = generic;
        this.specific = specific;
        Preconditions.checkArgument(enterprise != null || generic != null || specific != null,
                "Enterprise, generic or specific must be set.");
    }

    @Override
    public boolean matches(TrapEvent trapEvent) {
        if (enterprise != null && !Objects.equals(trapEvent.getEnterpriseOid(), enterprise)) {
            return false;
        } else if (generic != null && !Objects.equals(trapEvent.getGeneric(), generic)) {
            return false;
        } else if (specific != null && !Objects.equals(trapEvent.getSpecific(), specific)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean matches(SyslogEvent syslogEvent) {
        return false;
    }

    @Override
    public void visit(EventMatcherVisitor visitor) {
        visitor.visitSnmpTrapEventMatcher(this);
    }

    public OID getEnterprise() {
        return enterprise;
    }

    public Integer getGeneric() {
        return generic;
    }

    public Integer getSpecific() {
        return specific;
    }
}
