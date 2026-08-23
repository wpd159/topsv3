export type StoryAudioAvailability = "UNKNOWN" | "PRESENT" | "ABSENT";
export type StorySoundPreference = "AUDIBLE" | "MUTED";

type VideoWithAudioSignals = HTMLVideoElement & {
  audioTracks?: { length: number };
  mozHasAudio?: boolean;
  webkitAudioDecodedByteCount?: number;
};

export function detectarDisponibilidadeAudio(
  video: HTMLVideoElement,
): StoryAudioAvailability {
  const extended = video as VideoWithAudioSignals;

  if (typeof extended.audioTracks?.length === "number") {
    return extended.audioTracks.length > 0 ? "PRESENT" : "ABSENT";
  }

  if (typeof extended.mozHasAudio === "boolean") {
    return extended.mozHasAudio ? "PRESENT" : "ABSENT";
  }

  if (typeof extended.webkitAudioDecodedByteCount === "number") {
    if (extended.webkitAudioDecodedByteCount > 0) return "PRESENT";
    if (video.currentTime >= 0.35 || video.ended) return "ABSENT";
  }

  return "UNKNOWN";
}

export function erroBloqueioAutoplay(error: unknown): boolean {
  return error instanceof DOMException
    ? error.name === "NotAllowedError"
    : Boolean(
        error &&
        typeof error === "object" &&
        "name" in error &&
        error.name === "NotAllowedError",
      );
}
