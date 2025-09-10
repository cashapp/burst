Releasing
=========

1. Update `CHANGELOG.md`.
   1. Change the `Unreleased` header to the release version.
   2. Add a link URL to ensure the header link works.
   3. Add a new `Unreleased` section to the top.


2. Update compatibility chart at the bottom of the `README.md`.

3. Set versions:

    ```
    export RELEASE_VERSION=X.Y.Z
    export NEXT_VERSION=X.Y.Z-SNAPSHOT
    ```

4. Update versions, tag the release, and prepare for the next release.

    ```
    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$RELEASE_VERSION/g" \
      gradle.properties
    sed -i "" \
      "s/\"app.cash.burst:\([^\:]*\):[^\"]*\"/\"app.cash.burst:\1:$RELEASE_VERSION\"/g" \
      `find . -name "*.md"`

    git commit -am "Prepare version $RELEASE_VERSION"
    git tag -am "Version $RELEASE_VERSION" $RELEASE_VERSION

    sed -i "" \
      "s/VERSION_NAME=.*/VERSION_NAME=$NEXT_VERSION/g" \
      gradle.properties
    git commit -am "Prepare next development version"

    git push && git push --tags
    ```

5. Wait for [GitHub Actions][github_actions] to build and promote the release.

[github_actions]: https://github.com/cashapp/burst/actions
