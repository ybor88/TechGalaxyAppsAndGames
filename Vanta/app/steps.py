# Copyright (c) Roberto Di Flumeri
from PySide6.QtCore import Qt
from PySide6.QtGui import QPixmap
from PySide6.QtWidgets import (
    QLabel, QLineEdit, QHBoxLayout, QPlainTextEdit,
    QCheckBox, QFileDialog, QButtonGroup, QComboBox, QPushButton
)

from .widgets import StepWidget, ChoiceButton
from .models import (
    Variable, Operation, VarType, OPERATIONS, is_valid_name, parse_literal,
    VALUE_SOURCE_FIXED, VALUE_SOURCE_USER,
)
from .codegen import generate_source
from .executor import run_source, launch_interactive


class WelcomeStep(StepWidget):
    def __init__(self, controller):
        super().__init__(controller)

        logo = QLabel()
        pixmap = QPixmap(str(controller.logo_path))
        if not pixmap.isNull():
            logo.setPixmap(pixmap.scaledToWidth(140, Qt.SmoothTransformation))
        logo.setAlignment(Qt.AlignCenter)
        self.card_layout.addWidget(logo)

        title = QLabel("VANTA")
        title.setObjectName("title")
        title.setAlignment(Qt.AlignCenter)
        self.card_layout.addWidget(title)

        tagline = QLabel("Chiedi. V Crea. Tu Realizzi.")
        tagline.setObjectName("subtitle")
        tagline.setAlignment(Qt.AlignCenter)
        self.card_layout.addWidget(tagline)

        self.card_layout.addSpacing(12)
        desc = QLabel(
            "Crea un programma rispondendo a qualche domanda: "
            "quali dati usare, cosa farci e Vanta scrive il codice per te."
        )
        desc.setObjectName("subtitle")
        desc.setAlignment(Qt.AlignCenter)
        desc.setWordWrap(True)
        self.card_layout.addWidget(desc)
        self.card_layout.addSpacing(12)

        self.add_nav(on_next=controller.show_output_mode, next_label="Inizia un nuovo programma")

        copyright_label = QLabel("© Roberto Di Flumeri")
        copyright_label.setObjectName("subtitle")
        copyright_label.setAlignment(Qt.AlignCenter)
        self.card_layout.addWidget(copyright_label)


class OutputModeStep(StepWidget):
    def __init__(self, controller):
        super().__init__(controller)
        self.selected_mode = None

        self.add_progress("Passo 1")
        self.add_question("Come vuoi usare il programma che creerai?")
        self.add_subtitle(
            "Puoi scegliere se il programma funziona a righe di testo nel terminale, "
            "oppure con una finestra e dei pulsanti."
        )

        self.group = QButtonGroup(self)
        self.group.setExclusive(True)
        options = [
            ("console", "Da terminale, a righe di testo"),
            ("gui", "Con una finestra grafica (caselle e pulsanti)"),
        ]
        for mode_id, label in options:
            btn = ChoiceButton(label)
            btn.clicked.connect(lambda checked, m=mode_id: self._select(m))
            self.group.addButton(btn)
            self.card_layout.addWidget(btn)

        self.add_error()
        self.add_nav(on_back=controller.show_welcome, on_next=self._next, next_enabled=False)

    def _select(self, mode_id):
        self.selected_mode = mode_id
        self.next_btn.setEnabled(True)
        self.show_error("")

    def _next(self):
        if self.selected_mode is None:
            self.show_error("Scegli come funzionerà il programma.")
            return
        self.controller.output_mode = self.selected_mode
        self.controller.show_variable_count()


class VariableCountStep(StepWidget):
    def __init__(self, controller):
        super().__init__(controller)
        self.add_progress("Passo 2")
        self.add_question("Quante variabili vuoi creare?")
        self.add_subtitle("Le variabili sono i dati di partenza del tuo programma.")

        self.selected_count = None
        row = QHBoxLayout()
        self.group = QButtonGroup(self)
        self.group.setExclusive(True)
        for n in range(2, 7):
            btn = ChoiceButton(str(n))
            btn.clicked.connect(lambda checked, n=n: self._select(n))
            self.group.addButton(btn)
            row.addWidget(btn)
        self.card_layout.addLayout(row)

        self.add_error()
        self.add_nav(
            on_back=controller.show_output_mode,
            on_next=self._next,
            next_enabled=False,
        )

    def _select(self, n):
        self.selected_count = n
        self.next_btn.setEnabled(True)
        self.show_error("")

    def _next(self):
        if self.selected_count is None:
            self.show_error("Seleziona un numero per continuare.")
            return
        self.controller.start_variable_definitions(self.selected_count)


