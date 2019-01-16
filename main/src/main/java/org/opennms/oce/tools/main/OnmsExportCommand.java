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
import java.io.IOException;

import org.kohsuke.args4j.Option;
import org.opennms.oce.tools.onms.onms2oce.OnmsOceGenerator;
import org.opennms.oce.tools.onms.view.ESBackedOnmsDatasetViewer;
import org.opennms.oce.tools.onms.view.OnmsDatasetView;

public class OnmsExportCommand extends AbstractCommand {

    public static final String NAME = "cpn-oce-export";

    @Option(name="--from",aliases = {"-f"}, usage="From date i.e. Oct 28 2018")
    private String from;

    @Option(name="--to",aliases = {"-t"}, usage="To date i.e. Oct 29 2018")
    private String to;

    @Option(name="--situation-id", usage="Situation ID")
    private Integer situationId;

    @Option(name="--output",aliases = {"-o"}, usage="Target folder", required = true)
    private File targetFolder;

    @Option(name="--no-model",usage="Disable model generation")
    private boolean modelGenerationDisabled = false;

    public OnmsExportCommand() {
        super(NAME);
    }

    @Override
    public void doExec(Context context) throws Exception {
        final OnmsDatasetView.Builder viewBuilder = new OnmsDatasetView.Builder();
        if (from != null) {
            viewBuilder.withStartTime(CommandUtils.parseDate(from));
        }
        if (to != null) {
            viewBuilder.withEndTime(CommandUtils.parseDate(to));
        }

        // Ensure the target directory exists
        if (!targetFolder.isDirectory() && !targetFolder.mkdirs()) {
            throw new IOException("Failed to create the target directory: " + targetFolder);
        }

        final OnmsOceGenerator oceGenerator = new OnmsOceGenerator.Builder()
                .withViewer(new ESBackedOnmsDatasetViewer(context.getEsClient(), viewBuilder.build()))
                .withSituationId(situationId)
                .withTargetFolder(targetFolder)
                .withModelGenerationDisabled(modelGenerationDisabled)
                .build();
        oceGenerator.generate();
        oceGenerator.writeResultsToDisk("opennms");
    }
}