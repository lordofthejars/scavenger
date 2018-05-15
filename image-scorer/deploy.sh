#!/bin/bash
set -e
source "./image-scorer-env.sh"

if oc get dc | grep yolo; then 
    info "Restarting yolo"
    oc rollout latest yolo    
else 
    info "Deployment created"
    oc apply -f deployment.yaml
fi

if oc apply -f service.yaml; then 
    info "Service created"
else 
    info "Service already existing"
fi

oc project openwhisk

if oc apply -f imagestream.yaml; then
    info "Creating imagestream"
else
    info "Imagestream already exists"
fi

oc project "${OS_PROJECT_NAME}"
