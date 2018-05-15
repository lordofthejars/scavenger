# Clearing the caches

The application contains a specific verticle / script to clear all the caches. Do not use in production ;-)

## Usage

```bash
$ oc get pods
NAME                                  READY     STATUS      RESTARTS   AGE
scavenger-hunt-microservice-1-build   0/1       Completed   0          12h
scavenger-hunt-microservice-2-build   0/1       Completed   0          1m
scavenger-hunt-microservice-2-jkmm8   1/1       Running     0          30s

$ oc rsh scavenger-hunt-microservice-2-jkmm8 
```

You are now connected to the pod. the following script must run from this pod:

```bash
cd /deployments
java -cp libs/*:classes io.vertx.core.Launcher run me.escoffier.keynote.scripts.ClearAllCaches -Dvertx.cacheDirBase=/tmp
```

Once executed, disconnect using the `exit` command.

