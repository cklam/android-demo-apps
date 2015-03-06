# The relayr Thermometer App

The relayr thermometer app uses the Temperature and Humidity sensor and the relayr Cloud Platform in order to display the temperature reading in the environmnet where the sensor is placed. This repository is built as a step by step sequesnce which allows you to see the different stages of creating the app. Please note tha each branch contains a different step. You can see the different code omissions and additions by clicking on the branch name. 

Check out the BLE Direct Connection version of the thermometer [here](https://github.com/relayr/android-demo-apps/tree/master/thermometer-ble) 

## A Note about Build Types

Android Studio uses **Gradle** for the app build process. 
Gradle includes a feature called "Build Types", which are, in essence variations of the same application.
These 'variations' of the app are distiguishable by the files chosen to be compiled in each of them.

In the thermometer demo app we have created 3 build types:
- Debug. The SDK is initialized in Mock mode, i.e.- displaying mock data in the app. 
- Production. The SDK is initialized in Production mode - i.e. displaying real data coming from the sensors. 
- Release. Displaying real data and including a signature - which is used for the release of the app to the Google Play store.

For a more elaborate explanation about Build Types - please see [this link](http://tools.android.com/tech-docs/new-build-system/user-guide#TOC-Build-Types)

When the application is created, the SDK is initialised either in Production mode or in Debug mode. In case the debug build is used, the SDK is initialised in mock mode (All data displazed is mock data). If you wish to see real data coming from your Temperature sensor, please make sure to install the production build type (./gradlew installProduction).

The Initialization of the SDK will then be performed [in this manner](https://github.com/relayr/android-demo-apps/blob/master/thermometer/src/release/java/io/relayr/demo/thermometer/RelayrSdkInitializer.java), rather than [in this manner](https://github.com/relayr/android-demo-apps/blob/master/thermometer/src/debug/java/io/relayr/demo/thermometer/RelayrSdkInitializer.java)
