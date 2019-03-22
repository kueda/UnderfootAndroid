set -e
./gradlew installDebug
adb shell am start -n rocks.underfoot.underfootandroid/rocks.underfoot.underfootandroid.MapActivity
adb logcat -T 100 -e underfoot | pygmentize -s -l java
# adb logcat | pygmentize -s -l java