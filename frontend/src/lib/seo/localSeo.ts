import type { Metadata } from "next";

import { publicEnv } from "../config/publicEnv";

const LOCAL_FALLBACK_ORIGIN = "http://localhost";

export function localCanonicalOrigin(): string {
  const configured = (publicEnv.canonicalDomain || LOCAL_FALLBACK_ORIGIN).replace(/\/+$/, "");

  if (publicEnv.appEnv === "local") {
    if (configured.startsWith("http://localhost") || configured.startsWith("http://127.0.0.1")) {
      return configured;
    }
    return LOCAL_FALLBACK_ORIGIN;
  }

  return configured || LOCAL_FALLBACK_ORIGIN;
}

export function localUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  return new URL(normalizedPath, `${localCanonicalOrigin()}/`).toString();
}

export function routeSegment(value: string): string {
  return encodeURIComponent(value.trim() || "skeleton-local");
}

export function skeletonMetadata(title: string, path: string): Metadata {
  return {
    title,
    description: "Skeleton local da V3, sem dados reais e sem indexação.",
    alternates: {
      canonical: localUrl(path)
    },
    robots: {
      index: false,
      follow: false,
      googleBot: {
        index: false,
        follow: false
      }
    }
  };
}
