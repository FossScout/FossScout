from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import compose_store_screenshot as compose_module  # noqa: E402


def solid_image(
    width: int,
    height: int,
    channels: int,
    color: tuple[int, ...],
) -> compose_module.PngImage:
    return compose_module.PngImage(
        width,
        height,
        channels,
        bytes(color) * (width * height),
    )


class PngTests(unittest.TestCase):
    def test_rgb_round_trip(self) -> None:
        image = compose_module.PngImage(
            2,
            2,
            3,
            bytes(
                [
                    255,
                    0,
                    0,
                    0,
                    255,
                    0,
                    0,
                    0,
                    255,
                    10,
                    20,
                    30,
                ]
            ),
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rgb.png"
            compose_module.encode_png(path, image)
            self.assertEqual(compose_module.decode_png(path), image)

    def test_rgba_round_trip(self) -> None:
        image = compose_module.PngImage(
            2,
            1,
            4,
            bytes([1, 2, 3, 4, 5, 6, 7, 8]),
        )
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rgba.png"
            compose_module.encode_png(path, image)
            self.assertEqual(compose_module.decode_png(path), image)


class ResizeTests(unittest.TestCase):
    def test_solid_color_is_preserved(self) -> None:
        image = solid_image(8, 8, 3, (200, 100, 50))
        resized = compose_module.resize_rgb(image, 0, 8, 4, 4)
        self.assertEqual(resized.width, 4)
        self.assertEqual(resized.height, 4)
        self.assertEqual(resized.channels, 3)
        for y in range(resized.height):
            for x in range(resized.width):
                self.assertEqual(resized.pixel(x, y), (200, 100, 50))


class RoundedCoverageTests(unittest.TestCase):
    def test_corners_are_rounded(self) -> None:
        coverage = compose_module.rounded_coverage(20, 20, 5)
        self.assertEqual(coverage[0], 0.0)
        self.assertEqual(coverage[10 * 20 + 10], 1.0)
        self.assertEqual(coverage[0 * 20 + 10], 1.0)
        self.assertEqual(coverage[5 * 20 + 5], 1.0)


class ComposeTests(unittest.TestCase):
    def test_content_is_replaced_and_frame_is_preserved(self) -> None:
        frame = (10, 20, 30)
        template = solid_image(32, 32, 3, frame)

        capture_pixels = bytearray()
        for y in range(30):
            if y < 3:
                color = (255, 0, 0, 255)
            elif y >= 27:
                color = (0, 0, 255, 255)
            else:
                color = (0, 255, 0, 255)
            capture_pixels.extend(bytes(color) * 20)
        capture = compose_module.PngImage(20, 30, 4, bytes(capture_pixels))

        output = compose_module.compose(
            template,
            capture,
            capture_top=3,
            capture_bottom=27,
            target_x=4,
            target_y=4,
            target_width=24,
            target_height=24,
            radius=4,
        )

        self.assertEqual((output.width, output.height, output.channels), (32, 32, 3))
        self.assertEqual(output.pixel(0, 0), frame)
        self.assertEqual(output.pixel(31, 31), frame)
        self.assertEqual(output.pixel(4, 4), frame)
        self.assertEqual(output.pixel(16, 16), (0, 255, 0))
        self.assertEqual(output.pixel(8, 8), (0, 255, 0))
        self.assertEqual(output.pixel(4, 16), (0, 255, 0))


if __name__ == "__main__":
    unittest.main()
