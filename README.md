# OutsourcedForwarding

## Build
In order to build the project run the commands below:
```bash
mvn clean install
```

## Installation
In order to install the app on ONOS run the commands below
```bash
export ONOS_ENDPOINT=<enter-onos-endpoint-here>
onos-app $ONOS_ENDPOINT install! target/outsourcedforwarding-1.0.oar
```

## Issues
Currently there is an issue in Core Services Initialization
