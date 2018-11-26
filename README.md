# OpenNMS Correlation Engine (OCE) Tools

This repository contains a collection of tools and utilities to help develop, test, and validate the functionality of the OCE.

# Building

## Requirements

Before building ensure you have built and installed (`mvn install`) the artifacts from:
* The `master` branch of [OCE](https://github.com/OpenNMS/oce)
* The `features/sextant` branch of [OpenNMS](https://github.com/OpenNMS/opennms/tree/features/sextant)
* The `features/events/syslog` project in [OpenNMS](https://github.com/OpenNMS/opennms)

## Compiling

Compile the project with:

```
mvn clean package
```

# Usage

Most of the commands interact with data stored in Elasticsearch.

```sh
mkdir -p ~/.oce
echo 'clusters:
  localhost:
    url: http://localhost:9200
    read-timeout: 120000
    conn-timeout: 30000' > ~/.oce/es-config.yaml
```

You can run the tool and enumerate the available commands using:
```
java -jar main/target/oce-tools.jar --help
```

In the remainder of the document, we'll alias this as `oce-tools` i.e.:
```
alias oce-tools="java -jar ~/git/oce-tools/main/target/oce-tools.jar"
```

## Commands

### cpn-report

This command can be used to find and automatically download .csv reports from CPN.
It assumes that the `Detailed Traps`, `Detailed Service Events`, `Detailed Syslogs` and `Detailed Tickets` reports are scheduled to be executed on a daily basis at the same times.

Example usage:
```
oce-tools cpn-report --url https://ana-cluster:6081/ana --username onms --password onms --output /tmp/cpn-exports/ --from "Nov 4 2018"
```

#### BQL

This command can also be leverage to run arbitrary BQL command (synchronously) against CPN i.e.
```
echo '<?xml version="1.0" encoding="UTF-8"?>
<command name="DeviceList"></command>' > /tmp/cpn.get.device.list.xml
oce-tools cpn-report --url https://ana-cluster:6081/ana --username onms --password onms --execute /tmp/cpn.get.device.list.xml
```

### cpn-csv-import

This command imports a series of .csv files exported by CPN into Elasticsearch.

All of the documents have unique IDs, so you re-import the same data many times without worrying about duplicates.

Example usage:
```
oce-tools cpn-csv-import --source="/tmp/cpn-exports/" --timezone="America/Chicago"
```

### cpn-oce-export

This command will generate .xml file suitable for importing with the JAXB datasource in OCE.

These files can be then be used for simulations and comparative analysis.

Example usage:
```
oce-tools cpn-oce-export --from "Nov 4 2018" --to "Nov 5 2018" --output /tmp
```

#### Using the data for simulations

Once the data has been exported, you can adapt the following commands to:
1. Simulate the situation generation from the same list of alarms
1. Compare the generated situations to the tickets and calculate a score

```
feature:install oce-features-shell oce-engine-cluster
oce:process-alarms -i /tmp/cpn.alarms.xml -o /tmp/oce.situations.xml -e cluster
oce:score-situations -s peer /tmp/cpn.situations.xml /tmp/oce.situations.xml
```

### cpn-opennms-event-definition-audit

Validates whether or not this tool has knowledge of the event definitions for all of the CPN events that have occurred in the given time range.

This can be used to validate whether or not the tool knows how to generate similar events for all of the events that have actually occurred.

Example usage:
```
oce-tools cpn-opennms-event-definition-audit --from "Nov 4 2018" --to "Nov 5 2018"
```

### cpn-opennms-event-handling-audit

Generate SNMP traps and syslog messages for all of the known types and verifies that the corresponding alarms are created in OpenNMS.

When used in conjunction with the `cpn-opennms-event-definition-audit` this helps ensure that the system is capable of handling all of the known event types.

Example usage:
```
oce-tools cpn-opennms-event-handling-audit \
  --node-a-label localhost --node-a-id 1 --node-a-ifindex 2 --node-a-ifdescr ens33 \
  --node-z-label localhost --node-z-id 1 --node-z-ifindex 1 --node-z-ifdescr lo \
  --opennms-host localhost
```

### cpn-opennms-event-materialization-audit

Match CPN events (syslogs & traps) to OpenNMS events for the given time range.
This can be used to help ensure that both systems are receiving the same events.

This requires OpenNMS events and CPN events to be available in the same Elastisearch instance.

Example usage:
```
oce-tools cpn-opennms-event-materialization-audit --from "Nov 4 2018" --to "Nov 5 2018"
```

### cpn-opennms-situation-materialization-audit

Match CPN tickets (particularly the syslogs & traps alarms they contain) to OpenNMS situations for the given time range.
This can be used to help validate whether or not both systems are generating similar situations/tickets.

This requires OpenNMS events, OpenNMS alarms, CPN events and CPN tickets to be available in the same Elastisearch instance.

Example usage:
```
oce-tools cpn-opennms-situation-materialization-audit --from "Oct 25th 2018" --to "Oct 26th 2018"
```

### generate-kem-config

Generate the configuration for the KEM tool based on the known traps and syslog messages.

Example usage:
```
oce-tools generate-kem-config
```

### cpn-opennms-syslog-audit

Reports syslog messages that were not parsed correctly.

Example usage:
```
oce-tools cpn-opennms-syslog-audit --from "Oct 1 2018" --to "Nov 1 2018"
```