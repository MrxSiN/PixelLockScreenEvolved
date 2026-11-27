# Notice

Pixel Lock Screen Evolved is an independent project. It is not affiliated with,
endorsed by, or sponsored by Google LLC or Apple Inc. Android and Pixel are
trademarks of Google LLC; iOS is a trademark of Apple Inc.; HyperOS, One UI,
ColorOS, OxygenOS and Nothing OS are trademarks of their owners. These names are used
only to identify the software this module extends and the design it imitates.

The module uses device fonts (Roboto Flex and the system sans-serif) for the
other clock styles.

## AXS66Clock

`app/src/main/assets/fonts/AXS66Clock.otf` is the iOS clock's default font.
The file identifies Apple Inc. as the copyright holder (2015–2019) and
refers to the applicable Apple OS Software License Agreement.

## Inter

`app/src/main/assets/fonts/Inter.ttf` is Inter 4.001 by The Inter Project
Authors, https://github.com/rsms/inter, Copyright 2020, licensed under the SIL
Open Font License 1.1 (`app/src/main/assets/fonts/Inter-OFL.txt`). It is a
subset of Google Fonts' `ofl/inter/Inter[opsz,wght].ttf`, keeping the figures,
the colon and the capital I with both variation axes. Inter has no Reserved
Font Name. It is the iOS clock's fallback font.

```sh
pyftsubset 'Inter[opsz,wght].ttf' --text='0123456789:I' --layout-features=pnum,tnum,kern,ss01,cv01 --output-file=Inter.ttf
```

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

## Depth Anything V2 Small

`app/src/main/assets/models/depth_anything_v2_small.onnx` estimates how near
each part of the lock screen photo is, for the depth effect's depth map
layering. It is Depth Anything V2 Small, from "Depth Anything V2" by Lihe Yang,
Bingyi Kang, Zilong Huang, Zhen Zhao, Xiaogang Xu, Jiashi Feng and Hengshuang
Zhao, https://github.com/DepthAnything/Depth-Anything-V2, Apache License 2.0.
The file is `onnx/model_quantized.onnx` (int8 weights) from
https://huggingface.co/onnx-community/depth-anything-v2-small, unchanged. The
licence is in `app/src/main/assets/models/DepthAnythingV2-LICENSE.txt` and
ships inside the APK next to the model.

## DexKit

Host classes and methods a build renamed are found with DexKit
(`org.luckypray:dexkit`), Copyright (c) LuckyPray, from
https://github.com/LuckyPray/DexKit, licensed under the GNU Lesser General
Public License v3.0. It is linked unmodified, as a separate library.

DexKit passes its queries with FlatBuffers for Java
(`com.google.flatbuffers:flatbuffers-java`), Copyright Google Inc., licensed
under the Apache License 2.0.

## ONNX Runtime

The depth effect runs the model with ONNX Runtime
(`com.microsoft.onnxruntime:onnxruntime-android`), Copyright (c) Microsoft
Corporation, from https://github.com/microsoft/onnxruntime, licensed under the
MIT License.
