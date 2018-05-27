#!/bin/bash
set -e

source "./openwhisk-env.sh"

export FEED_PROVIDER_DIR="infinispan-feed"
export FEED_ACTION_DIR="infinispan-feed-action"
export FN_DIR="function-c"
export MODEL_ENDPOINT="http://yolo.imagescorer.svc:8080/v2/yolo"
export INFINISPAN_URL="jdg-app-hotrod.datagrid-stage.svc"
export ACTION_NAME="game-logic"
export TRIGGER_NAME="ayers-trigger"
export RULE_NAME="game-rules"

function waitPodIsRunning {
  for (( i=0; i<120; ++i )); do
    state=$(oc get pods | grep "infinispan" | grep -v "deploy" | grep -v "s2i" | grep -v "wskinvoker" | awk '{print $3}')

    if [ "Running" = "${state}" ] ; then {
      echo -e "✔️  Pod is running"
      return
    } else {
      echo -e "⚙️  Pod is not running: ${state}"
      sleep 3
    }
    fi
  done    
}

echo -e "🔧  [Infinispan Feed] Deploying Infinispan Feed"
cd "${FEED_PROVIDER_DIR}"
echo -e "🔧  [Infinispan Feed] Building..."
oc project openwhisk
if oc apply -f openshift/volume-claim.yaml; then
  echo -e "🔧  [Infinispan Feed] Volument declared for the infinispan feed"
fi
mvn clean fabric8:deploy -DskipTests > /tmp/infinispan-feed-build.log
waitPodIsRunning
cd ..

echo "🔧  [Infinispan Feed Action] Deploying Infinispan Feed Action"
cd "${FEED_ACTION_DIR}"
echo "🔧  [Infinispan Feed Action] Building..."
mvn clean package -DskipTests -Dexec.skip=true > /tmp/infinispan-feed-action-build.log
echo "🔧  [Infinispan Feed Action] Creating action"
wsk -i action update -a feed true infinispan-feed \
  target/infinispan-feed-action.jar \
  --main org.workspace7.openwhisk.InfinispanFeedAction \
  -p feedActionURL http://infinispan-feed.openwhisk.svc:8080
echo "🔧  [Infinispan Feed Action] Deleting and creating trigger"
delete "trigger" "object-written"
wsk -i trigger create object-written \
  --feed infinispan-feed \
  -p hotrod_server_host ${INFINISPAN_URL} \
  -p hotrod_port 11222 \
  -p cache_name objects
cd ..

echo "🔧  [Forwarder] Deploying Function C"
cd "${FN_DIR}"
echo "🔧  [Forwarder] Building..."
mvn clean package -DskipTests > /tmp/function-c-build.log
echo "🔧  [Forwarder] Deploying action"
wsk -i action update compute-score \
  target/fn-c.jar \
  --main fn.dg.os.fnc.CalculateScoresAction \
  -p microservice-endpoint scavenger-hunt-microservice.scavenger-hunt-microservice.svc:8080
echo "🔧  [Forwarder] Deploying and enabling rule"
delete "rule" "score-rule"
wsk -i rule create score-rule object-written compute-score
wsk -i rule enable score-rule
cd ../..

echo -e "🥃  Done deploying function C"
