# Pythontopia R8 / ProGuard 规则（release minify 开启时生效）
# 目标：缩减 + 混淆，但保住反射/原生/序列化依赖的运行时正确性。

# ---- 安全加固（超强反编译防护）----
# 允许提升/修改访问修饰符并把类重打包进单一根包，最大化重命名与扁平化，显著抬高逆向门槛。
-allowaccessmodification
-repackageclasses ''
# 不保留调用栈中参数名等额外信息（崩溃定位靠 mapping.txt 反混淆）。
-keepattributes !LocalVariableTable,!LocalVariableTypeTable
# release 去除日志输出：减少信息泄露 + 移除调试线索。
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static boolean isLoggable(...);
}

# ---- 通用属性（序列化/反射/泛型/行号需要）----
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---- LibGDX（重度反射 + JNI；桌面后端类在 Android 缺失需 dontwarn）----
-keep class com.badlogic.gdx.** { *; }
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.utils.** { *; }
-keepclassmembers class com.badlogic.gdx.** { *; }
-dontwarn com.badlogic.gdx.backends.lwjgl.**
-dontwarn com.badlogic.gdx.backends.lwjgl3.**
-dontwarn com.badlogic.gdx.jni.**
-dontwarn java.awt.**
-dontwarn javax.**

# ---- androidx.graphics.path 原生库 ----
-keep class androidx.graphics.path.** { *; }
-dontwarn androidx.graphics.path.**

# ---- kotlinx.serialization（@Serializable DTO 经 Retrofit 转换器使用）----
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.pythontopia.**$$serializer { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# 保留所有项目内 DTO（数据类字段名用于 JSON 绑定）
-keep class com.pythontopia.**.dto.** { *; }
-keepclassmembers class com.pythontopia.** {
    @kotlinx.serialization.SerialName <fields>;
}

# ---- Retrofit / OkHttp（自带 consumer rules，这里补 dontwarn）----
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ---- Hilt / Dagger（绝大多数由插件 consumer rules 覆盖，补保险）----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-dontwarn dagger.hilt.**

# ---- Tink / security-crypto（EncryptedSharedPreferences 依赖；缺失的是编译期注解）----
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.crypto.tink.**
-keep class com.google.crypto.tink.** { *; }

# ---- Kotlin 元数据 / 协程 ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**

# ---- Compose（AGP 默认规则已覆盖，保留 tooling 安全）----
-dontwarn androidx.compose.**

# ---- 枚举（序列化/valueOf 反射）----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
