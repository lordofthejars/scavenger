#!/usr/bin/env bash
mkdir -p target/gatling/reports
gatling.sh \
    -rf target/gatling/reports \
    -sf src/test/gatling

