# Wear module application rules.
#
# Compose, coroutines and Google Play Services publish their consumer rules.
# Preserve source/line metadata so release crash reports remain actionable
# without disabling code shrinking or obfuscation.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
