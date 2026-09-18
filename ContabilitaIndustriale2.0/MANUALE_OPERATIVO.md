# Manuale Operativo — Contabilità Industriale 2.0

Guida pratica all'uso quotidiano dell'applicazione, modulo per modulo. Dalla schermata principale puoi accedere a tutti i moduli tramite le card visualizzate.

---

## Impostazione iniziale: il Piano dei Conti

Prima di iniziare a usare Movimenti, Fatturazione, Contabilità Generale, Ammortamenti o Gestione Paga Dipendenti, è necessario avere un piano dei conti (l'elenco dei "contenitori" contabili: cassa, banca, crediti, debiti, costi, ricavi...).

1. Vai in **Contabilità Generale** → sezione **Piano dei Conti**.
2. Clicca **"Inizializza piano standard"**.
3. Viene creato automaticamente un piano dei conti italiano completo (94 voci: disponibilità liquide, crediti, immobilizzazioni, debiti, costi, ricavi, fondi ammortamento, TFR, ecc.), pronto all'uso.

Questa operazione va fatta una sola volta.

---

## 1. Piano dei Conti

Qui puoi consultare, aggiungere o rimuovere i singoli conti contabili.

- Per aggiungerne uno manualmente: compila Codice, Descrizione, Tipo (attivo / passivo / costo / ricavo) e un eventuale saldo iniziale, poi salva.
- Ogni conto mostra il proprio saldo aggiornato in tempo reale.
- Un conto può essere rimosso dall'elenco tramite l'icona del cestino.

---

## 2. Movimenti di Cassa

Un registro semplice e veloce di entrate e uscite, utile per tenere sotto controllo la liquidità giorno per giorno senza dover fare una registrazione contabile completa.

1. Scegli se è un'**Entrata** o un'**Uscita**.
2. Inserisci data, importo, una breve descrizione e — se vuoi — una categoria (es. Vendite, Fornitori, Utenze, Personale...) e il conto di riferimento.
3. Salva: il movimento compare subito nell'elenco sottostante, e alimenta i grafici della Dashboard.
4. Puoi filtrare l'elenco per "Tutti / Entrate / Uscite" e rimuovere un movimento con il cestino.

---

## 3. Dashboard Finanziaria

La schermata di sintesi generale: saldo operativo, totale entrate, totale uscite e cashflow netto, con due grafici (andamento mensile e cashflow cumulativo) e un indicatore di liquidità. È una pagina di sola consultazione: si aggiorna automaticamente in base ai Movimenti registrati.

---

## 4. Fatturazione

Gestisce preventivi, ordini, fatture attive/passive e note di credito.

1. Vai nella sezione **Clienti/Fornitori** e crea l'anagrafica del cliente o fornitore (nome, tipo, partita IVA, contatti).
2. Clicca **"Nuovo Documento"**, scegli il tipo (es. Fattura attiva), la data, il cliente e aggiungi una o più righe con descrizione, quantità, prezzo e aliquota IVA. Il sistema calcola automaticamente subtotale, IVA e totale.
3. Dopo aver creato il documento puoi aggiornarne lo stato (bozza → emesso → pagato) direttamente dall'elenco, oppure scaricarlo in PDF.

---

## 5. OCR Contabile

Permette di importare automaticamente i dati da una fattura scansionata o da un PDF, senza doverli ricopiare a mano.

1. Trascina il file (PDF, foto o scansione della fattura) nell'apposita area, oppure selezionalo dal tuo computer.
2. Il sistema analizza il documento ed estrae in automatico fornitore, partita IVA, numero e data documento, imponibile, IVA e totale.
3. Puoi rivedere e correggere i dati estratti prima di collegarli a un documento di fatturazione.
4. Tutti i documenti già elaborati restano visibili nello storico in basso.

*Nota: per l'elaborazione di scansioni o foto (non di PDF con testo nativo) è necessario avere installato sul computer il programma gratuito Tesseract OCR.*

---

## 6. Contabilità Generale (Partita Doppia)

Il cuore della contabilità aziendale: qui si registra la "Prima Nota" secondo il metodo della partita doppia, si consulta il bilancio e si calcola l'IVA da versare.

### Prima Nota
1. Clicca **"Nuova"** registrazione, inserisci data e causale.
2. Aggiungi almeno due righe: una in **Dare** e una in **Avere**, ciascuna collegata a un conto del piano dei conti. Il sistema mostra in tempo reale se la registrazione è bilanciata e non permette di salvarla finché il totale a Dare non coincide esattamente col totale a Avere — questo garantisce che la contabilità resti sempre quadrata.
3. Una volta salvata, la registrazione può essere **chiusa** (per renderla definitiva e non più modificabile/eliminabile) oppure eliminata se non è ancora chiusa.

### Bilancio di Verifica
Mostra, per ogni conto, il totale Dare, il totale Avere e il saldo, con i totali generali di Attivo, Passivo, Costi, Ricavi e l'Utile/Perdita d'esercizio.

### Liquidazione IVA
Seleziona un intervallo di date per calcolare automaticamente l'IVA a credito (sugli acquisti) e a debito (sulle vendite), con il dettaglio per aliquota e il saldo da versare o a credito.

---

## 7. Ammortamenti

Gestisce i beni aziendali durevoli (macchinari, automezzi, attrezzature, software...) e calcola automaticamente il loro piano di ammortamento.

1. Clicca **"Nuovo"** e inserisci descrizione, categoria del bene (scegliendo tra le voci della tabella ministeriale, che propone già l'aliquota di ammortamento corretta), data di acquisto e costo storico.
2. Indica il conto di costo (dove viene registrata la quota annuale) e il conto del fondo ammortamento (dove si accumula il valore già ammortizzato).
3. Il programma genera automaticamente il **piano di ammortamento anno per anno**, sia in versione civilistica (quella di bilancio) sia fiscale (quella dichiarativa, con la riduzione al 50% prevista per legge nel primo anno).
4. Per ogni anno puoi cliccare **"Contabilizza"**: viene generata automaticamente la relativa scrittura in Prima Nota, sempre bilanciata. Ogni anno può essere contabilizzato una sola volta, così da evitare doppie registrazioni.
5. Se un bene viene venduto o dismesso, usa il pulsante **"Dismetti"** indicando la data: il piano di ammortamento si interrompe automaticamente a quell'anno.

*Le aliquote fiscali proposte coprono le categorie di beni più comuni; per casi particolari verifica sempre l'aliquota corretta con il tuo commercialista.*

---

## 8. Gestione Paga Dipendenti

Gestisce l'anagrafica dei dipendenti e il calcolo mensile del cedolino paga.

1. Clicca **"Nuovo"** e inserisci i dati del dipendente: nome, cognome, qualifica, data di assunzione, retribuzione lorda mensile e numero di mensilità (12, 13 o 14).
2. Seleziona il dipendente, scegli mese e anno, e clicca **"Anteprima"** per vedere il calcolo del cedolino: contributi INPS a carico del dipendente, IRPEF, netto in busta, contributi a carico dell'azienda, quota di TFR maturata e costo totale per l'azienda.
3. Clicca **"Genera cedolino"** per salvarlo definitivamente.
4. Clicca **"Contabilizza"** per registrare automaticamente il costo del personale in Prima Nota (retribuzioni, contributi, TFR e debiti verso dipendenti ed enti previdenziali/fiscali), con una scrittura sempre bilanciata.

*Il calcolo utilizza le aliquote IRPEF nazionali e la detrazione per lavoro dipendente; non include addizionali regionali/comunali, conguaglio di fine anno o carichi di famiglia, e non sostituisce la consulenza di un consulente del lavoro.*

---

## 9. CRM Economico

Tiene traccia dei rapporti con clienti e fornitori, oltre le semplici fatture.

- **Clienti/Fornitori**: anagrafica e punteggio di affidabilità, calcolato in base alla puntualità dei pagamenti ricevuti.
- **Scadenze**: da incassare o da pagare, con possibilità di segnarle come saldate.
- **Pipeline commerciale**: le trattative in corso, organizzate per fase (dal primo contatto alla chiusura).
- **Storico pagamenti**: ogni incasso o pagamento registrato, con metodo e data.

---

## 10. Workflow Aziendale

Per organizzare attività interne, richieste di approvazione, promemoria e richieste di acquisto.

1. Clicca **"Nuovo"**, scegli il tipo (Task, Approvazione, Reminder o Acquisto), imposta priorità, responsabile e scadenza.
2. Per le richieste di approvazione puoi definire una catena di approvatori in ordine: ciascuno riceve la richiesta solo dopo che il precedente ha approvato.
3. Ogni attività può essere aggiornata di stato (iniziata, completata, approvata, rifiutata) direttamente dal suo dettaglio.

---

## 11. Forecasting

Fornisce proiezioni economiche basate sui dati storici già inseriti (più dati ci sono, più le previsioni sono affidabili):

- **Previsione vendite**: andamento atteso nei prossimi mesi.
- **Liquidità**: quanti giorni di autonomia ha l'azienda al ritmo attuale di spesa.
- **Simulazione scenari**: confronto tra scenario ottimistico, base e pessimistico.
- **Rischio insolvenza**: un punteggio sintetico con i principali fattori di rischio e consigli pratici.

---

## 12. AI Assistant

Un assistente conversazionale che risponde a domande sui dati aziendali in linguaggio naturale (es. "Qual è il saldo operativo attuale?", "Quali fatture sono ancora da incassare?"). Funziona in locale sul tuo computer e richiede il programma gratuito Ollama installato e avviato: se il badge in alto mostra "Offline", questo modulo non è momentaneamente disponibile.

---

## 13. Centri di Costo

Il primo mattone della contabilità industriale: dove si sostengono i costi interni.

1. In **Centri di costo** crea i centri dell'azienda: **produttivo** (es. reparto Assemblaggio), **ausiliario** (es. Manutenzione) o **comune** (es. Direzione), con un eventuale centro padre per creare gerarchie.
2. In **Basi di riparto** definisci i driver (es. "Ore macchina") e registra, per periodo `YYYY-MM`, quanto ne consuma ciascun centro.
3. In **Riparto costi indiretti** crea un riparto (descrizione, centro che ha sostenuto il costo, importo, base e periodo) e clicca **"Calcola allocazione"**: l'importo viene ripartito sugli altri centri in proporzione al loro consumo della base scelta.

*Il riparto è a singolo livello (una tornata di centri destinatari): per ribaltamenti a cascata (ausiliari → produttivi → prodotti) ripeti l'operazione un centro alla volta.*

---

## 14. Prodotti e Distinta Base

L'anagrafica di materie prime, semilavorati e prodotti finiti, con la distinta base (BOM) e il costo standard.

1. Crea le **materie prime**, indicando il prezzo standard di acquisto.
2. Crea i **semilavorati** e i **prodotti finiti**, indicando ore di manodopera standard, costo orario standard e quota di indiretti standard per unità.
3. Nel dettaglio di un semilavorato/prodotto finito, in **Distinta base** aggiungi i componenti (materie prime o altri semilavorati) con la quantità necessaria per produrne 1 unità.
4. Il costo standard si calcola **ricorsivamente**: se un prodotto finito usa un semilavorato, entra nel calcolo il suo costo standard pieno (materiale + manodopera + indiretti), non solo un prezzo di listino.

---

## 15. Magazzino Industriale

Carichi e scarichi di materie prime, semilavorati e prodotti finiti, valorizzati a costo medio ponderato.

1. Clicca **"Nuovo movimento"**: scegli prodotto e tipo — **Carico** (richiede il costo unitario) o **Scarico** (valorizzato automaticamente al costo medio corrente).
2. Uno scarico viene rifiutato se supera la giacenza disponibile.
3. La spunta "Mostra solo sotto scorta" filtra i prodotti da riordinare.

*I consumi registrati in una Commessa (modulo 16) generano automaticamente lo scarico corrispondente: non serve registrarlo due volte.*

---

## 16. Commesse

Ogni commessa accumula i propri costi — materiale, manodopera diretta, quote di indiretti — fino alla chiusura (job order costing).

1. Clicca **"Nuova"**: scegli il prodotto finito, il centro di costo responsabile e la quantità pianificata.
2. Aggiungi righe di costo: **Materiale** (prodotto + quantità, scarica automaticamente il magazzino), **Manodopera** (ore + costo orario) o **Indiretto** (importo diretto).
3. Alla fine della produzione, clicca **"Chiudi commessa"** indicando la quantità effettivamente prodotta, necessaria per un confronto preciso con lo standard.

---

## 17. Costi Standard & Analisi Scostamenti

Confronta il costo standard atteso di una commessa con il costo consuntivo realmente registrato.

1. Seleziona una commessa (aperta o chiusa) dal menu.
2. Per materiale e manodopera lo scostamento si scompone in **scostamento di prezzo/tariffa** e **scostamento di quantità/efficienza**; per gli indiretti resta un blocco unico.
3. Convenzione dei segni: **positivo = sfavorevole** (il consuntivo è costato più dello standard), negativo = favorevole. Prezzo + quantità = scostamento totale della voce.

*La scomposizione è precisa quando i consumi vengono registrati allo stesso livello della distinta base del prodotto; il totale resta comunque sempre corretto.*

---

## 18. Controllo di Gestione

Direct costing, margine di contribuzione e punto di pareggio (break-even), a livello aziendale e per prodotto.

1. Classifica ogni conto di costo come **Fisso** o **Variabile**.
2. Il break-even aziendale si aggiorna: margine di contribuzione = ricavi − costi variabili; punto di pareggio = costi fissi ÷ (margine di contribuzione ÷ ricavi).
3. "Break-even per prodotto" mostra, per ogni prodotto finito con prezzo di vendita impostato, il margine unitario e le unità necessarie a coprire i costi fissi aziendali.

*I totali di ricavi/costi leggono i saldi del Bilancio (Contabilità Generale), non i Movimenti di cassa: senza registrazioni in Prima Nota questo modulo mostra tutto a zero.*

---

## Nota generale

Ammortamenti e Gestione Paga Dipendenti applicano un calcolo semplificato pensato per la gestione ordinaria: per situazioni particolari (regimi fiscali speciali, categorie di beni non standard, casi di paga complessi) fai sempre riferimento al tuo commercialista o consulente del lavoro.

I moduli 13–18 di contabilità industriale sono un livello analitico che legge i dati dei moduli aziendali (Piano dei Conti, Contabilità Generale, Anagrafiche) senza generare automaticamente scritture di prima nota per costi di commessa o magazzino — una scelta di progetto per evitare doppie contabilizzazioni, coerente con l'uso tipico della contabilità industriale nelle PMI come strumento di controllo di gestione interno, non obbligatorio per legge.
