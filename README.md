# Deployment scripts

This repository contain the set of scripts to provision the demo.

## Deploy functions

Be sure do be connected to your OpenShift cluster. OpenWhisk must be installed already and the `redhat` user created.

```bash
cd functions
./deploy-functions.sh
```

## Deploy microservices

```bash
cd microservices
./deploy-microservice-a.sh
./deploy-microservice-b.sh
```

## Deploy Yolo (image scorer)

```bash
cd image-scorer
./deploy.sh
```