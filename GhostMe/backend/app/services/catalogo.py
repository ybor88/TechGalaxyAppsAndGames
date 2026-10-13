"""
Questionario di profilo (F01) e sfide di auto-riflessione (F09).
Contenuti non clinici: nessun punteggio psicologico.

© Roberto Di Flumeri
"""

CATEGORIE = {
    "valori": "Valori",
    "abitudini": "Abitudini",
    "gusti": "Gusti",
    "obiettivi": "Obiettivi",
    "stile_comunicativo": "Stile comunicativo",
    "altro": "Altro",
}

QUESTIONARIO = [
    {"chiave": "valori_importanti", "categoria": "valori", "domanda": "Quali sono le tre cose più importanti nella tua vita in questo periodo?", "esempio": "La famiglia, la libertà di gestire il mio tempo, imparare cose nuove."},
    {"chiave": "valori_non_negoziabili", "categoria": "valori", "domanda": "C'è qualcosa su cui non scenderesti mai a compromessi?", "esempio": "Non rinuncio all'onestà, anche quando è scomoda."},
    {"chiave": "valori_successo", "categoria": "valori", "domanda": "Cosa significa per te avere successo?", "esempio": "Fare un lavoro che mi appassiona e avere tempo per le persone care."},
    {"chiave": "abitudini_giornata", "categoria": "abitudini", "domanda": "Com'è una tua giornata tipo?", "esempio": "Mi sveglio presto, lavoro meglio la mattina e la sera leggo."},
    {"chiave": "abitudini_energia", "categoria": "abitudini", "domanda": "Cosa ti ricarica quando sei stanco o stanca?", "esempio": "Camminare all'aria aperta e cucinare con calma."},
    {"chiave": "abitudini_organizzazione", "categoria": "abitudini", "domanda": "Come organizzi impegni e attività?", "esempio": "Uso liste, ma lascio spazio all'improvvisazione nel weekend."},
    {"chiave": "gusti_tempo_libero", "categoria": "gusti", "domanda": "Come ti piace passare il tempo libero?", "esempio": "Amo viaggiare in posti poco turistici e fare trekking."},
    {"chiave": "gusti_cultura", "categoria": "gusti", "domanda": "Libri, musica, film o serie che ti rappresentano?", "esempio": "Fantascienza, jazz e documentari sulla natura."},
    {"chiave": "gusti_cibo", "categoria": "gusti", "domanda": "Cibi o ambienti che preferisci?", "esempio": "Cucina mediterranea, posti tranquilli e luminosi."},
    {"chiave": "obiettivi_anno", "categoria": "obiettivi", "domanda": "Quali obiettivi vuoi raggiungere entro un anno?", "esempio": "Cambiare lavoro verso il design e correre una mezza maratona."},
    {"chiave": "obiettivi_lungo", "categoria": "obiettivi", "domanda": "Dove ti piacerebbe essere tra cinque anni?", "esempio": "Vivere vicino al mare e avere un progetto mio."},
    {"chiave": "obiettivi_crescita", "categoria": "obiettivi", "domanda": "Quale competenza vorresti sviluppare?", "esempio": "Parlare in pubblico con più sicurezza."},
    {"chiave": "stile_tono", "categoria": "stile_comunicativo", "domanda": "Come comunichi di solito? (diretto, ironico, riflessivo…)", "esempio": "Sono diretto ma cerco di essere gentile, uso spesso l'ironia."},
    {"chiave": "stile_conflitti", "categoria": "stile_comunicativo", "domanda": "Come affronti un disaccordo?", "esempio": "Preferisco parlarne subito, a voce, senza lasciar correre."},
    {"chiave": "stile_decisioni", "categoria": "stile_comunicativo", "domanda": "Come prendi le decisioni importanti?", "esempio": "Raccolgo informazioni, faccio una lista di pro e contro e poi seguo l'istinto."},
]

SFIDE = [
    {"id": "tre_parole", "titolo": "Tre parole", "categoria": "valori", "prompt": "Descriviti con tre parole. Perché proprio queste?"},
    {"id": "giornata_ideale", "titolo": "La giornata ideale", "categoria": "abitudini", "prompt": "Racconta come sarebbe una giornata perfetta, dall'alba alla sera."},
    {"id": "lettera_futuro", "titolo": "Lettera al te di domani", "categoria": "obiettivi", "prompt": "Scrivi qualche riga al te stesso di tra un anno: cosa speri sia cambiato?"},
    {"id": "scelta_orgoglio", "titolo": "Una scelta di cui vai fiero", "categoria": "valori", "prompt": "Quale scelta passata rifaresti senza esitare? Cosa ti ha guidato?"},
    {"id": "no_facile", "titolo": "Il no più facile", "categoria": "valori", "prompt": "A cosa diresti di no senza pensarci due volte?"},
    {"id": "energia_settimana", "titolo": "Energia della settimana", "categoria": "abitudini", "prompt": "Quale momento di questa settimana ti ha dato più energia? E quale te ne ha tolta?"},
    {"id": "piccolo_lusso", "titolo": "Piccoli piaceri", "categoria": "gusti", "prompt": "Qual è un piccolo piacere quotidiano a cui non rinunceresti?"},
    {"id": "consiglio_ricevuto", "titolo": "Il consiglio migliore", "categoria": "stile_comunicativo", "prompt": "Qual è il consiglio migliore che hai ricevuto e come lo hai applicato?"},
    {"id": "tempo_extra", "titolo": "Un'ora in più", "categoria": "gusti", "prompt": "Se ogni giorno avessi un'ora libera in più, come la useresti?"},
    {"id": "abilita_nuova", "titolo": "Da imparare", "categoria": "obiettivi", "prompt": "Cosa vorresti imparare se non avessi paura di sbagliare?"},
    {"id": "come_spiego", "titolo": "Come spiego le cose", "categoria": "stile_comunicativo", "prompt": "Come spiegheresti il tuo lavoro o la tua passione a un bambino?"},
    {"id": "cambiato_idea", "titolo": "Ho cambiato idea", "categoria": "valori", "prompt": "Su cosa hai cambiato idea negli ultimi anni? Cosa ti ha convinto?"},
]
