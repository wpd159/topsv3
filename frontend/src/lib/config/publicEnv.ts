export type PublicEnv = {
  appEnv: string;
  apiBaseUrl: string;
  canonicalDomain: string;
};

function withoutTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

export const publicEnv: PublicEnv = {
  appEnv: process.env.NEXT_PUBLIC_APP_ENV ?? "local",
  apiBaseUrl: withoutTrailingSlash(process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"),
  canonicalDomain: withoutTrailingSlash(process.env.NEXT_PUBLIC_CANONICAL_DOMAIN ?? "http://localhost")
};
