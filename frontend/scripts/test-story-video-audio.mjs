import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import ts from "typescript";

const [audioSource, playerSource, viewerSource] = await Promise.all([
  readFile(
    new URL("../src/components/stories/story-video-audio.ts", import.meta.url),
    "utf8",
  ),
  readFile(
    new URL(
      "../src/components/stories/story-video-player.tsx",
      import.meta.url,
    ),
    "utf8",
  ),
  readFile(
    new URL(
      "../src/components/stories/story-viewer-dialog.tsx",
      import.meta.url,
    ),
    "utf8",
  ),
]);

const transpiled = ts.transpileModule(audioSource, {
  compilerOptions: {
    module: ts.ModuleKind.ES2022,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const audio = await import(
  `data:text/javascript;base64,${Buffer.from(transpiled).toString("base64")}`
);

assert.equal(
  audio.detectarDisponibilidadeAudio({ audioTracks: { length: 1 } }),
  "PRESENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({ audioTracks: { length: 0 } }),
  "ABSENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({ mozHasAudio: true }),
  "PRESENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({ mozHasAudio: false }),
  "ABSENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({
    webkitAudioDecodedByteCount: 1024,
    currentTime: 0,
  }),
  "PRESENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({
    webkitAudioDecodedByteCount: 0,
    currentTime: 0.5,
  }),
  "ABSENT",
);
assert.equal(
  audio.detectarDisponibilidadeAudio({
    webkitAudioDecodedByteCount: 0,
    currentTime: 0,
  }),
  "UNKNOWN",
);
assert.equal(audio.erroBloqueioAutoplay({ name: "NotAllowedError" }), true);
assert.equal(audio.erroBloqueioAutoplay({ name: "NotSupportedError" }), false);

assert.match(playerSource, /const tryAudible = soundPreference === "AUDIBLE"/);
assert.match(playerSource, /erroBloqueioAutoplay\(error\)/);
assert.match(
  playerSource,
  /video\.muted = true[\s\S]*onSoundPreferenceChange\("MUTED"\)/,
);
assert.match(playerSource, /Ativar som/);
assert.match(playerSource, /Este vídeo não possui áudio/);
assert.match(playerSource, /audioAvailability === "PRESENT"/);
assert.match(playerSource, /audioAvailability === "ABSENT"/);
assert.match(playerSource, /video\.pause\(\)/);
assert.match(playerSource, /video\.removeAttribute\("src"\)/);
assert.match(playerSource, /preload="metadata"/);
assert.match(playerSource, /controls=\{false\}/);
assert.doesNotMatch(playerSource, /autoPlay/);

assert.equal((viewerSource.match(/<StoryVideoPlayer/g) ?? []).length, 2);
assert.match(viewerSource, /setStorySoundPreference\("AUDIBLE"\)/);
assert.doesNotMatch(viewerSource, /<video/);
assert.doesNotMatch(viewerSource, /\.play\(\)\.catch\(\(\) => \{\}\)/);

console.log("STORY_VIDEO_AUDIO_CHECKS=27");
console.log("STORY_VIDEO_AUDIO_RESULT=OK");