class VariableDetailStep(StepWidget):
    TYPE_PLACEHOLDERS = {
        VarType.INT: "es. 42",
        VarType.FLOAT: "es. 3.14",
        VarType.TEXT: "es. Mario",
        VarType.LIST: "es. 3, 7, 2, 9",
    }

    def __init__(self, controller, index, total):
        super().__init__(controller)
        self.index = index
        self.total = total
        self.selected_type = None
        self.selected_source = None

        self.add_progress(f"Passo 3 · Variabile {index + 1} di {total}")
        self.add_question("Come si chiama questa variabile e che dato contiene?")

        self.card_layout.addWidget(QLabel("Nome della variabile"))
        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("es. eta, nome, prezzo")
        self.card_layout.addWidget(self.name_edit)

        self.card_layout.addWidget(QLabel("Che tipo di dato è?"))
        type_row = QHBoxLayout()
        self.type_group = QButtonGroup(self)
        self.type_group.setExclusive(True)
        for vtype in (VarType.INT, VarType.FLOAT, VarType.TEXT, VarType.LIST):
            btn = ChoiceButton(vtype.value)
            btn.clicked.connect(lambda checked, t=vtype: self._select_type(t))
            self.type_group.addButton(btn)
            type_row.addWidget(btn)
        self.card_layout.addLayout(type_row)

        self.card_layout.addWidget(QLabel("Da dove arriva il valore?"))
        source_row = QHBoxLayout()
        self.source_group = QButtonGroup(self)
        self.source_group.setExclusive(True)
        fixed_btn = ChoiceButton("Lo scrivo io adesso")
        fixed_btn.clicked.connect(lambda checked: self._select_source(VALUE_SOURCE_FIXED))
        user_btn = ChoiceButton("Lo chiederà a chi userà il programma")
        user_btn.clicked.connect(lambda checked: self._select_source(VALUE_SOURCE_USER))
        self.source_group.addButton(fixed_btn)
        self.source_group.addButton(user_btn)
        source_row.addWidget(fixed_btn)
        source_row.addWidget(user_btn)
        self.card_layout.addLayout(source_row)

        self.value_label = QLabel("Che valore mettiamo?")
        self.card_layout.addWidget(self.value_label)
        self.value_edit = QLineEdit()
        self.value_edit.setPlaceholderText("scegli prima il tipo di dato")
        self.card_layout.addWidget(self.value_edit)

        self.add_error()
        self.add_nav(on_back=self._back, on_next=self._next)

    def _select_type(self, vtype):
        self.selected_type = vtype
        self.value_edit.setPlaceholderText(self.TYPE_PLACEHOLDERS[vtype])
        self.show_error("")

    def _select_source(self, source):
        self.selected_source = source
        is_fixed = source == VALUE_SOURCE_FIXED
        self.value_label.setVisible(is_fixed)
        self.value_edit.setVisible(is_fixed)
        if not is_fixed:
            self.value_edit.clear()
        self.show_error("")

    def _back(self):
        if self.index == 0:
            self.controller.show_variable_count()
        else:
            self.controller.show_variable_detail(self.index - 1, self.total)

    def _next(self):
        name = self.name_edit.text().strip()
        if not name:
            self.show_error("Dai un nome alla variabile.")
            return
        if not is_valid_name(name):
            self.show_error("Usa solo lettere, numeri e underscore, senza iniziare con un numero.")
            return
        if name in self.controller.program.all_names():
            self.show_error("Questo nome è già usato, scegline un altro.")
            return
        if self.selected_type is None:
            self.show_error("Scegli il tipo di dato.")
            return
        if self.selected_source is None:
            self.show_error("Indica da dove arriva il valore.")
            return

        if self.selected_source == VALUE_SOURCE_USER:
            value = None
        else:
            raw_value = self.value_edit.text()
            try:
                value = parse_literal(raw_value, self.selected_type)
            except ValueError:
                self.show_error("Il valore inserito non corrisponde al tipo di dato scelto.")
                return

        self.controller.program.variables.append(
            Variable(name, self.selected_type, value, self.selected_source)
        )

        if self.index + 1 < self.total:
            self.controller.show_variable_detail(self.index + 1, self.total)
        else:
            self.controller.show_operation_choice()


