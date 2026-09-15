"""Exports BiRefNet_lite to app/src/main/assets/models/birefnet_lite.onnx.

The depth effect's model is exported here rather than taken from a published
ONNX file. onnx-community's export spells each deformable convolution out in
GatherND and ScatterND, which on a Pixel 8 Pro took about 35 seconds and 6.5 GB
of memory for one photo. This export uses ONNX's own DeformConv (ONNX Runtime
1.29 has a CPU kernel for it): about 12 seconds and 2.3 GB, with the same mask
as PyTorch. The weights are then stored in half precision and cast back to full
precision as the session loads, which halves the file to fit in the repository
without computing in half precision.

Requirements (CPU is enough; versions it was run with):
    torch 2.14.0, torchvision 0.29.0, timm 1.0.29, transformers 5.17.0,
    einops 0.8.2, kornia 0.8.3, safetensors 0.8.0, onnx 1.22.0,
    huggingface_hub 1.31.0

Usage:
    python scripts/export_subject_model.py
"""

import hashlib
import importlib
import os
import shutil
import sys
import tempfile

import numpy as np
import onnx
import torch
from huggingface_hub import hf_hub_download
from onnx import TensorProto, helper, numpy_helper
from safetensors.torch import load_file
from torch.onnx import register_custom_op_symbolic
from torch.onnx import symbolic_helper

REPOSITORY = "ZhengPeng7/BiRefNet_lite"
REVISION = "aa62cd87eafb9cc43056d08ef3615a14628b831d"
FILES = {
    "birefnet.py": "af8568b5be406bf4d2a68a7ed6d72e40f73b37a1fb6fc9ebd71b5b3cbcd069c9",
    "BiRefNet_config.py": "e7b8c2a74f6cea6a59553d517f71d47f2c1d90e670a13416af17c25fe2f3dc52",
    "model.safetensors": "4417d89795250e698c3cb0ae8df15743810065f646f48a694fdfa7ca052d0815",
}
SIDE = 1024
PACKAGE = "birefnet_lite"
OUTPUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models", "birefnet_lite.onnx")

# Weights smaller than this stay in full precision: storing them in half would save little.
SMALLEST_HALVED = 1024
HALF_MAX = 65504


def download():
    paths = {}
    for name, expected in FILES.items():
        path = hf_hub_download(REPOSITORY, name, revision=REVISION)
        with open(path, "rb") as file:
            actual = hashlib.file_digest(file, "sha256").hexdigest()
        if actual != expected:
            sys.exit(f"{name} has SHA-256 {actual}, expected {expected}")
        paths[name] = path
    return paths


def load_model(paths, work):
    # birefnet.py imports its configuration relatively, as a Hugging Face remote module does,
    # so both are imported from a package of their own.
    package = os.path.join(work, PACKAGE)
    os.makedirs(package)
    open(os.path.join(package, "__init__.py"), "w").close()
    for name in ("birefnet.py", "BiRefNet_config.py"):
        shutil.copyfile(paths[name], os.path.join(package, name))
    sys.path.insert(0, work)
    birefnet = importlib.import_module(f"{PACKAGE}.birefnet")
    config = importlib.import_module(f"{PACKAGE}.BiRefNet_config")

    model = birefnet.BiRefNet(bb_pretrained=False, config=config.BiRefNetConfig(bb_pretrained=False))
    model.load_state_dict(load_file(paths["model.safetensors"]), strict=True)
    return model.eval()


def deform_conv2d(g, *args):
    """`torchvision::deform_conv2d` as ONNX DeformConv; the arguments are torchvision's, in its order."""
    x, weight, offset, mask, bias = args[:5]
    stride_h, stride_w, pad_h, pad_w, dilation_h, dilation_w, groups, offset_groups = (
        symbolic_helper._parse_arg(arg, "i") for arg in args[5:13]
    )
    use_mask = symbolic_helper._parse_arg(args[13], "b")
    inputs = [x, weight, offset, bias] + ([mask] if use_mask else [])
    return g.op(
        "DeformConv", *inputs,
        strides_i=[stride_h, stride_w],
        pads_i=[pad_h, pad_w, pad_h, pad_w],
        dilations_i=[dilation_h, dilation_w],
        group_i=groups,
        offset_group_i=offset_groups,
        kernel_shape_i=symbolic_helper._get_tensor_sizes(weight)[2:],
    )


def export(model, path):
    register_custom_op_symbolic("torchvision::deform_conv2d", deform_conv2d, 19)
    with torch.no_grad():
        torch.onnx.export(
            model, torch.randn(1, 3, SIDE, SIDE), path, opset_version=19,
            input_names=["input_image"], output_names=["output_image"], dynamo=False,
        )


def halve_weights(source, target):
    """Stores large weights in half precision, each cast back to full precision where it is used."""
    model = onnx.load(source)
    graph = model.graph
    casts = []
    for weight in list(graph.initializer):
        if weight.data_type != TensorProto.FLOAT:
            continue
        values = numpy_helper.to_array(weight)
        if values.size < SMALLEST_HALVED or np.abs(values).max() > HALF_MAX:
            continue
        half = numpy_helper.from_array(values.astype(np.float16), weight.name + "_fp16")
        graph.initializer.remove(weight)
        graph.initializer.append(half)
        casts.append(helper.make_node("Cast", [half.name], [weight.name], to=TensorProto.FLOAT, name=weight.name + "_to_fp32"))
    for cast in reversed(casts):
        graph.node.insert(0, cast)
    onnx.checker.check_model(model)
    onnx.save(model, target)


def main():
    paths = download()
    with tempfile.TemporaryDirectory() as work:
        model = load_model(paths, work)
        full = os.path.join(work, "birefnet_lite_fp32.onnx")
        export(model, full)
        halve_weights(full, OUTPUT)
    with open(OUTPUT, "rb") as file:
        print(os.path.normpath(OUTPUT), hashlib.file_digest(file, "sha256").hexdigest())


if __name__ == "__main__":
    main()
