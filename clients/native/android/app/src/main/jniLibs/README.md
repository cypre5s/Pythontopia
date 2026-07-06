# LibGDX 原生库 (vendored)

## 来源

| 路径 | 大小 | 来源 |
| --- | --- | --- |
| `arm64-v8a/libgdx.so` | ~164 KB | `com.badlogicgames.gdx:gdx-platform:1.13.1:natives-arm64-v8a` |
| `armeabi-v7a/libgdx.so` | ~160 KB | `com.badlogicgames.gdx:gdx-platform:1.13.1:natives-armeabi-v7a` |
| `x86_64/libgdx.so` | ~193 KB | `com.badlogicgames.gdx:gdx-platform:1.13.1:natives-x86_64` |

## 为什么是 vendored 不是 Gradle 自动拉？

LibGDX 官方 Android 工程使用 `natives` 自定义 Gradle configuration + `copyAndroidNatives`
任务从 `gdx-platform` classifier JAR 解包 `libgdx.so` 到 `src/main/jniLibs/<abi>/`。

Android-R12 开发机（WSL2）下 Cloudflare 偶尔对 Maven Central 返回 403，
触发首次依赖解析失败。Aliyun 镜像可用，但 Configuration Cache 不允许直接在
`doLast {}` 里读 `Configuration` 内容。综合考量后选择：

1. 一次性从 Aliyun 把 `gdx-platform` natives JAR 拉到本地；
2. 解出 3 个 ABI 下的 `libgdx.so`；
3. 提交到仓库的 `jniLibs/<abi>/libgdx.so`。

代价：三个 .so 文件加起来约 520 KB 进入 git 历史。
收益：构建脚本零网络依赖（除常规 Gradle 缓存），构建可重现。

## 后续维护

升级 LibGDX 版本（例 1.14.0）时按下列流程重新生成：

```bash
cd /tmp
GDX=1.14.0
for abi in arm64-v8a armeabi-v7a x86_64; do
  curl -sSL -o gdx-platform-$abi.jar \
    "https://maven.aliyun.com/repository/central/com/badlogicgames/gdx/gdx-platform/$GDX/gdx-platform-$GDX-natives-$abi.jar"
  unzip -o -d /home/cypress/Pythontopia/clients/native/android/app/src/main/jniLibs/$abi \
    gdx-platform-$abi.jar libgdx.so
done
```

同步把 `gradle/libs.versions.toml` 的 `libgdx` 改为新版本，再执行
`./gradlew :app:assembleDebug` 验证 .so 与 backend AAR 同版本。

## 不要做的事

- 不要手工修改 .so 文件（会破坏 LibGDX runtime ABI 兼容）。
- 不要复制其他来源的 libgdx.so（比如 desktop 用的 libgdx64.so）。
- 不要新增其他 ABI（如 x86 32 位），除非演示环境真的需要——现代 Android 模拟器都是 x86_64 或 arm64。
