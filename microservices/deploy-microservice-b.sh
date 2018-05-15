#!/bin/bash
set -e

source ./microservice-env.sh

export DIR="score-gateway-microservice"
export APP_NAME="score-gateway"


cd "${DIR}"  || exit

echo -e "🔧 [Score Gateway] Creating build if needed"
if oc new-build --binary --name=${APP_NAME} -l app=${APP_NAME}; then 
    info "[Score Gateway] New build ${APP_NAME} created"
fi

echo -e "🔧 [Score Gateway] Building app"
mvn clean package -DskipTests=true > /tmp/${APP_NAME}-build.log

echo -e "🔧 [Score Gateway] Triggering build"
oc start-build ${APP_NAME} --from-dir=. --follow

echo -e "🔧  [Score Gateway] Creating deployment config, service and route if needed"

if oc apply -f openshift/deployment.yaml; then 
    info "[Score Gateway] Deployment created for ${APP_NAME}"
fi
if oc apply -f openshift/service.yaml; then 
    info "[Score Gateway] Service created for ${APP_NAME}"
fi
if oc apply -f openshift/route.yaml; then 
    info "[Score Gateway] Route created for ${APP_NAME}"
fi

cd .. || exit

echo -e "🥃  [Score Gateway] Done deploying Microservice B (Score Gateway)"
