# AI-Monitor 混淆规则
# 项目无反射调用; org.json 为平台内置类; OkHttp/Tink/Compose 自带 consumer 规则。
# 如 release 冒烟发现问题, 优先排查此处缺失的 -keep。

# 崩溃堆栈可读性: 保留源码行号与源文件名
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留调试日志页可读的异常类名 (友好错误文案依赖 e.javaClass.simpleName)
-keepnames class java.io.IOException { *; }

# OkHttp 平台探测用的可选依赖 (Conscrypt/OpenJSSE 等) 不存在时忽略警告
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink (security-crypto) 可选依赖的编译期注解, 运行时不需要
-dontwarn com.google.errorprone.annotations.**
