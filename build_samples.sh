#!/usr/bin/env bash

OUTPUT_DIR="test/java"
PROTO_RESOURCES_DIR="resources/proto"

mkdir -p ${OUTPUT_DIR}

protoc --proto_path=${PROTO_RESOURCES_DIR} --java_out=${OUTPUT_DIR} ${PROTO_RESOURCES_DIR}/*.proto
