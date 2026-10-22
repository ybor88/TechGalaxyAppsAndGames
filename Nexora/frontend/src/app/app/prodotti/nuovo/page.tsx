"use client";
// Nuovo prodotto a catalogo. © Roberto Di Flumeri
import Link from "next/link";
import { useRouter } from "next/navigation";
import FormProdotto from "@/components/FormProdotto";
import { Titolo } from "@/components/ui";

export default function NuovoProdotto() {
  const router = useRouter();
  return (
    <>
      <Titolo titolo="Nuovo prodotto" sottotitolo="Dopo il salvataggio potrai caricare le immagini e la giacenza iniziale"
        indietro={<Link href="/app/prodotti" className="link">← Prodotti</Link>} />
      <FormProdotto onSalvato={(id) => router.replace(`/app/prodotti/${id}`)} />
    </>
  );
}