class OperationChoiceStep(StepWidget):
    def __init__(self, controller):
        super().__init__(controller)
        self.selected_op = None
        types_map = controller.program.types_map()

        self.add_progress("Passo 4")
        self.add_question("Cosa vuoi fare con questi dati?")

        available_ops = [
            op_id for op_id, spec in OPERATIONS.items()
            if self._is_available(types_map, spec["allowed"], spec["arity"])
        ]

        if not available_ops:
            self.add_subtitle(
                "Non ci sono abbastanza dati compatibili per un'altra operazione. "
                "Crea un nuovo programma con più variabili per continuare."
            )
            self.add_nav(on_back=controller.show_result, on_next=None)
            return

        self.group = QButtonGroup(self)
        self.group.setExclusive(True)
        for op_id in available_ops:
            label = OPERATIONS[op_id]["label"]
            btn = ChoiceButton(label)
            btn.clicked.connect(lambda checked, o=op_id: self._select(o))
            self.group.addButton(btn)
            self.card_layout.addWidget(btn)

        if controller.program.operations:
            on_back = controller.show_result
        else:
            total = controller.variable_total
            on_back = lambda: controller.show_variable_detail(total - 1, total)

        self.add_error()
        self.add_nav(
            on_back=on_back,
            on_next=self._next,
            next_enabled=False,
        )

    @staticmethod
    def _is_available(types_map, allowed, arity):
        names = [n for n, t in types_map.items() if allowed is None or t in allowed]
        minimum = arity if arity is not None else 2
        return len(names) >= minimum

    def _select(self, op_id):
        self.selected_op = op_id
        self.next_btn.setEnabled(True)
        self.show_error("")

    def _next(self):
        if self.selected_op is None:
            self.show_error("Seleziona un'operazione per continuare.")
            return
        self.controller.show_operand_choice(self.selected_op)


class OperandChoiceStep(StepWidget):
    def __init__(self, controller, op_id):
        super().__init__(controller)
        self.op_id = op_id
        spec = OPERATIONS[op_id]
        label = spec["label"]
        allowed = spec["allowed"]
        self.arity = spec["arity"]
        types_map = controller.program.types_map()
        self.names = [n for n, t in types_map.items() if allowed is None or t in allowed]

        self.add_progress("Passo 5")
        self.add_question(f"Con quali dati vuoi fare: {label}?")

        self.checkboxes = []
        self.combo_first = None
        self.combo_second = None
        self.combo_single = None

        if self.arity == 1:
            self.card_layout.addWidget(QLabel("Su quale dato vuoi operare?"))
            self.combo_single = QComboBox()
            self.combo_single.addItems(self.names)
            self.card_layout.addWidget(self.combo_single)
        elif self.arity == 2:
            self.card_layout.addWidget(QLabel("Primo valore"))
            self.combo_first = QComboBox()
            self.combo_first.addItems(self.names)
            self.card_layout.addWidget(self.combo_first)

            self.card_layout.addWidget(QLabel("Secondo valore"))
            self.combo_second = QComboBox()
            self.combo_second.addItems(self.names)
            if len(self.names) > 1:
                self.combo_second.setCurrentIndex(1)
            self.card_layout.addWidget(self.combo_second)
        else:
            self.add_subtitle("Seleziona almeno due dati da combinare insieme.")
            for name in self.names:
                cb = QCheckBox(name)
                self.checkboxes.append(cb)
                self.card_layout.addWidget(cb)

        self.add_error()
        self.add_nav(on_back=controller.show_operation_choice, on_next=self._next)

    def _next(self):
        if self.arity == 1:
            chosen = self.combo_single.currentText()
            if not chosen:
                self.show_error("Seleziona un dato.")
                return
            operands = [chosen]
        elif self.arity == 2:
            first = self.combo_first.currentText()
            second = self.combo_second.currentText()
            if not first or not second:
                self.show_error("Seleziona entrambi i valori.")
                return
            if first == second:
                self.show_error("Scegli due dati diversi.")
                return
            operands = [first, second]
        else:
            operands = [cb.text() for cb in self.checkboxes if cb.isChecked()]
            if len(operands) < 2:
                self.show_error("Seleziona almeno due dati.")
                return

        self.controller.show_result_name(self.op_id, operands)


