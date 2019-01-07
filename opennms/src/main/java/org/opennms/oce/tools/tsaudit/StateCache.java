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

import java.io.File;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Objects;

public class StateCache {

    private final File dbFile;
    private final String url;

    private final long startMs;
    private final long endMs;

    public StateCache(long startMs, long endMs) {
        this(Paths.get(System.getProperty("user.home"), ".oce", "state.db").toFile(), startMs, endMs);
    }

    public StateCache(File dbFile, long startMs, long endMs) {
        this.dbFile = Objects.requireNonNull(dbFile);
        this.startMs = startMs;
        this.endMs = endMs;

        // SQLite connection string
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

        init();
    }

    private void init() {
        // SQL statement for creating a new table
        String sql = "CREATE TABLE IF NOT EXISTS node_and_facts(\n"
                + "	start_ms long,\n"
                + "	end_ms long,\n"
                + "	cpn_hostname text,\n"
                + "	opennms_node_label text,\n"
                + "	opennms_node_id integer\n"
                + ");";

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {
            // create a new table
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean findOpennmsNodeInfo(NodeAndFacts nodeAndFacts) {
        String sql = "SELECT opennms_node_label, opennms_node_id FROM node_and_facts WHERE start_ms = ? AND end_ms = ? AND cpn_hostname = ?";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt  = conn.prepareStatement(sql)){

            pstmt.setLong(1, startMs);
            pstmt.setLong(2, endMs);
            pstmt.setString(3, nodeAndFacts.getCpnHostname());

            ResultSet rs  = pstmt.executeQuery();
            if (!rs.next()) {
                return false;
            }

            nodeAndFacts.setOpennmsNodeLabel(rs.getString("opennms_node_label"));
            Integer nodeId = rs.getInt("opennms_node_id");
            if (nodeId == 0) {
                nodeId = null;
            }
            nodeAndFacts.setOpennmsNodeId(nodeId);
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveOpennmsNodeInfo(NodeAndFacts nodeAndFacts) {
        String sql = "INSERT INTO node_and_facts(start_ms, end_ms, cpn_hostname, opennms_node_label, opennms_node_id) VALUES(?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, startMs);
                pstmt.setLong(2, endMs);
                pstmt.setString(3, nodeAndFacts.getCpnHostname());
                pstmt.setString(4, nodeAndFacts.getOpennmsNodeLabel());
                if (nodeAndFacts.getOpennmsNodeId() != null) {
                    pstmt.setInt(5, nodeAndFacts.getOpennmsNodeId());
                } else {
                    pstmt.setNull(5, Types.INTEGER);
                }

                pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}
