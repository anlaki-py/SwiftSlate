Run BASE_VERSION=$(grep "val baseVersion" app/build.gradle.kts | sed 's/.*"\([^"]*\)".*/\1/')
  BASE_VERSION=$(grep "val baseVersion" app/build.gradle.kts | sed 's/.*"\([^"]*\)".*/\1/')
  
  if [[ "refs/heads/master" == refs/tags/v* ]]; then
    TAG="master"
    CREATE_TAG=false
  else
    PATCH=$(git tag -l "v${BASE_VERSION}.*" | grep -v beta | sed "s/v${BASE_VERSION}\.//" | sort -n | tail -1)
    NEXT=$((${PATCH:--1} + 1))
    TAG="v${BASE_VERSION}.${NEXT}"
    CREATE_TAG=true
  fi
  
  VERSION_NAME="${TAG#v}"
  VERSION_CODE=$(git rev-list --count HEAD)
  
  echo "tag=$TAG" >> "$GITHUB_OUTPUT"
  echo "version_name=$VERSION_NAME" >> "$GITHUB_OUTPUT"
  echo "version_code=$VERSION_CODE" >> "$GITHUB_OUTPUT"
  echo "create_tag=${CREATE_TAG}" >> "$GITHUB_OUTPUT"
  shell: /usr/bin/bash --noprofile --norc -e -o pipefail {0}
  env:
    GRADLE_OPTS: -Dorg.gradle.jvmargs="-Xmx3g -XX:+UseG1GC"
Error: Process completed with exit code 1.
