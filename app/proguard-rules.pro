# Keep all native method declarations so the JNI linker can resolve them at runtime.
# R8 would otherwise rename or strip the external fun declarations in FilmrEngine.kt,
# causing UnsatisfiedLinkError when libfilmr.so tries to resolve the mangled names.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FilmrEngine and its companion object intact.
-keep class com.reilandeubank.unprocess.engine.FilmrEngine { *; }
-keep class com.reilandeubank.unprocess.engine.FilmrEngine$* { *; }
