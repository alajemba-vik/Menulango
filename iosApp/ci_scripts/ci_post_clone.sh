#!/bin/sh
# Xcode Cloud runs this right after cloning. The clone has none of what a developer's Mac has:
# no JDK for Gradle (which builds the Kotlin framework and bundles its fonts and translations),
# no CocoaPods install (ML Kit), and no git-ignored Secrets.xcconfig. This supplies all three.
set -eu

REPO="$CI_PRIMARY_REPOSITORY_PATH"

# 1. A JDK for Gradle. Environment variables don't reach Xcode's build phases, so Gradle is told
#    where Java lives through its own properties file.
brew install openjdk@17
JDK="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
mkdir -p "$HOME/.gradle"
echo "org.gradle.java.home=$JDK" >> "$HOME/.gradle/gradle.properties"

# 2. Secrets, from Xcode Cloud's environment (set them as secret variables in the workflow).
# xcconfig reads "//" as a comment, so the URL's slashes are split with $() as the example shows.
SAFE_PROXY_URL="$(printf '%s' "${PROXY_URL:-}" | sed 's|//|/$()/|')"
cat > "$REPO/iosApp/Configuration/Secrets.xcconfig" <<EOF
TEAM_ID=${TEAM_ID:-}
PROXY_URL=$SAFE_PROXY_URL
REVENUECAT_IOS_KEY=${REVENUECAT_IOS_KEY:-}
FIREBASE_IOS_APP_ID=${FIREBASE_IOS_APP_ID:-}
FIREBASE_API_KEY=${FIREBASE_API_KEY:-}
FIREBASE_PROJECT_ID=${FIREBASE_PROJECT_ID:-}
EOF

# 3. Pods (ML Kit), exactly as Podfile.lock pins them.
brew install cocoapods
cd "$REPO/iosApp"
pod install

# 4. Build the Kotlin framework and its resources now, so a Gradle failure shows up here with a
#    clear log rather than as an app that crashes at launch.
cd "$REPO"
./gradlew --no-daemon :composeApp:linkReleaseFrameworkIosArm64
