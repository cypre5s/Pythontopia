# Gradle Wrapper 首次生成指引

本目录已经提交了 `gradle/wrapper/gradle-wrapper.properties`（指向 Gradle 8.11.1），
但 **没有** 提交以下三个文件（理由：`gradle-wrapper.jar` 是二进制，需本地 Gradle 一次性生成）：

- `android/gradlew`（POSIX 启动脚本）
- `android/gradlew.bat`（Windows 启动脚本）
- `android/gradle/wrapper/gradle-wrapper.jar`（启动器二进制，约 60 KB）

> 在 R1 阶段完成本指引、跑通 `./gradlew help` 之后，请把上述三个文件 commit 进 git，
> 后续协作者就不需要再做任何 wrapper 初始化。

## 1. 前置条件

- JDK 21（Temurin / Microsoft / Zulu 任选一种）已安装且 `JAVA_HOME` 已设置。
- 本机已安装一份 Gradle 8.11.1 **或** 其他 8.x（仅用一次，用完可卸载）。

## 2. 生成命令（推荐：apt / sdkman 装一次性 Gradle）

```bash
# 方式 A：用 SDKMAN（最方便）
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle 8.11.1

# 方式 B：apt（Ubuntu 24.04 默认是较旧版本，可能不够）
sudo apt update && sudo apt install -y gradle

# 方式 C：官网下载
curl -L https://services.gradle.org/distributions/gradle-8.11.1-bin.zip -o /tmp/gradle.zip
unzip /tmp/gradle.zip -d $HOME/.local/share
export PATH="$HOME/.local/share/gradle-8.11.1/bin:$PATH"

# 进入 android 目录跑 wrapper 任务
cd /home/cypress/Pythontopia/android
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

执行后会生成：

```text
android/gradlew
android/gradlew.bat
android/gradle/wrapper/gradle-wrapper.jar
```

`gradle/wrapper/gradle-wrapper.properties` 已存在，`gradle` 命令会按现有版本号自动匹配。

## 3. 备选方案：从其他 Android 项目复制

如果暂时无法安装本机 Gradle，可以从任何 **Gradle 8.11.x** 的 Android 项目（例：Android Studio 新建工程）
直接复制以下三个文件到对应路径：

```bash
cp ~/AndroidStudioProjects/SomeProject/gradlew                    android/
cp ~/AndroidStudioProjects/SomeProject/gradlew.bat                android/
cp ~/AndroidStudioProjects/SomeProject/gradle/wrapper/gradle-wrapper.jar \
   android/gradle/wrapper/
```

复制后跑一次 `cd android && ./gradlew --version` 验证版本号是 `Gradle 8.11.1`。

## 4. 验证

```bash
cd /home/cypress/Pythontopia/android
chmod +x gradlew
./gradlew --version              # 期望：Gradle 8.11.1 + JVM 21
./gradlew :app:assembleDebug     # 期望：BUILD SUCCESSFUL
```

## 5. commit 入仓

```bash
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar
git commit -m "chore(android-r1): commit gradle wrapper jar + scripts"
```

之后所有协作者都可以 `./gradlew` 直接跑，**无需** 再装本机 Gradle。

## 6. FAQ

**Q：为什么不直接把 `gradle-wrapper.jar` 也提交？**
A：当前 R1 阶段还没在本机跑过 Gradle，无法生成可信的 wrapper jar；用 `gradle wrapper` 自动生成可保证
   与 `gradle-wrapper.properties` 严格对齐，避免手动放置出错。

**Q：CI 上是否需要这个 wrapper？**
A：需要。`.github/workflows/android.yml` 通过 `gradle/actions/setup-gradle@v4` 自动按
   `gradle-wrapper.properties` 拉取对应版本的 Gradle，但仍要求项目里有 `gradlew` 启动脚本。
