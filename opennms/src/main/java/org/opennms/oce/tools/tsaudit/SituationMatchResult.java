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

package org.opennms.oce.tools.tsaudit;

import java.util.List;
import java.util.Objects;

public class SituationMatchResult {

    private final CanonicalSituation source;
    private final CanonicalSituation exactMatch;
    private final List<CanonicalSituation> partialMatches;

    public SituationMatchResult(CanonicalSituation source, CanonicalSituation exactMatch, List<CanonicalSituation> partialMatches) {
        this.source = Objects.requireNonNull(source);
        this.exactMatch = exactMatch;
        this.partialMatches = Objects.requireNonNull(partialMatches);
    }

    public CanonicalSituation getSource() {
        return source;
    }

    public CanonicalSituation getExactMatch() {
        return exactMatch;
    }

    public List<CanonicalSituation> getPartialMatches() {
        return partialMatches;
    }

    public boolean anyMatch() {
        return exactMatch != null || !partialMatches.isEmpty();
    }
}
