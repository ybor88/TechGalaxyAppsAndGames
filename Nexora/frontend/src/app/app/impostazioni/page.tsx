"use client";
// Impostazioni: azienda, utenti e ruoli, sicurezza personale (password, verifica in due passaggi), client API OAuth2, SSO e registro attività. © Roberto Di Flumeri
import { Copy, KeyRound, Plus, ShieldCheck, ShieldOff, UserPlus } from "lucide-react";
import { useEffect, useState } from "react";
import { Avviso, Campo, Caricamento, Errore, Interruttore, Modale, NOMI_RUOLO, Schede, Titolo, useAzione, useDati } from "@/components/ui";
import { api } from "@/lib/api";
import { dataOra, fa } from "@/lib/formato";
import { useSessione } from "@/lib/sessione";

type Scheda = "azienda" | "utenti" | "sicurezza" | "api" | "sso" | "audit";

export default function Impostazioni() {
  const { puo } = useSessione();
  const admin = puo("impostazioni");
  const [scheda, setScheda] = useState<Scheda>(admin ? "azienda" : "sicurezza");
  useEffect(() => { const s = new URLSearchParams(window.location.search).get("scheda") as Scheda | null; if (s) setScheda(s); }, []);
  const voci: { id: Scheda; etichetta: string }[] = [
    ...(admin ? [{ id: "azienda" as const, etichetta: "Azienda" }, { id: "utenti" as const, etichetta: "Utenti e ruoli" }] : []),
    { id: "sicurezza", etichetta: "Il mio account" },
    ...(admin ? [{ id: "api" as const, etichetta: "Client API" }, { id: "sso" as const, etichetta: "SSO" }, { id: "audit" as const, etichetta: "Registro attività" }] : []),
  ];
  return (
    <>
      <Titolo titolo="Impostazioni" sottotitolo="Dati aziendali, accessi e sicurezza" />
      <Schede voci={voci} attiva={scheda} onCambia={setScheda} />
      {scheda === "azienda" && <Azienda />}
      {scheda === "utenti" && <Utenti />}
      {scheda === "sicurezza" && <Sicurezza />}
      {scheda === "api" && <ClientApi />}
      {scheda === "sso" && <Sso />}
      {scheda === "audit" && <Audit />}
    </>
  );
}

