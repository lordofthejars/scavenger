#!/bin/bash
set -e

source "./openwhisk-env.sh"

export DIR="function-b-alt"
export INFINISPAN_URL="jdg-app-hotrod.datagrid.svc"
export FETCH_ACTION_NAME="fetchImage"
export PERSIST_ACTION_NAME="persistScore"
export SCORE_ACTION_NAME="scoreImage"
export SCORE_ACTION_MEMORY="3072"
export SCORE_ACTION_IMAGE="imagescorer:rhdemo"
export SEQUENCE_NAME="imageSequence"
export TRIGGER_NAME="functionBTrigger"
export RULE_NAME="sequenceRule"
# Make sure we're deploying the right code every time - bump this when
# making changes
export COMMIT="13c14709c75fd1e5fe76005c11a4b8e2b6e4b91a"

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

echo "🔧 [Score-Logic]  Building action"
mvn clean package -DskipTests

echo "🔧 [Score-Logic]  Creating action ${FETCH_ACTION_NAME}"
wsk -i action update ${FETCH_ACTION_NAME} target/ayers-1.0-SNAPSHOT.jar \
    --main com.redhat.summit2018.FunctionB1 \
    -p jdg-url ${INFINISPAN_URL}

echo "🔧 [Score-Logic]  Creating action ${PERSIST_ACTION_NAME}"
wsk -i action update ${PERSIST_ACTION_NAME} target/ayers-1.0-SNAPSHOT.jar \
    --main com.redhat.summit2018.FunctionB2 \
    -p jdg-url ${INFINISPAN_URL}

cd ..

echo "🔧 [Score-Logic]  Creating action ${SCORE_ACTION_NAME}"
wsk -i action update ${SCORE_ACTION_NAME} --docker ${SCORE_ACTION_IMAGE} \
    --memory ${SCORE_ACTION_MEMORY}

echo "🔧 [Score-Logic]  Creating sequence ${SEQUENCE_NAME}"
wsk -i action update ${SEQUENCE_NAME} \
    --sequence ${FETCH_ACTION_NAME},${SCORE_ACTION_NAME},${PERSIST_ACTION_NAME}

echo "🔧 [Score-Logic]  Create disabled rule ${RULE_NAME}"
delete "rule" "${RULE_NAME}"
wsk -i rule create ${RULE_NAME} ${TRIGGER_NAME} ${SEQUENCE_NAME}
wsk -i rule enable ${RULE_NAME}

echo -e "🥃  Done deploying scoring sequence"
