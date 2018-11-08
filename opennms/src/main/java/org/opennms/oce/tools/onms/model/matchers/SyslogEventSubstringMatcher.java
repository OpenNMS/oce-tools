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

public class SyslogEventSubstringMatcher implements EventMatcher {

    private final String substring;

    public SyslogEventSubstringMatcher(String substring) {
        this.substring = Objects.requireNonNull(substring);
    }

    @Override
    public boolean matches(TrapEvent trapEvent) {
        return false;
    }

    @Override
    public boolean matches(SyslogEvent syslogEvent) {
        return syslogEvent.getMessage().contains(substring);
    }

    @Override
    public void visit(EventMatcherVisitor visitor) {
        visitor.visitSyslogEventSubstringMatcher(this);
    }

    public String getSubstring() {
        return substring;
    }
}
