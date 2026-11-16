#!/usr/bin/env python3
"""Compose the Stores Play and fastlane screenshots from a device-smoke capture."""

from __future__ import annotations

import argparse
import hashlib
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

# Geometry for the existing 1080x1920 Play canvas and the 1080x2400 device-smoke AVD.
CANVAS_WIDTH = 1080
CANVAS_HEIGHT = 1920
CAPTURE_WIDTH = 1080
CAPTURE_HEIGHT = 2400
CAPTURE_TOP = 84
CAPTURE_BOTTOM = 2274
CONTENT_X = 128
CONTENT_Y = 66
CONTENT_WIDTH = 823
CONTENT_HEIGHT = 1788
CORNER_RADIUS = 20


@dataclass(frozen=True)
class PngImage:
    width: int
    height: int
    channels: int
    data: bytes

    def pixel(self, x: int, y: int) -> tuple[int, ...]:
        index = (y * self.width + x) * self.channels
        return tuple(self.data[index : index + self.channels])


def _paeth(a: int, b: int, c: int) -> int:
    p = a + b - c
    pa = abs(p - a)
    pb = abs(p - b)
    pc = abs(p - c)
    if pa <= pb and pa <= pc:
        return a
    if pb <= pc:
        return b
    return c


def decode_png(path: Path) -> PngImage:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")

    offset = 8
    idat = bytearray()
    width = height = bit_depth = color_type = interlace = None
    while offset < len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        payload = data[offset + 8 : offset + 8 + length]
        if chunk_type == b"IHDR":
            (
                width,
                height,
                bit_depth,
                color_type,
                compression,
                filter_method,
                interlace,
            ) = struct.unpack(">IIBBBBB", payload)
            if compression != 0 or filter_method != 0:
                raise ValueError(f"{path} uses unsupported PNG compression settings")
        elif chunk_type == b"IDAT":
            idat.extend(payload)
        elif chunk_type == b"IEND":
            break
        offset += 12 + length

    if None in (width, height, bit_depth, color_type, interlace):
        raise ValueError(f"{path} has no IHDR")
    if bit_depth != 8 or interlace != 0:
        raise ValueError(f"{path} must be an 8-bit, non-interlaced PNG")
    channels = {2: 3, 6: 4}.get(color_type)
    if channels is None:
        raise ValueError(f"{path} must use the RGB or RGBA color type")

    raw = zlib.decompress(bytes(idat))
    stride = width * channels
    if len(raw) != height * (stride + 1):
        raise ValueError(f"{path} has an unexpected decompressed size")

    output = bytearray(height * stride)
    previous = bytearray(stride)
    position = 0
    for y in range(height):
        filter_type = raw[position]
        position += 1
        row = bytearray(raw[position : position + stride])
        position += stride
        if filter_type == 0:
            pass
        elif filter_type == 1:
            for index in range(channels, stride):
                row[index] = (row[index] + row[index - channels]) & 0xFF
        elif filter_type == 2:
            for index in range(stride):
                row[index] = (row[index] + previous[index]) & 0xFF
        elif filter_type == 3:
            for index in range(stride):
                left = row[index - channels] if index >= channels else 0
                row[index] = (row[index] + ((left + previous[index]) >> 1)) & 0xFF
        elif filter_type == 4:
            for index in range(stride):
                left = row[index - channels] if index >= channels else 0
                up = previous[index]
                upper_left = previous[index - channels] if index >= channels else 0
                row[index] = (row[index] + _paeth(left, up, upper_left)) & 0xFF
        else:
            raise ValueError(f"{path} uses unsupported PNG filter {filter_type}")
        output[y * stride : (y + 1) * stride] = row
        previous = row

    return PngImage(width, height, channels, bytes(output))


def _chunk(chunk_type: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + chunk_type
        + payload
        + struct.pack(">I", zlib.crc32(chunk_type + payload) & 0xFFFFFFFF)
    )


