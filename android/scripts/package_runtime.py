"""Package the pinned upstream ARM64 server and its shared libraries in the APK."""
import hashlib
import os
from pathlib import Path
import shutil
import sys
import tarfile
import urllib.request

TAG = "b12651"
SHA = "db5bea99872af1bbdde729bfc98fb3060dd58af24ae15270af82523992673381"
output = Path(sys.argv[1])
output.mkdir(parents=True, exist_ok=True)
archive = output.parent / f"llama-{TAG}.tar.gz"
if not archive.exists():
    temp = archive.with_suffix(".part")
    urllib.request.urlretrieve(f"https://github.com/ggml-org/llama.cpp/releases/download/{TAG}/llama-{TAG}-bin-android-arm64.tar.gz", temp)
    os.replace(temp, archive)
if hashlib.sha256(archive.read_bytes()).hexdigest() != SHA:
    archive.unlink()
    sys.exit("Invalid llama.cpp archive checksum")
with tarfile.open(archive) as package:
    for member in package:
        name = Path(member.name).name
        if name == "llama-server":
            name = "libjarvis-server.so"
        elif name not in {"libllama-server-impl.so", "libllama-common.so", "libllama.so", "libmtmd.so", "libggml.so", "libggml-base.so", "libggml-rpc.so"} and not name.startswith("libggml-cpu-"):
            continue
        with package.extractfile(member) as source, (output / name).open("wb") as target:
            shutil.copyfileobj(source, target)
