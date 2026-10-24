# Keep kotlinx.serialization generated serializers for shared data models.
-keep class com.ahu.ahutong.next.data.**$$serializer { *; }
-keepclassmembers class com.ahu.ahutong.next.data.** {
    *** Companion;
}
