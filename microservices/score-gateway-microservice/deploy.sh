#!/usr/bin/env bash

set -e -x

echo "Be sure to be connected to the openshift cluster"
export PROJECT_NAME=scavenger-hunt-microservice
export APP=score-gateway

oc project ${PROJECT_NAME}

echo -e "Creating build if needed"
if oc new-build --binary --name=${APP} -l app=${APP}; then
    info "[Game Server] New build ${APP} created"
fi

echo "Building app"
mvn clean package -DskipTests=true;

echo -e "Triggering build"
oc start-build ${APP} --from-dir=. --follow

echo -e "Creating deployment config, service and route if needed"

if oc apply -f openshift/deployment.yaml; then
    echo -e "Deployment created for ${APP}"
fi
if oc apply -f openshift/service.yaml; then
    echo -e "Service created for ${APP}"
fi
if oc apply -f openshift/route.yaml; then
    echo -e "Route created for ${APP}"
fi