def encode_png(path: Path, image: PngImage) -> None:
    color_type = {3: 2, 4: 6}.get(image.channels)
    if color_type is None:
        raise ValueError("PNG output must have three or four channels")

    raw = bytearray()
    stride = image.width * image.channels
    for y in range(image.height):
        raw.append(0)
        raw.extend(image.data[y * stride : (y + 1) * stride])
    compressed = zlib.compress(bytes(raw), 9)

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + _chunk(
            b"IHDR",
            struct.pack(
                ">IIBBBBB",
                image.width,
                image.height,
                8,
                color_type,
                0,
                0,
                0,
            ),
        )
        + _chunk(b"sRGB", b"\x00")
        + _chunk(b"IDAT", compressed)
        + _chunk(b"IEND", b"")
    )


def _resize_weights(src_len: int, dst_len: int) -> list[list[tuple[int, float]]]:
    scale = src_len / dst_len
    radius = 1.0 / scale if scale < 1.0 else 1.0
    weights = []
    for x in range(dst_len):
        center = (x + 0.5) * scale - 0.5
        low = max(0, int(center - radius + 1))
        high = min(src_len, int(center + radius) + 1)
        values = []
        total = 0.0
        for source in range(low, high):
            weight = max(0.0, 1.0 - abs(source - center) / radius)
            if weight:
                values.append((source, weight))
                total += weight
        if not values:
            values = [(min(src_len - 1, max(0, int(center))), 1.0)]
            total = 1.0
        weights.append([(source, weight / total) for source, weight in values])
    return weights


def resize_rgb(
    image: PngImage,
    source_top: int,
    source_bottom: int,
    target_width: int,
    target_height: int,
) -> PngImage:
    if image.channels not in (3, 4):
        raise ValueError("capture must be RGB or RGBA")
    if not 0 <= source_top < source_bottom <= image.height:
        raise ValueError("capture crop is outside the image")

    source_height = source_bottom - source_top
    x_weights = _resize_weights(image.width, target_width)
    y_weights = _resize_weights(source_height, target_height)

    rows: list[list[float]] = []
    for source_y in range(source_top, source_bottom):
        row: list[float] = []
        base = source_y * image.width * image.channels
        for weights in x_weights:
            red = green = blue = 0.0
            for source_x, weight in weights:
                index = base + source_x * image.channels
                red += image.data[index] * weight
                green += image.data[index + 1] * weight
                blue += image.data[index + 2] * weight
            row.extend((red, green, blue))
        rows.append(row)

    output = bytearray(target_width * target_height * 3)
    for y, weights in enumerate(y_weights):
        values = [0.0] * (target_width * 3)
        for source_y, weight in weights:
            row = rows[source_y]
            for index in range(target_width * 3):
                values[index] += row[index] * weight
        base = y * target_width * 3
        for index, value in enumerate(values):
            output[base + index] = min(255, max(0, int(value + 0.5)))

    return PngImage(target_width, target_height, 3, bytes(output))


def _inside_rounded_rectangle(
    x: float,
    y: float,
    width: int,
    height: int,
    radius: int,
) -> bool:
    if x < 0 or x >= width or y < 0 or y >= height:
        return False
    if x < radius and y < radius:
        return (x - radius) ** 2 + (y - radius) ** 2 <= radius**2
    if x >= width - radius and y < radius:
        return (x - (width - radius)) ** 2 + (y - radius) ** 2 <= radius**2
    if x < radius and y >= height - radius:
        return (x - radius) ** 2 + (y - (height - radius)) ** 2 <= radius**2
    if x >= width - radius and y >= height - radius:
        return (x - (width - radius)) ** 2 + (
            y - (height - radius)
        ) ** 2 <= radius**2
    return True


