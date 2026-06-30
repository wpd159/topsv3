export type FutureSchemaType =
  | "Organization"
  | "WebSite"
  | "BreadcrumbList"
  | "FAQPage"
  | "CollectionPage"
  | "WebPage";

export const futureSchemaTypes: FutureSchemaType[] = [
  "Organization",
  "WebSite",
  "BreadcrumbList",
  "FAQPage",
  "CollectionPage",
  "WebPage"
];

export function isFutureSchemaType(value: string): value is FutureSchemaType {
  return futureSchemaTypes.includes(value as FutureSchemaType);
}
