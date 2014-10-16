# The relayr Thermometer App

The relayr thermometer app uses the Temperature and Humidity sensor and the relayr Cloud Platform in order to display the temperature reading in the environmnet where the sensor is placed. This repository is built as a step by step sequesnce which allows you to see the different stages of creating the app.

**Please note**: When the application is created, the SDK is initialised either in Production mode or in Mock mode. In case the debug build is used, the SDK is initialised in mock mode and thus, all data which will be presented in it is mock data. If you wish to see real data coming from your Temperature sensor, please make sure to install the production build type (./gradlew installProduction).

The Initialization of the SDK will then be performed [in this manner](https://github.com/relayr/android-demo-apps/blob/master/thermometer/src/release/java/io/relayr/demo/thermometer/RelayrSdkInitializer.java), rather than [in this manner](https://github.com/relayr/android-demo-apps/blob/master/thermometer/src/debug/java/io/relayr/demo/thermometer/RelayrSdkInitializer.java)
