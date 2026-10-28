# --- 反射与序列化需要保留的元数据 ---
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keepattributes InnerClasses,EnclosingMethod

# --- JNI（Rust SDK）---
-keepclasseswithmembernames class * {
    native <methods>;
}

# --- Gson 反射模型 ---
# 接口返回的模型既会被 Gson 反序列化，也会作为本地缓存格式，字段名与泛型签名必须稳定。
-keep class com.ahu.ahutong.data.model.** { *; }
-keep class com.ahu.ahutong.data.crawler.model.** { *; }
-keep class com.ahu.ahutong.data.notice.CampusNotice { *; }
-keep class com.ahu.ahutong.data.notice.CampusNoticeSnapshot { *; }
-keep class com.ahu.ahutong.data.notice.CampusNoticeSourceStatus { *; }
-keep class com.ahu.ahutong.data.dao.AHUCache { *; }
# 其余模块的 Gson 响应/缓存模型（R8 的类合并会把仅反序列化的类改成抽象类）。
-keep class com.ahu.ahutong.data.canteen.** { *; }
-keep class com.ahu.ahutong.data.weather.** { *; }
-keep class com.ahu.ahutong.data.repository.** { *; }
-keep class com.ahu.ahutong.data.payment.** { *; }
-keep class com.ahu.ahutong.data.recharge.** { *; }
-keep class com.ahu.ahutong.data.server.** { *; }
-keep class com.ahu.ahutong.data.mail.** { *; }
-keep class com.ahu.ahutong.data.notice.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# 枚举在 Gson 中按名称解析，保留 values()/valueOf()。
-keepclassmembers class * extends java.lang.Enum {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# --- Retrofit ---
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# --- kotlinx.serialization ---
-keep class com.ahu.ahutong.next.data.**$$serializer { *; }
-keepclassmembers class com.ahu.ahutong.next.data.** {
    *** Companion;
}

# --- 协程 ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation {
    volatile <fields>;
}

# --- Jsoup / OkHttp 等第三方库的可选依赖 ---
-keeppackagenames org.jsoup.nodes
-keep class org.jsoup.Connection { *; }
-dontwarn org.jsoup.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn kotlin.Unit
-dontwarn java.lang.instrument.**
-dontwarn sun.misc.**
