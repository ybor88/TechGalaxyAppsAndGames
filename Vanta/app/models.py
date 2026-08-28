# Copyright (c) Roberto Di Flumeri
import keyword
from dataclasses import dataclass, field
from enum import Enum


def is_valid_name(name: str) -> bool:
    return name.isidentifier() and not keyword.iskeyword(name)


class VarType(Enum):
    INT = "Numero intero"
    FLOAT = "Numero decimale"
    TEXT = "Testo"
    BOOL = "Vero/Falso"
    LIST = "Lista di numeri"


NUMERIC_TYPES = (VarType.INT, VarType.FLOAT)

VALUE_SOURCE_FIXED = "fisso"
VALUE_SOURCE_USER = "utente"

# op_id -> definizione dell'operazione.
# kind "infix"  -> unisce gli operandi con "symbol" (es. a + b + c)
# kind "call"   -> chiama una funzione python su un solo operando (es. abs(a))
OPERATIONS = {
    "somma": dict(label="Somma (+)", allowed=NUMERIC_TYPES, arity=None, kind="infix", symbol="+"),
    "sottrazione": dict(label="Sottrazione (-)", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol="-"),
    "moltiplicazione": dict(label="Moltiplicazione (×)", allowed=NUMERIC_TYPES, arity=None, kind="infix", symbol="*"),
    "divisione": dict(label="Divisione (÷)", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol="/"),
    "resto": dict(label="Resto della divisione (%)", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol="%"),
    "potenza": dict(label="Elevamento a potenza (^)", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol="**"),
    "concatena": dict(label="Unisci testo", allowed=(VarType.TEXT,), arity=None, kind="infix", symbol="+"),
    "maggiore": dict(label="Confronta: maggiore >", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol=">"),
    "minore": dict(label="Confronta: minore <", allowed=NUMERIC_TYPES, arity=2, kind="infix", symbol="<"),
    "uguale": dict(label="Confronta: uguale ==", allowed=None, arity=2, kind="infix", symbol="=="),
    "radice": dict(label="Radice quadrata", allowed=NUMERIC_TYPES, arity=1, kind="call",
                    call="math.sqrt", needs_import="math"),
    "valore_assoluto": dict(label="Valore assoluto", allowed=NUMERIC_TYPES, arity=1, kind="call", call="abs"),
    "lista_somma": dict(label="Somma degli elementi della lista", allowed=(VarType.LIST,), arity=1,
                         kind="call", call="sum"),
    "lista_media": dict(label="Media della lista", allowed=(VarType.LIST,), arity=1, kind="call",
                         call="statistics.mean", needs_import="statistics"),
    "lista_max": dict(label="Il valore più grande della lista", allowed=(VarType.LIST,), arity=1,
                       kind="call", call="max"),
    "lista_min": dict(label="Il valore più piccolo della lista", allowed=(VarType.LIST,), arity=1,
                       kind="call", call="min"),
    "lista_lunghezza": dict(label="Quanti elementi ha la lista", allowed=(VarType.LIST,), arity=1,
                             kind="call", call="len"),
    "lista_ordina": dict(label="Ordina la lista", allowed=(VarType.LIST,), arity=1, kind="call", call="sorted"),
}


def parse_literal(raw: str, vtype: VarType):
    """Converte il testo inserito dall'utente nel tipo scelto. Solleva ValueError se non valido."""
    if vtype is VarType.INT:
        return int(raw.strip())
    if vtype is VarType.FLOAT:
        return float(raw.strip().replace(",", "."))
    if vtype is VarType.LIST:
        return parse_list_literal(raw)
    return raw


def parse_list_literal(raw: str):
    """Converte 'es. 3, 7.5, 2' in [3, 7.5, 2]. Solleva ValueError se non valido."""
    parts = [p.strip() for p in raw.split(",") if p.strip() != ""]
    if not parts:
        raise ValueError("lista vuota")
    values = []
    for part in parts:
        cleaned = part.replace(",", ".")
        if "." in cleaned:
            values.append(float(cleaned))
        else:
            values.append(int(cleaned))
    return values


@dataclass
class Variable:
    name: str
    type: VarType
    value: object  # gia' convertito (int/float/str/list), None se source e' 'utente'
    source: str = VALUE_SOURCE_FIXED


@dataclass
class Operation:
    op_id: str
    operand_names: list
    result_name: str
    result_type: VarType


@dataclass
class Program:
    variables: list = field(default_factory=list)
    operations: list = field(default_factory=list)

    def variable_names(self):
        return [v.name for v in self.variables]

    def get_variable(self, name):
        for v in self.variables:
            if v.name == name:
                return v
        return None

    def all_names(self):
        names = self.variable_names()
        names += [op.result_name for op in self.operations]
        return names

    def types_map(self):
        mapping = {v.name: v.type for v in self.variables}
        for op in self.operations:
            mapping[op.result_name] = op.result_type
        return mapping
