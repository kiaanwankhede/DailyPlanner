# Keep Roz's own code whole: Room, WorkManager, the alarm receiver and kotlinx.serialization find
# these classes by name. Only unused library code gets removed.
-keep class app.roz.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-dontobfuscate
