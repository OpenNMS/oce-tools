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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StateCacheTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void canInitializeManyTimes() throws IOException {
        File dbFile = temporaryFolder.newFile("my.db");
        StateCache stateCache = new StateCache(dbFile, 0, 1);
        stateCache = new StateCache(dbFile, 0, 2);
    }

    @Test
    public void canSaveAndLoad() throws IOException {
       StateCache stateCache = new StateCache(temporaryFolder.newFile("my.db"), 0, 1);

       NodeAndFacts nodeAndFacts = new NodeAndFacts("n1");
       // Miss
       assertThat(stateCache.findOpennmsNodeInfo(nodeAndFacts), equalTo(false));
       // Save
       stateCache.saveOpennmsNodeInfo(nodeAndFacts);
       // Hit
       assertThat(stateCache.findOpennmsNodeInfo(nodeAndFacts), equalTo(true));
    }
}
