#!/bin/bash
set -e

source ./microservice-env.sh

export DIR="scavenger-microservice"
export CONFIG="openshift/aws"
export APP_NAME="scavenger-hunt-microservice"

cd "${DIR}"

# Detect on which cluster we are running
PROJECT=$(oc project)
if [[ $PROJECT = *"summit-gce"* ]]; then
    info "[Game Server] Using GCE configuration" 
    CONFIG="openshift/gce"
elif [[ $PROJECT = *"summit-azr"* ]]; then
    info "[Game Server] Using AZURE configuration" 
    CONFIG="openshift/azr"
else 
    info "[Game Server] Using AWS configuration" 
    CONFIG="openshift/aws"
fi

info "[Game Server] Using config: ${CONFIG}"

if oc delete configmap scavenger-config; then 
    info "[Game Server] Configmap scavenger-config deleted"
fi

echo -e "🔧 [Game Server] Creating config map"
oc create configmap scavenger-config --from-file ${CONFIG}

echo -e "🔧 [Game Server] Creating build if needed"
if oc new-build --binary --name=${APP_NAME} -l app=${APP_NAME}; then 
    info "[Game Server] New build ${APP_NAME} created"
fi

echo -e "🔧 [Game Server] Building the game frontend"
cd src/main/resources/webroot || exit
bower install && npm install
polymer build
cd ../../../../  || exit

echo -e "🔧 [Game Server] Building app"
mvn clean dependency:copy-dependencies compile -DincludeScope=runtime > /tmp/microservice-a-build.log

echo -e "🔧 [Game Server] Triggering build"
oc start-build ${APP_NAME} --from-dir=. --follow

echo -e "🔧  [Game Server] Creating deployment config, service and route if needed"

if oc apply -f openshift/deployment.yaml; then 
    info "[Game Server] Deployment created for ${APP_NAME}"
fi
if oc apply -f openshift/mail-secret.yaml; then 
    info "[Game Server] Mail secrets created for ${APP_NAME}"
fi
if oc apply -f openshift/service.yaml; then 
    info "[Game Server] Service created for ${APP_NAME}"
fi
if oc apply -f openshift/route.yaml; then 
    info "[Game Server] Route created for ${APP_NAME}"
fi

cd ..  || exit
echo -e "🥃  [Game Server] Done deploying Microservice A (Game Server)"
