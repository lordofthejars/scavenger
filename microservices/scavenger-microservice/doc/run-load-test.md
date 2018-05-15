# Run load tests

This document explains how to run load tests. The load test is implemented in `src/test/gatling/ConnectionSimulation.scala`.

## Common options

Regardless of how you run the load test, there are some options you
can set with the `JAVA_OPTS` environment variable to control the
test scenario:

  * `users` [20] the number of simulated users
  * `uploads` [5] the number of images uploaded per user
  * `host` [AWS] either a hostname or a valid alias (AWS, GCE, AZR)

For example:
```
JAVA_OPTS="-Dusers=10 -Duploads=3 -Dhost=AWS"
```

## Running directly on your local machine

### Prerequisites

1. Install Gatling (https://gatling.io/) and add the `bin` directory from Gatling in your system `$PATH`
2. Install Scala (not totally sure it's required)
3. Clone this repository

### Run the load test

```bash
JAVA_OPTS="-Dhost=AWS -Dusers=250" ./run-gatling.sh
```

## Running via Docker

```bash
docker build -f Dockerfile.gatling . -t gatling
docker run -v /tmp/results:/results -it -e JAVA_OPTS="-Dhost=AWS" --rm gatling
```
Results from the run may then be found beneath `/tmp/results`

## Running via OpenShift

The docker image is available on
[DockerHub](https://hub.docker.com/r/projectodd/gatling/) so that you
can initiate load from a remote OpenShift cluster:

```bash
oc run load-test --restart=Never --image=projectodd/gatling --env="JAVA_OPTS=-Dusers=500 -Duploads=5 -Dhost=AWS"
```

# What does a simulated user do:

1. Connect
2. Repeat `uploads` times:
    1. Upload a picture
    2. Wait for the associated score frames
3. Disconnect
4. Reconnect
5. Disconnect
