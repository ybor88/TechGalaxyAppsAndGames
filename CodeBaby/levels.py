"""I 15 livelli di CodeBaby: dal primo passo ai concetti piu' avanzati
(variabili, array, strutture dati, funzioni). Ogni livello introduce UN
concetto informatico semplice, spiegato con parole da maestra/o alla classe.
facing: 0=su 1=destra 2=giu 3=sinistra."""

LEVELS = [
    dict(
        id=1,
        title="La Sequenza",
        concept="LA SEQUENZA",
        teach="Ciao! Io sono CodeBaby! Il computer fa i comandi ESATTAMENTE "
              "nell'ordine in cui li metti, uno dopo l'altro. Mettiamo in fila "
              "i comandi giusti per portarmi fino alla stella!",
        hint="Usa i blocchi AVANTI per raggiungere la stella.",
        grid=(5, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(3, 1),
        unlock=["fwd"], ideal=3,
    ),
    dict(
        id=2,
        title="Conta i Passi",
        concept="LA PRECISIONE",
        teach="Bravissimo! Ora la strada e' piu' lunga. Ogni blocco AVANTI mi "
              "fa muovere di UNA sola casella: contiamo bene i passi prima di "
              "premere Gioca!",
        hint="Quante caselle ci sono fino alla stella?",
        grid=(7, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(5, 1),
        unlock=["fwd"], ideal=5,
    ),
    dict(
        id=3,
        title="Girare a Destra",
        concept="LA ROTAZIONE",
        teach="Adesso imparo a GIRARE! Il blocco Gira Destra mi fa ruotare di "
              "un quarto di giro, ma non mi sposta dalla mia casella. Dopo "
              "aver girato, guardo da un'altra parte!",
        hint="Arriva in fondo, poi gira per proseguire.",
        grid=(5, 5), start=(0, 0), start_facing=1,
        walls=[], goal=(2, 2),
        unlock=["fwd", "right"], ideal=5,
    ),
    dict(
        id=4,
        title="Destra e Sinistra",
        concept="LE DUE DIREZIONI",
        teach="Ora conosco anche Gira Sinistra! Prima di muovermi, pensa bene: "
              "da che parte devo girare per non sbattere contro i bordi?",
        hint="Serviranno sia svolte a destra che a sinistra.",
        grid=(6, 5), start=(0, 3), start_facing=0,
        walls=[], goal=(3, 0),
        unlock=["fwd", "left", "right"], ideal=8,
    ),
    dict(
        id=5,
        title="La Ripetizione",
        concept="IL CICLO (RIPETI)",
        teach="I programmatori sono un po' pigri: invece di scrivere tanti "
              "comandi Avanti tutti uguali, usano il blocco RIPETI! Prova a "
              "dirmi: 'Ripeti 6 volte: Avanti'.",
        hint="Un solo blocco RIPETI basta per tutta la strada.",
        grid=(8, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(6, 1),
        unlock=["fwd", "left", "right", "repeat"], ideal=1,
    ),
    dict(
        id=6,
        title="Ripeti e Gira",
        concept="COMBINARE I BLOCCHI",
        teach="Ottimo lavoro! Ora unisco la ripetizione e le svolte nello "
              "stesso programma, per fare percorsi piu' lunghi con pochi "
              "blocchi.",
        hint="Ripeti i passi, poi gira, poi ripeti ancora.",
        grid=(6, 6), start=(0, 0), start_facing=1,
        walls=[], goal=(3, 3),
        unlock=["fwd", "left", "right", "repeat"], ideal=3,
    ),
    dict(
        id=7,
        title="Attenzione ai Muri!",
        concept="OSSERVARE PRIMA DI AGIRE",
        teach="Occhio! C'e' un muro proprio sulla strada dritta. Un bravo "
              "programmatore guarda tutta la mappa PRIMA di scrivere il "
              "programma, e trova un'altra via.",
        hint="Il muro blocca la strada dritta: gira intorno.",
        grid=(6, 5), start=(0, 2), start_facing=1,
        walls=[(2, 2)], goal=(3, 2),
        unlock=["fwd", "left", "right", "repeat"], ideal=8,
    ),
    dict(
        id=8,
        title="Il Percorso a Quadrato",
        concept="RICONOSCERE I PATTERN",
        teach="Guarda bene la mappa: riesci a riconoscere un disegno che si "
              "ripete? Usa piu' blocchi RIPETI insieme per risparmiare "
              "comandi, come fanno i veri programmatori!",
        hint="Il percorso forma una specie di spirale: procedi un lato alla volta.",
        grid=(7, 7), start=(1, 1), start_facing=1,
        walls=[], goal=(1, 2),
        unlock=["fwd", "left", "right", "repeat"], ideal=7,
    ),
    dict(
        id=9,
        title="La Sfida del Labirinto",
        concept="PROVARE, SBAGLIARE, CORREGGERE",
        teach="Questo e' un vero labirinto! Se sbatto contro un muro non "
              "succede niente di male: torno alla partenza e tu puoi "
              "correggere il programma. Sbagliare aiuta a imparare!",
        hint="Segui il corridoio libero: alcune strade sono chiuse.",
        grid=(7, 6), start=(0, 0), start_facing=1,
        walls=[
            (3, 0),
            (0, 1), (1, 1), (3, 1), (5, 1),
            (5, 2),
            (1, 3), (2, 3), (3, 3), (5, 3),
            (1, 4),
            (4, 5), (5, 5),
        ],
        goal=(2, 4),
        unlock=["fwd", "left", "right", "repeat"], ideal=9,
    ),
    dict(
        id=10,
        title="Il Grande Finale",
        concept="TUTTO INSIEME!",
        teach="Ultima missione! Usa tutto quello che hai imparato: sequenza, "
              "svolte e ripetizioni. Il percorso gira come una spirale fino "
              "al centro. Sei pronto/a, piccolo/a programmatore/programmatrice?",
        hint="Segui il bordo esterno a spirale fino al centro.",
        grid=(5, 5), start=(0, 0), start_facing=1,
        walls=[], goal=(2, 2),
        unlock=["fwd", "left", "right", "repeat"], ideal=11,
    ),
    dict(
        id=11,
        title="Le Gemme",
        concept="LA VARIABILE",
        teach="Guarda questa gemma scintillante! Il blocco RACCOGLI la mette "
              "dentro la mia scatolina magica: e' una VARIABILE, un numero "
              "che il computer ricorda e aggiorna ogni volta che raccolgo "
              "qualcosa. Prendi TUTTE le gemme, poi vai alla stella!",
        hint="Vai su ogni gemma e usa RACCOGLI, poi arriva alla stella.",
        grid=(7, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(6, 1),
        gems=[(2, 1), (4, 1)],
        unlock=["fwd", "left", "right", "grab", "repeat"], ideal=5,
    ),
    dict(
        id=12,
        title="La Lista della Spesa",
        concept="L'ARRAY (LISTA ORDINATA)",
        teach="Questo e' un ARRAY: una lista ordinata di cose, come una "
              "lista della spesa! Le gemme sono numerate: raccoglile "
              "ESATTAMENTE in ordine, prima la 1, poi la 2, poi la 3. Se "
              "sbagli l'ordine, si torna alla partenza: si puo' sempre "
              "riprovare!",
        hint="Segui i numeri sul pavimento: 1, poi 2, poi 3, in quest'ordine.",
        grid=(8, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(7, 1),
        gems=[(2, 1), (4, 1), (6, 1)], gems_ordered=True,
        unlock=["fwd", "left", "right", "grab", "repeat"], ideal=7,
    ),
    dict(
        id=13,
        title="Lo Zaino Magico",
        concept="LE STRUTTURE DATI",
        teach="Nel mio zaino tengo insieme informazioni collegate: una "
              "CHIAVE e il suo COLORE! Ogni porta si apre solo con la "
              "chiave dello stesso colore. Raccogli la chiave gialla per la "
              "porta gialla, poi quella blu per la porta blu.",
        hint="Raccogli sempre la chiave prima della porta dello stesso colore.",
        grid=(7, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(6, 1),
        keys=[{"pos": (1, 1), "color": "gold"}, {"pos": (4, 1), "color": "blue"}],
        doors=[{"pos": (3, 1), "color": "gold"}, {"pos": (5, 1), "color": "blue"}],
        unlock=["fwd", "left", "right", "grab", "repeat"], ideal=5,
    ),
    dict(
        id=14,
        title="La Funzione Magica",
        concept="LE FUNZIONI (BLOCCHI RIUSABILI)",
        teach="Una FUNZIONE e' un mini-programma con un nome: la costruisci "
              "UNA volta con il tasto 'Definisci FUNZIONE', poi la richiami "
              "tutte le volte che vuoi, senza riscrivere sempre gli stessi "
              "blocchi! Non e' come RIPETI, perche' una funzione puo' "
              "mescolare comandi diversi insieme.",
        hint="Definisci la funzione 'Avanti, Avanti, Gira Destra', poi richiamala 3 volte.",
        grid=(5, 5), start=(0, 0), start_facing=1,
        walls=[], goal=(0, 2),
        allow_function=True, function_name="Passo", function_capacity=3,
        unlock=["fwd", "left", "right"], ideal=3,
    ),
    dict(
        id=15,
        title="La Missione Finale 2",
        concept="TUTTO INSIEME: VARIABILI, STRUTTURE E FUNZIONI",
        teach="Ultima sfida! Raccogli le gemme, prendi la chiave giusta per "
              "la porta, e se vuoi risparmiare blocchi puoi anche definire "
              "e richiamare una FUNZIONE per il tratto finale. Usa tutto "
              "cio' che hai imparato, piccolo/a programmatore/programmatrice!",
        hint="Gemma, poi chiave, poi porta, poi la seconda gemma, poi dritto fino alla stella.",
        grid=(9, 3), start=(0, 1), start_facing=1,
        walls=[], goal=(8, 1),
        gems=[(1, 1), (5, 1)],
        keys=[{"pos": (2, 1), "color": "blue"}], doors=[{"pos": (3, 1), "color": "blue"}],
        allow_function=True, function_name="Passo", function_capacity=4,
        unlock=["fwd", "left", "right", "grab", "repeat"], ideal=8,
    ),
]

LEVELS_BY_ID = {lv["id"]: lv for lv in LEVELS}
