# The relayr Android-Bridge Module-Cloud Demo App

This app displays a simple and fun implementation of the relayr bridge module connecting a button and a siren via the relayr cloud.

The app consists of 4 components: A siren, a physical button and two bridge modules. 
The siren is connected to one bridge module and the button is connected to the second. 
When the button is pressed, a notification is sent via the bridge module to the relayr cloud and then to the app. The app then sends a command via the bridge module to the siren to start making noise.