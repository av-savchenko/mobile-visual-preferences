This repository contains code of mobile application develped to extract user preferences from a gallery of photos.

Our approach is described in the latest version of [arXiv paper](https://arxiv.org/abs/1907.04519). The apk file for testing is [publicly available](https://drive.google.com/drive/folders/1rQkJZifq_89pu0sT_UnYXziuxutTpNEN).

Required: Android Studio, Gradle, Flask (for support of client-server object detection)

In order to check the possibility to seacrh nearby places, you must set your API_KEY of Places Query in [MapsActivity.kt](tf_android/app/src/main/java/com/pdmi_samsung/android/visual_preferences/MapsActivity.kt) and [enable Billing on the Google Cloud Project](https://console.cloud.google.com/project/_/billing/enable). Learn more at [Google Maps](https://developers.google.com/maps/gmp-get-started)

The offline processing of user preferences is presented in [webserver](webserver)
