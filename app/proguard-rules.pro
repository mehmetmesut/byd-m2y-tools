# Keep native methods
-keepclassmembers class * {
    native <methods>;
}

# Keep classes that are used as a parameter type of methods that are also marked as keep
# to preserve changing those methods' signature.
-keep class helium314.keyboard.latin.dictionary.Dictionary
-keep class helium314.keyboard.latin.NgramContext
-keep class helium314.keyboard.latin.makedict.ProbabilityInfo

# after upgrading to gradle 8, stack traces contain "unknown source"
-keepattributes SourceFile,LineNumberTable
-dontobfuscate

# M2Y Board: release drops debug/verbose/info logging; warnings and errors still reach logcat
-assumenosideeffects class helium314.keyboard.latin.utils.Log {
    public static void d(...);
    public static void v(...);
    public static void i(...);
}
