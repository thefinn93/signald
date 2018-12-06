#!/usr/bin/env bash
set -euo pipefail

while read upgrade; do
  git checkout "$CI_COMMIT_REF_NAME"

  GROUP=$(echo "$upgrade" | jq -r .group)
  NAME=$(echo "$upgrade" | jq -r .name)
  CURRENT=$(echo "$upgrade" | jq -r .current)
  LATEST=$(echo "$upgrade" | jq -r .latest)

  BRANCH="automated-upgrade/${GROUP}-${NAME}"

  # Check out the branch
  if ! git checkout "$BRANCH" 2> /dev/null; then
    echo "Creating branch $BRANCH"
    git checkout -b "$BRANCH"
  else
    git merge "$CI_COMMIT_REF_NAME"
  fi

  echo "Editing build.gradle to upgrade to $GROUP:$NAME:$LATEST"
  sed -i "s#compile '$GROUP:$NAME:.*'#compile '$GROUP:$NAME:$LATEST'#g" build.gradle

  git commit -m "autocommit: Upgrade $GROUP:$NAME to $LATEST" build.gradle || echo "Nothing to commit, continuing"

  git push -u origin "$BRANCH"

  existing=$(curl -sH "Private-Token: $GITLAB_TOKEN" "https://git.callpipe.com/api/v4/projects/92/merge_requests?source_branch=${BRANCH}&target_branch=${CI_COMMIT_REF_NAME}&state=opened" | jq length)
  if [[ "$existing" == "0" ]]; then
    echo "Creating a merge request for ${BRANCH} -> ${CI_COMMIT_REF_NAME}"
    curl -sH "Private-Token: $GITLAB_TOKEN" \
        -o /dev/null \
        -d "source_branch=${BRANCH}" \
        -d "target_branch=${CI_COMMIT_REF_NAME}" \
        -d "title=Upgrade dependency ${GROUP}:${NAME}" \
        -d "description=Looks like there's a new version of ${NAME}. Consider upgrading." \
        -d "assignee_id=${GITLAB_USER_ID}" \
        -d "labels=Automatically+Generated" \
        "https://git.callpipe.com/api/v4/projects/92/merge_requests"
  else
    echo "A merge requests from ${BRANCH} to ${CI_COMMIT_REF_NAME} already exists."
  fi
done
