"""Bounded document conversion and parsing with explicit failures."""

from __future__ import annotations

import json
import multiprocessing
import os
import subprocess
import time
from pathlib import Path
from uuid import uuid4

import httpx

from app.materials.uploads import MAX_UPLOAD, UploadValidationError, validate_upload


def _parse_in_process(source: str, output: str):
    # Parsing untrusted PDF/text is separate from request processing and killable.
    try:
        if os.name != "nt":
            import resource

            resource.setrlimit(resource.RLIMIT_AS, (512 * 1024 * 1024,) * 2)
            resource.setrlimit(resource.RLIMIT_CPU, (15, 15))
        path = Path(source)
        if path.suffix == ".pdf":
            from pypdf import PdfReader

            reader = PdfReader(path)
            if len(reader.pages) > 600:
                raise ValueError("PDF 超过600页处理限制")
            pages = [
                {"id": i + 1, "page_no": i + 1, "title": f"第{i + 1}页", "text": (page.extract_text() or "")[:40000]}
                for i, page in enumerate(reader.pages)
            ]
        else:
            text = path.read_text(encoding="utf-8-sig")
            if len(text) > 2_000_000:
                raise ValueError("文本超过200万字符限制")
            pages = [
                {"id": i + 1, "page_no": i + 1, "title": f"第{i + 1}页", "text": text[i * 6000 : (i + 1) * 6000]}
                for i in range(max(1, (len(text) + 5999) // 6000))
            ]
        Path(output).write_text(json.dumps({"pages": pages}, ensure_ascii=False), encoding="utf-8")
    except Exception:
        # Do not propagate raw parser exceptions or filesystem paths into UI.
        Path(output).write_text(json.dumps({"error": "资料解析失败：文件损坏、受保护或超过处理限制"}), encoding="utf-8")


def parse_bounded(path: Path, tmp: Path):
    output = tmp / "parsed.json"
    process = multiprocessing.get_context("spawn").Process(target=_parse_in_process, args=(str(path), str(output)))
    process.start()
    process.join(25)
    if process.is_alive():
        process.terminate()
        process.join(3)
        raise ValueError("资料解析超时")
    if not output.exists() or output.stat().st_size > 32 * 1024 * 1024:
        raise ValueError("资料解析进程失败或输出超过限制")
    content = json.loads(output.read_text(encoding="utf-8"))
    if content.get("error"):
        raise ValueError(content["error"])
    return content


def convert_presentation(source: Path, tmp: Path) -> Path:
    converter_url = os.getenv("REBORN_CONVERTER_URL", "").rstrip("/")
    if converter_url:
        token = os.getenv("REBORN_CONVERTER_TOKEN", "")
        if len(token) < 24:
            raise ValueError("私有转换服务token未配置")
        destination = tmp / "converted.pdf"
        started = time.monotonic()
        try:
            with source.open("rb") as data, httpx.Client(timeout=httpx.Timeout(70, connect=5), follow_redirects=False) as client:
                with client.stream(
                    "POST",
                    converter_url + "/convert",
                    headers={
                        "X-Convert-Token": token,
                        "X-Source-Type": source.suffix.lstrip("."),
                        "Content-Type": "application/octet-stream",
                        "Content-Length": str(source.stat().st_size),
                    },
                    content=data,
                ) as response:
                    response.raise_for_status()
                    size = 0
                    with destination.open("wb") as output:
                        for chunk in response.iter_bytes(65536):
                            size += len(chunk)
                            if size > MAX_UPLOAD or time.monotonic() - started > 75:
                                raise ValueError("转换输出过大或服务超时")
                            output.write(chunk)
            validate_upload(destination, ".pdf")
            return destination
        except (httpx.HTTPError, OSError, UploadValidationError) as exc:
            raise ValueError("隔离PPT转换服务失败、繁忙或超时，请检查处理服务") from exc
    image = os.getenv("REBORN_CONVERTER_IMAGE", "")
    if not image:
        raise ValueError("PPT转换器未配置：请配置经过审计的LibreOffice容器镜像")
    output = tmp / "converted"
    output.mkdir()
    container_name = "reborn-presentation-" + uuid4().hex
    macro_policy = (
        '<?xml version="1.0" encoding="UTF-8"?><oor:items xmlns:oor="http://openoffice.org/2001/registry">'
        '<item oor:path="/org.openoffice.Office.Common/Security/Scripting">'
        '<prop oor:name="MacroSecurityLevel" oor:op="fuse"><value>3</value></prop></item></oor:items>'
    )
    command = [
        "docker",
        "run",
        "--rm",
        "--name",
        container_name,
        "--label",
        "com.pythontopia.project=reborn",
        "--network=none",
        "--memory=512m",
        "--cpus=1",
        "--pids-limit=64",
        "--cap-drop=ALL",
        "--security-opt=no-new-privileges:true",
        "--read-only",
        "--env",
        "HOME=/tmp",
        "--env",
        "TMPDIR=/tmp",
        "--tmpfs",
        "/tmp:rw,noexec,nosuid,size=256m",
        "--mount",
        f"type=bind,src={source.parent},dst=/input,readonly",
        image,
        "timeout",
        "--signal=TERM",
        "--kill-after=5",
        "55s",
        "python3",
        "-c",
        "import os,subprocess,sys;from pathlib import Path;"
        "os.umask(0o022);source=Path(sys.argv[1]);output=Path(sys.argv[2]);output.mkdir();"
        "profile=Path('/tmp/lo/user');profile.mkdir(parents=True);(profile/'registrymodifications.xcu').write_text(sys.argv[4]);"
        "subprocess.run(['libreoffice','-env:UserInstallation=file:///tmp/lo','--headless','--convert-to','pdf','--outdir',str(output),str(source)],check=True,timeout=50,stdout=sys.stderr);"
        "pdf=output/(source.stem+'.pdf');assert 0<pdf.stat().st_size<=int(sys.argv[3]);sys.stdout.buffer.write(pdf.read_bytes())",
        "/input/" + source.name,
        "/tmp/output",
        str(MAX_UPLOAD),
        macro_policy,
    ]
    try:
        completed = subprocess.run(command, check=True, timeout=70, capture_output=True)
    except subprocess.TimeoutExpired as exc:
        # Killing the Docker client does not guarantee that its container stops.
        # Scope cleanup to the generated name of this one conversion job.
        try:
            subprocess.run(["docker", "rm", "--force", container_name], timeout=10, capture_output=True)
        except (OSError, subprocess.SubprocessError):
            pass
        raise ValueError("隔离PPT转换超时") from exc
    except (OSError, subprocess.SubprocessError) as exc:
        raise ValueError("隔离PPT转换失败；请检查转换服务") from exc
    result = output / (source.stem + ".pdf")
    if not 0 < len(completed.stdout) <= MAX_UPLOAD:
        raise ValueError("PPT转换输出为空或超过大小上限")
    result.write_bytes(completed.stdout)
    if not result.is_file() or not 0 < result.stat().st_size <= MAX_UPLOAD:
        raise ValueError("PPT转换未产生有效PDF")
    validate_upload(result, ".pdf")
    return result
