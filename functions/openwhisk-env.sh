#!/bin/bash
set -e

# Colors are important
export RED='\033[0;31m'
export NC='\033[0m' # No Color
export YELLOW='\033[0;33m'
export BLUE='\033[0;34m'

export TOKEN="7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"

function info {
    echo -e "  ${BLUE} $1 ${NC}"
}

###
# AWS Summit Demo: openwhisk-openwhisk.apps.summit-aws.sysdeseng.com
# GCP Summit Demo: openwhisk-openwhisk.apps.summit-gce.sysdeseng.com
# Azure Summit Demo: openwhisk-openwhisk.apps.summit-azr.sysdeseng.com
###
# Detect on which cluster we are running
PROJECT=$(oc project)
export HOST="openwhisk-openwhisk.apps.summit-aws.sysdeseng.com"
if [[ $PROJECT = *"summit-gce"* ]]; then
    info "[OpenWhisk] Using GCE" 
    HOST="openwhisk-openwhisk.apps.summit-gce.sysdeseng.com"
elif [[ $PROJECT = *"summit-azr"* ]]; then
    info "[OpenWhisk] Using Azure" 
    HOST="openwhisk-openwhisk.apps.summit-azr.sysdeseng.com"
elif [[ $PROJECT = *"workspace7"* ]]; then
    info "[OpenWhisk] Using Workspace 7"
    HOST="openwhisk-openwhisk.apps.workspace7.org"
    TOKEN="789c46b1-71f6-4ed5-8c54-816aa4f8c502:F082iqnYlvcQmdMAYBAfvGYxlVqRgYDDcQiLtXE1Ad02HOqmxCawHRlfoST8Rh2S"
fi



echo "Log in as 'redhat' on '${HOST}'"
wsk property set --auth "${TOKEN}" --apihost "${HOST}"

function warning {
    echo -e "  ${YELLOW} $1 ${NC}"
}

function delete {
    TYPE=$1
    NAME=$2
    if wsk -i "${TYPE}" delete "${NAME}"; then
        info "Deleted ${TYPE} named ${NAME}"
    else 
        warning "Deletion of ${TYPE} named ${NAME} failed"
    fi
}