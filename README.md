# Smart Glasses Detector

Smart Glasses Detector is a privacy and personal-safety tool that helps you become aware of  smartglasses nearby. It scans nearby Bluetooth and Wi-Fi signals and alerts you when it finds a supported device or possible image/video transfer activity.

## Features

- Detect nearby smartglasses, estimate their approximate distance, and identify possible image/video activity.
- Stay informed with background scanning, notifications, sound, and vibration.
- Adjust detection sensitivity and start or stop scanning from your Android home screen.

The app may be useful in public places, events, cafés, workplaces, public transport, and other situations where you may be concerned about being photographed or recorded without your knowledge.

## How it works

When unpaired,  smartglasses continuously broadcast Bluetooth Low Energy (BLE) advertising PDUs on channels 37–39. These advertisements contain fixed vendor and model identifiers, such as a  Company Identifier and Service UUID. The app uses Android's BLE scanner to match these identifiers and uses RSSI and advertised TX power to estimate distance.

The glasses normally use a randomized Bluetooth address that remains stable for one power cycle. This lets the app track the same nearby device during that session, although restarting the glasses may make them appear as a new device.

Shortly after media capture, the glasses may start a temporary Wi-Fi Direct/P2P session for automatic media import. If that fails, they may use a SoftAP-style Wi-Fi network instead. The app checks Android's Wi-Fi Direct peer list and nearby Wi-Fi results for model-specific names or SSID prefixes.

## Important- Regarding Possible False Detections
A matching Bluetooth signal or Wi-Fi transfer pattern can trigger a notification, sound, or vibration alert. These signals can indicate a nearby device or possible media transfer, but they cannot confirm that someone is currently recording or identify what was captured. False detections are possible due to how the app relies on BLE PDUs and nearby Wi-Fi session detection
