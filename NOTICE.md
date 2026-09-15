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

## BiRefNet_lite

`app/src/main/assets/models/birefnet_lite.onnx` finds the subject of the lock
screen photo for the depth effect. It is BiRefNet_lite, from "Bilateral
Reference for High-Resolution Dichotomous Image Segmentation" by Peng Zheng,
Dehong Gao, Deng-Ping Fan, Li Liu, Jorma Laaksonen, Wanli Ouyang and Nicu Sebe,
https://github.com/ZhengPeng7/BiRefNet, Copyright (c) 2024 ZhengPeng, MIT
License. It was converted to ONNX for this project by
`scripts/export_subject_model.py` from the weights published at
https://huggingface.co/ZhengPeng7/BiRefNet_lite (revision
`aa62cd87eafb9cc43056d08ef3615a14628b831d`): the weights are unchanged except
that most are stored in half precision. The MIT License is in
`app/src/main/assets/models/BiRefNet-LICENSE.txt` and ships inside the APK next
to the model.

## ONNX Runtime

The depth effect runs the model with ONNX Runtime
(`com.microsoft.onnxruntime:onnxruntime-android`), Copyright (c) Microsoft
Corporation, from https://github.com/microsoft/onnxruntime, licensed under the
MIT License.
