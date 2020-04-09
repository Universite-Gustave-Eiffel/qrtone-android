# qrtone-android
QRTone demo application

This demo of the QRTone library (https://github.com/Ifsttar/qrtone) is an android offline chat application.

 It uses only the microphone and speaker to communicate with other Android phones.

Advantages of data transfer via sound:

- Intermediate solution between QRCode and BlueTooth technology
- Low energy consumption and no electromagnetic waves emitted
- No user intervention is required (no device pairing)
- A single message can be sent to an unlimited number of devices.

# How it works ?

The data transmission method is similar to the first Internet modems except that it is adapted to the disturbances caused by acoustic propagation.

Here a spectrogram of a sequence:

![OpenWarble spectrogram](noise.png)
[Audible tones of QRTone](test_qrtone_chat.mp3)


*Top source signal, bottom recorded audio in real situation*

QRTone contain a forward correction code in order to reconstruct loss tones.
