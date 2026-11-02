# OkHttp references optional Android/Conscrypt platform classes at compile time.
# They are resolved dynamically only on the corresponding runtimes; suppress
# the missing-class warnings so desktop release build passes.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn android.**
