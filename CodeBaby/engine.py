"""Motore di gioco: la mappa a griglia, il robottino e l'esecuzione dei comandi.

Direzioni: 0 = su, 1 = destra, 2 = giu, 3 = sinistra (in senso orario).
"""

DIR_VECT = {0: (0, -1), 1: (1, 0), 2: (0, 1), 3: (-1, 0)}


def turn_right(facing):
    return (facing + 1) % 4


def turn_left(facing):
    return (facing - 1) % 4


class Block:
    """Un blocco di programma cosi' come lo vede il bambino: puo' essere un
    comando singolo, un 'Ripeti N volte' (racchiude un solo comando) oppure
    un 'Richiama FUNZIONE' (racchiude la sequenza salvata in precedenza)."""

    def __init__(self, action, count=1):
        self.action = action  # "fwd" | "left" | "right" | "grab" | "call"
        self.count = count    # 1 per i blocchi semplici, >=2 per i ripeti

    @property
    def is_repeat(self):
        return self.count > 1

    def label(self, function_name="FUNZIONE"):
        names = {"fwd": "Avanti", "left": "Gira Sinistra", "right": "Gira Destra",
                 "grab": "Raccogli"}
        if self.action == "call":
            return f"Richiama {function_name}"
        base = names[self.action]
        if self.is_repeat:
            return f"Ripeti {self.count}x: {base}"
        return base


def flatten(blocks, function_body=None):
    """Trasforma la lista di blocchi (visibili) in singoli passi atomici,
    ricordando a quale blocco (indice) appartiene ogni passo per evidenziarlo
    durante l'esecuzione. Un blocco 'call' si espande nella sequenza salvata
    come FUNZIONE (function_body), esattamente come 'repeat' si espande nel
    comando ripetuto N volte."""
    steps = []
    for i, b in enumerate(blocks):
        if b.action == "call":
            for a in (function_body or []):
                steps.append((i, a))
        else:
            for _ in range(b.count):
                steps.append((i, b.action))
    return steps


class World:
    def __init__(self, level):
        self.cols, self.rows = level["grid"]
        self.walls = set(level.get("walls", []))
        self.goal = level["goal"]
        self.start = level["start"]
        self.start_facing = level["start_facing"]
        # gemme (VARIABILE / ARRAY): elenco di celle da raccogliere col blocco "grab"
        self.gems = list(level.get("gems", []))
        self.gems_ordered = level.get("gems_ordered", False)
        self.collected_gems = set()
        self.next_gem_index = 0
        # chiavi/porte abbinate per colore (STRUTTURE DATI)
        self.keys = list(level.get("keys", []))
        self.doors = list(level.get("doors", []))
        self.collected_keys = set()

    def in_bounds(self, pos):
        x, y = pos
        return 0 <= x < self.cols and 0 <= y < self.rows

    def is_free(self, pos):
        if not self.in_bounds(pos) or pos in self.walls:
            return False
        for d in self.doors:
            if d["pos"] == pos and d["color"] not in self.collected_keys:
                return False
        return True

    def gem_at(self, pos):
        for i, g in enumerate(self.gems):
            if g == pos and i not in self.collected_gems:
                return i
        return None

    def key_at(self, pos):
        for k in self.keys:
            if k["pos"] == pos and k["color"] not in self.collected_keys:
                return k
        return None

    def gems_satisfied(self):
        return len(self.collected_gems) >= len(self.gems)


class RunResult:
    OK = "ok"
    CRASH = "crash"
    WIN = "win"
    COLLECT = "collect"
    ORDER_ERROR = "order_error"


def simulate_step(world, pos, facing, action):
    """Applica UN passo atomico e ritorna (nuova_pos, nuova_facing, esito)."""
    if action == "left":
        return pos, turn_left(facing), RunResult.OK
    if action == "right":
        return pos, turn_right(facing), RunResult.OK
    if action == "grab":
        gi = world.gem_at(pos)
        if gi is not None:
            if world.gems_ordered and gi != world.next_gem_index:
                return pos, facing, RunResult.ORDER_ERROR
            world.collected_gems.add(gi)
            if world.gems_ordered:
                world.next_gem_index += 1
            return pos, facing, RunResult.COLLECT
        key = world.key_at(pos)
        if key is not None:
            world.collected_keys.add(key["color"])
            return pos, facing, RunResult.COLLECT
        return pos, facing, RunResult.OK
    # fwd
    dx, dy = DIR_VECT[facing]
    new_pos = (pos[0] + dx, pos[1] + dy)
    if not world.is_free(new_pos):
        return pos, facing, RunResult.CRASH
    if new_pos == world.goal and world.gems_satisfied():
        return new_pos, facing, RunResult.WIN
    return new_pos, facing, RunResult.OK
