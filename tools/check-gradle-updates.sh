#!/usr/bin/env bash
set -euo pipefail
if [[ "$CI_COMMIT_REF_NAME" == "feature/autoupdates" ]]; then
  echo "Branch detected as $CI_COMMIT_REF_NAME, enabling debug mode"
  set -x
fi

while read upgrade; do
  echo "Processing upgrade: $upgrade"
  git reset --hard
  git checkout "$CI_COMMIT_REF_NAME"
  git pull origin "$CI_COMMIT_REF_NAME"

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
    if git pull origin "$BRANCH"; then
      git merge -X theirs "$CI_COMMIT_REF_NAME"
    else
      git checkout "$CI_COMMIT_REF_NAME"
      git branch -D "$BRANCH"
      git checkout -b "$BRANCH"
    fi
  fi

  echo "Editing build.gradle to upgrade $GROUP:$NAME from $CURRENT to $LATEST"
  sed -i "s#compile '$GROUP:$NAME:.*'#compile '$GROUP:$NAME:$LATEST'#g" build.gradle


  if ! git diff --quiet; then
    git commit -m "autocommit: Upgrade $GROUP:$NAME to $LATEST" build.gradle || echo "Nothing to commit, continuing"

    if ! git diff --quiet "$CI_COMMIT_REF_NAME" ; then
      git push origin "$BRANCH"
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
    else
      echo "$BRANCH is up to date with $CI_COMMIT_REF_NAME, not opening an MR"
    fi
  else
    echo "No changes made."
  fi
done
