### JADX Android Linter Plugin

#### Features

- Constant replacement using android linter rules
- Partial support for constant unfolding
- Rules from Android SDK and all libraries of Google Maven repository (https://maven.google.com/) included. Partial support for Maven Central and Jitpack.
- Basic dependency detection support based on linter rule usage (see logs for detected dependencies).

#### Limitations

- Rules for return values, instance members and numeric values instead of constant names were filtered out. These rules would only be usable with a static analysis inside jadx.

Example:

    void test(android.view.View view) {
        view.setVisibility(getValue());
    }

    private int getValue() {
        return 8; // should be View.GONE
    }

- Only the latest version of each library was taken to identify the linter rules. The base assumption are stable linter rules between versions. If there are version specific rules, this could be used to calculate versions of dependencies.
- Replaced constants by the linter plugin may be missing in the decompiled source code of obfuscated or minimized apps and may cause compilation issues. This affects only constants of third party libraries. In those cases please use the detected dependencies to avoid compilation issues.

### Build from source

JDK 11 or higher must be installed.

```bash
git clone https://github.com/jadx-decompiler/jadx-android-linter-plugin.git
cd jadx-android-linter-plugin
./gradlew assemble
```

(on Windows, use gradlew.bat instead of ./gradlew)

### Install

Install using location id: `github:jadx-decompiler:jadx-android-linter-plugin`

In jadx-cli:
```bash
  jadx plugins --install "github:jadx-decompiler:jadx-android-linter-plugin"
```

### How to update the rules

#### Android SDK rules

```bash
./gradlew updateSDKRules -Plinter.sdk.location=<SDK location> -Plinter.sdk.api=<api level>
```

#### Google Maven repository

```bash
./gradlew updateGoogleMavenRules -Pmaven.mirror.url=<optional mirror/proxy location for maven.google.com>
```

Hint: Use a (local) repository mirror/proxy repository for frequent updates to save time and bandwidth.

#### Maven central, Jitpack

```bash
./gradlew updateMavenRules -Plinter.maven.repo=<maven-central/jitpack> -Pmaven.mirror.url=<optional mirror/proxy location>
```

Update the lists in `src/test/resources/` to add new libraries providing linter rules.

### Todo
- Expressions for unfolded constants are added as a comment. Those expression should be converted to instructions to generate them into source code.

Please report missing rules of other third party libraries.
