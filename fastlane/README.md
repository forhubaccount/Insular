fastlane documentation
<<<<<<< HEAD
================
=======
----

>>>>>>> dev-ci
# Installation

Make sure you have the latest version of the Xcode command line tools installed:

<<<<<<< HEAD
```
xcode-select --install
```

Install _fastlane_ using
```
[sudo] gem install fastlane -NV
```
or alternatively using `brew cask install fastlane`

# Available Actions
## Android
### android test
```
fastlane android test
```
Runs all the tests
### android beta
```
fastlane android beta
```
Submit a new Beta Build to Crashlytics Beta
### android screenshots
```
fastlane android screenshots
```
Build debug and test APK for screenshots
### android deploy
```
fastlane android deploy
```
Deploy a new version to the Google Play

----

This README.md is auto-generated and will be re-generated every time [fastlane](https://fastlane.tools) is run.
More information about fastlane can be found on [fastlane.tools](https://fastlane.tools).
The documentation of fastlane can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
=======
```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Runs all the tests

### android buildGooglePlayDebugBundle

```sh
[bundle exec] fastlane android buildGooglePlayDebugBundle
```

Build Google Play bundle for debugging

### android buildGooglePlayReleaseBundle

```sh
[bundle exec] fastlane android buildGooglePlayReleaseBundle
```

Build Google Play bundle for release

### android buildFdroidDebugApk

```sh
[bundle exec] fastlane android buildFdroidDebugApk
```

Build apk for debugging

### android buildFdroidReleaseApk

```sh
[bundle exec] fastlane android buildFdroidReleaseApk
```

Build apk for release

### android internal

```sh
[bundle exec] fastlane android internal
```

Submit a new Internal Build to Play Store

### android promote_internal_to_alpha

```sh
[bundle exec] fastlane android promote_internal_to_alpha
```

Promote Internal to Alpha

### android promote_alpha_to_beta

```sh
[bundle exec] fastlane android promote_alpha_to_beta
```

Promote Alpha to Beta

### android promote_beta_to_production

```sh
[bundle exec] fastlane android promote_beta_to_production
```

Promote Beta to Production

### android screenshots

```sh
[bundle exec] fastlane android screenshots
```

Build debug and test APK for screenshots

### android deploy

```sh
[bundle exec] fastlane android deploy
```

Deploy a new version to the Google Play

### android pre_release_build

```sh
[bundle exec] fastlane android pre_release_build
```

Bump version name and version code

### android post_release_build

```sh
[bundle exec] fastlane android post_release_build
```

Generate changelogs and tag after incrementing the version

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
>>>>>>> dev-ci
