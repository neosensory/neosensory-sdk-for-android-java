# Neosensory SDK for Android (Java edition).
A Java-based SDK to help streamline controlling Neosensory devices over Bluetooth Low Energy on Android. This project is comprised of an example app and reusable module called `neosensoryblessed`, which is built on top of GitHub user weliem's [BLESSED for Android](https://github.com/weliem/blessed-android) library. The built in handler should be good enough for getting started, but you may want to customize it further. We will also be rolling out a Kotlin-based SDK in the coming months.

## Installation

Use Jitpack to add the module dependency in your project + app build.gradle files. Click the jitback button on this GitHub repo page for instructions. Alternatively, you can clone this repo and import the neosensoryblessed module into your own project. You will still need to add the line `maven { url 'https://jitpack.io' }` in your project's build.gradle file under `allprojects { repositories { ... ` to ensure that BLESSED for Android dependency can be imported.

## Dependencies

This library depends on Weliem's [BLESSED for Android](https://github.com/weliem/blessed-android) library. It should already be included in the Module's build.gradle via Jitpack as a dependency for the module.

## Hardware

This library connects any Neosensory hardware (currently just Buzz).

## Documentation

See the pages for the module at https://neosensory.github.io/neosensory-sdk-for-android-java.

## Examples

Currently this repo contains an Android example app that connects and vibrates to the first device it finds with "Buzz" in the name (i.e. a Neosensory Buzz device).

## License

Please note that while this Neosensory SDK has an Apache 2.0 license, 
usage of the Neosensory API to interface with Neosensory products is 
still  subject to the Neosensory developer terms of service located at:
https://neosensory.com/legal/dev-terms-service.

See [LICENSE](https://github.com/neosensory/neosensory-java-sdk-for-android/blob/master/LICENSE).
