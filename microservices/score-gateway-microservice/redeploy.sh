#!/usr/bin/env bash

set -e -x

APP=score-gateway

mvn clean package -DskipTests=true; oc start-build ${APP} --from-dir=. --follow
