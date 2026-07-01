export type PublicEnv = {
  appEnv: string;
  apiBaseUrl: string;
  canonicalDomain: string;
};

const LOCAL_API_BASE_URL = "http://localhost:8080";

function withoutTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

function resolveApiBaseUrl(appEnv: string): string {
  const configured = process.env.NEXT_PUBLIC_API_BASE_URL;
  if (configured) {
    return withoutTrailingSlash(configured);
  }
  return appEnv === "local" ? LOCAL_API_BASE_URL : "";
}

const appEnv = process.env.NEXT_PUBLIC_APP_ENV ?? "local";

export const publicEnv: PublicEnv = {
  appEnv,
  apiBaseUrl: resolveApiBaseUrl(appEnv),
  canonicalDomain: withoutTrailingSlash(process.env.NEXT_PUBLIC_CANONICAL_DOMAIN ?? "http://localhost")
};
