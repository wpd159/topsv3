"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { SpeakerWaveIcon, SpeakerXMarkIcon } from "@heroicons/react/24/solid";
import {
  detectarDisponibilidadeAudio,
  erroBloqueioAutoplay,
  type StoryAudioAvailability,
  type StorySoundPreference,
} from "./story-video-audio";

type Props = {
  mediaKey: string;
  src: string;
  videoClassName?: string;
  soundPreference: StorySoundPreference;
  onSoundPreferenceChange: (preference: StorySoundPreference) => void;
  onReady: () => void;
  onEnded: () => void;
  onError: () => void;
  onProgress: (progress: number) => void;
};

export function StoryVideoPlayer({
  mediaKey,
  src,
  videoClassName,
  soundPreference,
  onSoundPreferenceChange,
  onReady,
  onEnded,
  onError,
  onProgress,
}: Props) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const startedRef = useRef(false);
  const [audioAvailability, setAudioAvailability] =
    useState<StoryAudioAvailability>("UNKNOWN");
  const [muted, setMuted] = useState(soundPreference === "MUTED");

  const updateAudioAvailability = useCallback((video: HTMLVideoElement) => {
    const next = detectarDisponibilidadeAudio(video);
    setAudioAvailability((current) => (next === "UNKNOWN" ? current : next));
  }, []);

  const startPlayback = useCallback(
    async (video: HTMLVideoElement) => {
      if (startedRef.current) return;
      startedRef.current = true;

      const tryAudible = soundPreference === "AUDIBLE";
      video.volume = 1;
      video.muted = !tryAudible;
      setMuted(video.muted);

      try {
        await video.play();
      } catch (error) {
        if (!tryAudible || !erroBloqueioAutoplay(error)) return;

        video.muted = true;
        setMuted(true);
        onSoundPreferenceChange("MUTED");
        try {
          await video.play();
        } catch {
          // O evento onError continua sendo a autoridade para falha de mídia.
        }
      }
    },
    [onSoundPreferenceChange, soundPreference],
  );

  const enableSound = async () => {
    const video = videoRef.current;
    if (!video || audioAvailability !== "PRESENT") return;

    video.volume = 1;
    video.muted = false;
    try {
      await video.play();
      setMuted(false);
      onSoundPreferenceChange("AUDIBLE");
    } catch {
      video.muted = true;
      setMuted(true);
      onSoundPreferenceChange("MUTED");
    }
  };

  const disableSound = () => {
    const video = videoRef.current;
    if (!video) return;
    video.muted = true;
    setMuted(true);
    onSoundPreferenceChange("MUTED");
  };

  useEffect(() => {
    startedRef.current = false;
    setAudioAvailability("UNKNOWN");

    return () => {
      const video = videoRef.current;
      if (!video) return;
      video.pause();
      video.removeAttribute("src");
      videoRef.current = null;
    };
  }, [mediaKey]);

  return (
    <div className="relative h-full min-h-0 w-full max-w-[100vw] overflow-hidden bg-black">
      <video
        ref={videoRef}
        src={src}
        className={videoClassName}
        muted={muted}
        playsInline
        preload="metadata"
        controls={false}
        onLoadedMetadata={(event) => {
          updateAudioAvailability(event.currentTarget);
          void startPlayback(event.currentTarget);
        }}
        onPlaying={(event) => {
          updateAudioAvailability(event.currentTarget);
          onReady();
        }}
        onEnded={onEnded}
        onError={onError}
        onTimeUpdate={(event) => {
          const video = event.currentTarget;
          updateAudioAvailability(video);
          if (!video.duration || Number.isNaN(video.duration)) return;
          onProgress(Math.min(1, video.currentTime / video.duration));
        }}
      />

      {audioAvailability === "PRESENT" ? (
        <button
          type="button"
          onClick={(event) => {
            event.preventDefault();
            event.stopPropagation();
            if (muted) void enableSound();
            else disableSound();
          }}
          className="absolute right-3 top-3 z-40 flex min-h-10 items-center gap-2 rounded-full bg-black/75 px-3 py-2 text-xs font-semibold text-white ring-1 ring-white/25 transition hover:bg-black/90 focus:outline-none focus-visible:ring-2 focus-visible:ring-white"
          aria-label={muted ? "Ativar som" : "Desativar som"}
        >
          {muted ? (
            <SpeakerXMarkIcon className="h-4 w-4" aria-hidden="true" />
          ) : (
            <SpeakerWaveIcon className="h-4 w-4" aria-hidden="true" />
          )}
          {muted ? "Ativar som" : "Desativar som"}
        </button>
      ) : null}

      {audioAvailability === "ABSENT" ? (
        <p
          className="pointer-events-none absolute right-3 top-3 z-40 rounded-full bg-black/75 px-3 py-2 text-xs font-medium text-white/90 ring-1 ring-white/20"
          role="status"
        >
          Este vídeo não possui áudio
        </p>
      ) : null}
    </div>
  );
}
