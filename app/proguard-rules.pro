# R8 is on for release for speed, not size — see the note on isMinifyEnabled in
# build.gradle.kts. Ported from BitChord's rules. These keep R8 away from
# everything that finds classes by name at runtime, where a missing class shows
# up as a crash on a device rather than as a build error.

# Nothing is renamed. Stack traces stay readable, and every lookup by name —
# enum names in preferences, Media3 custom commands, library reflection — keeps
# working without a rule of its own.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# ---- Script engines and extractors ------------------------------------------
# Rhino compiles and runs YouTube's player JavaScript and binds Java classes into
# it reflectively; NewPipe and InnerTubeX drive it and parse with nanojson/jsoup.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-keep class org.schabi.newpipe.** { *; }
-keep class com.grack.nanojson.** { *; }
-keep class org.jsoup.** { *; }
-keep class com.metrolist.** { *; }
-keep class com.dokar.quickjs.** { *; }

# ---- Native and crypto --------------------------------------------------------
# TagLib and QuickJS are called from JNI, which R8 cannot see; the native side
# also builds TagLib's result objects by class name.
-keep class com.kyant.taglib.** { *; }
-keep class org.bouncycastle.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# ---- Named only in the manifest's meta-data ---------------------------------
# The Cast framework instantiates this from a meta-data string value, which R8
# does not read as a class reference.
-keep class com.music.bitchord.playback.cast.CastOptionsProvider { *; }

# ---- Serialization --------------------------------------------------------------
# protobuf-lite reads its generated messages' fields reflectively.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
# Ktor resolves serializers and engine pieces by type at runtime.
-keep class io.ktor.** { *; }
# Our own @Serializable models, whole: kotlinx.serialization's bundled rules
# cover the generated serializers, and keeping the classes too costs little.
-keep @kotlinx.serialization.Serializable class com.music.bitchord.** { *; }
-keep @kotlinx.serialization.Serializable class com.my.kizzy.** { *; }

# ---- WebView bridges ------------------------------------------------------------
# PoToken minting calls back into these from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ---- JVM-only APIs ------------------------------------------------------------
# Referenced by desktop-only paths in Rhino (JSR-223, java.beans, dynalink) and
# Ktor's IntelliJ debugger probe. None of them exist on Android and none of
# those paths run here.
-dontwarn java.beans.**
-dontwarn java.lang.management.**
-dontwarn javax.el.**
-dontwarn javax.script.**
-dontwarn jdk.dynalink.**
-dontwarn org.ietf.jgss.**
