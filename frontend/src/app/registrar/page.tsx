import { redirect } from "next/navigation";

export default function RegistrarPage() {
  redirect("/entrar?modo=registro");
}
