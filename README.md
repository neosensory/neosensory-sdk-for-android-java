# neosensory-java-sdk-for-android
A Java-based SDK to help streamline controlling Neosensory devices over Bluetooth Low Energy on Android. Built on top of GitHub user weliem's BLESSED library. The built in handler should be good enough for getting started, but you may want to customize it further.

## Installation

Use Jitpack to add the dependency in your project + app build.gradle files. Click the jitback button the GitHub repo page for instructions.

## Dependencies

This library depends on Weliem's [BLESSED for Android](https://github.com/weliem/blessed-android) library. It should already be included in the Module's build.gradle via Jitpack as a dependency for the module.

## Hardware

This library connects any Neosensory hardware (currently just Buzz).

## Documentation

See GitHub repo at https://github.com/neosensory/neosensory-java-sdk-for-android.

## Examples

Currently this repo contains an Android example app that connects and vibrates to the first device it finds with "Buzz" in the name (i.e. a Neosensory Buzz device).

## License

Please note that while this Neosensory SDK has an MIT license, 
usage of the Neosensory API to interface with Neosensory products is 
still  subject to the Neosensory developer terms of service located at:
https://neosensory.com/legal/dev-terms-service.

See [LICENSE](https://github.com/neosensory/neosensory-sdk-for-bluefruit/blob/master/LICENSE).
