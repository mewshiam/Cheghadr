# Cheghadr R8/ProGuard rules

# --- kotlinx.serialization (official recommended rules) ---
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public ** Companion;
}
-keepclassmembers class <1> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
-dontnote kotlinx.serialization.**

# --- Ktor client ---
# Ktor references slf4j's StaticLoggerBinder only when a backend is present;
# Android ships none, so silence the missing-class warning.
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.**

# --- Jsoup (tgju.org HTML scraper) ---
# R8 shrinks Jsoup fine; only silence optional-dependency notes.
-dontwarn org.jsoup.**
-keepclassmembers class io.ktor.** {
    volatile <fields>;
}

# --- Coroutines debug metadata ---
-dontwarn kotlinx.coroutines.debug.**

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
