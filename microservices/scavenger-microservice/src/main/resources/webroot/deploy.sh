# #!/bin/bash
# echo "==> Building the application"
# bower install && npm install
# polymer build
#
# # sed -i -e "s/return'ws:\/\/localhost:8080\/game'/return location.protocol==='https:'?'wss:\/\/'+location.hostname+'\/game':'ws:\/\/'+location.hostname+'\/game'/g" build/es5-bundled/src/sh-app/sh-app.html
# sed -i -e "s/'ws:\/\/localhost:8080\/game'/'wss:\/\/scavenger-hunt-microservice-scavenger-hunt-microservice.apps.summit-aws.sysdeseng.com\/game'/g" build/es5-bundled/src/sh-app/sh-app.html
# rm build/es5-bundled/src/sh-app/sh-app.html-e
#
# export APP_NAME=scavenger-hunt-game
# echo "==> Creating build and pushing bits"
# oc new-build --binary --name=${APP_NAME} -l app=${APP_NAME}
# oc start-build ${APP_NAME} --from-dir=. --follow
#
# echo "==> Creating deployment config, service and route"
# oc apply -f openshift/deployment.yaml
# oc apply -f openshift/service.yaml
# oc apply -f openshift/route.yaml
