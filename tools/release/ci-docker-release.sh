#!/bin/bash
set -exuo pipefail

DOCKER_HUB_IMAGE=signaldrobot/signald

for platform in amd64 arm64-v8 arm-v7; do
    docker tag "${CI_REGISTRY_IMAGE}:${VERSION}-${platform}" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-${platform}"
    docker push --quiet "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-${platform}"
done

docker manifest create "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-amd64" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-arm64-v8" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-arm-v7"
docker manifest push "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}"

docker manifest create "docker.io/${DOCKER_HUB_IMAGE}:latest" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-amd64" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-arm64-v8" "docker.io/${DOCKER_HUB_IMAGE}:${VERSION}-arm-v7"
docker manifest push "docker.io/${DOCKER_HUB_IMAGE}:latest"

set +x # to avoid printing tokens
echo "Authenticating to docker hub..."
token=$(curl -sfd "{\"username\": \"${DOCKER_HUB_USERNAME}\", \"password\": \"${DOCKER_HUB_PASSWORD}\"}" -H "Content-Type: application/json" https://hub.docker.com/v2/users/login/ | jq -r '.token')
for platform in amd64 arm64-v8 arm-v7; do
    echo "deleting tag ${VERSION}-${platform}"
    curl --fail -s -X DELETE -H "Authorization: JWT ${token}" "https://hub.docker.com/v2/repositories/${DOCKER_HUB_IMAGE}/tags/${VERSION}-${platform}/"
done

echo -e "\e[32m"
echo "image pushed to docker.io/${DOCKER_HUB_IMAGE}:${VERSION}"
echo "also available at ${CI_REGISTRY_IMAGE}:${VERSION}"
echo -e "\e[0m"