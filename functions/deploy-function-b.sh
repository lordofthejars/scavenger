#!/bin/bash
set -e

source "./openwhisk-env.sh"

export DIR="function-b"
export MODEL_ENDPOINT="http://yolo.imagescorer.svc:8080/v2/yolo"
export INFINISPAN_URL="jdg-app-hotrod.datagrid.svc"
export ACTION_NAME="functionBAction"
export TRIGGER_NAME="functionBTrigger"
export RULE_NAME="functionBRule"
# Make sure we're deploying the right code - in this case the last
# commit before image scoring as a function was merged
export COMMIT="09afc231a278dcef11efb8907eee1adc3cc373e9"

if [ ! -d "${DIR}" ] ; then
    echo "🔧 Retrieving code for function-b"
    git clone "git@github.com:rhdemo/ayers.git" "${DIR}"
fi

cd ${DIR}

set +e
git checkout $COMMIT
if [ $? -ne 0 ] ; then
    echo "🔧 Fetching latest code for function-b"
    git checkout master
    git pull
fi
set -e
git checkout $COMMIT

source environment
echo "🔧 [Score-Logic]  Building action"
mvn clean package -DskipTests

echo "🔧 [Score-Logic]  Creating action"
wsk -i action update ${ACTION_NAME} target/ayers-1.0-SNAPSHOT.jar \
    --main com.redhat.summit2018.FunctionB \
    --web=true \
    -p model-endpoint ${MODEL_ENDPOINT} \
    -p jdg-url ${INFINISPAN_URL}

echo "🔧 [Score-Logic]  Create trigger and rule"
# TODO Must keep the name as it's linked to the webhook url
delete "trigger" "${TRIGGER_NAME}"
delete "rule" "${RULE_NAME}"
wsk -i trigger create ${TRIGGER_NAME}
wsk -i rule create ${RULE_NAME} ${TRIGGER_NAME} ${ACTION_NAME}
wsk -i rule disable ${RULE_NAME}

cd ..

echo -e "🥃  Done deploying function B"

