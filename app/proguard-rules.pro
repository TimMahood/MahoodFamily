# OkHttp optional platform integrations
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# WorkManager instantiates workers by class name
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
