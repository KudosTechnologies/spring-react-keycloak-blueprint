#!/bin/bash

declare -A DOCKERFILE_PATHS
DOCKERFILE_PATHS["kudconnect-keycloak"]="../KudconnectKeycloak.Dockerfile"


function stop_service {
  local SERVICE_NAME=$1
  echo "Stopping service: $SERVICE_NAME..."
  docker-compose stop $SERVICE_NAME
}

function build_image {
  local SERVICE_NAME=$1
  local DOCKERFILE=${DOCKERFILE_PATHS[$SERVICE_NAME]}
  if [ -z "$DOCKERFILE" ]; then
    echo "Dockerfile path not found for service: $SERVICE_NAME"
    exit 1
  fi
  echo "Rebuilding image for service: $SERVICE_NAME..."
  docker build -t $SERVICE_NAME -f $DOCKERFILE ../..
}

function start_service {
  local SERVICE_NAME=$1
  local REBUILD=$2
  if [ "$REBUILD" == "--rebuild" ]; then
    build_image $SERVICE_NAME
  fi
  if [ -z "$SERVICE_NAME" ]; then
    echo "Starting all services..."
    docker-compose up -d
  else
    echo "Starting service: $SERVICE_NAME..."
    docker-compose up -d $SERVICE_NAME
  fi
}

function restart_service {
  local SERVICE_NAME=$1
  local REBUILD=$2
  stop_service $SERVICE_NAME
  start_service $SERVICE_NAME $REBUILD
  echo "Service $SERVICE_NAME has been restarted."
}


# Main part of the script
ACTION=$1
SERVICE_NAME=$2
REBUILD_OPTION=$3

if [ "$ACTION" == "start" ]; then
  start_service $SERVICE_NAME $REBUILD_OPTION
elif [ "$ACTION" == "restart" ]; then
  if [ -z "$SERVICE_NAME" ]; then
    echo "Service name must be provided for restart."
    exit 1
  fi
  restart_service $SERVICE_NAME $REBUILD_OPTION
elif [ "$ACTION" == "delete" ]; then
  stop_service $SERVICE_NAME
  echo "Removing containers for service: $SERVICE_NAME..."
  docker-compose rm -f $SERVICE_NAME
  echo "Service $SERVICE_NAME has been stopped and containers have been removed."
else
  echo "Usage: $0 start [service-name] [--rebuild] to start services"
  echo "       $0 restart <service-name> [--rebuild] to restart a specific service"
  echo "       $0 delete <service-name> to delete a specific service"
  exit 1
fi