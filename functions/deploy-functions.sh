#!/bin/bash
set -e

./cleanup-function.sh
./deploy-function-b.sh
./deploy-sequence-score.sh
./deploy-function-c.sh

wsk -i list

echo -e "🥃  Done deploying functions"
