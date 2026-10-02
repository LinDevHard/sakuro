# Project-specific R8 rules belong here. Keep this file intentionally narrow:
# AndroidX, Compose, Kotlin serialization, Koin, Decompose, Coil, and Media3
# publish their own consumer rules or do not require reflection-based keeps.
#
# Add a rule only for a reproduced optimized-release failure. Broad rules such
# as `-keep class ** { *; }` hide problems and defeat release optimization.