function Azienda() {
  const { ricarica: ricaricaSessione } = useSessione();
  const { dati, errore } = useDati<any>("/api/azienda");
  const [d, setD] = useState<Record<string, any> | null>(null);
  const azione = useAzione();
  const [ok, setOk] = useState(false);
  useEffect(() => { if (dati) setD(Object.fromEntries(Object.entries(dati).map(([k, v]) => [k, v ?? ""]))); }, [dati]);
  if (errore) return <Errore messaggio={errore} />;
  if (!d) return <Caricamento />;
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => setD({ ...d, [k]: e.target.value });
  const salva = (e: React.FormEvent) => {
    e.preventDefault();
    setOk(false);
    azione.esegui(async () => {
      const campi = ["ragioneSociale", "partitaIva", "codiceFiscale", "indirizzo", "cap", "citta", "provincia", "regimeFiscale", "email", "pec", "telefono", "iban"];
      await api("/api/azienda", { metodo: "PATCH", corpo: Object.fromEntries(campi.map((k) => [k, k === "iban" ? String(d[k]).replace(/\s/g, "").toUpperCase() : d[k]])) });
      await ricaricaSessione();
      setOk(true);
    });
  };
  return (
    <form onSubmit={salva} className="scheda grid gap-4 md:grid-cols-6">
      <p className="text-sm text-tenue md:col-span-6">Questi dati compaiono su PDF e fatture elettroniche (cedente/prestatore).</p>
      <Campo etichetta="Ragione sociale" className="md:col-span-4"><input className="campo" value={d.ragioneSociale} onChange={set("ragioneSociale")} required /></Campo>
      <Campo etichetta="Regime fiscale" className="md:col-span-2">
        <select className="campo" value={d.regimeFiscale} onChange={set("regimeFiscale")}>
          {[["RF01", "RF01 · Ordinario"], ["RF02", "RF02 · Contribuenti minimi"], ["RF19", "RF19 · Forfettario"], ["RF04", "RF04 · Agricoltura"], ["RF18", "RF18 · Altro"]].map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
      </Campo>
      <Campo etichetta="Partita IVA" className="md:col-span-2"><input className="campo font-mono" value={d.partitaIva} onChange={set("partitaIva")} required /></Campo>
      <Campo etichetta="Codice fiscale" className="md:col-span-2"><input className="campo font-mono" value={d.codiceFiscale} onChange={set("codiceFiscale")} /></Campo>
      <Campo etichetta="IBAN" className="md:col-span-2"><input className="campo font-mono" value={d.iban} onChange={set("iban")} /></Campo>
      <Campo etichetta="Indirizzo" className="md:col-span-3"><input className="campo" value={d.indirizzo} onChange={set("indirizzo")} required /></Campo>
      <Campo etichetta="CAP"><input className="campo" value={d.cap} onChange={set("cap")} pattern="\d{5}" required /></Campo>
      <Campo etichetta="Città"><input className="campo" value={d.citta} onChange={set("citta")} required /></Campo>
      <Campo etichetta="Prov."><input className="campo uppercase" value={d.provincia} onChange={set("provincia")} maxLength={2} required /></Campo>
      <Campo etichetta="Email" className="md:col-span-2"><input className="campo" type="email" value={d.email} onChange={set("email")} /></Campo>
      <Campo etichetta="PEC" className="md:col-span-2"><input className="campo" type="email" value={d.pec} onChange={set("pec")} /></Campo>
      <Campo etichetta="Telefono" className="md:col-span-2"><input className="campo" value={d.telefono} onChange={set("telefono")} /></Campo>
      {azione.errore && <div className="md:col-span-6"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
      {ok && <div className="md:col-span-6"><Avviso tipo="ok">Dati aziendali aggiornati.</Avviso></div>}
      <div className="flex justify-end md:col-span-6"><button className="btn-primario" disabled={azione.inCorso}>Salva</button></div>
    </form>
  );
}

function Utenti() {
  const { utente } = useSessione();
  const { dati, errore, ricarica } = useDati<any[]>("/api/utenti");
  const { dati: ruoli } = useDati<{ ruolo: string; permessi: string[] }[]>("/api/ruoli");
  const [invito, setInvito] = useState(false);
  const [creato, setCreato] = useState<{ email: string; passwordTemporanea: string } | null>(null);
  const azione = useAzione();
  const modifica = (id: string, corpo: Record<string, unknown>) => azione.esegui(async () => { await api(`/api/utenti/${id}`, { metodo: "PATCH", corpo }); await ricarica(); });
  if (errore) return <Errore messaggio={errore} />;
  return (
    <div className="space-y-6">
      {creato && <Avviso tipo="ok">Utente <strong>{creato.email}</strong> creato. Password temporanea: <code className="font-bold">{creato.passwordTemporanea}</code> — comunicala in modo sicuro; potrà cambiarla dal proprio account.</Avviso>}
      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      <div className="flex justify-end"><button className="btn-primario" onClick={() => setInvito(true)}><UserPlus className="h-4 w-4" /> Nuovo utente</button></div>
      {!dati ? <Caricamento /> : (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Utente</th><th>Ruolo</th><th>Verifica in 2 passaggi</th><th>Ultimo accesso</th><th>Attivo</th></tr></thead>
            <tbody>{dati.map((u) => (
              <tr key={u.id}>
                <td><p className="font-medium">{u.nome}{u.id === utente?.id && " (tu)"}</p><p className="text-xs text-tenue">{u.email}</p></td>
                <td><select className="campo w-44" value={u.ruolo} disabled={u.id === utente?.id} onChange={(e) => modifica(u.id, { ruolo: e.target.value })} aria-label={`Ruolo di ${u.nome}`}>
                  {Object.entries(NOMI_RUOLO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></td>
                <td>{u.mfaAttivo ? <span className="pillola bg-ok/15 text-ok"><ShieldCheck className="h-3 w-3" /> Attiva</span> : <span className="pillola bg-bordo text-tenue">Non attiva</span>}</td>
                <td className="text-tenue">{u.ultimoAccesso ? fa(u.ultimoAccesso) : "mai"}</td>
                <td><Interruttore attivo={u.attivo} etichetta="" disabilitato={u.id === utente?.id} onChange={(v) => modifica(u.id, { attivo: v })} /></td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {ruoli && (
        <section className="scheda">
          <h2 className="mb-3 font-semibold">Cosa può fare ogni ruolo</h2>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">{ruoli.map((r) => (
            <div key={r.ruolo} className="rounded-xl border border-bordo p-3 text-sm"><p className="font-semibold">{NOMI_RUOLO[r.ruolo]}</p>
              <p className="mt-1 text-xs text-tenue">{r.permessi.length ? r.permessi.join(", ") : "consultazione di tutti i dati, nessuna modifica"}</p></div>
          ))}</div>
        </section>
      )}
      {invito && <Invito onChiudi={() => setInvito(false)} onFatto={(r) => { setInvito(false); setCreato(r); ricarica(); }} />}
    </div>
  );
}

function Invito({ onChiudi, onFatto }: { onChiudi: () => void; onFatto: (r: any) => void }) {
  const [d, setD] = useState({ nome: "", email: "", ruolo: "vendite" });
  const { inCorso, errore, esegui } = useAzione();
  return (
    <Modale titolo="Nuovo utente" onChiudi={onChiudi}>
      <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); esegui(async () => onFatto(await api("/api/utenti", { metodo: "POST", corpo: d }))); }}>
        <Campo etichetta="Nome e cognome"><input className="campo" value={d.nome} onChange={(e) => setD({ ...d, nome: e.target.value })} required minLength={2} /></Campo>
        <Campo etichetta="Email"><input className="campo" type="email" value={d.email} onChange={(e) => setD({ ...d, email: e.target.value })} required /></Campo>
        <Campo etichetta="Ruolo"><select className="campo" value={d.ruolo} onChange={(e) => setD({ ...d, ruolo: e.target.value })}>
          {Object.entries(NOMI_RUOLO).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Campo>
        {errore && <Avviso tipo="errore">{errore}</Avviso>}
        <button className="btn-primario w-full" disabled={inCorso}>Crea utente</button>
      </form>
    </Modale>
  );
}

function Sicurezza() {
  const { utente, ricarica, esci } = useSessione();
  const [nome, setNome] = useState(utente?.nome ?? "");
  const [pw, setPw] = useState({ attuale: "", nuova: "" });
  const [mfa, setMfa] = useState<{ segreto: string; qr: string } | null>(null);
  const [codice, setCodice] = useState("");
  const [recupero, setRecupero] = useState<string[] | null>(null);
  const [disattiva, setDisattiva] = useState("");
  const [ok, setOk] = useState<string | null>(null);
  const azione = useAzione();
  if (!utente) return null;
  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <section className="scheda space-y-4">
        <h2 className="font-semibold">Profilo</h2>
        <form className="flex gap-2" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => { await api("/api/auth/me", { metodo: "PATCH", corpo: { nome } }); await ricarica(); setOk("Nome aggiornato"); }); }}>
          <input className="campo" value={nome} onChange={(e) => setNome(e.target.value)} minLength={2} aria-label="Nome" /><button className="btn-secondario">Salva</button>
        </form>
        <p className="text-sm text-tenue">{utente.email} · {NOMI_RUOLO[utente.ruolo]}</p>
        <h2 className="pt-2 font-semibold">Cambia password</h2>
        <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => {
          await api("/api/auth/password", { metodo: "POST", corpo: pw });
          alert("Password cambiata: per sicurezza tutte le sessioni sono state chiuse. Accedi di nuovo.");
          await esci(); window.location.href = "/accedi";
        }); }}>
          <input className="campo" type="password" placeholder="Password attuale" value={pw.attuale} onChange={(e) => setPw({ ...pw, attuale: e.target.value })} required autoComplete="current-password" aria-label="Password attuale" />
          <input className="campo" type="password" placeholder="Nuova password (min. 10 caratteri, lettere e numeri)" value={pw.nuova} onChange={(e) => setPw({ ...pw, nuova: e.target.value })} required minLength={10} autoComplete="new-password" aria-label="Nuova password" />
          <button className="btn-secondario" disabled={azione.inCorso}><KeyRound className="h-4 w-4" /> Cambia password</button>
        </form>
      </section>
      <section className="scheda space-y-4">
        <h2 className="flex items-center gap-2 font-semibold"><ShieldCheck className="h-4 w-4 text-ok" /> Verifica in due passaggi (TOTP)</h2>
        <p className="text-sm text-tenue">Oltre alla password serve un codice a 6 cifre generato da un&apos;app gratuita come Google Authenticator, Microsoft Authenticator, Aegis o 2FAS.</p>
        {recupero ? (
          <Avviso tipo="ok"><p className="font-medium">Verifica attivata. Conserva questi codici di recupero monouso in un luogo sicuro:</p>
            <ul className="mt-2 grid grid-cols-2 gap-1 font-mono">{recupero.map((c) => <li key={c}>{c}</li>)}</ul>
            <button className="btn-secondario btn-piccolo mt-2" onClick={() => navigator.clipboard?.writeText(recupero.join("\n"))}><Copy className="h-3.5 w-3.5" /> Copia</button></Avviso>
        ) : utente.mfaAttivo ? (
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => { await api("/api/auth/mfa/disattiva", { metodo: "POST", corpo: { password: disattiva } }); await ricarica(); setDisattiva(""); setOk("Verifica in due passaggi disattivata"); }); }}>
            <Avviso tipo="ok">La verifica in due passaggi è attiva sul tuo account.</Avviso>
            <input className="campo" type="password" placeholder="Password per disattivarla" value={disattiva} onChange={(e) => setDisattiva(e.target.value)} required aria-label="Password" />
            <button className="btn-secondario text-errore" disabled={azione.inCorso}><ShieldOff className="h-4 w-4" /> Disattiva</button>
          </form>
        ) : mfa ? (
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => { const r = await api("/api/auth/mfa/attiva", { metodo: "POST", corpo: { codice } }); setRecupero(r.codiciRecupero); setMfa(null); await ricarica(); }); }}>
            <div className="flex flex-wrap items-center gap-4">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={mfa.qr} alt="QR code per l'app di autenticazione" className="h-44 w-44 rounded-xl bg-white p-2" />
              <div className="text-sm"><p>1. Inquadra il QR con l&apos;app.</p><p>2. Oppure inserisci la chiave:</p><code className="mt-1 block break-all font-bold">{mfa.segreto}</code><p className="mt-2">3. Scrivi il codice che compare.</p></div>
            </div>
            <input className="campo text-center text-xl tracking-[0.4em]" value={codice} onChange={(e) => setCodice(e.target.value)} inputMode="numeric" maxLength={6} placeholder="000000" required aria-label="Codice a 6 cifre" />
            <button className="btn-primario" disabled={azione.inCorso || codice.length !== 6}>Attiva</button>
          </form>
        ) : (
          <button className="btn-primario" disabled={azione.inCorso} onClick={() => azione.esegui(async () => setMfa(await api("/api/auth/mfa/configura", { metodo: "POST" })))}><ShieldCheck className="h-4 w-4" /> Configura</button>
        )}
      </section>
      {ok && <div className="xl:col-span-2"><Avviso tipo="ok">{ok}</Avviso></div>}
      {azione.errore && <div className="xl:col-span-2"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
    </div>
  );
}