def rounded_coverage(width: int, height: int, radius: int) -> list[float]:
    if radius <= 0 or radius * 2 > min(width, height):
        raise ValueError("corner radius is outside the image")

    coverage = [1.0] * (width * height)
    corners = (
        (0, 0),
        (width - radius, 0),
        (0, height - radius),
        (width - radius, height - radius),
    )
    for corner_x, corner_y in corners:
        for y in range(radius):
            for x in range(radius):
                samples = 0
                for sample_y in range(4):
                    for sample_x in range(4):
                        if _inside_rounded_rectangle(
                            corner_x + x + (sample_x + 0.5) / 4,
                            corner_y + y + (sample_y + 0.5) / 4,
                            width,
                            height,
                            radius,
                        ):
                            samples += 1
                coverage[(corner_y + y) * width + corner_x + x] = samples / 16.0
    return coverage


def compose(
    template: PngImage,
    capture: PngImage,
    *,
    capture_top: int,
    capture_bottom: int,
    target_x: int,
    target_y: int,
    target_width: int,
    target_height: int,
    radius: int,
) -> PngImage:
    if template.channels != 3:
        raise ValueError("template must be RGB")
    if (
        target_x < 0
        or target_y < 0
        or target_x + target_width > template.width
        or target_y + target_height > template.height
    ):
        raise ValueError("target rectangle is outside the template")

    resized = resize_rgb(
        capture,
        capture_top,
        capture_bottom,
        target_width,
        target_height,
    )
    coverage = rounded_coverage(target_width, target_height, radius)
    output = bytearray(template.data)

    for y in range(target_height):
        for x in range(target_width):
            amount = coverage[y * target_width + x]
            if amount == 0.0:
                continue
            template_index = (
                (target_y + y) * template.width + target_x + x
            ) * template.channels
            resized_index = (y * target_width + x) * resized.channels
            if amount >= 1.0:
                output[template_index : template_index + 3] = resized.data[
                    resized_index : resized_index + 3
                ]
            else:
                for channel in range(3):
                    output[template_index + channel] = int(
                        resized.data[resized_index + channel] * amount
                        + template.data[template_index + channel] * (1.0 - amount)
                        + 0.5
                    )

    return PngImage(template.width, template.height, 3, bytes(output))


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def compose_store_screenshot(
    capture_path: Path,
    template_path: Path,
    play_out: Path,
    fastlane_out: Path,
) -> dict[str, object]:
    template = decode_png(template_path)
    capture = decode_png(capture_path)

    if (template.width, template.height) != (CANVAS_WIDTH, CANVAS_HEIGHT):
        raise ValueError(
            f"template must be {CANVAS_WIDTH}x{CANVAS_HEIGHT}, "
            f"got {template.width}x{template.height}"
        )
    if (capture.width, capture.height) != (CAPTURE_WIDTH, CAPTURE_HEIGHT):
        raise ValueError(
            f"capture must be {CAPTURE_WIDTH}x{CAPTURE_HEIGHT}, "
            f"got {capture.width}x{capture.height}"
        )

    output = compose(
        template,
        capture,
        capture_top=CAPTURE_TOP,
        capture_bottom=CAPTURE_BOTTOM,
        target_x=CONTENT_X,
        target_y=CONTENT_Y,
        target_width=CONTENT_WIDTH,
        target_height=CONTENT_HEIGHT,
        radius=CORNER_RADIUS,
    )
    encode_png(play_out, output)
    if fastlane_out != play_out:
        encode_png(fastlane_out, output)

    return {
        "width": output.width,
        "height": output.height,
        "play_sha256": _sha256(play_out),
        "fastlane_sha256": _sha256(fastlane_out),
    }


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, required=True)
    parser.add_argument("--template", type=Path, required=True)
    parser.add_argument("--play-out", type=Path, required=True)
    parser.add_argument("--fastlane-out", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    result = compose_store_screenshot(
        args.capture,
        args.template,
        args.play_out,
        args.fastlane_out,
    )
    print(f"capture: {args.capture}")
    print(f"template: {args.template}")
    print(f"play: {args.play_out} sha256={result['play_sha256']}")
    print(f"fastlane: {args.fastlane_out} sha256={result['fastlane_sha256']}")
    print(f"output: {result['width']}x{result['height']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
