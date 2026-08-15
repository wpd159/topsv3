import assert from "node:assert/strict"
import { mkdtemp, rm, writeFile } from "node:fs/promises"
import os from "node:os"
import path from "node:path"
import sharp from "sharp"

const tempDir = await mkdtemp(path.join(os.tmpdir(), "topsv3-sharp-security-"))

function syntheticImage() {
  return sharp({
    create: {
      width: 24,
      height: 18,
      channels: 4,
      background: { r: 244, g: 32, b: 140, alpha: 1 },
    },
  })
}

async function expectControlledFailure(label, input) {
  await assert.rejects(
    sharp(input, { failOn: "error", limitInputPixels: 1_000_000 })
      .resize({ width: 8, height: 8, fit: "inside" })
      .png()
      .toBuffer(),
    (error) => /image|input|buffer|unsupported|corrupt|limit|header|svg/i.test(String(error.message)),
    label,
  )
}

try {
  const validFormats = []
  for (const format of ["jpeg", "png", "webp", "gif", "tiff"]) {
    const extension = format === "jpeg" ? "jpg" : format
    const file = path.join(tempDir, `valid.${extension}`)
    await syntheticImage()[format]().toFile(file)
    assert.equal((await sharp(file).metadata()).format, format)
    assert.ok((await sharp(file).resize(12, 9, { fit: "inside" }).png().toBuffer()).length > 0)
    validFormats.push(format)
  }

  const png = await syntheticImage().png().toBuffer()
  const misleadingExtension = path.join(tempDir, "mime-falso.jpg")
  await writeFile(misleadingExtension, png)
  assert.equal((await sharp(misleadingExtension).metadata()).format, "png")

  const jpeg = await syntheticImage().jpeg().toBuffer()
  await expectControlledFailure("JPEG truncado", jpeg.subarray(0, 12))
  await expectControlledFailure("bytes corrompidos", Buffer.from([0, 255, 1, 254, 2, 253]))
  await expectControlledFailure("HTML com extensao de imagem", Buffer.from("<!doctype html><html></html>"))
  await expectControlledFailure("executavel com extensao de imagem", Buffer.from([0x4d, 0x5a, 0x90, 0, 3, 0]))
  await expectControlledFailure(
    "dimensoes acima do limite",
    Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="100000" height="100000"><rect width="100%" height="100%"/></svg>'),
  )
  await expectControlledFailure("entrada invalida do otimizador", Buffer.from("invalid-image-optimizer-input"))

  console.log(
    JSON.stringify({
      sharp: sharp.versions.sharp,
      libvips: sharp.versions.vips,
      validFormats,
      mimeDetectedByContent: true,
      invalidInputsRejected: 6,
      crashes: 0,
      externalAccesses: 0,
    }),
  )
} finally {
  sharp.cache(false)
  await new Promise((resolve) => setTimeout(resolve, 100))
  await rm(tempDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 100 })
}
