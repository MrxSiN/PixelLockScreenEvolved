# Notice

Pixel Lock Screen Evolved is an independent project. It is not affiliated with,
endorsed by, or sponsored by Google LLC or Apple Inc. Android and Pixel are
trademarks of Google LLC; iOS is a trademark of Apple Inc. These names are used
only to identify the software this module extends and the design it imitates.

The module draws its clocks with fonts already on the device (Roboto Flex and
the system sans-serif) and with Inter, which it bundles. It does not
redistribute any Google or Apple asset, and does not include SF Pro or any
other Apple font.

## Inter

`app/src/main/assets/fonts/InterVariable.ttf` is Inter 4.1 by Rasmus Andersson,
from https://github.com/rsms/inter, licensed under the SIL Open Font License
1.1. The license is in `app/src/main/assets/fonts/Inter-LICENSE.txt` and ships
inside the APK next to the font.

## U²-Net lite (U2NetP)

`app/src/main/assets/models/u2netp.onnx` finds the subject of the lock screen
photo for the depth effect. It is the U²-Net lite (U2NetP) salient object
detection model by Xuebin Qin, Zichen Zhang, Chenyang Huang, Masood Dehghan,
Osmar R. Zaiane and Martin Jagersand, from https://github.com/xuebinqin/U-2-Net,
licensed under the Apache License 2.0, in the ONNX export published by rembg
(https://github.com/danielgatis/rembg, release v0.0.0, MD5
`8e83ca70e441ab06c318d82300c84806`), Copyright (c) 2020 Daniel Gatis, MIT
License. The file is unmodified. The Apache License 2.0 is in
`app/src/main/assets/models/U2Net-LICENSE.txt` and ships inside the APK next to
the model.

## ONNX Runtime

The depth effect runs the model with ONNX Runtime
(`com.microsoft.onnxruntime:onnxruntime-android`), Copyright (c) Microsoft
Corporation, from https://github.com/microsoft/onnxruntime, licensed under the
MIT License.
