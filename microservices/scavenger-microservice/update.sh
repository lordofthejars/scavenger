#!/usr/bin/env bash
echo "Be sure to be connected to the openshift cluster"

export CONFIG=openshift/aws
export APP_NAME=scavenger-hunt-microservice

echo "==> Building the application"
echo "==> Changing directory to the webroot"
cd src/main/resources/webroot || exit
bower install && npm install
polymer build

HASH=$(git rev-parse --short HEAD)
sed -i "" 's/sh-app.html/sh-app.html?version='"$HASH"'/g' build/es5-bundled/index.html

cd ../../../../ || exit

echo "==> Back to the root directory"

oc project ${APP_NAME}

PROJECT=$(oc project)
if [[ ${PROJECT} = *"summit-gce"* ]]; then
    CONFIG="openshift/gce"
elif [[ ${PROJECT} = *"summit-azr"* ]]; then
    CONFIG="openshift/azr"
else
    CONFIG="openshift/aws"
fi

echo "Using config: ${CONFIG}"

echo "Updating config map"
oc delete configmap scavenger-config
oc create configmap scavenger-config --from-file ${CONFIG}

echo "Building app"
mvn dependency:copy-dependencies compile -DincludeScope=runtime


echo "Triggering build"
oc start-build ${APP_NAME} --from-dir=. --follow
