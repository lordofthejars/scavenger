#!/bin/bash
oc cp scavenger-hunt-microservice/$(oc get pods | grep scavenger-hunt-microservice | grep -v build | awk '{print $1}'):/deployments/ranks.txt ranks.txt