function ClientApi() {
  const { dati, errore, ricarica } = useDati<any[]>("/api/api-client");
  const { dati: scopes } = useDati<string[]>("/api/api-client/scopes");
  const [nuovo, setNuovo] = useState(false);
  const [creato, setCreato] = useState<any>(null);
  const [d, setD] = useState<{ nome: string; scopes: string[] }>({ nome: "", scopes: [] });
  const azione = useAzione();
  if (errore) return <Errore messaggio={errore} />;
  const origine = typeof window !== "undefined" ? window.location.origin : "";
  return (
    <div className="space-y-6">
      <Avviso tipo="info">Le applicazioni esterne ottengono un token con <code>POST {origine}/api/oauth/token</code> (grant <code>client_credentials</code>) e chiamano le API REST
        con <code>Authorization: Bearer …</code>. Ogni client vede solo gli scope concessi.</Avviso>
      {creato && (
        <Avviso tipo="ok"><p className="font-medium">{creato.avviso}</p>
          <p className="mt-1">client_id: <code className="font-bold">{creato.clientId}</code></p>
          <p>client_secret: <code className="break-all font-bold">{creato.clientSecret}</code></p>
          <pre className="mt-2 overflow-x-auto rounded-lg bg-notte p-3 text-xs text-white">{`curl -X POST ${origine}/api/oauth/token \\\n  -d grant_type=client_credentials -d client_id=${creato.clientId} -d client_secret=${creato.clientSecret}`}</pre></Avviso>
      )}
      {azione.errore && <Avviso tipo="errore">{azione.errore}</Avviso>}
      <div className="flex justify-end"><button className="btn-primario" onClick={() => setNuovo(true)}><Plus className="h-4 w-4" /> Nuovo client API</button></div>
      {!dati ? <Caricamento /> : (
        <div className="scheda overflow-x-auto p-0">
          <table className="tabella">
            <thead><tr><th>Nome</th><th>Client ID</th><th>Scope</th><th>Ultimo uso</th><th>Stato</th><th /></tr></thead>
            <tbody>{dati.map((c) => (
              <tr key={c.id}><td className="font-medium">{c.nome}</td><td className="font-mono text-xs">{c.clientId}</td>
                <td className="text-xs">{c.scopes.map((s: string) => <span key={s} className="pillola mb-1 mr-1 bg-fondo">{s}</span>)}</td>
                <td className="text-tenue">{c.ultimoUso ? fa(c.ultimoUso) : "mai"}</td>
                <td>{c.revocato ? <span className="pillola bg-errore/15 text-errore">Revocato</span> : <span className="pillola bg-ok/15 text-ok">Attivo</span>}</td>
                <td>{!c.revocato && <button className="btn-secondario btn-piccolo text-errore" onClick={() => confirm(`Revocare «${c.nome}»?`) && azione.esegui(async () => { await api(`/api/api-client/${c.id}`, { metodo: "DELETE" }); await ricarica(); })}>Revoca</button>}</td></tr>
            ))}</tbody>
          </table>
          {!dati.length && <p className="p-5 text-sm text-tenue">Nessun client API.</p>}
        </div>
      )}
      {nuovo && (
        <Modale titolo="Nuovo client API" onChiudi={() => setNuovo(false)}>
          <form className="space-y-3" onSubmit={(e) => { e.preventDefault(); azione.esegui(async () => {
            setCreato(await api("/api/api-client", { metodo: "POST", corpo: d })); setNuovo(false); setD({ nome: "", scopes: [] }); await ricarica();
          }); }}>
            <Campo etichetta="Nome"><input className="campo" value={d.nome} onChange={(e) => setD({ ...d, nome: e.target.value })} required minLength={2} placeholder="es. Connettore BI" /></Campo>
            <fieldset><legend className="etichetta">Scope concessi</legend>
              <div className="grid grid-cols-2 gap-2">{scopes?.map((s) => (
                <label key={s} className="flex items-center gap-2 text-sm"><input type="checkbox" checked={d.scopes.includes(s)} onChange={(e) => setD({ ...d, scopes: e.target.checked ? [...d.scopes, s] : d.scopes.filter((x) => x !== s) })} /> <code>{s}</code></label>
              ))}</div></fieldset>
            <button className="btn-primario w-full" disabled={!d.scopes.length || azione.inCorso}>Crea credenziali</button>
          </form>
        </Modale>
      )}
    </div>
  );
}

