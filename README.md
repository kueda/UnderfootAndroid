# UnderfootAndroid

Underfoot is an app that shows geoloical map data for California. I hope to add hydrological and soil data one day, and maybe expand beyond California in the future, but for now it's just rocks in CA.

## Setup

Obviously you'll need a working Android dev environment, but on top of that, you'll need to build your own AAR file for Mapzen since I'm not sure the version they have in Maven actually supports MBTiles. That's going to require [NDK](https://developer.android.com/ndk) and then look something like this:

```bash
git clone git@github.com:tangrams/tangram-es.git
cd tangram-es
make android-sdk
cp platforms/android/tangram/build/outputs/aar/tangram-full-release.aar /path/to/UnderfootAndroid/tangram-full-release/
```

Hopefully this won't always be necessary.