class ResultNameStep(StepWidget):
    def __init__(self, controller, op_id, operand_names):
        super().__init__(controller)
        self.op_id = op_id
        self.operand_names = operand_names

        self.add_progress("Passo 6")
        self.add_question("Come vuoi chiamare il risultato?")
        self.add_subtitle(f"{OPERATIONS[op_id]['label']} · {', '.join(operand_names)}")

        self.name_edit = QLineEdit()
        self.name_edit.setPlaceholderText("es. totale, media, risultato")
        self.card_layout.addWidget(self.name_edit)

        self.add_error()
        self.add_nav(
            on_back=lambda: controller.show_operand_choice(op_id),
            on_next=self._next,
        )

    def _result_type(self):
        if self.op_id in ("maggiore", "minore", "uguale"):
            return VarType.BOOL
        if self.op_id == "concatena":
            return VarType.TEXT
        if self.op_id in ("divisione", "radice", "lista_media"):
            return VarType.FLOAT
        if self.op_id == "lista_lunghezza":
            return VarType.INT
        if self.op_id == "lista_ordina":
            return VarType.LIST
        if self.op_id in ("lista_somma", "lista_max", "lista_min"):
            var = self.controller.program.get_variable(self.operand_names[0])
            if var and isinstance(var.value, list) and var.value:
                return VarType.FLOAT if any(isinstance(x, float) for x in var.value) else VarType.INT
            return VarType.FLOAT
        if self.op_id == "valore_assoluto":
            return self.controller.program.types_map()[self.operand_names[0]]

        types_map = self.controller.program.types_map()
        operand_types = [types_map[n] for n in self.operand_names]
        return VarType.FLOAT if VarType.FLOAT in operand_types else VarType.INT

    def _next(self):
        name = self.name_edit.text().strip()
        if not name:
            self.show_error("Dai un nome al risultato.")
            return
        if not is_valid_name(name):
            self.show_error("Usa solo lettere, numeri e underscore, senza iniziare con un numero.")
            return
        if name in self.controller.program.all_names():
            self.show_error("Questo nome è già usato, scegline un altro.")
            return

        operation = Operation(self.op_id, self.operand_names, name, self._result_type())
        self.controller.program.operations.append(operation)
        self.controller.show_result()


class ResultStep(StepWidget):
    def __init__(self, controller):
        super().__init__(controller)
        self.card.setMaximumWidth(760)

        self.add_progress("Programma pronto")
        self.add_question("Ecco il codice generato da Vanta")

        source = generate_source(controller.program, controller.output_mode)
        self.code_view = QPlainTextEdit(source)
        self.code_view.setObjectName("code")
        self.code_view.setReadOnly(True)
        self.code_view.setMinimumHeight(180)
        self.card_layout.addWidget(self.code_view)

        exec_row = QHBoxLayout()
        self.run_btn = QPushButton("▶ Esegui il programma")
        self.run_btn.clicked.connect(self._run)
        exec_row.addWidget(self.run_btn)

        self.save_btn = QPushButton("Salva codice (.py)")
        self.save_btn.setObjectName("secondary")
        self.save_btn.clicked.connect(self._save)
        exec_row.addWidget(self.save_btn)
        self.card_layout.addLayout(exec_row)

        self.output_view = QPlainTextEdit("")
        self.output_view.setObjectName("code")
        self.output_view.setReadOnly(True)
        self.output_view.setMaximumHeight(120)
        self.output_view.setVisible(False)
        self.card_layout.addWidget(self.output_view)

        self.add_nav(
            on_back=None,
            on_next=controller.show_operation_choice,
            next_label="Aggiungi un'altra operazione",
        )

        extra_row = QHBoxLayout()
        new_btn = QPushButton("Nuovo programma")
        new_btn.setObjectName("secondary")
        new_btn.clicked.connect(controller.reset)
        extra_row.addWidget(new_btn)
        extra_row.addStretch(1)
        self.card_layout.addLayout(extra_row)

        copyright_label = QLabel("© Roberto Di Flumeri")
        copyright_label.setObjectName("subtitle")
        copyright_label.setAlignment(Qt.AlignCenter)
        self.card_layout.addWidget(copyright_label)

    def _run(self):
        program = self.controller.program
        mode = self.controller.output_mode
        source = generate_source(program, mode)
        self.output_view.setVisible(True)

        if mode == "gui":
            launch_interactive(source, new_console=False)
            self.output_view.setPlainText("La finestra del programma si è aperta separatamente.")
            return

        has_user_input = any(v.source == VALUE_SOURCE_USER for v in program.variables)
        if has_user_input:
            launch_interactive(source, new_console=True)
            self.output_view.setPlainText("Il programma è stato avviato in una nuova finestra del terminale.")
            return

        result = run_source(source)
        if result.success:
            self.output_view.setPlainText(result.output or "(nessun output)")
        else:
            self.output_view.setPlainText("Errore:\n" + result.error)

    def _save(self):
        path, _ = QFileDialog.getSaveFileName(self, "Salva programma", "programma.py", "Python (*.py)")
        if path:
            source = generate_source(self.controller.program, self.controller.output_mode)
            with open(path, "w", encoding="utf-8") as f:
                f.write(source)
