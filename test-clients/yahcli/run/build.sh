#! /bin/sh
TAG=${1:-'0.1.12'}

cd ..
mvn clean compile assembly:single@yahcli-jar
cd -
run/refresh-jar.sh

docker build -t yahcli:$TAG .