function Sso() {
  const { dati, errore } = useDati<any>("/api/azienda");
  const [d, setD] = useState<{ ssoIssuer: string; ssoClientId: string; ssoClientSecret: string } | null>(null);
  const [ok, setOk] = useState(false);
  const azione = useAzione();
  useEffect(() => { if (dati) setD({ ssoIssuer: dati.ssoIssuer ?? "", ssoClientId: dati.ssoClientId ?? "", ssoClientSecret: dati.ssoClientSecret ?? "" }); }, [dati]);
  if (errore) return <Errore messaggio={errore} />;
  if (!d) return <Caricamento />;
  const origine = typeof window !== "undefined" ? window.location.origin : "";
  return (
    <form className="scheda grid gap-4 md:grid-cols-2" onSubmit={(e) => { e.preventDefault(); setOk(false); azione.esegui(async () => {
      await api("/api/azienda", { metodo: "PATCH", corpo: { ssoIssuer: d.ssoIssuer, ssoClientId: d.ssoClientId || null, ssoClientSecret: d.ssoClientSecret || null } }); setOk(true);
    }); }}>
      <div className="md:col-span-2 text-sm text-tenue">
        <p>Accesso unico con il provider di identità aziendale tramite <strong>OpenID Connect</strong> (authorization code + PKCE). Funziona con provider gratuiti come
          Keycloak, Authentik, Google Workspace o Microsoft Entra ID (piano free). Gli utenti devono già esistere in Nexora con la stessa email.</p>
        <p className="mt-2">URL di reindirizzamento da registrare sul provider: <code className="font-bold text-testo">{origine}/api/auth/sso/callback</code></p>
      </div>
      <Campo etichetta="Issuer (URL del provider)" className="md:col-span-2" aiuto="es. https://accounts.google.com oppure https://keycloak.miodominio.it/realms/azienda">
        <input className="campo font-mono text-sm" type="url" value={d.ssoIssuer} onChange={(e) => setD({ ...d, ssoIssuer: e.target.value })} /></Campo>
      <Campo etichetta="Client ID"><input className="campo font-mono text-sm" value={d.ssoClientId} onChange={(e) => setD({ ...d, ssoClientId: e.target.value })} /></Campo>
      <Campo etichetta="Client secret" aiuto="Conservato cifrato (AES-256-GCM)"><input className="campo font-mono text-sm" type="password" value={d.ssoClientSecret} onChange={(e) => setD({ ...d, ssoClientSecret: e.target.value })} autoComplete="off" /></Campo>
      {ok && <div className="md:col-span-2"><Avviso tipo="ok">Configurazione SSO salvata: dalla pagina di accesso scegli «Accedi con SSO aziendale».</Avviso></div>}
      {azione.errore && <div className="md:col-span-2"><Avviso tipo="errore">{azione.errore}</Avviso></div>}
      <div className="flex justify-end md:col-span-2"><button className="btn-primario" disabled={azione.inCorso}>Salva</button></div>
    </form>
  );
}

const AZIONI_AUDIT: Record<string, string> = {
  accesso: "Accesso", accesso_mfa: "Accesso con verifica in 2 passaggi", accesso_sso: "Accesso SSO", accesso_fallito: "Accesso non riuscito", password_cambiata: "Password cambiata",
  mfa_attivata: "Verifica in 2 passaggi attivata", mfa_disattivata: "Verifica in 2 passaggi disattivata", azienda_creata: "Azienda creata", azienda_modificata: "Dati aziendali modificati",
};

function Audit() {
  const { dati, errore } = useDati<any[]>("/api/audit");
  const [q, setQ] = useState("");
  if (errore) return <Errore messaggio={errore} />;
  if (!dati) return <Caricamento />;
  const lista = dati.filter((r) => !q || `${r.azione} ${r.utente} ${r.dettagli ?? ""}`.toLowerCase().includes(q.toLowerCase()));
  return (
    <>
      <div className="scheda mb-4 p-4"><input className="campo" placeholder="Filtra per azione, utente o dettaglio…" value={q} onChange={(e) => setQ(e.target.value)} aria-label="Filtra" /></div>
      <div className="scheda overflow-x-auto p-0">
        <table className="tabella">
          <thead><tr><th>Quando</th><th>Utente</th><th>Azione</th><th>Dettagli</th></tr></thead>
          <tbody>{lista.map((r) => (
            <tr key={r.id}><td className="whitespace-nowrap">{dataOra(r.createdAt)}</td><td>{r.utente}</td>
              <td className={r.azione === "accesso_fallito" ? "text-errore" : ""}>{AZIONI_AUDIT[r.azione] ?? r.azione.replace(/_/g, " ")}</td>
              <td className="max-w-md truncate text-xs text-tenue" title={r.dettagli ?? ""}>{r.dettagli ?? "—"}</td></tr>
          ))}</tbody>
        </table>
      </div>
    </>
  );
}
